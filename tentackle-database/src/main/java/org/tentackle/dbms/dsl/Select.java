/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.dbms.dsl;

import org.tentackle.dbms.Db;
import org.tentackle.dbms.Query;
import org.tentackle.dbms.ResultSetWrapper;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.sql.JoinType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code SELECT}-statement.
 * <p>
 * Created by {@link DSL#select} and refined by the clause methods, each returning this select:
 * <pre>
 *   List&lt;Turnover&gt; turnovers =
 *       DSL.select(ORDER.CUSTOMER, DSL.sum(ORDER.NET).as("turnover"))
 *          .from(ORDER)
 *          .where(ORDER.ORDERED.ge(firstOfYear))
 *          .groupBy(ORDER.CUSTOMER)
 *          .orderBy(ORDER.CUSTOMER.asc())
 *          .toList(db, Turnover.class);
 * </pre>
 * The clauses may be added in any order and more than once. Conditions passed to
 * {@link #where} and {@link #having} are combined by a logical AND, which is what
 * queries assembled step by step usually need:
 * <pre>
 *   Select select = DSL.select().from(CUSTOMER);
 *   if (name != null) {
 *     select.where(CUSTOMER.NAME.like(name + "%"));
 *   }
 * </pre>
 * A select is a mutable builder and thus not thread-safe. The expressions and
 * conditions it is built from, however, are immutable and may be shared.
 * <p>
 * Selects deliver plain data, not PDOs, and there is no security manager involved.
 * Use them for projections, aggregates and reports, not to load domain objects.
 *
 * @author harald
 */
public class Select implements SqlElement {

  private static final String SQL_DISTINCT = "DISTINCT ";
  private static final String SQL_HAVING = " HAVING ";
  private static final String SQL_AS = " AS ";

  private final boolean distinct;                       // true for SELECT DISTINCT
  private final List<Expression<?>> projection;         // the selected expressions, empty for *
  private final List<Table> tables;                     // the tables of the FROM-clause
  private final List<Join> joins;                       // the joined tables
  private final List<Condition> whereConditions;        // the conditions of the WHERE-clause
  private final List<Expression<?>> groupByExpressions; // the expressions of the GROUP BY-clause
  private final List<Condition> havingConditions;       // the conditions of the HAVING-clause
  private final List<SortField> sortFields;             // the expressions of the ORDER BY-clause

  private int limit;                                    // max. number of rows, 0 if unlimited
  private int offset;                                   // number of rows to skip, 0 if none
  private int fetchSize;                                // the fetch size, 0 for the driver's default
  private int maxRows;                                  // max. number of rows to fetch, 0 if unlimited
  private boolean statementCached;                      // true to cache the prepared statement


  /**
   * Creates a select.
   *
   * @param distinct true for {@code SELECT DISTINCT}
   * @param projection the selected expressions, empty to select all columns
   */
  public Select(boolean distinct, Collection<? extends Expression<?>> projection) {
    this.distinct = distinct;
    this.projection = new ArrayList<>(projection);
    this.tables = new ArrayList<>();
    this.joins = new ArrayList<>();
    this.whereConditions = new ArrayList<>();
    this.groupByExpressions = new ArrayList<>();
    this.havingConditions = new ArrayList<>();
    this.sortFields = new ArrayList<>();
  }


  // ---------------------- building ---------------------------

  /**
   * Adds tables to the {@code FROM}-clause.
   *
   * @param tables the tables
   * @return this select
   */
  public Select from(Table... tables) {
    Collections.addAll(this.tables, tables);
    return this;
  }

  /**
   * Adds an inner join.
   *
   * @param table the joined table
   * @return the step expecting the join condition
   */
  public JoinStep join(Table table) {
    return join(JoinType.INNER, table);
  }

  /**
   * Adds a left outer join.
   *
   * @param table the joined table
   * @return the step expecting the join condition
   */
  public JoinStep leftJoin(Table table) {
    return join(JoinType.LEFT, table);
  }

  /**
   * Adds a right outer join.
   *
   * @param table the joined table
   * @return the step expecting the join condition
   */
  public JoinStep rightJoin(Table table) {
    return join(JoinType.RIGHT, table);
  }

  /**
   * Adds a full outer join.
   *
   * @param table the joined table
   * @return the step expecting the join condition
   */
  public JoinStep fullJoin(Table table) {
    return join(JoinType.FULL, table);
  }

  /**
   * Adds a join.
   *
   * @param joinType the kind of join
   * @param table the joined table
   * @return the step expecting the join condition
   */
  public JoinStep join(JoinType joinType, Table table) {
    return new JoinStep(this, joinType, table);
  }

  /**
   * Adds conditions to the {@code WHERE}-clause.<br>
   * All conditions of a select are combined by a logical AND.
   *
   * @param conditions the conditions
   * @return this select
   */
  public Select where(Condition... conditions) {
    Collections.addAll(whereConditions, conditions);
    return this;
  }

  /**
   * Adds expressions to the {@code GROUP BY}-clause.
   *
   * @param expressions the expressions
   * @return this select
   */
  public Select groupBy(Expression<?>... expressions) {
    Collections.addAll(groupByExpressions, expressions);
    return this;
  }

  /**
   * Adds conditions to the {@code HAVING}-clause.<br>
   * All conditions of a select are combined by a logical AND.
   *
   * @param conditions the conditions
   * @return this select
   */
  public Select having(Condition... conditions) {
    Collections.addAll(havingConditions, conditions);
    return this;
  }

  /**
   * Adds sort fields to the {@code ORDER BY}-clause.
   *
   * @param sortFields the sort fields
   * @return this select
   */
  public Select orderBy(SortField... sortFields) {
    Collections.addAll(this.sortFields, sortFields);
    return this;
  }

  /**
   * Adds ascending expressions to the {@code ORDER BY}-clause.
   *
   * @param expressions the expressions
   * @return this select
   */
  public Select orderBy(Expression<?>... expressions) {
    for (Expression<?> expression : expressions) {
      sortFields.add(expression.asc());
    }
    return this;
  }

  /**
   * Sets the maximum number of rows to retrieve.<br>
   * The {@code LIMIT}-clause is backend-specific and thus added by the {@link Backend}.
   * It applies to the executed statement only, i.e. a select used as a sub-select
   * must not define a limit.
   *
   * @param limit the maximum number of rows, 0 if unlimited (default)
   * @return this select
   * @see Query#setLimit(int)
   */
  public Select limit(int limit) {
    this.limit = limit;
    return this;
  }

  /**
   * Sets the number of rows to skip.<br>
   * The {@code OFFSET}-clause is backend-specific and thus added by the {@link Backend}.
   * It applies to the executed statement only, i.e. a select used as a sub-select
   * must not define an offset.
   *
   * @param offset the number of rows to skip, 0 if none (default)
   * @return this select
   * @see Query#setOffset(int)
   */
  public Select offset(int offset) {
    this.offset = offset;
    return this;
  }

  /**
   * Sets the fetch size.
   *
   * @param fetchSize the fetch size, 0 for the driver's default
   * @return this select
   * @see Query#setFetchSize(int)
   */
  public Select fetchSize(int fetchSize) {
    this.fetchSize = fetchSize;
    return this;
  }

  /**
   * Sets the maximum number of rows fetched in total.
   *
   * @param maxRows the maximum number of rows, 0 if unlimited
   * @return this select
   * @see Query#setMaxRows(int)
   */
  public Select maxRows(int maxRows) {
    this.maxRows = maxRows;
    return this;
  }

  /**
   * Enables caching of the prepared statement.
   *
   * @param statementCached true to prepare only once and reuse the cached statement, false if one-shot (default)
   * @return this select
   * @see Query#setStatementCached(boolean)
   */
  public Select statementCached(boolean statementCached) {
    this.statementCached = statementCached;
    return this;
  }


  // ---------------------- rendering ---------------------------

  /**
   * Renders this select as a sub-select.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public void render(RenderContext context) {
    if (limit != 0 || offset != 0) {
      // the LIMIT/OFFSET-clauses are backend-specific and applied to the executed statement only
      throw new PersistenceException("limit and offset are not applicable to a sub-select");
    }
    renderSelect(context);
  }

  /**
   * Creates the SQL-code of this select.<br>
   * Notice that the {@code LIMIT}- and {@code OFFSET}-clauses are added by the
   * backend when the query is executed and thus are not part of the returned code.
   *
   * @param backend the backend
   * @return the SQL-code
   */
  public String toSql(Backend backend) {
    RenderContext context = new RenderContext(backend);
    renderSelect(context);
    return context.getSql();
  }

  /**
   * Creates the query for this select.
   *
   * @param backend the backend
   * @return the query
   */
  public Query toQuery(Backend backend) {
    RenderContext context = new RenderContext(backend);
    renderSelect(context);
    Query query = new Query();
    query.add(context.getSql(), context.getParameters());
    query.setLimit(limit);
    query.setOffset(offset);
    query.setFetchSize(fetchSize);
    query.setMaxRows(maxRows);
    query.setStatementCached(statementCached);
    return query;
  }

  /**
   * Creates the query for this select.
   *
   * @param db the session
   * @return the query
   */
  public Query toQuery(Db db) {
    return toQuery(db.getBackend());
  }


  // ---------------------- execution ---------------------------

  /**
   * Executes this select.<br>
   * The application is responsible for closing the returned resultset.
   *
   * @param db the session
   * @return the resultset
   */
  public ResultSetWrapper execute(Db db) {
    return toQuery(db).execute(db);
  }

  /**
   * Executes this select and maps the rows to DTOs.
   *
   * @param db the session
   * @param dtoClass the DTO class
   * @param <T> the DTO type
   * @return the list of DTOs
   * @see org.tentackle.dbms.DbUtilities#resultSetToList(ResultSetWrapper, Class)
   */
  public <T> List<T> toList(Db db, Class<T> dtoClass) {
    try (ResultSetWrapper rs = execute(db)) {
      return rs.toList(dtoClass);
    }
  }

  /**
   * Executes this select and maps at most one row to a DTO.
   *
   * @param db the session
   * @param dtoClass the DTO class
   * @param <T> the DTO type
   * @return the optional DTO, empty if no row at all
   * @throws PersistenceException if more than one row was returned
   */
  public <T> Optional<T> toOptional(Db db, Class<T> dtoClass) {
    List<T> dtos = toList(db, dtoClass);
    if (dtos.size() > 1) {
      throw new PersistenceException("more than one row returned by " + this);
    }
    return dtos.isEmpty() ? Optional.empty() : Optional.of(dtos.getFirst());
  }

  /**
   * Executes this select and retrieves the value of the first expression of the projection
   * for each row.
   *
   * @param db the session
   * @param type the java type of the value
   * @param <T> the value type
   * @return the list of values
   */
  public <T> List<T> toScalarList(Db db, Class<T> type) {
    DataType<T> dataType = getScalarDataType(type);
    int[] positions = createScalarPositions(db.getBackend(), dataType);
    List<T> values = new ArrayList<>();
    try (ResultSetWrapper rs = execute(db)) {
      while (rs.next()) {
        values.add(rs.get(dataType, positions, false, null));
      }
    }
    return values;
  }

  /**
   * Executes this select and retrieves the value of the first expression of the projection
   * of at most one row.
   *
   * @param db the session
   * @param type the java type of the value
   * @param <T> the value type
   * @return the optional value, empty if no row at all or if the value is null
   * @throws PersistenceException if more than one row was returned
   */
  public <T> Optional<T> toScalar(Db db, Class<T> type) {
    DataType<T> dataType = getScalarDataType(type);
    int[] positions = createScalarPositions(db.getBackend(), dataType);
    T value = null;
    try (ResultSetWrapper rs = execute(db)) {
      if (rs.next()) {
        value = rs.get(dataType, positions, false, null);
        if (rs.next()) {
          throw new PersistenceException("more than one row returned by " + this);
        }
      }
    }
    return Optional.ofNullable(value);
  }

  /**
   * Gets the number of rows returned by this select.<br>
   * The rows are counted by the backend, i.e. they are not retrieved.
   *
   * @param db the session
   * @return the number of rows
   * @see Query#getRowCount(Db)
   */
  public int getRowCount(Db db) {
    return toQuery(db).getRowCount(db);
  }


  @Override
  public String toString() {
    return projection + (tables.isEmpty() ? "" : " FROM " + tables);
  }


  /**
   * Adds a join.<br>
   * Invoked by the {@link JoinStep} as soon as the join condition is known.
   *
   * @param join the join
   * @return this select
   */
  Select addJoin(Join join) {
    joins.add(join);
    return this;
  }


  /**
   * Renders this select without the backend-specific limit and offset clauses.
   *
   * @param context the render context
   */
  private void renderSelect(RenderContext context) {
    context.append(Backend.SQL_SELECT);
    if (distinct) {
      context.append(SQL_DISTINCT);
    }
    renderProjection(context);

    if (!tables.isEmpty()) {
      context.append(Backend.SQL_FROM);
      boolean needComma = false;
      for (Table table : tables) {
        if (needComma) {
          context.appendComma();
        }
        else {
          needComma = true;
        }
        table.render(context);
      }
    }
    else if (!joins.isEmpty()) {
      throw new PersistenceException("missing FROM-clause for joined tables");
    }

    for (Join join : joins) {
      join.render(context);
    }

    if (!whereConditions.isEmpty()) {
      context.append(Backend.SQL_WHERE);
      renderConditions(context, whereConditions);
    }

    if (!groupByExpressions.isEmpty()) {
      context.append(Backend.SQL_GROUPBY);
      boolean needComma = false;
      for (Expression<?> expression : groupByExpressions) {
        if (needComma) {
          context.appendComma();
        }
        else {
          needComma = true;
        }
        expression.render(context);
      }
    }

    if (!havingConditions.isEmpty()) {
      context.append(SQL_HAVING);
      renderConditions(context, havingConditions);
    }

    if (!sortFields.isEmpty()) {
      context.append(Backend.SQL_ORDERBY);
      boolean needComma = false;
      for (SortField sortField : sortFields) {
        if (needComma) {
          context.appendComma();
        }
        else {
          needComma = true;
        }
        sortField.render(context);
      }
    }
  }

  /**
   * Renders the projection.<br>
   * Expressions spanning more than one column are rendered column by column,
   * with the alias suffixed just like a column name, so that the labels in the
   * resultset match the members of the DTO.
   *
   * @param context the render context
   */
  private void renderProjection(RenderContext context) {
    if (projection.isEmpty()) {
      context.append(Backend.SQL_ALLSTAR);
      return;
    }

    Backend backend = context.getBackend();
    boolean needComma = false;

    for (Expression<?> expression : projection) {
      String alias = expression.getAlias();
      DataType<?> dataType = expression.getDataType();
      int columnCount = expression.getColumnCount(backend);

      for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
        if (needComma) {
          context.appendComma();
        }
        else {
          needComma = true;
        }
        expression.render(context, columnIndex);
        if (alias != null) {
          context.append(SQL_AS).append(
              dataType == null ? alias : alias + dataType.getColumnSuffix(backend, columnIndex).orElse(""));
        }
      }
    }
  }

  /**
   * Renders conditions combined by a logical AND.
   *
   * @param context the render context
   * @param conditions the conditions
   */
  private void renderConditions(RenderContext context, List<Condition> conditions) {
    boolean needJunction = false;
    for (Condition condition : conditions) {
      if (needJunction) {
        context.append(Junction.AND.getSql());
      }
      else {
        needJunction = true;
      }
      condition.render(context);
    }
  }

  /**
   * Gets the datatype to retrieve a scalar value.
   *
   * @param type the java type of the value
   * @param <T> the value type
   * @return the datatype
   */
  private <T> DataType<T> getScalarDataType(Class<T> type) {
    DataType<T> dataType = DataTypeFactory.getInstance().get(
        Objects.requireNonNull(type, "type must be provided"));
    if (dataType == null) {
      throw new PersistenceException("no datatype registered for " + type.getName());
    }
    return dataType;
  }

  /**
   * Creates the resultset positions of the leading columns.
   *
   * @param backend the backend
   * @param dataType the datatype
   * @return the positions, starting at 1
   */
  private int[] createScalarPositions(Backend backend, DataType<?> dataType) {
    int[] positions = new int[dataType.getColumnCount(backend)];
    for (int index = 0; index < positions.length; index++) {
      positions[index] = index + 1;
    }
    return positions;
  }

}

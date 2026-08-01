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

import org.tentackle.dbms.SqlSupplier;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The entry point of the query DSL.
 * <p>
 * Applications usually import the methods statically:
 * <pre>
 *   import static org.tentackle.dbms.dsl.DSL.*;
 * </pre>
 * and declare the tables and columns they work with once:
 * <pre>
 *   Table          CUSTOMER = table("customer");
 *   Field&lt;Long&gt;    ID       = CUSTOMER.field("id", Long.class);
 *   Field&lt;String&gt;  NAME     = CUSTOMER.field("name", String.class);
 *   Field&lt;BMoney&gt;  TURNOVER = CUSTOMER.field("turnover", BMoney.class);
 * </pre>
 * If the table is a regular entity of the application's model, the
 * DSL-wurblet is a better choice since it automatically keeps the
 * generated constants in sync with the model:
 * <pre>
 *   // &#64;wurblet dsl DSL Customer id name turnover
 * </pre>
 * The queries are then built and executed as follows:
 * <pre>
 *   List&lt;CustomerDTO&gt; customers =
 *       select(ID, NAME, TURNOVER)
 *          .from(CUSTOMER)
 *          .where(NAME.like("Miller%"), TURNOVER.gt(new BMoney(1000, 2)))
 *          .orderBy(NAME.asc())
 *          .toList(db, CustomerDTO.class);
 * </pre>
 * All values are applied as parameters of the prepared statement, never as SQL-literals.
 * Table-, column- and alias names are verified syntactically, since they end up in
 * the SQL-code verbatim.
 *
 * @author harald
 * @see Select
 */
public final class DSL {

  /** the COUNT aggregate. */
  private static final String SQL_COUNT = "COUNT";

  /** the SUM aggregate. */
  private static final String SQL_SUM = "SUM";

  /** the AVG aggregate. */
  private static final String SQL_AVG = "AVG";

  /** the UPPER function. */
  private static final String SQL_UPPER = "UPPER";

  /** the LOWER function. */
  private static final String SQL_LOWER = "LOWER";

  /** the ABS function. */
  private static final String SQL_ABS = "ABS";


  // ---------------------- statements ---------------------------

  /**
   * Creates a select.
   *
   * @param projection the selected expressions, none to select all columns
   * @return the select
   */
  public static Select select(Expression<?>... projection) {
    return new Select(false, Arrays.asList(projection));
  }

  /**
   * Creates a select.
   *
   * @param projection the selected expressions, empty to select all columns
   * @return the select
   */
  public static Select select(Collection<? extends Expression<?>> projection) {
    return new Select(false, projection);
  }

  /**
   * Creates a select returning distinct rows only.
   *
   * @param projection the selected expressions, none to select all columns
   * @return the select
   */
  public static Select selectDistinct(Expression<?>... projection) {
    return new Select(true, Arrays.asList(projection));
  }

  /**
   * Creates a select returning distinct rows only.
   *
   * @param projection the selected expressions, empty to select all columns
   * @return the select
   */
  public static Select selectDistinct(Collection<? extends Expression<?>> projection) {
    return new Select(true, projection);
  }


  // ---------------------- tables and columns ---------------------------

  /**
   * Creates a table.
   *
   * @param name the table name, optionally qualified by a schema name
   * @return the table
   */
  public static Table table(String name) {
    return new Table(name, null);
  }

  /**
   * Creates an aliased table.
   *
   * @param name the table name, optionally qualified by a schema name
   * @param alias the table alias
   * @return the table
   */
  public static Table table(String name, String alias) {
    return new Table(name, alias);
  }

  /**
   * Creates a derived table, i.e. a sub-select in the {@code FROM}-clause.
   *
   * @param select the sub-select
   * @param alias the table alias, mandatory for derived tables
   * @return the table
   */
  public static Table table(Select select, String alias) {
    return new Table(select, alias);
  }

  /**
   * Creates a typed field that is not qualified by a table.
   *
   * @param name the column name
   * @param type the java type of the column
   * @param <T> the java type of the column
   * @return the field
   */
  public static <T> Field<T> field(String name, Class<T> type) {
    return new Field<>(null, name, dataTypeOf(type));
  }

  /**
   * Creates a typed field that is not qualified by a table.
   *
   * @param name the column name
   * @param dataType the datatype of the column
   * @param <T> the java type of the column
   * @return the field
   */
  public static <T> Field<T> field(String name, DataType<T> dataType) {
    return new Field<>(null, name, Objects.requireNonNull(dataType, "datatype must be provided"));
  }

  /**
   * Creates an untyped field that is not qualified by a table.
   *
   * @param name the column name
   * @return the field
   */
  public static Field<Object> field(String name) {
    return new Field<>(null, name, null);
  }

  /**
   * Creates an expression for all columns.
   *
   * @return the expression rendered as {@code *}
   */
  public static Expression<Object> asterisk() {
    return new RawExpression<>(Backend.SQL_ALLSTAR, null);
  }


  // ---------------------- values ---------------------------

  /**
   * Creates a value bound to the prepared statement.
   *
   * @param value the value, never null
   * @param <T> the java type of the value
   * @return the bind value
   */
  public static <T> BindValue<T> value(T value) {
    return value(value, null);
  }

  /**
   * Creates a value bound to the prepared statement.
   *
   * @param value the value, never null
   * @param dataType the datatype, null to determine it from the value's class
   * @param <T> the java type of the value
   * @return the bind value
   */
  @SuppressWarnings("unchecked")
  public static <T> BindValue<T> value(T value, DataType<T> dataType) {
    Objects.requireNonNull(value, "value must be provided, use isNull() or isNotNull() instead");
    return new BindValue<>(value, dataType != null ? dataType
                                                   : (DataType<T>) DataTypeFactory.getInstance().get(value.getClass()));
  }

  /**
   * Creates values bound to the prepared statement.
   *
   * @param values the values, none of them null
   * @param dataType the datatype, null to determine it from the values' classes
   * @param <T> the java type of the values
   * @return the bind values
   */
  public static <T> List<Expression<?>> values(Collection<? extends T> values, DataType<T> dataType) {
    List<Expression<?>> bindValues = new ArrayList<>(values.size());
    for (T value : values) {
      bindValues.add(value(value, dataType));
    }
    return bindValues;
  }


  // ---------------------- conditions ---------------------------

  /**
   * Combines conditions by a logical AND.
   *
   * @param conditions the conditions, none for a condition that is always true
   * @return the combined condition
   */
  public static Condition and(Condition... conditions) {
    return new CombinedCondition(Junction.AND, Arrays.asList(conditions));
  }

  /**
   * Combines conditions by a logical OR.
   *
   * @param conditions the conditions, none for a condition that is never true
   * @return the combined condition
   */
  public static Condition or(Condition... conditions) {
    return new CombinedCondition(Junction.OR, Arrays.asList(conditions));
  }

  /**
   * Negates a condition.
   *
   * @param condition the condition
   * @return the negated condition
   */
  public static Condition not(Condition condition) {
    return condition.not();
  }

  /**
   * Creates a condition testing a sub-select to return at least one row.
   *
   * @param select the sub-select
   * @return the condition
   */
  public static Condition exists(Select select) {
    return new ExistsCondition(select, false);
  }

  /**
   * Creates a condition testing a sub-select to return no rows at all.
   *
   * @param select the sub-select
   * @return the condition
   */
  public static Condition notExists(Select select) {
    return new ExistsCondition(select, true);
  }

  /**
   * Creates a condition from plain SQL-code.
   *
   * @param sql the SQL-code
   * @param parameters the parameters, in the order of the question marks
   * @return the condition
   */
  public static Condition condition(CharSequence sql, Object... parameters) {
    return new RawCondition(sql, parameters);
  }

  /**
   * Creates a condition from backend-specific SQL-code.
   *
   * @param sqlSupplier the supplier for the SQL-code
   * @param parameters the parameters, in the order of the question marks
   * @return the condition
   */
  public static Condition condition(SqlSupplier sqlSupplier, Object... parameters) {
    return new RawCondition(sqlSupplier, parameters);
  }


  // ---------------------- expressions ---------------------------

  /**
   * Creates an untyped expression from plain SQL-code.
   *
   * @param sql the SQL-code
   * @param parameters the parameters, in the order of the question marks
   * @return the expression
   */
  public static Expression<Object> sql(CharSequence sql, Object... parameters) {
    return new RawExpression<>(sql, null, parameters);
  }

  /**
   * Creates a typed expression from plain SQL-code.
   *
   * @param sql the SQL-code
   * @param type the java type of the expression
   * @param parameters the parameters, in the order of the question marks
   * @param <T> the java type of the expression
   * @return the expression
   */
  public static <T> Expression<T> sql(CharSequence sql, Class<T> type, Object... parameters) {
    return new RawExpression<>(sql, dataTypeOf(type), parameters);
  }

  /**
   * Creates an untyped expression from backend-specific SQL-code.
   *
   * @param sqlSupplier the supplier for the SQL-code
   * @param parameters the parameters, in the order of the question marks
   * @return the expression
   */
  public static Expression<Object> sql(SqlSupplier sqlSupplier, Object... parameters) {
    return new RawExpression<>(sqlSupplier, null, parameters);
  }

  /**
   * Creates a scalar sub-select.
   *
   * @param select the sub-select returning a single row and column
   * @param type the java type of the column
   * @param <T> the java type of the column
   * @return the expression
   */
  public static <T> Expression<T> subSelect(Select select, Class<T> type) {
    return new SubSelect<>(select, dataTypeOf(type));
  }


  // ---------------------- functions ---------------------------

  /**
   * Creates a function.
   *
   * @param name the function name
   * @param type the java type of the result
   * @param arguments the arguments
   * @param <T> the java type of the result
   * @return the expression
   */
  public static <T> Expression<T> function(String name, Class<T> type, Expression<?>... arguments) {
    return new SqlFunction<>(name, dataTypeOf(type), arguments);
  }

  /**
   * Creates the {@code COUNT(*)} aggregate.
   *
   * @return the expression
   */
  public static Expression<Long> count() {
    return new SqlFunction<>(SQL_COUNT, dataTypeOf(Long.class), asterisk());
  }

  /**
   * Creates the {@code COUNT} aggregate for an expression.
   *
   * @param expression the counted expression
   * @return the expression
   */
  public static Expression<Long> count(Expression<?> expression) {
    return new SqlFunction<>(SQL_COUNT, dataTypeOf(Long.class), expression);
  }

  /**
   * Creates the {@code COUNT DISTINCT} aggregate for an expression.
   *
   * @param expression the counted expression
   * @return the expression
   */
  public static Expression<Long> countDistinct(Expression<?> expression) {
    return new SqlFunction<>(SQL_COUNT, dataTypeOf(Long.class), true, List.of(expression));
  }

  /**
   * Creates the {@code SUM} aggregate.
   *
   * @param expression the summed expression
   * @param <T> the java type of the expression
   * @return the expression
   */
  public static <T> Expression<T> sum(Expression<T> expression) {
    return new SqlFunction<>(SQL_SUM, expression.getDataType(), expression);
  }

  /**
   * Creates the {@code AVG} aggregate.
   *
   * @param expression the averaged expression
   * @return the expression
   */
  public static Expression<Double> avg(Expression<?> expression) {
    return new SqlFunction<>(SQL_AVG, dataTypeOf(Double.class), expression);
  }

  /**
   * Creates the {@code MIN} aggregate.
   *
   * @param expression the expression
   * @param <T> the java type of the expression
   * @return the expression
   */
  public static <T> Expression<T> min(Expression<T> expression) {
    return new SqlFunction<>(Backend.SQL_MIN, expression.getDataType(), expression);
  }

  /**
   * Creates the {@code MAX} aggregate.
   *
   * @param expression the expression
   * @param <T> the java type of the expression
   * @return the expression
   */
  public static <T> Expression<T> max(Expression<T> expression) {
    return new SqlFunction<>(Backend.SQL_MAX, expression.getDataType(), expression);
  }

  /**
   * Creates the {@code UPPER} function.
   *
   * @param expression the expression
   * @return the expression
   */
  public static Expression<String> upper(Expression<String> expression) {
    return new SqlFunction<>(SQL_UPPER, expression.getDataType(), expression);
  }

  /**
   * Creates the {@code LOWER} function.
   *
   * @param expression the expression
   * @return the expression
   */
  public static Expression<String> lower(Expression<String> expression) {
    return new SqlFunction<>(SQL_LOWER, expression.getDataType(), expression);
  }

  /**
   * Creates the {@code ABS} function.
   *
   * @param expression the expression
   * @param <T> the java type of the expression
   * @return the expression
   */
  public static <T> Expression<T> abs(Expression<T> expression) {
    return new SqlFunction<>(SQL_ABS, expression.getDataType(), expression);
  }

  /**
   * Creates the {@code COALESCE} function, i.e. the first argument that is not null.<br>
   * The keyword is backend-specific ({@code NVL} for some backends).
   *
   * @param expression the expression
   * @param defaultExpressions the expressions applied if the expression is null
   * @param <T> the java type of the expression
   * @return the expression
   */
  @SafeVarargs
  public static <T> Expression<T> coalesce(Expression<T> expression, Expression<T>... defaultExpressions) {
    List<Expression<?>> arguments = new ArrayList<>();
    arguments.add(expression);
    Collections.addAll(arguments, defaultExpressions);
    return new SqlFunction<>(Backend::getCoalesceKeyword, expression.getDataType(), false, arguments);
  }


  /**
   * Gets the datatype for a java type.
   *
   * @param type the java type
   * @param <T> the java type
   * @return the datatype
   */
  private static <T> DataType<T> dataTypeOf(Class<T> type) {
    DataType<T> dataType = DataTypeFactory.getInstance().get(
        Objects.requireNonNull(type, "type must be provided"));
    if (dataType == null) {
      throw new PersistenceException("no datatype registered for " + type.getName());
    }
    return dataType;
  }


  private DSL() {
  }

}

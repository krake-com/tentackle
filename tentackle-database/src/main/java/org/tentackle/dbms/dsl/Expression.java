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

import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;

import java.util.Collection;

/**
 * A typed SQL expression.
 * <p>
 * Expressions are the building blocks of the projection, the conditions and the
 * {@code GROUP BY}- and {@code ORDER BY}-clauses. They are immutable and thus can be
 * shared between queries and threads.
 * <p>
 * An expression may span more than one database column, because some
 * {@link DataType}s are mapped to more than one column ({@link org.tentackle.common.BMoney}
 * or {@link java.time.ZonedDateTime}, for example). This is why expressions are rendered
 * column by column via {@link #render(RenderContext, int)}.
 *
 * @param <T> the java type of the expression
 * @author harald
 */
public interface Expression<T> extends SqlElement {

  /**
   * Gets the datatype of this expression.
   *
   * @return the datatype, null if untyped
   */
  DataType<T> getDataType();

  /**
   * Gets the number of database columns this expression spans.
   *
   * @param backend the backend
   * @return the number of columns, always &gt;= 1
   */
  default int getColumnCount(Backend backend) {
    DataType<T> dataType = getDataType();
    return dataType == null ? 1 : dataType.getColumnCount(backend);
  }

  /**
   * Renders a single column of this expression.
   *
   * @param context the render context
   * @param columnIndex the column index, 0 for the first column
   */
  void render(RenderContext context, int columnIndex);

  /**
   * Renders all columns of this expression, separated by commas.
   * <p>
   * {@inheritDoc}
   */
  @Override
  default void render(RenderContext context) {
    int columnCount = getColumnCount(context.getBackend());
    for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
      if (columnIndex > 0) {
        context.appendComma();
      }
      render(context, columnIndex);
    }
  }

  /**
   * Gets the alias of this expression.
   *
   * @return the alias, null if none
   */
  default String getAlias() {
    return null;
  }

  /**
   * Creates an aliased expression.<br>
   * The alias becomes the label of the column in the resultset and thus determines
   * the member of the DTO the value is applied to
   * (see {@link org.tentackle.dbms.DbUtilities#resultSetToList}).
   * For expressions spanning more than one column, the alias of the second and
   * following columns is suffixed just like a column name.
   *
   * @param alias the column alias
   * @return the aliased expression
   */
  default Expression<T> as(String alias) {
    return new AliasedExpression<>(this, alias);
  }


  /**
   * Creates an ascending sort field for this expression.
   *
   * @return the sort field
   */
  default SortField asc() {
    return new SortField(this, SortOrder.ASCENDING);
  }

  /**
   * Creates a descending sort field for this expression.
   *
   * @return the sort field
   */
  default SortField desc() {
    return new SortField(this, SortOrder.DESCENDING);
  }


  /**
   * Creates a condition testing this expression to be equal to a value.
   *
   * @param value the value, never null
   * @return the condition
   */
  default Condition eq(T value) {
    return eq(DSL.value(value, getDataType()));
  }

  /**
   * Creates a condition testing this expression to be equal to another expression.
   *
   * @param expression the other expression
   * @return the condition
   */
  default Condition eq(Expression<T> expression) {
    return new ComparisonCondition(this, Operator.EQUAL, expression);
  }

  /**
   * Creates a condition testing this expression to be not equal to a value.
   *
   * @param value the value, never null
   * @return the condition
   */
  default Condition ne(T value) {
    return ne(DSL.value(value, getDataType()));
  }

  /**
   * Creates a condition testing this expression to be not equal to another expression.
   *
   * @param expression the other expression
   * @return the condition
   */
  default Condition ne(Expression<T> expression) {
    return new ComparisonCondition(this, Operator.NOT_EQUAL, expression);
  }

  /**
   * Creates a condition testing this expression to be less than a value.
   *
   * @param value the value, never null
   * @return the condition
   */
  default Condition lt(T value) {
    return lt(DSL.value(value, getDataType()));
  }

  /**
   * Creates a condition testing this expression to be less than another expression.
   *
   * @param expression the other expression
   * @return the condition
   */
  default Condition lt(Expression<T> expression) {
    return new ComparisonCondition(this, Operator.LESS, expression);
  }

  /**
   * Creates a condition testing this expression to be less than or equal to a value.
   *
   * @param value the value, never null
   * @return the condition
   */
  default Condition le(T value) {
    return le(DSL.value(value, getDataType()));
  }

  /**
   * Creates a condition testing this expression to be less than or equal to another expression.
   *
   * @param expression the other expression
   * @return the condition
   */
  default Condition le(Expression<T> expression) {
    return new ComparisonCondition(this, Operator.LESS_OR_EQUAL, expression);
  }

  /**
   * Creates a condition testing this expression to be greater than a value.
   *
   * @param value the value, never null
   * @return the condition
   */
  default Condition gt(T value) {
    return gt(DSL.value(value, getDataType()));
  }

  /**
   * Creates a condition testing this expression to be greater than another expression.
   *
   * @param expression the other expression
   * @return the condition
   */
  default Condition gt(Expression<T> expression) {
    return new ComparisonCondition(this, Operator.GREATER, expression);
  }

  /**
   * Creates a condition testing this expression to be greater than or equal to a value.
   *
   * @param value the value, never null
   * @return the condition
   */
  default Condition ge(T value) {
    return ge(DSL.value(value, getDataType()));
  }

  /**
   * Creates a condition testing this expression to be greater than or equal to another expression.
   *
   * @param expression the other expression
   * @return the condition
   */
  default Condition ge(Expression<T> expression) {
    return new ComparisonCondition(this, Operator.GREATER_OR_EQUAL, expression);
  }

  /**
   * Creates a condition testing this expression to match a pattern.
   *
   * @param pattern the SQL pattern, never null
   * @return the condition
   */
  default Condition like(String pattern) {
    return new ComparisonCondition(this, Operator.LIKE, DSL.value(pattern));
  }

  /**
   * Creates a condition testing this expression not to match a pattern.
   *
   * @param pattern the SQL pattern, never null
   * @return the condition
   */
  default Condition notLike(String pattern) {
    return new ComparisonCondition(this, Operator.NOT_LIKE, DSL.value(pattern));
  }

  /**
   * Creates a condition testing this expression to be null.
   *
   * @return the condition
   */
  default Condition isNull() {
    return new NullCondition(this, false);
  }

  /**
   * Creates a condition testing this expression not to be null.
   *
   * @return the condition
   */
  default Condition isNotNull() {
    return new NullCondition(this, true);
  }

  /**
   * Creates a condition testing this expression to be within a range.
   *
   * @param lowValue the lower bound, inclusive, never null
   * @param highValue the upper bound, inclusive, never null
   * @return the condition
   */
  default Condition between(T lowValue, T highValue) {
    return new BetweenCondition(this, DSL.value(lowValue, getDataType()),
                                DSL.value(highValue, getDataType()), false);
  }

  /**
   * Creates a condition testing this expression to be outside a range.
   *
   * @param lowValue the lower bound, inclusive, never null
   * @param highValue the upper bound, inclusive, never null
   * @return the condition
   */
  default Condition notBetween(T lowValue, T highValue) {
    return new BetweenCondition(this, DSL.value(lowValue, getDataType()),
                                DSL.value(highValue, getDataType()), true);
  }

  /**
   * Creates a condition testing this expression to be one of the given values.<br>
   * An empty collection results in a condition that is never true.
   *
   * @param values the values, none of them null
   * @return the condition
   */
  default Condition in(Collection<? extends T> values) {
    return new InCondition(this, DSL.values(values, getDataType()), false);
  }

  /**
   * Creates a condition testing this expression not to be one of the given values.<br>
   * An empty collection results in a condition that is always true.
   *
   * @param values the values, none of them null
   * @return the condition
   */
  default Condition notIn(Collection<? extends T> values) {
    return new InCondition(this, DSL.values(values, getDataType()), true);
  }

  /**
   * Creates a condition testing this expression to be one of the rows returned by a sub-select.
   *
   * @param select the sub-select returning a single column
   * @return the condition
   */
  default Condition in(Select select) {
    return new InCondition(this, select, false);
  }

  /**
   * Creates a condition testing this expression not to be one of the rows returned by a sub-select.
   *
   * @param select the sub-select returning a single column
   * @return the condition
   */
  default Condition notIn(Select select) {
    return new InCondition(this, select, true);
  }

}

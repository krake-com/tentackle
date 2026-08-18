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

import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * An expression tested to be one of a set of values or sub-select rows.
 * <p>
 * An empty set of values is rendered as a condition that is never true
 * (always true if negated), which is what applications expect when they
 * build the value set dynamically.
 *
 * @author harald
 */
public class InCondition implements Condition {

  private final Expression<?> expression;             // the tested expression
  private final List<Expression<?>> values;           // the values, null if sub-select
  private final Select select;                        // the sub-select, null if values
  private final boolean negated;                      // true for NOT IN


  /**
   * Creates a condition testing an expression against a set of values.
   *
   * @param expression the tested expression
   * @param values the values, may be empty
   * @param negated true for {@code NOT IN}, false for {@code IN}
   */
  public InCondition(Expression<?> expression, Collection<? extends Expression<?>> values, boolean negated) {
    this.expression = Objects.requireNonNull(expression, "expression must be provided");
    this.values = List.copyOf(values);
    this.select = null;
    this.negated = negated;
  }

  /**
   * Creates a condition testing an expression against the rows of a sub-select.
   *
   * @param expression the tested expression
   * @param select the sub-select returning a single column
   * @param negated true for {@code NOT IN}, false for {@code IN}
   */
  public InCondition(Expression<?> expression, Select select, boolean negated) {
    this.expression = Objects.requireNonNull(expression, "expression must be provided");
    this.select = Objects.requireNonNull(select, "sub-select must be provided");
    this.values = null;
    this.negated = negated;
  }


  /**
   * Gets the tested expression.
   *
   * @return the expression
   */
  public Expression<?> getExpression() {
    return expression;
  }

  /**
   * Gets the values.
   *
   * @return the unmodifiable list of values, null if this condition refers to a sub-select
   */
  public List<Expression<?>> getValues() {
    return values;
  }

  /**
   * Gets the sub-select.
   *
   * @return the sub-select, null if this condition refers to a list of values
   */
  public Select getSelect() {
    return select;
  }

  /**
   * Returns whether the expression is tested not to be one of the values.
   *
   * @return true for {@code NOT IN}, false for {@code IN}
   */
  public boolean isNegated() {
    return negated;
  }


  @Override
  public void render(RenderContext context) {
    Backend backend = context.getBackend();

    if (expression.getColumnCount(backend) != 1) {
      throw new PersistenceException(
          "IN is not applicable to " + expression.getDataType() + " spanning more than one column");
    }

    if (values != null && values.isEmpty()) {
      // nothing matches an empty set
      context.append(negated ? Junction.AND.getNeutralSql() : Junction.OR.getNeutralSql());
      return;
    }

    expression.render(context, 0);
    context.append(' ').append(negated ? Backend.SQL_ARRAY_NOT_IN : Backend.SQL_ARRAY_IN).append(" (");

    if (values != null) {
      boolean needComma = false;
      for (Expression<?> value : values) {
        if (needComma) {
          context.appendComma();
        }
        else {
          needComma = true;
        }
        if (value.getColumnCount(backend) != 1) {
          throw new PersistenceException(
              "IN is not applicable to " + value.getDataType() + " spanning more than one column");
        }
        value.render(context, 0);
      }
    }
    else {
      select.render(context);
    }

    context.append(')');
  }

}

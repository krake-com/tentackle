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

import java.util.Objects;

/**
 * An expression tested to be within a range of values.
 *
 * @author harald
 */
public class BetweenCondition implements Condition {

  private static final String SQL_BETWEEN = " BETWEEN ";
  private static final String SQL_NOT_BETWEEN = " NOT BETWEEN ";

  private final Expression<?> expression;       // the tested expression
  private final Expression<?> lowExpression;    // the lower bound
  private final Expression<?> highExpression;   // the upper bound
  private final boolean negated;                // true for NOT BETWEEN


  /**
   * Creates a range condition.
   *
   * @param expression the tested expression
   * @param lowExpression the lower bound, inclusive
   * @param highExpression the upper bound, inclusive
   * @param negated true for {@code NOT BETWEEN}, false for {@code BETWEEN}
   */
  public BetweenCondition(Expression<?> expression, Expression<?> lowExpression,
                          Expression<?> highExpression, boolean negated) {
    this.expression = Objects.requireNonNull(expression, "expression must be provided");
    this.lowExpression = Objects.requireNonNull(lowExpression, "lower bound must be provided");
    this.highExpression = Objects.requireNonNull(highExpression, "upper bound must be provided");
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
   * Gets the lower bound.
   *
   * @return the expression
   */
  public Expression<?> getLowExpression() {
    return lowExpression;
  }

  /**
   * Gets the upper bound.
   *
   * @return the expression
   */
  public Expression<?> getHighExpression() {
    return highExpression;
  }

  /**
   * Returns whether the expression is tested to be outside the range.
   *
   * @return true for {@code NOT BETWEEN}, false for {@code BETWEEN}
   */
  public boolean isNegated() {
    return negated;
  }


  @Override
  public void render(RenderContext context) {
    Backend backend = context.getBackend();

    if (expression.getColumnCount(backend) != 1 ||
        lowExpression.getColumnCount(backend) != 1 || highExpression.getColumnCount(backend) != 1) {
      throw new PersistenceException(
          "BETWEEN is not applicable to " + expression.getDataType() + " spanning more than one column");
    }

    expression.render(context, 0);
    context.append(negated ? SQL_NOT_BETWEEN : SQL_BETWEEN);
    lowExpression.render(context, 0);
    context.append(Backend.SQL_AND);
    highExpression.render(context, 0);
  }

}

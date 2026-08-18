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
 * Two expressions compared by an {@link Operator}.
 * <p>
 * Expressions spanning more than one column are compared column by column,
 * which is applicable to equality only (see {@link Operator#isMultiColumnApplicable()}).
 *
 * @author harald
 */
public class ComparisonCondition implements Condition {

  private final Expression<?> leftExpression;     // the left expression
  private final Operator operator;                // the comparison operator
  private final Expression<?> rightExpression;    // the right expression


  /**
   * Creates a comparison.
   *
   * @param leftExpression the left expression
   * @param operator the comparison operator
   * @param rightExpression the right expression
   */
  public ComparisonCondition(Expression<?> leftExpression, Operator operator, Expression<?> rightExpression) {
    this.leftExpression = Objects.requireNonNull(leftExpression, "left expression must be provided");
    this.operator = Objects.requireNonNull(operator, "operator must be provided");
    this.rightExpression = Objects.requireNonNull(rightExpression, "right expression must be provided");
  }


  /**
   * Gets the left expression.
   *
   * @return the expression
   */
  public Expression<?> getLeftExpression() {
    return leftExpression;
  }

  /**
   * Gets the comparison operator.
   *
   * @return the operator
   */
  public Operator getOperator() {
    return operator;
  }

  /**
   * Gets the right expression.
   *
   * @return the expression
   */
  public Expression<?> getRightExpression() {
    return rightExpression;
  }


  @Override
  public void render(RenderContext context) {
    Backend backend = context.getBackend();
    int columnCount = leftExpression.getColumnCount(backend);
    int rightColumnCount = rightExpression.getColumnCount(backend);

    if (columnCount != rightColumnCount) {
      throw new PersistenceException("cannot compare " + columnCount + " columns to " + rightColumnCount);
    }

    if (columnCount == 1) {
      leftExpression.render(context, 0);
      context.append(operator.getSql());
      rightExpression.render(context, 0);
    }
    else {
      if (!operator.isMultiColumnApplicable()) {
        throw new PersistenceException("operator '" + operator + "' not applicable to " +
                                       leftExpression.getDataType() + " spanning " + columnCount + " columns");
      }
      if (operator == Operator.NOT_EQUAL) {
        // all columns must be equal to be equal, hence the negation applies to the whole comparison
        context.append(Backend.SQL_NOT);
      }
      context.append('(');
      for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
        if (columnIndex > 0) {
          context.append(Backend.SQL_AND);
        }
        leftExpression.render(context, columnIndex);
        context.append(Operator.EQUAL.getSql());
        rightExpression.render(context, columnIndex);
      }
      context.append(')');
    }
  }

}

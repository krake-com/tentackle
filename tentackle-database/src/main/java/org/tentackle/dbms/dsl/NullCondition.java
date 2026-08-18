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

import java.util.Objects;

/**
 * An expression tested to be (not) null.
 * <p>
 * An expression spanning more than one column is null if <em>all</em> of its columns are null.
 * This is why the negated condition is rendered as {@code NOT (... IS NULL AND ... IS NULL)}
 * rather than testing each column to be not null: an {@link org.tentackle.common.I18NText},
 * for example, is stored either in its {@code VARCHAR}- or in its {@code CLOB}-column,
 * so the other column is null although the value is not.
 *
 * @author harald
 */
public class NullCondition implements Condition {

  private final Expression<?> expression;     // the tested expression
  private final boolean negated;              // true for IS NOT NULL


  /**
   * Creates a null condition.
   *
   * @param expression the tested expression
   * @param negated true for {@code IS NOT NULL}, false for {@code IS NULL}
   */
  public NullCondition(Expression<?> expression, boolean negated) {
    this.expression = Objects.requireNonNull(expression, "expression must be provided");
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
   * Returns whether the expression is tested not to be null.
   *
   * @return true for {@code IS NOT NULL}, false for {@code IS NULL}
   */
  public boolean isNegated() {
    return negated;
  }


  @Override
  public Condition not() {
    return new NullCondition(expression, !negated);
  }

  @Override
  public void render(RenderContext context) {
    int columnCount = expression.getColumnCount(context.getBackend());

    if (columnCount == 1) {
      expression.render(context, 0);
      context.append(negated ? Backend.SQL_ISNOTNULL : Backend.SQL_ISNULL);
    }
    else {
      if (negated) {
        context.append(Backend.SQL_NOT);
      }
      context.append('(');
      for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
        if (columnIndex > 0) {
          context.append(Backend.SQL_AND);
        }
        expression.render(context, columnIndex);
        context.append(Backend.SQL_ISNULL);
      }
      context.append(')');
    }
  }

}

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

import java.util.Objects;

/**
 * An expression with a column alias.
 *
 * @param <T> the java type of the expression
 * @author harald
 */
public class AliasedExpression<T> implements Expression<T> {

  private final Expression<T> expression;     // the aliased expression
  private final String alias;                 // the column alias


  /**
   * Creates an aliased expression.
   *
   * @param expression the expression
   * @param alias the column alias
   */
  public AliasedExpression(Expression<T> expression, String alias) {
    this.expression = Objects.requireNonNull(expression, "expression must be provided");
    this.alias = Identifiers.check(alias, "column alias");
  }


  /**
   * Gets the aliased expression.
   *
   * @return the expression
   */
  public Expression<T> getExpression() {
    return expression;
  }

  @Override
  public String getAlias() {
    return alias;
  }

  @Override
  public Expression<T> as(String alias) {
    return new AliasedExpression<>(expression, alias);
  }

  @Override
  public DataType<T> getDataType() {
    return expression.getDataType();
  }

  @Override
  public int getColumnCount(Backend backend) {
    return expression.getColumnCount(backend);
  }

  @Override
  public void render(RenderContext context, int columnIndex) {
    expression.render(context, columnIndex);
  }

}

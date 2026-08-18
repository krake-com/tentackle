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
 * A sub-select tested to return at least one row.
 *
 * @author harald
 */
public class ExistsCondition implements Condition {

  private static final String SQL_EXISTS = "EXISTS ";

  private final Select select;      // the sub-select
  private final boolean negated;    // true for NOT EXISTS


  /**
   * Creates an exists condition.
   *
   * @param select the sub-select
   * @param negated true for {@code NOT EXISTS}, false for {@code EXISTS}
   */
  public ExistsCondition(Select select, boolean negated) {
    this.select = Objects.requireNonNull(select, "sub-select must be provided");
    this.negated = negated;
  }


  /**
   * Gets the sub-select.
   *
   * @return the sub-select
   */
  public Select getSelect() {
    return select;
  }

  /**
   * Returns whether the sub-select is tested to return no rows at all.
   *
   * @return true for {@code NOT EXISTS}, false for {@code EXISTS}
   */
  public boolean isNegated() {
    return negated;
  }


  @Override
  public Condition not() {
    return new ExistsCondition(select, !negated);
  }

  @Override
  public void render(RenderContext context) {
    if (negated) {
      context.append(Backend.SQL_NOT);
    }
    context.append(SQL_EXISTS).append('(');
    select.render(context);
    context.append(')');
  }

}

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
 * A negated condition.
 *
 * @author harald
 */
public class NotCondition implements Condition {

  private final Condition condition;      // the negated condition


  /**
   * Creates a negated condition.
   *
   * @param condition the condition to negate
   */
  public NotCondition(Condition condition) {
    this.condition = Objects.requireNonNull(condition, "condition must be provided");
  }


  /**
   * Gets the negated condition.
   *
   * @return the condition
   */
  public Condition getCondition() {
    return condition;
  }


  @Override
  public Condition not() {
    return condition;    // double negation
  }

  @Override
  public void render(RenderContext context) {
    context.append(Backend.SQL_NOT).append('(');
    condition.render(context);
    context.append(')');
  }

}

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

import java.util.List;

/**
 * A boolean SQL expression.
 * <p>
 * Conditions make up the {@code WHERE}-, {@code HAVING}- and {@code ON}-clauses.
 * They are immutable and thus can be shared between queries and threads.
 *
 * @author harald
 */
@FunctionalInterface
public interface Condition extends SqlElement {

  /**
   * Combines this condition with another one by a logical AND.
   *
   * @param condition the other condition
   * @return the combined condition
   */
  default Condition and(Condition condition) {
    return new CombinedCondition(Junction.AND, List.of(this, condition));
  }

  /**
   * Combines this condition with another one by a logical OR.
   *
   * @param condition the other condition
   * @return the combined condition
   */
  default Condition or(Condition condition) {
    return new CombinedCondition(Junction.OR, List.of(this, condition));
  }

  /**
   * Negates this condition.
   *
   * @return the negated condition
   */
  default Condition not() {
    return new NotCondition(this);
  }

}

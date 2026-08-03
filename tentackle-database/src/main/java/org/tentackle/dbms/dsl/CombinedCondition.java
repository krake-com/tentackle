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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Conditions combined by a logical {@code AND} or {@code OR}.
 *
 * @author harald
 */
public class CombinedCondition implements Condition {

  private final Junction junction;              // the logical operator
  private final List<Condition> conditions;     // the combined conditions


  /**
   * Creates a combined condition.
   *
   * @param junction the logical operator
   * @param conditions the conditions; may be empty
   */
  public CombinedCondition(Junction junction, Collection<Condition> conditions) {
    this.junction = Objects.requireNonNull(junction, "junction must be provided");
    this.conditions = List.copyOf(conditions);
  }


  /**
   * Gets the logical operator.
   *
   * @return the junction
   */
  public Junction getJunction() {
    return junction;
  }

  /**
   * Gets the combined conditions.
   *
   * @return the unmodifiable list of conditions
   */
  public List<Condition> getConditions() {
    return conditions;
  }


  @Override
  public Condition and(Condition condition) {
    return junction == Junction.AND ? append(condition) : Condition.super.and(condition);
  }

  @Override
  public Condition or(Condition condition) {
    return junction == Junction.OR ? append(condition) : Condition.super.or(condition);
  }


  @Override
  public void render(RenderContext context) {
    if (conditions.isEmpty()) {
      context.append(junction.getNeutralSql());
    }
    else if (conditions.size() == 1) {
      conditions.getFirst().render(context);
    }
    else {
      context.append('(');
      boolean needJunction = false;
      for (Condition condition : conditions) {
        if (needJunction) {
          context.append(junction.getSql());
        }
        else {
          needJunction = true;
        }
        condition.render(context);
      }
      context.append(')');
    }
  }


  /**
   * Appends another condition to this junction.
   *
   * @param condition the condition
   * @return the flattened combined condition
   */
  private CombinedCondition append(Condition condition) {
    List<Condition> combined = new ArrayList<>(conditions);
    combined.add(condition);
    return new CombinedCondition(junction, combined);
  }

}

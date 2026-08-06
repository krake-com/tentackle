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

package org.tentackle.persist;

import org.tentackle.pdo.PersistentDomainObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The mutable state of a running {@link JoinedSelect}.
 * <p>
 * {@link Join}s are immutable and shared: the eager joins of an entity are created once in the
 * static class variables of its persistence class and are therefore used concurrently by every
 * thread selecting that entity. The PDOs joined so far must consequently <em>not</em> be tracked
 * within the {@code Join} itself, but per query &ndash; which is what this class is for.
 * <p>
 * The state is owned by the {@link JoinedSelect} and is thus confined to the thread running the
 * select. It is <em>not</em> thread-safe and must not be shared between queries.
 *
 * @author harald
 */
public class JoinState {

  /**
   * The PDOs joined so far, per join and per PDO holding the relation.<br>
   * The joins are keyed by identity, the PDOs by their {@code equals} (as before), and the
   * innermost map by the object ID of the joined PDO.
   */
  private final Map<Join<?,?>, Map<Object, Map<Long, Object>>> joinedPdoMap = new HashMap<>();


  /**
   * Creates an empty join state.
   */
  public JoinState() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  /**
   * Forgets all PDOs joined so far.<br>
   * Invoked whenever the joined select is initialized, i.e., once per query.
   */
  public void clear() {
    joinedPdoMap.clear();
  }


  /**
   * Gets the PDOs already joined to given PDO by given join.
   * <p>
   * The returned map is mutable and maps the object ID of the joined PDO to that PDO.
   *
   * @param <T> the PDO type holding the relation to the joined PDO
   * @param <J> the joined PDO type
   * @param join the join
   * @param pdo the PDO that gets the join
   * @return the joined PDOs by their object ID, never null
   */
  @SuppressWarnings("unchecked")
  protected <T extends PersistentDomainObject<T>, J extends PersistentDomainObject<J>>
         Map<Long, J> getJoinedPdos(Join<T,J> join, T pdo) {
    return (Map<Long, J>) (Map<Long, ?>) joinedPdoMap.computeIfAbsent(join, _ -> new HashMap<>())
                                                     .computeIfAbsent(pdo, _ -> new HashMap<>());
  }

}

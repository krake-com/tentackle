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

package org.tentackle.persist.trip;

import org.tentackle.pdo.PdoMember;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.persist.AbstractPersistentObject;
import org.tentackle.session.Persistent;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFilter;
import org.tentackle.trip.TripStream;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link TripFilter} to serialize PDOs without their components.
 * <p>
 * Skips the composite relations of a PDO, i.e., the relations annotated with
 * {@link Persistent#component()} {@code = true}, together with their load flags.
 * The receiver gets the PDO with the composite relations not loaded, so they are loaded
 * lazily on first access, just as for a PDO freshly selected from the database.
 * <p>
 * Example:
 * <pre>
 *   stream.setFilter(PdoComponentTripFilter.INSTANCE);
 *   try {
 *     ...
 *   }
 *   finally {
 *     stream.setFilter(null);
 *   }
 * </pre>
 *
 * @see TripStream#setFilter(TripFilter)
 */
public class PdoComponentTripFilter implements TripFilter {

  /**
   * The shared filter instance.
   */
  public static final PdoComponentTripFilter INSTANCE = new PdoComponentTripFilter();


  /** the names of the skipped components mapped by the persistence implementation class. */
  private final Map<Class<?>, Set<String>> skippedComponents = new ConcurrentHashMap<>();

  /**
   * Creates the filter.<br>
   * Usually, {@link #INSTANCE} is used.
   */
  public PdoComponentTripFilter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean accept(Object parent, TripComponent<?, ?> component) {
    if (parent instanceof AbstractPersistentObject<?, ?> po) {
      return !skippedComponents.computeIfAbsent(po.getClass(), _ -> determineSkippedComponents(po))
                               .contains(component.getName());
    }
    return true;
  }

  /**
   * Determines the names of the components to skip for the given persistent object.
   * <p>
   * The field holding a composite relation is named like the relation's getter and
   * accompanied by boolean flags indicating whether the relation is loaded. All of them are
   * skipped, since the receiver would otherwise take a relation for loaded that isn't.
   *
   * @param po the persistent object
   * @return the component names
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected Set<String> determineSkippedComponents(AbstractPersistentObject<?, ?> po) {
    Set<String> names = new HashSet<>();
    List<PdoMember> members = PdoUtilities.getInstance().getMembers((Class) po.getClassVariables().pdoClass);
    for (PdoMember member : members) {
      if (member.getType() == PdoMember.Type.RELATION && member.isComponent()) {
        String name = member.getName();
        names.add(name);
        names.add(name + "Loaded");
        names.add(name + "BluntLoaded");
      }
    }
    return names;
  }

}

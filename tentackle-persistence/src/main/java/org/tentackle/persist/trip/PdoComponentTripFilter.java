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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
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


interface PdoComponentTripFilterHolder {
  PdoComponentTripFilter INSTANCE = ServiceFactory.createService(PdoComponentTripFilter.class, PdoComponentTripFilter.class);
}


/**
 * A {@link TripFilter} to serialize PDOs without their related objects.
 * <p>
 * Skips all relations of a PDO, together with their load flags, except the embedded PDOs,
 * i.e., the relations annotated with {@link Persistent#embedded()} {@code = true}.
 * The receiver gets the PDO with the relations not loaded, so they are loaded
 * lazily on first access, just as for a PDO freshly selected from the database.
 * Embedded PDOs are always sent, since they have no table of their own to be loaded from.
 * <p>
 * Example:
 * <pre>
 *   stream.setFilter(PdoComponentTripFilter.getInstance());
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
@Service(PdoComponentTripFilter.class)    // defaults to self
public class PdoComponentTripFilter implements TripFilter {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static PdoComponentTripFilter getInstance() {
    return PdoComponentTripFilterHolder.INSTANCE;
  }


  /** the names of the skipped components mapped by the persistence implementation class. */
  private final Map<Class<?>, Set<String>> skippedComponents = new ConcurrentHashMap<>();

  /**
   * Creates the filter.<br>
   * Usually, the singleton {@link #getInstance()} is used.
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
   * The field holding a relation is named like the relation's getter and
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
      if (member.getType() == PdoMember.Type.RELATION && !member.isEmbedded()) {
        // skip all relations, except embedded PDOs
        String name = member.getName();
        names.add(name);
        names.add(name + "Loaded");
        names.add(name + "BluntLoaded");
      }
    }
    return names;
  }

}

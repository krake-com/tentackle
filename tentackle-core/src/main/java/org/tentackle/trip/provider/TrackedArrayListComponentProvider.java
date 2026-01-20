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

package org.tentackle.trip.provider;

import org.tentackle.misc.TrackedArrayList;
import org.tentackle.trip.TripComponentProviderService;

/**
 * Component provider for {@link TrackedArrayList}.
 */
@SuppressWarnings("rawtypes")
@TripComponentProviderService(TrackedArrayList.class)
public class TrackedArrayListComponentProvider extends AbstractTripComponentProvider<TrackedArrayList> {

  /**
   * Field names for TrackedArrayList.
   * <br>
   * private boolean noNulls: true if adding null-values throws an NPE
   * private boolean modified: initial modification parameter or from setModified(true)
   * private boolean removed: some object has been removed
   * private boolean added: some object has been added
   * private Set<E> removedObjects: set of removed objects
   * private boolean removedObjectsFiltered: true if removed objects filtered
   */
  private static final String[] FIELD_NAMES = { "noNulls",
                                                "modified",
                                                "removed",
                                                "added",
                                                "removedObjects",
                                                "removedObjectsFiltered" };

  /**
   * Creates the provider.
   */
  public TrackedArrayListComponentProvider() {
    super(TrackedArrayList.class, FIELD_NAMES);
  }

}

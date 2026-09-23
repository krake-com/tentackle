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

package org.tentackle.trip;

/**
 * Decides which components of an object are serialized.
 * <p>
 * By default, TRIP serializes all components of an object and thus the whole object graph
 * reachable from it. A filter set via {@link TripStream#setFilter(TripFilter)} allows skipping
 * components, for example, to send a PDO without its composite relations.
 * <p>
 * A skipped component is treated exactly like a component holding its default value: it is not
 * written to the stream at all. Hence, the receiver leaves it at the value the object was
 * created with, usually {@code null}, {@code 0} or {@code false}. The wire format does not
 * change, and the receiver does not need a filter.
 * <p>
 * The filter applies to the components (fields or getters) of regular objects, including the
 * extra fields of collection or map subclasses. It is not consulted for record components,
 * collection elements or map entries.
 * <p>
 * The filter decides on the component's metadata and does not get the value, since reading
 * a getter-backed component twice would invoke the getter twice. If the value is needed,
 * {@link TripComponent#getValue(Object)} provides it.
 */
@FunctionalInterface
public interface TripFilter {

  /**
   * Returns whether the component of the given object should be serialized.
   *
   * @param parent the object the component belongs to
   * @param component the component
   * @return true to serialize the component, false to skip it
   */
  boolean accept(Object parent, TripComponent<?, ?> component);

}

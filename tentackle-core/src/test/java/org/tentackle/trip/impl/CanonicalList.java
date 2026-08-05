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

package org.tentackle.trip.impl;

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalField;
import org.tentackle.reflect.CanonicalGetter;

import java.io.Serial;
import java.util.ArrayList;

/**
 * A list that provides neither a sized nor a no-args constructor, but a canonical one.
 * <p>
 * {@code name} is a field of its own and becomes a component the ordinary way.
 * {@code capacity} is transient, hence not serializable, and is transported by its
 * {@link CanonicalGetter} instead &ndash; just like the defaults of
 * {@link org.tentackle.misc.ChainedProperties}, which live in a {@code java.util} field.
 *
 * @param <E> the element type
 */
public class CanonicalList<E> extends ArrayList<E> {

  @Serial
  private static final long serialVersionUID = 1L;

  @CanonicalField(ordinal = 0)
  private final String name;

  private final transient int capacity;

  /**
   * Creates the list.
   *
   * @param name the name of the list
   * @param capacity the configured capacity
   */
  @CanonicalConstructor
  public CanonicalList(String name, int capacity) {
    this.name = name;
    this.capacity = capacity;
  }

  public String getName() {
    return name;
  }

  @CanonicalGetter(ordinal = 1)
  public int getCapacity() {
    return capacity;
  }

}

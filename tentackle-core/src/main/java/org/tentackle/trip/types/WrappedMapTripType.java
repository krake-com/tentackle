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

package org.tentackle.trip.types;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Trip type for JDK maps that provide no usable constructors, such as the
 * {@link java.util.Collections} wrappers (unmodifiable, empty, singleton).
 * <p>
 * The entries are read into a {@link LinkedHashMap} first, which is then converted
 * by the wrapper function.
 *
 * @param <T> the map type
 */
// created by MapTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class WrappedMapTripType<T extends Map> extends MapTripType<T> {

  private final Function<LinkedHashMap<Object, Object>, Map<?, ?>> wrapper;

  /**
   * Creates the type.
   *
   * @param clazz the map's class
   * @param wrapper converts the deserialized entries into the final map
   */
  public WrappedMapTripType(Class<T> clazz, Function<LinkedHashMap<Object, Object>, Map<?, ?>> wrapper) {
    super(clazz);
    this.wrapper = wrapper;
  }

  @Override
  public boolean isInstantiable() {
    return false;   // no sized or no-args constructor: created by the wrapper function instead
  }


  @Override
  protected boolean isConverted() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T convert(T value) {
    return (T) wrapper.apply((LinkedHashMap<Object, Object>) value);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T createMap(int size) {
    return (T) new LinkedHashMap<>(size);
  }

}

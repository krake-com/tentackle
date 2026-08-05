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

import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripStream;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Trip type for the {@link java.util.Collections} wrappers of sorted maps, i.e.
 * {@code unmodifiableSortedMap}, {@code unmodifiableNavigableMap},
 * {@code synchronizedSortedMap} and {@code synchronizedNavigableMap}.
 * <p>
 * Just as {@link WrappedMapTripType}, the wrappers provide no usable constructors.
 * The entries are read into a {@link TreeMap} first, which is then converted by the
 * wrapper function. Unlike the plain wrappers, the sorted ones must retain the
 * {@link java.util.Comparator} of the wrapped map, which is written as part of the header
 * (see {@link SortedMapTripType}).
 * <p>
 * Notice that the concrete class of the wrapped map is always a {@link TreeMap} after
 * deserialization, which is by design.
 *
 * @param <T> the map type
 */
// created by MapTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class WrappedSortedMapTripType<T extends Map> extends MapTripType<T> {

  private final Function<SortedMap<Object, Object>, Map<?, ?>> wrapper;

  /**
   * Creates the type.
   *
   * @param clazz the map's class
   * @param wrapper converts the deserialized entries into the final map
   */
  public WrappedSortedMapTripType(Class<T> clazz, Function<SortedMap<Object, Object>, Map<?, ?>> wrapper) {
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
    return (T) wrapper.apply((SortedMap<Object, Object>) value);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T createMap(int size) {
    return (T) new TreeMap<>();
  }

  @Override
  protected void writeHeader(T value, TripStream stream) {
    super.writeHeader(value, stream);
    ComparatorSupport.write(((SortedMap) value).comparator(), stream);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected MapHeader<T> readHeader(TripStream stream, OpCode opCode) {
    int size = checkSize(MapTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode));
    return new MapHeader<>(size, (T) new TreeMap<>(ComparatorSupport.read(stream)));
  }

}

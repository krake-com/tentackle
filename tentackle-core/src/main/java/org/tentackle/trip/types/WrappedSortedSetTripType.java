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
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;


interface WrappedSortedSetTypeHolder {
  TripType<Integer> INTEGER_TRIP_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
}


/**
 * Trip type for the {@link java.util.Collections} wrappers of sorted sets, i.e.
 * {@code unmodifiableSortedSet}, {@code unmodifiableNavigableSet},
 * {@code synchronizedSortedSet} and {@code synchronizedNavigableSet}.
 * <p>
 * Just as {@link WrappedCollectionTripType}, the wrappers provide no usable constructors.
 * The elements are read into a {@link TreeSet} first, which is then converted by the
 * wrapper function. Unlike the plain wrappers, the sorted ones must retain the
 * {@link java.util.Comparator} of the wrapped set, which is written as part of the header
 * (see {@link SortedSetTripType}).
 * <p>
 * Notice that the concrete class of the wrapped set is always a {@link TreeSet} after
 * deserialization, which is by design.
 *
 * @param <T> the collection type
 */
// created by CollectionTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class WrappedSortedSetTripType<T extends Collection> extends CollectionTripType<T> {

  private final Function<SortedSet<Object>, Collection<?>> wrapper;

  /**
   * Creates the type.
   *
   * @param clazz the collection's class
   * @param wrapper converts the deserialized elements into the final collection
   */
  public WrappedSortedSetTripType(Class<T> clazz, Function<SortedSet<Object>, Collection<?>> wrapper) {
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
    return (T) wrapper.apply((SortedSet<Object>) value);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T createCollection(int size) {
    return (T) new TreeSet<>();
  }

  @Override
  protected void writeHeader(T value, TripStream stream) {
    super.writeHeader(value, stream);
    ComparatorSupport.write(((SortedSet) value).comparator(), stream);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected CollectionHeader<T> readHeader(TripStream stream, OpCode opCode) {
    int size = checkSize(WrappedSortedSetTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode));
    return new CollectionHeader<>(size, (T) new TreeSet<>(ComparatorSupport.read(stream)));
  }

}

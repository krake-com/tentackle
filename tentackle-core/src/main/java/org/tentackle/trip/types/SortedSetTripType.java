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
import org.tentackle.trip.TripInstantiationException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;


interface SortedSetTypeHolder {
  TripType<Integer> INTEGER_TRIP_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
}


/**
 * Trip type for {@link SortedSet}s, i.e. {@link TreeSet} and {@link ConcurrentSkipListSet}.
 * <p>
 * The {@link Comparator} is serialized as part of the header. It lives in a JDK-internal field
 * and would be lost otherwise (see {@link CollectionTripType}), leaving the receiver to silently
 * re-sort by natural ordering, or to fail altogether for elements that are not {@link Comparable}.
 * The comparator itself must be serializable, i.e., a static or a top-level class, since an
 * anonymous inner class would expect the enclosing instance as a constructor argument.
 * <p>
 * Only sorted sets providing a comparator constructor can be handled this way,
 * see {@link #isApplicable(Class)}.
 *
 * @param <T> the sorted set type
 */
// created by CollectionTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class SortedSetTripType<T extends SortedSet> extends CollectionTripType<T> {

  /**
   * Tells whether the given class can be handled by this trip type.
   *
   * @param clazz the class
   * @return true if it is a sorted set providing a comparator constructor
   */
  public static boolean isApplicable(Class<?> clazz) {
    if (SortedSet.class.isAssignableFrom(clazz)) {
      try {
        clazz.getDeclaredConstructor(Comparator.class);
        return true;
      }
      catch (NoSuchMethodException e) {
        // no comparator constructor: the comparator could not be restored anyway
      }
    }
    return false;
  }


  private final Constructor<T> comparatorConstructor;

  /**
   * Creates the type.
   *
   * @param clazz the sorted set's class
   */
  public SortedSetTripType(Class<T> clazz) {
    super(clazz);
    try {
      comparatorConstructor = clazz.getDeclaredConstructor(Comparator.class);
    }
    catch (NoSuchMethodException e) {
      throw new TripInstantiationException(clazz, "missing comparator constructor", e);
    }
  }

  /**
   * Gets the comparator constructor.
   *
   * @return the comparator constructor, never null
   */
  public Constructor<T> getComparatorConstructor() {
    return comparatorConstructor;
  }

  @Override
  protected void writeHeader(T value, TripStream stream) {
    super.writeHeader(value, stream);
    ComparatorSupport.write(value.comparator(), stream);
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected CollectionHeader<T> readHeader(TripStream stream, OpCode opCode) {
    int size = checkSize(SortedSetTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode));
    Comparator comparator = ComparatorSupport.read(stream);
    if (comparator != null) {
      try {
        return new CollectionHeader<>(size, comparatorConstructor.newInstance(comparator));
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new TripInstantiationException(getObjectClass(),
                                             "cannot create instance with comparator " +
                                             comparator.getClass().getName(), e);
      }
    }
    return new CollectionHeader<>(size, createCollection(size));
  }

}

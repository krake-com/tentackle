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
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;


interface PriorityQueueTypeHolder {
  TripType<Integer> INTEGER_TRIP_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
}


/**
 * Trip type for the priority queues, i.e. {@link PriorityQueue} and {@link PriorityBlockingQueue}.
 * <p>
 * Just as for the {@link SortedSetTripType}, the {@link Comparator} is serialized as part of the
 * header, since it lives in a JDK-internal field and would be lost otherwise (see
 * {@link CollectionTripType}). The receiver would then re-order the queue by natural ordering,
 * silently changing what the next {@code poll()} returns, or fail altogether for elements that
 * are not {@link Comparable}. The comparator itself must be serializable, i.e., a static or a
 * top-level class, since an anonymous inner class would expect the enclosing instance as a
 * constructor argument.
 * <p>
 * The priority queues are no {@link java.util.SortedSet}s and expose their comparator without a
 * common interface, hence both the capacity/comparator constructor and the {@code comparator()}
 * accessor are determined reflectively, see {@link #isApplicable(Class)}.
 * <p>
 * Notice that only the <em>heap</em> is restored, not its layout: the elements are re-inserted
 * one by one, so the iteration order may differ from the sender's. The head of the queue and
 * the polling order are of course identical.
 *
 * @param <T> the queue type
 */
// created by CollectionTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class PriorityQueueTripType<T extends Queue> extends CollectionTripType<T> {

  /**
   * Tells whether the given class can be handled by this trip type.
   *
   * @param clazz the class
   * @return true if it is a queue providing a capacity/comparator constructor and a comparator accessor
   */
  public static boolean isApplicable(Class<?> clazz) {
    if (Queue.class.isAssignableFrom(clazz)) {
      try {
        clazz.getDeclaredConstructor(Integer.TYPE, Comparator.class);
        clazz.getMethod("comparator");
        return true;
      }
      catch (NoSuchMethodException e) {
        // not a priority queue: the comparator could not be restored anyway
      }
    }
    return false;
  }


  private final Constructor<T> capacityComparatorConstructor;
  private final Method comparatorMethod;

  /**
   * Creates the type.
   *
   * @param clazz the queue's class
   */
  public PriorityQueueTripType(Class<T> clazz) {
    super(clazz);
    try {
      capacityComparatorConstructor = clazz.getDeclaredConstructor(Integer.TYPE, Comparator.class);
      comparatorMethod = clazz.getMethod("comparator");
    }
    catch (NoSuchMethodException e) {
      throw new TripInstantiationException(clazz, "missing capacity/comparator constructor or comparator method", e);
    }
  }

  /**
   * Gets the capacity/comparator constructor.
   *
   * @return the constructor, never null
   */
  public Constructor<T> getCapacityComparatorConstructor() {
    return capacityComparatorConstructor;
  }

  @Override
  protected void writeHeader(T value, TripStream stream) {
    super.writeHeader(value, stream);
    ComparatorSupport.write(comparatorOf(value), stream);
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected CollectionHeader<T> readHeader(TripStream stream, OpCode opCode) {
    int size = checkSize(PriorityQueueTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode));
    Comparator comparator = ComparatorSupport.read(stream);   // null means natural ordering
    try {
      // the priority queues reject a capacity below one
      return new CollectionHeader<>(size, capacityComparatorConstructor.newInstance(
                                            Math.max(limitPreAllocation(size), 1), comparator));
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new TripInstantiationException(getObjectClass(), "cannot instantiate priority queue", e);
    }
  }

  /**
   * Gets the comparator of the given queue.
   *
   * @param value the queue
   * @return the comparator, null if natural ordering
   */
  private Comparator comparatorOf(T value) {
    try {
      return (Comparator) comparatorMethod.invoke(value);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new TripRuntimeException("cannot determine the comparator of " + getObjectClass().getName(), e);
    }
  }

}

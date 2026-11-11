/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.misc;

import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;

import java.io.Serial;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An enhanced {@link ArrayList}.<br>
 * Implements {@link Immutable}.
 * <p>
 * Also differs from {@link ArrayList} in the way {@code equals()} and {@code hashCode()} are implemented:
 * if {@code simpleEqualsAndHashCode==true}, those methods refer to the list instance and don't check the elements.
 * Useful if the list is contained in another collection.
 * <p>
 * Notice that despite its name, {@link ImmutableArrayList}s are mutable by default. {@link #setImmutable} must be invoked
 * to make it immutable.<br>
 * Operations which would not change the list, such as replacing an element by the same instance or
 * removing elements which are not in the list, are allowed even if the list is immutable.
 * Sorting is not considered a modification.<br>
 * Cloning an immutable list returns a mutable copy.
 *
 * @author harald
 * @param <E> the element type
 */
public class ImmutableArrayList<E> extends ArrayList<E> implements ImmutableCollection<E> {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(ImmutableArrayList.class);


  private final transient int hash = new Object().hashCode();   // some hashcode (transient: never mutate final fields)

  /** true if equals and hashcode refer to the list instance, not its contents. */
  protected boolean simpleEqualsAndHashCode;
  /** true if the list is immutable. */
  protected boolean immutable;
  /** true if the list is finally immutable. */
  protected boolean finallyImmutable;
  /** optional logging for immutable violations instead of an exception. */
  protected Level immutableLoggingLevel;


  /**
   * Constructs an empty list with the specified initial capacity.
   *
   * @param initialCapacity the initial capacity of the list
   * @throws IllegalArgumentException if the specified initial capacity is negative
   */
  public ImmutableArrayList(int initialCapacity) {
    super(initialCapacity);
  }

  /**
   * Constructs a list containing the elements of the specified collection, in the order they are returned by the
   * collection's iterator.
   *
   * @param c the collection whose elements are to be placed into this list
   * @throws NullPointerException if the specified collection is null
   */
  public ImmutableArrayList(Collection<? extends E> c) {
    super(c);
  }

  /**
   * Constructs an empty list with an initial capacity of ten.
   */
  public ImmutableArrayList() {
    super();
  }


  @Override
  public void setSimpleEqualsAndHashCode(boolean simpleEqualsAndHashCode) {
    this.simpleEqualsAndHashCode = simpleEqualsAndHashCode;
  }

  @Override
  public boolean isSimpleEqualsAndHashCode() {
    return simpleEqualsAndHashCode;
  }

  @Override
  public int hashCode() {
    return simpleEqualsAndHashCode ? hash : super.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return simpleEqualsAndHashCode ? (this == o) : super.equals(o);
  }

  @Override
  @SuppressWarnings("unchecked")
  public ImmutableArrayList<E> clone() {
    ImmutableArrayList<E> list = (ImmutableArrayList<E>) super.clone();
    list.immutable = false;
    list.finallyImmutable = false;
    return list;
  }

  @Override
  public void setImmutable(boolean immutable, boolean withElements) {
    if (!immutable && finallyImmutable) {
      throw new ImmutableException("list is finally immutable");
    }
    this.immutable = immutable;

    if (withElements) {
      for (Object obj: this) {
        if (obj instanceof Immutable) {
          ((Immutable) obj).setImmutable(immutable);
        }
      }
    }
  }

  @Override
  public void setFinallyImmutable(boolean withElements) {
    immutable = true;
    finallyImmutable = true;

    if (withElements) {
      for (Object obj: this) {
        if (obj instanceof Immutable) {
          ((Immutable) obj).setFinallyImmutable();
        }
      }
    }
  }

  @Override
  public void setImmutable(boolean immutable) {
    setImmutable(immutable, true);
  }

  @Override
  public void setFinallyImmutable() {
    setFinallyImmutable(true);
  }

  @Override
  public boolean isImmutable() {
    return immutable;
  }

  @Override
  public boolean isFinallyImmutable() {
    return finallyImmutable;
  }

  @Override
  public void setImmutableLoggingLevel(Level immutableLoggingLevel) {
    this.immutableLoggingLevel = immutableLoggingLevel;
  }

  @Override
  public Level getImmutableLoggingLevel() {
    return immutableLoggingLevel;
  }


  @Override
  public boolean add(E e) {
    assertMutable();
    return super.add(e);
  }

  @Override
  public void add(int index, E element) {
    assertMutable();
    super.add(index, element);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    assertMutable();
    return super.addAll(c);
  }

  @Override
  public boolean addAll(int index, Collection<? extends E> c) {
    assertMutable();
    return super.addAll(index, c);
  }

  @Override
  public E remove(int index) {
    assertMutable();
    return super.remove(index);
  }

  @Override
  public boolean remove(Object o) {
    assertMutable();
    return super.remove(o);
  }

  @Override
  public E removeFirst() {
    assertMutable();
    return super.removeFirst();
  }

  @Override
  public E removeLast() {
    assertMutable();
    return super.removeLast();
  }

  @Override
  public boolean removeAll(Collection<?> collection) {
    Objects.requireNonNull(collection);
    return removeIndexes(matchIndexes(collection::contains));
  }

  @Override
  public boolean retainAll(Collection<?> collection) {
    Objects.requireNonNull(collection);
    return removeIndexes(matchIndexes(element -> !collection.contains(element)));
  }

  @Override
  public boolean removeIf(Predicate<? super E> filter) {
    Objects.requireNonNull(filter);
    return removeIndexes(matchIndexes(filter));
  }

  @Override
  public void clear() {
    if (!isEmpty()) {
      assertMutable();
    }
    super.clear();
  }

  @Override
  protected void removeRange(int fromIndex, int toIndex) {
    // invoked by subList(...).clear()
    if (toIndex > fromIndex) {
      assertMutable();
    }
    super.removeRange(fromIndex, toIndex);
  }

  @Override
  public void replaceAll(UnaryOperator<E> operator) {
    // re-implemented via the list iterator, whose set() invokes set(int, E) and thus checks mutability
    Objects.requireNonNull(operator, "operator");
    ListIterator<E> iter = listIterator();
    while (iter.hasNext()) {
      iter.set(operator.apply(iter.next()));
    }
  }

  @Override
  public E set(int index, E element) {
    E obj = super.set(index, element);
    if (obj != element)  {  // if not exactly the same instance
      try {
        assertMutable();
      }
      catch (ImmutableException ix) {
        super.set(index, obj);
        throw ix;
      }
    }
    return obj;
  }


  /**
   * Determines the indexes of the elements matching a filter.<br>
   * The filter is evaluated exactly once per element.
   *
   * @param filter the filter
   * @return the indexes of the matching elements, null if none
   */
  protected BitSet matchIndexes(Predicate<? super E> filter) {
    BitSet indexes = null;
    int size = size();
    for (int i = 0; i < size; i++) {
      if (filter.test(super.get(i))) {
        if (indexes == null) {
          indexes = new BitSet(size);
        }
        indexes.set(i);
      }
    }
    return indexes;
  }

  /**
   * Removes the elements at the given indexes in a single pass.<br>
   * Asserts the list is mutable if there is anything to remove.
   * <p>
   * Unlike removing them one by one via {@link #remove(int)} or an iterator, which shifts the
   * remaining elements each time, this takes linear time. The overridden mutators are bypassed.
   *
   * @param indexes the indexes, null if none
   * @return true if elements were removed
   */
  protected boolean removeIndexes(BitSet indexes) {
    if (indexes == null) {
      return false;
    }
    assertMutable();
    int size = size();
    int target = indexes.nextSetBit(0);
    for (int source = target + 1; source < size; source++) {
      if (!indexes.get(source)) {
        super.set(target++, super.get(source));
      }
    }
    super.removeRange(target, size);
    return true;
  }

  /**
   * Asserts that the object is mutable.<br>
   * If an immutable logging level is set, the violation is only logged.
   *
   * @throws ImmutableException if the list is immutable and no logging level is set
   */
  protected void assertMutable() {
    if (isImmutable()) {
      RuntimeException ex = new ImmutableException("list is immutable");
      if (immutableLoggingLevel == null) {
        throw ex;
      }
      LOGGER.log(immutableLoggingLevel, ex.getMessage(), ex);
    }
  }

}

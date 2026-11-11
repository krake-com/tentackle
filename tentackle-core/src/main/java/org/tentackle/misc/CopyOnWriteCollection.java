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

package org.tentackle.misc;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalName;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A copy-on-write wrapper for collections.<br>
 * The first modifying operation creates a clone to leave the original collection untouched.
 * <p>
 * The wrapped collection must be {@link Cloneable} and provide a public {@code clone()} method.
 * <p>
 * With {@code iteratorModifying=false}, all operations are supported except {@link Iterator#remove()},
 * which will throw an {@link UnsupportedOperationException}.<br>
 * With {@code iteratorModifying=true}, {@code iterator()} will create a copy of the collection
 * to allow modifications by the iterator.
 * <p>
 * Not to be mixed up with the classes in {@code java.util.concurrent}, which
 * are thread-safe and create a copy for each modification. {@link CopyOnWriteCollection}
 * is not thread-safe and creates a copy only once before the first modification
 * is performed.
 *
 * @author harald
 * @param <E> the type of elements in this collection
 */
public class CopyOnWriteCollection<E> implements Collection<E>, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * The wrapped or cloned collection.
   */
  @SuppressWarnings("serial")
  private Collection<E> collection;

  /**
   * Flag initially true if the collection is not cloned.<br>
   * Transient to avoid cloning after serialization because the deserialized
   * collection is already a copy of the original collection.
   */
  private transient boolean notCloned = true;

  /**
   * The clone-method.<br>
   * Not used after deserialization.
   */
  private final transient Method cloneMethod;

  /**
   * If true, iterator() will clone the collection.
   */
  private final boolean iteratorModifying;


  /**
   * Creates a wrapper for a collection.
   *
   * @param collection the collection
   * @param iteratorModifying true if iterators will be modifying, else readonly (avoids copy)
   * @throws IllegalArgumentException if the collection is not cloneable
   */
  @CanonicalConstructor
  public CopyOnWriteCollection(@CanonicalName("collection") Collection<E> collection,
                               @CanonicalName("iteratorModifying") boolean iteratorModifying) {
    if (!(collection instanceof Cloneable)) {
      throw new IllegalArgumentException(collection.getClass() + " does not implement Cloneable");
    }
    try {
      cloneMethod = collection.getClass().getMethod("clone");
    }
    catch (NoSuchMethodException ex) {
      throw new IllegalArgumentException(collection.getClass() + " does not provide a clone-method");
    }
    this.collection = collection;
    this.iteratorModifying = iteratorModifying;
  }

  /**
   * Creates a wrapper for a collection with read-only iterators.
   *
   * @param collection the collection
   * @throws IllegalArgumentException if the collection is not cloneable
   */
  public CopyOnWriteCollection(Collection<E> collection) {
    this(collection, false);
  }

  /**
   * Creates a wrapper for an empty collection.
   */
  public CopyOnWriteCollection() {
    this(new ImmutableArrayList<>(), false);
  }


  /**
   * Gets the cloned collection.<br>
   * Clones the collection, if not done yet.
   *
   * @return the cloned collection
   * @throws TentackleRuntimeException if cloning failed
   */
  @SuppressWarnings("unchecked")
  protected Collection<E> clonedCollection() {
    if (notCloned) {
      try {
        collection = (Collection<E>) cloneMethod.invoke(collection);
        notCloned = false;
      }
      catch (InvocationTargetException | IllegalAccessException ex) {
        throw new TentackleRuntimeException("cloning collection failed", ex);
      }
    }
    return collection;
  }

  /**
   * Returns whether the collection is cloned.
   *
   * @return true if cloned
   */
  public boolean isCloned() {
    return !notCloned;
  }

  /**
   * Gets the collection.
   *
   * @return the original or cloned collection
   */
  protected Collection<E> collection() {
    return collection;
  }


  @Override
  public int size() {
    return collection.size();
  }

  @Override
  public boolean isEmpty() {
    return collection.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return collection.contains(o);
  }

  @Override
  public Iterator<E> iterator() {
    if (iteratorModifying) {
      return clonedCollection().iterator();
    }
    return createReadOnlyIterator(collection.iterator());
  }

  @Override
  public Object[] toArray() {
    return collection.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return collection.toArray(a);
  }

  @Override
  public boolean add(E e) {
    return clonedCollection().add(e);
  }

  @Override
  public boolean remove(Object o) {
    return clonedCollection().remove(o);
  }

  @Override
  public boolean removeIf(Predicate<? super E> filter) {
    return clonedCollection().removeIf(filter);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return collection.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    return clonedCollection().addAll(c);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return clonedCollection().removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return clonedCollection().retainAll(c);
  }

  @Override
  public void clear() {
    clonedCollection().clear();
  }

  @Override
  public boolean equals(Object obj) {
    return collection.equals(obj);
  }

  @Override
  public int hashCode() {
    return collection.hashCode();
  }

  @Override
  public Spliterator<E> spliterator() {
    return collection.spliterator();
  }

  @Override
  public Stream<E> stream() {
    return collection.stream();
  }

  @Override
  public Stream<E> parallelStream() {
    return collection.parallelStream();
  }

  /**
   * Creates a read-only iterator for the collection.
   *
   * @param iterator the original iterator
   * @return the iterator
   */
  protected Iterator<E> createReadOnlyIterator(Iterator<E> iterator) {
    return new ReadOnlyIterator<>(iterator);
  }


  /**
   * Wrapper for an iterator that forbids invocations of the {@code remove()} method.
   *
   * @param <E> the element type
   */
  public static final class ReadOnlyIterator<E> implements Iterator<E> {

    private final Iterator<E> iterator;

    /**
     * Creates a wrapper for an iterator.
     *
     * @param iterator the wrapped iterator
     */
    public ReadOnlyIterator(Iterator<E> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public E next() {
      return iterator.next();
    }

    @Override
    public void forEachRemaining(Consumer<? super E> action) {
      iterator.forEachRemaining(action);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Iterator#remove() not supported with iteratorModifying=false");
    }

  }

}

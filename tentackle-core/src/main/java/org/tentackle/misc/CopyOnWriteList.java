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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalName;

import java.io.Serial;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.UnaryOperator;

/**
 * A copy-on-write wrapper for lists.<br>
 * The first modifying operation creates a clone to leave the original list untouched.
 * <p>
 * With {@code iteratorModifying=false}, all operations are supported except {@link java.util.Iterator#remove()},
 * which will throw an {@link UnsupportedOperationException}.<br>
 * With {@code iteratorModifying=true}, {@code iterator()} will create a copy of the list
 * to allow modifications by the iterator.
 * <p>
 * With {@code listIteratorModifying=false}, all operations are supported except {@link java.util.ListIterator#remove()},
 * {@link ListIterator#add(java.lang.Object)} and {@link ListIterator#set(java.lang.Object)}, which will
 * throw an {@link UnsupportedOperationException}.<br>
 * With {@code listIteratorModifying=true}, {@code listIterator()} will create a copy of the list
 * to allow modifications by the list iterator.
 * <p>
 * The views returned by {@link #subList(int, int)} and {@link #reversed()} are backed by the copy,
 * hence they create the copy as well.
 * <p>
 * Not to be mixed up with {@link java.util.concurrent.CopyOnWriteArrayList}, which
 * is thread-safe and creates a copy for each modification. {@link CopyOnWriteList}
 * is not thread-safe and creates a copy only once before the first modification
 * is performed.
 *
 * @author harald
 * @param <E> the type of elements in this list
 */
public class CopyOnWriteList<E> extends CopyOnWriteCollection<E> implements List<E> {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * If true, listIterator() will clone the collection.
   */
  private final boolean listIteratorModifying;


  /**
   * Creates a wrapper for a list.
   *
   * @param list the list
   * @param iteratorModifying true if iterators will be modifying, else readonly (avoids copy)
   * @param listIteratorModifying true if list-iterators will be modifying, else readonly (avoids copy)
   * @throws IllegalArgumentException if the list is not cloneable
   */
  @CanonicalConstructor
  public CopyOnWriteList(@CanonicalName("collection") List<E> list,
                         @CanonicalName("iteratorModifying") boolean iteratorModifying,
                         @CanonicalName("listIteratorModifying") boolean listIteratorModifying) {
    super(list, iteratorModifying);
    this.listIteratorModifying = listIteratorModifying;
  }

  /**
   * Creates a wrapper for a list with read-only iterators and modifying list-iterators.
   *
   * @param list the list
   * @throws IllegalArgumentException if the list is not cloneable
   */
  public CopyOnWriteList(List<E> list) {
    this(list, false, true);
    // listIteratorModifying=true because ArrayList.sort uses ListIterator#set.
  }

  /**
   * Creates a wrapper for an empty list.
   */
  public CopyOnWriteList() {
    this(new ImmutableArrayList<>(), false, true);
  }


  /**
   * Gets the list.
   *
   * @return the original or cloned list
   */
  protected List<E> list() {
    return (List<E>) collection();
  }

  /**
   * Gets the cloned list.<br>
   * Clones the list, if not done yet.
   *
   * @return the cloned list
   */
  protected List<E> clonedList() {
    return (List<E>) clonedCollection();
  }


  @Override
  public boolean addAll(int index, Collection<? extends E> c) {
    return clonedList().addAll(index, c);
  }

  @Override
  public E get(int index) {
    return list().get(index);
  }

  @Override
  public E getFirst() {
    return list().getFirst();
  }

  @Override
  public E getLast() {
    return list().getLast();
  }

  @Override
  public E set(int index, E element) {
    return clonedList().set(index, element);
  }

  @Override
  public void add(int index, E element) {
    clonedList().add(index, element);
  }

  @Override
  public void addFirst(E e) {
    clonedList().addFirst(e);
  }

  @Override
  public void addLast(E e) {
    clonedList().addLast(e);
  }

  @Override
  public E remove(int index) {
    return clonedList().remove(index);
  }

  @Override
  public E removeFirst() {
    return clonedList().removeFirst();
  }

  @Override
  public E removeLast() {
    return clonedList().removeLast();
  }

  @Override
  public int indexOf(Object o) {
    return list().indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return list().lastIndexOf(o);
  }

  @Override
  public List<E> reversed() {
    // the view may be modified: must not refer to the original list
    return clonedList().reversed();
  }

  @Override
  public void replaceAll(UnaryOperator<E> operator) {
    clonedList().replaceAll(operator);
  }

  @Override
  public void sort(Comparator<? super E> c) {
    clonedList().sort(c);
  }

  @Override
  public ListIterator<E> listIterator() {
    if (listIteratorModifying) {
      return clonedList().listIterator();
    }
    return createReadOnlyListIterator(list().listIterator());
  }

  @Override
  public ListIterator<E> listIterator(int index) {
    if (listIteratorModifying) {
      return clonedList().listIterator(index);
    }
    return createReadOnlyListIterator(list().listIterator(index));
  }

  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    // the view may be modified: must not refer to the original list
    return clonedList().subList(fromIndex, toIndex);
  }


  /**
   * Creates a read-only list iterator for the list.
   *
   * @param iterator the original list iterator
   * @return the read-only list iterator
   */
  protected ListIterator<E> createReadOnlyListIterator(ListIterator<E> iterator) {
    return new ReadOnlyListIterator<>(iterator);
  }


  /**
   * Wrapper for a list iterator that forbids modifying method invocations.
   *
   * @param <E> the element type
   */
  public static final class ReadOnlyListIterator<E> implements ListIterator<E> {

    private final ListIterator<E> iterator;

    /**
     * Creates a wrapper for a list iterator.
     *
     * @param iterator the wrapped iterator
     */
    public ReadOnlyListIterator(ListIterator<E> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasPrevious() {
      return iterator.hasPrevious();
    }

    @Override
    public E previous() {
      return iterator.previous();
    }

    @Override
    public int nextIndex() {
      return iterator.nextIndex();
    }

    @Override
    public int previousIndex() {
      return iterator.previousIndex();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("ListIterator#remove() not supported with listIteratorModifying=false");
    }

    @Override
    public void set(E e) {
      throw new UnsupportedOperationException("ListIterator#set() not supported with listIteratorModifying=false");
    }

    @Override
    public void add(E e) {
      throw new UnsupportedOperationException("ListIterator#add() not supported with listIteratorModifying=false");
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public E next() {
      return iterator.next();
    }

  }

}

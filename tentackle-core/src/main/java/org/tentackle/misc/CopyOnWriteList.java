/**
 * Tentackle - http://www.tentackle.org
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

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

/**
 * A copy-on-write wrapper for lists.<br>
 * The first modifying operation creates a clone to leave the original list untouched.
 * <p>
 * With iteratorModifying=false, all operations are supported except {@link Iterator#remove()},
 * which will throw an {@link UnsupportedOperationException}.<br>
 * With iteratorModifying=true, iterator() will create a copy of the collection
 * to allow modifications by the iterator.
 * <p>
 * With listIteratorModifying=false, all operations are supported except {@link java.util.ListIterator#remove()},
 * {@link ListIterator#add(java.lang.Object)} and {@link ListIterator#set(java.lang.Object)} which will
 * throw an {@link UnsupportedOperationException}.<br>
 * With listIteratorModifying=true, listIterator() will create a copy of the list
 * to allow modifications by the listIterator.
 * <p>
 * Not to be mixed up with {@link java.util.concurrent.CopyOnWriteArrayList} which
 * is threadsafe and creates a copy for each modification. {@link CopyOnWriteList}
 * is not threadsafe and creates a copy only once before the first modification
 * is performed.
 *
 * @author harald
 * @param <E> the type of elements in this list
 */
public class CopyOnWriteList<E> extends CopyOnWriteCollection<E> implements List<E> {

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
   */
  public CopyOnWriteList(List<E> list, boolean iteratorModifying, boolean listIteratorModifying) {
    super(list, iteratorModifying);
    this.listIteratorModifying = listIteratorModifying;
  }

  /**
   * Creates a wrapper for a list with read-only iterators and modifying list-iterators.
   *
   * @param list the list
   */
  public CopyOnWriteList(List<E> list) {
    this(list, false, true);
    // listIteratorModifying=true because ArrayList.sort uses ListIterator#set.
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
   * Gets the cloned list.
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
  public E set(int index, E element) {
    return clonedList().set(index, element);
  }

  @Override
  public void add(int index, E element) {
    clonedList().add(index, element);
  }

  @Override
  public E remove(int index) {
    return clonedList().remove(index);
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
    return new CopyOnWriteList<>(list().subList(fromIndex, toIndex));
  }


  /**
   * Creates a read-only iterator for the collection.
   *
   * @param iterator the original iterator
   * @return the iterator
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

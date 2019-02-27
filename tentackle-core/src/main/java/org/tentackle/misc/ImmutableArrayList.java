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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;

/**
 * An enhanced {@link ArrayList}.<br>
 * Implements {@link Immutable}.<br>
 * Also differs from {@link ArrayList} the way equals() and hashCode() is implemented:
 * if simpleEqualsAndHashCode==true, those methods refer to the list instance and don't check the elements.
 * Useful if the list is contained in another collection.<br>
 * Notice that despite its name, {@link ImmutableArrayList}s are mutable by default. {@link #setImmutable} must be invoked
 * to make it immutable.<br>
 *
 * @author harald
 * @param <E> the element type
 */
public class ImmutableArrayList<E> extends ArrayList<E> implements ImmutableCollection<E> {

  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(ImmutableArrayList.class);



  private final int hash = new Object().hashCode();   // some hashcode

  protected boolean simpleEqualsAndHashCode;          // true if equals and hashcode refer to list instance, not its contents
  protected boolean immutable;                        // true if list is immutable
  protected boolean finallyImmutable;                 // true if list is finally immutable
  protected Level immutableLoggingLevel;              // optional logging for immutable violations instead of exception


  /**
   * Constructs an empty list with the specified initial capacity.
   * <p>
   * @param initialCapacity the initial capacity of the list
   * <p>
   * @throws IllegalArgumentException if the specified initial capacity is negative
   */
  public ImmutableArrayList(int initialCapacity) {
    super(initialCapacity);
  }

  /**
   * Constructs a list containing the elements of the specified collection, in the order they are returned by the
   * collection's iterator.
   * <p>
   * @param c the collection whose elements are to be placed into this list
   * <p>
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


  /**
   * Sets whether the equals() and hashCode() methods should refer to the list instance alone.
   *
   * @param simpleEqualsAndHashCode true if instance alone, false if check list contents as well
   */
  @Override
  public void setSimpleEqualsAndHashCode(boolean simpleEqualsAndHashCode) {
    this.simpleEqualsAndHashCode = simpleEqualsAndHashCode;
  }

  /**
   * Returns whether the equals() and hashCode() methods should refer to the list instance alone.
   *
   * @return true if instance alone, false if check list contents as well (default)
   */
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


  /**
   * Defines whether this list or any objects of this list may be changed.
   *
   * @param immutable true if object is immutable
   * @param withElements true if set elements as well if they implement {@link Immutable}
   */
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
  public boolean retainAll(Collection<?> collection) {
    // re-implemented here to catch removed objects
    boolean someRemoved = false;
    Iterator<E> iter = iterator();
    while (iter.hasNext()) {
      E obj = iter.next();
      if (!collection.contains(obj)) {
        iter.remove();
        assertMutable();
        someRemoved = true;
      }
    }
    return someRemoved;
  }


  @Override
  public E set(int index, E element) {
    E obj = super.set(index, element);
    if (obj != element)  {  // if not exactly the same instance
      try {
        assertMutable();
      }
      catch (ImmutableException imex) {
        super.set(index, obj);
        throw imex;
      }
    }
    return obj;
  }


  /**
   * Asserts that object is mutable.
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

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

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

/**
 * A copy-on-write wrapper for sets.<br>
 * The first modifying operation creates a clone to leave the original set untouched.
 * <p>
 * With {@code iteratorModifying=false}, all operations are supported except {@link java.util.Iterator#remove()},
 * which will throw an {@link UnsupportedOperationException}.<br>
 * With {@code iteratorModifying=true}, {@code iterator()} will create a copy of the set
 * to allow modifications by the iterator.
 * <p>
 * Not to be mixed up with {@link java.util.concurrent.CopyOnWriteArraySet}, which
 * is thread-safe and creates a copy for each modification. {@link CopyOnWriteSet}
 * is not thread-safe and creates a copy only once before the first modification
 * is performed.
 *
 * @author harald
 * @param <E> the type of elements in this set
 */
public class CopyOnWriteSet<E> extends CopyOnWriteCollection<E> implements Set<E> {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a wrapper for a set.
   *
   * @param set the set
   * @param iteratorModifying true if iterators will be modifying, else readonly (avoids copy)
   */
  public CopyOnWriteSet(Set<E> set, boolean iteratorModifying) {
    super(set, iteratorModifying);
  }

  /**
   * Creates a wrapper for a set with read-only iterators.
   *
   * @param set the set
   */
  public CopyOnWriteSet(Set<E> set) {
    super(set);
  }

  /**
   * Creates a wrapper for an empty set.
   */
  public CopyOnWriteSet() {
    this(new HashSet<>());
  }

}

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

import java.util.Collection;

/**
 * Enhanced collection.
 * <p>
 * Adds the {@link Immutable} contract and an option for identity-based {@code equals} and {@code hashCode}.
 *
 * @author harald
 * @param <E> the element type
 */
public interface ImmutableCollection<E> extends Collection<E>, Immutable {

  /**
   * Sets the immutable state.
   *
   * @param immutable true if object is immutable
   * @param withElements true to apply to all elements as well that implement {@link Immutable}
   */
  void setImmutable(boolean immutable, boolean withElements);

  /**
   * Makes this collection finally immutable.<br>
   * Any attempt to make it mutable again results in an {@link ImmutableException}.
   *
   * @param withElements true to apply to all elements as well that implement {@link Immutable}
   */
  void setFinallyImmutable(boolean withElements);

  /**
   * Sets whether the {@code equals()} and {@code hashCode()} methods should refer to the collection instance alone.
   *
   * @param simpleEqualsAndHashCode true if instance alone, false if check the contents as well
   */
  void setSimpleEqualsAndHashCode(boolean simpleEqualsAndHashCode);

  /**
   * Returns whether the {@code equals()} and {@code hashCode()} methods should refer to the collection instance alone.
   *
   * @return true if instance alone, false if check the contents as well (default)
   */
  boolean isSimpleEqualsAndHashCode();

}

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

import org.tentackle.log.Logger.Level;

/**
 * Objects implementing this interface may be mutable or immutable.
 * <p>
 * If an object is immutable, none of its properties/attributes can be changed
 * and any attempt to do so will result in an {@link ImmutableException}.
 * <p>
 * An immutable object can be made mutable again and vice versa. The (im)mutable property
 * applies to all components as well (provided they implement {@link Immutable}).
 * <p>
 * If an object is finally immutable, any attempt to make it mutable again results
 * in an {@link ImmutableException}.
 * <p>
 * Notice the semantic differences between {@link Immutable} and {@link org.tentackle.common.Freezable}.
 *
 * @author harald
 */
public interface Immutable {

  /**
   * Sets the immutable property.
   * <p>
   * Any attempt to modify an immutable object
   * results in an {@link ImmutableException}, unless an immutable logging level is set.
   *
   * @param immutable true if object is immutable
   * @throws ImmutableException if argument is false and the object is finally immutable
   */
  void setImmutable(boolean immutable);

  /**
   * Makes this object finally immutable.<br>
   * Any attempt to make it mutable again results in an {@link ImmutableException}.
   */
  void setFinallyImmutable();

  /**
   * Returns whether the object is immutable.
   * <p>
   * By default, objects are mutable.
   *
   * @return true if immutable
   */
  boolean isImmutable();

  /**
   * Returns whether the object is finally immutable.
   *
   * @return true if finally immutable
   */
  boolean isFinallyImmutable();

  /**
   * Sets the optional logging level.<br>
   * If set, any attempt to modify an immutable object will just
   * log the violation instead of throwing an {@link ImmutableException}.
   *
   * @param immutableLoggingLevel the logging level, null if no logging
   */
  void setImmutableLoggingLevel(Level immutableLoggingLevel);

  /**
   * Gets the logging level.
   *
   * @return the logging level, null if no logging (default)
   */
  Level getImmutableLoggingLevel();

}

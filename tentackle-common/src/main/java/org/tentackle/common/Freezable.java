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

package org.tentackle.common;

/**
 * A freezable object.
 * <p>
 * Some classes are value objects by nature, but are not immutable, for example {@link java.util.Date}.<br>
 * Whenever the use case requires immutability, such objects cannot be used safely.<br>
 * That's why Tentackle provides extended implementations of those types that can be made immutable,
 * such as {@link Date}, {@link Time}, {@link Timestamp}, {@link Binary} and {@link TBinary}.
 * <p>
 * Once an object is frozen, it cannot be unfrozen anymore. However, its clones are unfrozen (if cloneable).<br>
 * Any attempt to modify a frozen object results in a {@link TentackleRuntimeException}.
 */
public interface Freezable {

  /**
   * Makes this object immutable.
   */
  void freeze();

  /**
   * Returns whether this object is frozen.
   *
   * @return true if frozen
   */
  boolean isFrozen();

  /**
   * Freezes a value object null-safe.<br>
   * Method provided to reduce generated code.
   *
   * @param freezable the value object to freeze, may be null
   */
  static void freeze(Freezable freezable) {
    if (freezable != null) {
      freezable.freeze();
    }
  }
}

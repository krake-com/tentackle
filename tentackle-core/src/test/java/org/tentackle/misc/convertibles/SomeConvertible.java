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


package org.tentackle.misc.convertibles;

import org.tentackle.misc.Convertible;

/**
 * Convertible test object.
 *
 * @author harald
 */
public enum SomeConvertible implements Convertible<Integer> {

  ONE(100),
  TWO(200),
  THREE(300);

  private final Integer whatsoever;

  /**
   * Creates a convertible enum.
   *
   * @param whatsoever the integer
   */
  SomeConvertible(Integer whatsoever) {
    this.whatsoever = whatsoever;
  }

  /**
   * Implements the static toInternal mapping.
   *
   * @param external the external value
   * @return the convertible
   */
  public static SomeConvertible toInternal(Integer external) {
    if (external != null) {
      for (SomeConvertible value: values()) {
        if (value.whatsoever.equals(external)) {
          return value;
        }
      }
    }
    throw new IllegalArgumentException("invalid external value " + external);
  }

  @Override
  public Integer toExternal() {
    return whatsoever;
  }

}

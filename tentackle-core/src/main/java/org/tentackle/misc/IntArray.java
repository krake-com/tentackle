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

import java.util.Arrays;

/**
 * Object wrapper for an ints array.
 */
public class IntArray {

  private final int[] ints;

  public IntArray(int[] ints) {
    this.ints = ints;
  }

  public int[] getInts() {
    return ints;
  }

  public void clear() {
    if (ints != null) {
      for (int i=0; i < ints.length; i++) {
        ints[i] = 0;
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IntArray intArray = (IntArray) o;
    return Arrays.equals(ints, intArray.ints);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(ints);
  }
}

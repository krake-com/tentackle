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
 * Object wrapper for a long array.
 */
public class LongArray {

  private final long[] longs;

  public LongArray(long[] longs) {
    this.longs = longs;
  }

  public long[] getLongs() {
    return longs;
  }

  public void clear() {
    if (longs != null) {
      for (int i=0; i < longs.length; i++) {
        longs[i] = 0L;
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
    LongArray longArray = (LongArray) o;
    return Arrays.equals(longs, longArray.longs);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(longs);
  }
}

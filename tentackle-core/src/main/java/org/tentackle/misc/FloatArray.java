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
 * Object wrapper for a char array.
 */
public class FloatArray {

  private final float[] floats;

  public FloatArray(float[] floats) {
    this.floats = floats;
  }

  public float[] getFloats() {
    return floats;
  }

  public void clear() {
    if (floats != null) {
      for (int i=0; i < floats.length; i++) {
        floats[i] = 0.0f;
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
    FloatArray that = (FloatArray) o;
    return Arrays.equals(floats, that.floats);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(floats);
  }
}

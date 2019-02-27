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
 * Object wrapper for a byte array.
 */
public class ByteArray {

  private final byte[] bytes;

  public ByteArray(byte[] bytes) {
    this.bytes = bytes;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public void clear() {
    if (bytes != null) {
      for (int i=0; i < bytes.length; i++) {
        bytes[i] = 0;
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
    ByteArray byteArray = (ByteArray) o;
    return Arrays.equals(bytes, byteArray.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}

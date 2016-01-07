/**
 * Tentackle - http://www.tentackle.org
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

/**
 * Fast array for long primitives.<br>
 * 
 * We cannot use generics because they don't work with primitives (at least not without auto-boxing).
 * Currently, we need that class only as a fast replacement for ArrayList<Long>.
 * 
 * @author harald
 */
public class LongArray {
  
  private long[] array;
  private int count;
  
  /**
   * Creates an empty array of longs.
   *
   * @param size the initial size
   */
  public LongArray(int size) {
    array = new long[size];
    count = 0;
  }
  
  /**
   * Creates an empty array of longs with a default initial size.
   */
  public LongArray() {
    this(32);
  }
  
  
  /**
   * Adds a long value to the array
   *
   * @param value the long value
   */ 
  public void add(long value) {
    if (count >= array.length) {
      long[] nArray = new long[array.length << 1];    // double the size
      System.arraycopy(array, 0, nArray, 0, array.length);
      array = nArray;
    }
    array[count++] = value;
  }
  
  
  /**
   * Gets the array.
   *
   * @return the array, never null.
   */
  public long[] values() {
    if (count < array.length) {
      long[] nArray = new long[count];    // effective size
      if (count > 0) {
        System.arraycopy(array, 0, nArray, 0, count);
      }
      return nArray;
    }
    else {
      return array;
    }
  }
  
}

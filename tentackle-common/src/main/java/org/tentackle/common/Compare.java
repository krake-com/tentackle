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
 * Some handy static null-safe methods to compare two {@link Comparable}s.
 *
 * @author harald
 */
public final class Compare {

  /**
   * Compares two objects.<br>
   * {@code null} is being treated as smallest value.
   * <p>
   * Notice the different semantics compared to {@link java.util.Objects#compare(java.lang.Object, java.lang.Object, java.util.Comparator)}
   * for null-values.
   *
   * @param <T> the class type
   * @param o1 the first object
   * @param o2 the second object
   * @return a negative integer, zero, or a positive integer as o1
   *         is less than, equal to, or greater than o2.
   */
  public static <T extends Comparable<? super T>> int compare (T o1, T o2)  {
    if (o1 == null) {
      return o2 == null ? 0 : -1;
    }
    // o1 != null
    if (o2 == null) {
      return 1;
    }
    return o1.compareTo(o2);
  }

  /**
   * Compares two objects.<br>
   * {@code null} is being treated as largest value.
   *
   * @param <T> the class type
   * @param o1 the first object
   * @param o2 the second object
   * @return a negative integer, zero, or a positive integer as o1
   *         is less than, equal to, or greater than o2.
   */
  public static <T extends Comparable<? super T>> int compareWithNullLargest (T o1, T o2)  {
    if (o1 == null) {
      return o2 == null ? 0 : 1;
    }
    // o1 != null
    if (o2 == null) {
      return -1;
    }
    return o1.compareTo(o2);
  }


  private Compare() {
  }

}
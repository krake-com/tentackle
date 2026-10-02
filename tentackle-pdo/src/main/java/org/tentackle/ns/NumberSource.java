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

package org.tentackle.ns;

/**
 * A source of unique numbers.
 *
 * @author harald
 */
public interface NumberSource {

  /*
   * Notice: all methods throw a NumberSourceException if the pool vanished.
   * All methods except isOnline() throw a NumberSourceException if the pool is offline.
   */

  /**
   * A range of numbers.
   *
   * @param begin the first number
   * @param end   the last number + 1
   */
  record Range(long begin, long end) {

    /**
     * Gets the size of the range.
     *
     * @return the size
     */
    public long size() {
      return end - begin;
    }
  }


  /**
   * Gets the ranges of this source.<br>
   * Does not consume any numbers.
   * The ranges are not sorted and may contain empty ranges.
   *
   * @return the ranges, never null
   */
  Range[] getRanges();

  /**
   * Returns the count of numbers in the pool.
   *
   * @return the number of numbers
   */
  long getCount();

  /**
   * Returns whether pool is empty.
   *
   * @return true if empty
   */
  boolean isEmpty();

  /**
   * Returns whether pool is online.<br>
   * Offline pools reject all other methods with a {@link NumberSourceException}.
   *
   * @return true if online (enabled), else offline (disabled)
   */
  boolean isOnline();

  /**
   * Pops the next unique number from the source.
   *
   * @return the next number
   * @throws NumberSourceEmptyException if pool is empty
   */
  long popNumber();

  /**
   * Pops the next unique numbers from the source.<br>
   * If the pool runs out of numbers, fewer than {@code count} numbers are returned.
   *
   * @param count the number count
   * @return the next numbers as ranges sorted by numbers (ascending), never null
   * @throws NumberSourceEmptyException if pool is empty
   */
  Range[] popNumbers(long count);

  /**
   * Pops a range or ranges of unique numbers from the source.<br>
   * The returned ranges are sorted by numbers (ascending).
   *
   * @param begin the first number
   * @param end the last number + 1
   * @return the numbers range(s) &ge; <code>begin</code> and &lt; <code>end</code>, never null
   */
  Range[] popNumbers(long begin, long end);

  /**
   * Pushes a number to the number source.<br>
   * If the number is already in the number source, nothing happens.
   *
   * @param number number to be pushed
   */
  void pushNumber(long number);

  /**
   * Pushes a range of unique numbers to the source.<br>
   * The range may overlap with existing numbers.
   *
   * @param begin the first number
   * @param end the last number + 1
   */
  void pushNumbers(long begin, long end);

}

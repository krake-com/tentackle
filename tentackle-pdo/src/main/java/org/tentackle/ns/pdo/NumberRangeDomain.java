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


package org.tentackle.ns.pdo;

import org.tentackle.ns.NumberSource.Range;
import org.tentackle.pdo.DomainObject;


/**
 * Number range domain interface.
 *
 * @author harald
 */
public interface NumberRangeDomain extends DomainObject<NumberRange> {

  /**
   * Returns whether range is empty.
   *
   * @return true if empty
   */
  boolean isEmpty();

  /**
   * Gets the size of the range.
   *
   * @return the numbers in that range
   */
  long size();

  /**
   * Pops the next number from the range.
   *
   * @return the number
   * @throws org.tentackle.ns.NumberSourceEmptyException if range is empty
   */
  long popNumber();

  /**
   * Pops the next numbers from the range.
   *
   * @param count amount of numbers to pop
   * @return the numbers
   * @throws org.tentackle.ns.NumberSourceEmptyException if range is empty
   */
  Range popNumbers(long count);

  /**
   * Returns whether requested range intersects with this range.
   *
   * @param begin the first number
   * @param end the last number + 1
   * @return true if intersects, false if not
   */
  boolean intersects(long begin, long end);

}

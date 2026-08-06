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

import org.tentackle.pdo.DomainObject;

/**
 * Number pool domain interface.
 *
 * @author harald
 */
public interface NumberPoolDomain extends DomainObject<NumberPool> {

  /**
   * Get the current range.<br>
   * This is the first non-empty range with the lowest "begin".
   * <p>
   * Despite its name, the method <strong>modifies the pool</strong>: exhausted ranges are removed
   * from {@link NumberPool#getNumberRangeList()} along the way and are deleted from the database
   * when the pool is next persisted. This also happens when the pool turns out to be empty, i.e.,
   * before the {@link org.tentackle.ns.NumberSourceEmptyException} is thrown.
   * <p>
   * Invoke it only within a transaction that persists the pool afterward or is rolled back, and
   * with the pool locked, as {@code DefaultNumberSource} does. It performs no locking itself.
   *
   * @return the current range
   * @throws org.tentackle.ns.NumberSourceEmptyException if no more non-empty range found
   */
  NumberRange getCurrentRange();

  /**
   * Returns whether this pool is a slave pool and gets its ranges from an uplink.
   *
   * @return true if slave
   */
  boolean isSlave();

}

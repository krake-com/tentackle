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

package org.tentackle.persist;

import org.tentackle.common.Timestamp;

/**
 * A token lock as maintained by the {@link LockManager}.<br>
 * Mirrors the token lock attributes (editedBy, editedSince and editedExpiry) of a PDO.
 */
public interface TokenLock {

  /**
   * Gets the class ID of the locked PDO.
   *
   * @return the class ID
   */
  int getPdoClassId();

  /**
   * Gets the object ID of the locked PDO.
   *
   * @return the object ID
   */
  long getPdoId();

  /**
   * Gets the object ID of the user holding the token.
   *
   * @return the user ID, 0 = unlocked
   */
  long getLockedBy();

  /**
   * Gets the timestamp when editing started.
   *
   * @return the timestamp when editing started
   */
  Timestamp getLockedSince();

  /**
   * Gets the timestamp when the token will expire.
   *
   * @return the timestamp when the token will expire
   */
  Timestamp getLockExpiry();

}

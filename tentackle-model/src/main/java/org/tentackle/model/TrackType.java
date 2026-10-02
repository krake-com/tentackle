/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.model;

/**
 * Object tracking type.
 *
 * @author harald
 */
public enum TrackType {

  /** PDO is untracked. */
  UNTRACKED,

  /** PDO knows whether modified or not. */
  TRACKED,

  /** like TRACKED but also knows which attributes are modified. */
  ATTRACKED,

  /** like ATTRACKED but keeps the old persisted values as well. */
  FULLTRACKED;


  /**
   * Returns true if TRACKED, ATTRACKED or FULLTRACKED.
   *
   * @return true if tracked
   */
  public boolean isTracked() {
    return this != UNTRACKED;
  }

  /**
   * Returns true if ATTRACKED or FULLTRACKED.
   *
   * @return true if attribute-tracked
   */
  public boolean isAttracked() {
    return this == ATTRACKED || this == FULLTRACKED;
  }

}

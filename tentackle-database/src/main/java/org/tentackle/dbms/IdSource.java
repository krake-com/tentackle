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

// Created on September 10, 2004, 2:57 PM

package org.tentackle.dbms;

/**
 * Source of ID numbers.<p>
 *
 * Id sources are unique instances per Db.
 * For example, by default there is one ObjectId instance per Db.
 * However, implementations _may_ be able to be shared among Dbs,
 * so it's up to the implementation whether it tolerates different
 * Dbs or not.
 *
 * @author harald
 */

public interface IdSource {

  /**
   * Get the next unique ID.
   *
   * @param db the current db connection to be checked against getDb()
   * @return a free ID.
   */
  long nextId(Db db);


  /**
   * Returns whether retrieving an id is a lock-free operation.<br>
   *
   * @return true if lockfree
   */
  boolean isLockFree();

}

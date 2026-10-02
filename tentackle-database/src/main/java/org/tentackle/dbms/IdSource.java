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

package org.tentackle.dbms;

/**
 * Source of ID numbers.
 * <p>
 * Whether an id source is shared depends on the implementation: an {@link ObjectSequenceId}
 * is unique per sequence name and shared by all sessions, whereas an {@link ObjectId} is
 * created per configuration.
 * Each persistent class uses the id source configured for it, or the default id source of the
 * first session requesting an id for that class.
 *
 * @see IdSourceConfigurator
 * @see DbObjectClassVariables#getIdSource(Db)
 *
 * @author harald
 */
public interface IdSource {

  /**
   * Gets the next unique ID.
   *
   * @param db the current session
   * @return a free ID.
   */
  long nextId(Db db);

  /**
   * Returns whether retrieving an id is a lock-free operation.
   *
   * @return true if lock-free
   */
  boolean isLockFree();

}

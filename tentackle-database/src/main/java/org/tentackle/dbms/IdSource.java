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
 * Each persistent class uses the id source configured for it, or the default id source of the
 * session requesting the id.
 * <p>
 * There is usually one id source per configuration. The implementation must
 * make sure that the ids are unique per physical backend.<br>
 * While {@link ObjectId} does not maintain any internal state, {@link ObjectSequenceId}
 * maintains an id range per physical backend (via the backend identifier).
 *
 * @see IdSourceConfigurator
 * @see DbObjectClassVariables#getIdSource(Db)
 * @see Db#getBackendIdentity()
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

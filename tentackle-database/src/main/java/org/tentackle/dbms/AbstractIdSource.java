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

package org.tentackle.dbms;

import org.tentackle.session.PersistenceException;

/**
 * Base implementation for {@link IdSource}s.
 *
 * @author harald
 */
public abstract class AbstractIdSource implements IdSource {

  private final String name;    // the source's name

  /**
   * Creates an ID source.
   *
   * @param name the name
   */
  public AbstractIdSource(String name) {
    this.name = name;
  }


  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }


  /**
   * Asserts that the session is not remote.
   *
   * @param db the session
   * @throws IdSourceException if the session is remote
   */
  protected void assertDbNotRemote(Db db) {
    if (db.isRemote()) {
      throw new IdSourceException(db, "remote Db not allowed");
    }
  }

  /**
   * Asserts that an update affects exactly one row.
   *
   * @param db the session
   * @param rowCount the number of rows affected
   */
  protected void assertOneRowAffected(Db db, int rowCount) {
    if (rowCount != 1) {
      throw new PersistenceException(db, "unexpected number of rows affected: " + rowCount);
    }
  }

}

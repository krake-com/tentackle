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

package org.tentackle.sql.backends;

import org.tentackle.common.Service;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.NonStandardCommons;

/**
 * Backend for MariaDB.
 * <p>
 * Requires at least MariaDB 10.3, which is the first version providing sequences.
 *
 * @author harald
 */
@Service(Backend.class)
public class MariaDb extends MySql {

  /** minimum major version. */
  private static final int MIN_MAJOR_VERSION = 10;

  /** minimum minor version if {@link #MIN_MAJOR_VERSION}. */
  private static final int MIN_MINOR_VERSION = 3;


  /**
   * Creates the MariaDb backend.
   */
  public MariaDb() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":mariadb");
  }

  @Override
  public String getName() {
    return "MariaDB";
  }

  @Override
  public String getDriverClassName() {
    return "org.mariadb.jdbc.Driver";
  }

  @Override
  public void validateVersion(int databaseMajorVersion, int databaseMinorVersion) {
    if (databaseMajorVersion < MIN_MAJOR_VERSION ||
        databaseMajorVersion == MIN_MAJOR_VERSION && databaseMinorVersion < MIN_MINOR_VERSION) {
      throw new BackendException(this + " needs at least MariaDB " + MIN_MAJOR_VERSION + "." + MIN_MINOR_VERSION +
                                 ", but is connected to " + databaseMajorVersion + "." + databaseMinorVersion +
                                 " (the MariaDB driver connects to MySQL servers as well: consider adding '|MySQL' " +
                                 "to the connection URL if this is a MySQL server)");
    }
  }

  @Override
  protected boolean isDropIfExistsSupported() {
    return true;
  }

  @Override
  public boolean isFunctionBasedIndexSupported() {
    // unlike MySQL, MariaDB has no functional indexes at all: an expression can only be
    // indexed by adding a generated column for it, which is beyond the model's scope.
    return false;
  }

  @Override
  public boolean isSequenceSupported() {
    return true;
  }

  @Override
  public String sqlNextFromSequence(String name) {
    // NEXTVAL takes the sequence's identifier, not a string literal as in postgres
    return "SELECT NEXTVAL(" + name + ")";
  }

  @Override
  public String sqlCreateSequence(String name, Long start, Long increment) {
    return NonStandardCommons.sqlCreateSequence(name, start, increment);
  }
}

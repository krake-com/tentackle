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

import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.DefaultScriptRunner;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Script runner for PostgreSQL.<br>
 * Determines the support for POSIX escapes from the server setting {@code standard_conforming_strings}.
 */
public class PostgresScriptRunner extends DefaultScriptRunner {

  /**
   * Creates a script runner.
   *
   * @param backend the backend
   * @param connection the SQL connection
   * @see Postgres#createScriptRunner(Connection)
   */
  public PostgresScriptRunner(Backend backend, Connection connection) {
    super(backend, connection);
  }


  @Override
  protected boolean determinePosixEscapeSyntaxSupported() {
    // depends on the connection settings
    try (Statement statement = getConnection().createStatement()) {
      ResultSet resultSet = statement.executeQuery("SELECT setting FROM pg_settings WHERE name = 'standard_conforming_strings'");
      if (resultSet.next()) {
        String setting = resultSet.getString(1);
        return "off".equals(setting);
      }
      return false;
    }
    catch (SQLException ex) {
      throw new BackendException("cannot determine POSIX escape syntax support", ex);
    }
  }

}

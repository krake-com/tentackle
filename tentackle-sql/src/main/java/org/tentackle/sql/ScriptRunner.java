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

package org.tentackle.sql;

import java.sql.Connection;
import java.util.List;

/**
 * SQL script runner.
 */
public interface ScriptRunner {

  /**
   * Gets the backend for this script runner.
   *
   * @return the backend
   */
  Backend getBackend();

  /**
   * Gets the connection for this script runner.
   *
   * @return the connection
   */
  Connection getConnection();

  /**
   * Enables or disables JDBC escape processing.
   *
   * @param enabled true if escape processing is enabled
   * @see java.sql.Statement#setEscapeProcessing(boolean)
   */
  void setEscapeProcessingEnabled(boolean enabled);

  /**
   * Returns whether JDBC escape processing is enabled.
   *
   * @return true if enabled (default)
   */
  boolean isEscapeProcessingEnabled();

  /**
   * Runs the SQL script.
   * <p>
   * Throws {@link BackendException} if script execution failed.
   *
   * @param script the SQL script
   * @return the results, never null
   */
  List<ScriptRunnerResult> run(String script);

}

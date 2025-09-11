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
import java.util.function.Consumer;

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
   * Sets the maximum number of rows to be returned by each statement within the script.<br>
   * The limit must be set before executing the script!
   *
   * @param maxRows the maximum number of rows, 0 if no limit (default)
   */
  void setMaxRows(int maxRows);

  /**
   * Gets the maximum number of rows to be returned by each statement within the script.
   *
   * @return the maximum number of rows, 0 if no limit (default)
   */
  int getMaxRows();

  /**
   * Sets the execution timeout for each statement in seconds.<br>
   * The timeout must be set before executing the script!
   *
   * @param seconds the timeout in seconds, 0 if no timeout (default)
   */
  void setExecutionTimeout(int seconds);

  /**
   * Gets the execution timeout in seconds.
   *
   * @return the timeout in seconds, 0 if no timeout (default)
   */
  int getExecutionTimeout();

  /**
   * Runs the SQL script.
   * <p>
   * Throws {@link ScriptRunnerException} if the execution of some SQL statement within the failed.<br>
   * Throws {@link BackendException} if some other error occurred.
   *
   * @param script the SQL script
   * @return the results, never null
   */
  List<ScriptRunnerResult> run(String script);

  /**
   * Cancels the script execution.
   */
  void cancel();

  /**
   * Returns whether the script execution was canceled.
   *
   * @return true if canceled
   */
  boolean isCanceled();

  /**
   * Adds a listener to be notified when each statement is submitted to the backend.
   *
   * @param listener the SQL code being executed by the backend
   */
  void addExecutionListener(Consumer<String> listener);

  /**
   * Adds a listener to be notified when each statement is successfully executed.
   *
   * @param listener the execution result
   */
  void addResultListener(Consumer<ScriptRunnerResult> listener);

}

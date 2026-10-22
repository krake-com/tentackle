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
import java.sql.ResultSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * SQL script runner.<br>
 * Splits a script into statements according to the backend's statement separator and comment syntax,
 * and executes them one by one.
 * <p>
 * For each executed statement, a {@link ScriptRunnerResult} is returned. Additional results without SQL code
 * report warnings not related to a single statement, such as the warnings of the connection.
 *
 * @see Backend#createScriptRunner(Connection)
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
   * Sets the connection to read-only mode during execution.<br>
   * If the connection is already in read-only mode, this method has no effect,
   * even if {@code readOnly} is false.
   * The connection is switched back to read-write mode after the script execution
   * if the connection was in read-write mode before.
   *
   * @param readOnly true if read-only
   */
  void setReadOnly(boolean readOnly);

  /**
   * Returns whether the connection is used in read-only mode.
   *
   * @return true if read-only
   */
  boolean isReadOnly();

  /**
   * Requests that the script should be executed within a transaction.<br>
   * If there is already a transaction running, this method has no effect,
   * even if {@code transactional} is false.
   * <p>
   * A transaction started by the script runner is committed if the script ran to completion
   * and rolled back if the script failed or was canceled.
   *
   * @param transactional true if start a transaction if none is running
   */
  void setTransactional(boolean transactional);

  /**
   * Returns whether the script is executed within a transaction.
   *
   * @return true if transactional
   */
  boolean isTransactional();

  /**
   * Runs the SQL script.
   * <p>
   * Throws {@link ScriptRunnerException} if the execution of some SQL statement within the script failed.<br>
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
   * @param listener the listener receives the SQL code
   */
  void addExecutionListener(Consumer<String> listener);

  /**
   * Adds a listener to be notified when each statement is successfully executed.
   *
   * @param listener the listener receives the runner result
   */
  void addResultListener(Consumer<ScriptRunnerResult> listener);

  /**
   * Adds a listener to be notified when each result set has been processed.<br>
   * Since the result set is positioned after the end of the last row,
   * the listener must invoke {@link ResultSet#beforeFirst()} to process it again.
   * <p>
   * The backend must support {@link ResultSet#TYPE_SCROLL_INSENSITIVE}
   * to be able to use this feature.
   * <p>
   * The result set is closed after all result set listeners have been notified.
   *
   * @param listener the listener receives the SQL code and the result set
   */
  void addResultSetListener(BiConsumer<String, ResultSet> listener);

}

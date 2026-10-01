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

import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.StringHelper;
import org.tentackle.sql.backends.AbstractBackend;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Default implementation of a script runner.
 * <p>
 * The script is split into statements at the backend's {@link Backend#getStatementSeparator() statement separator}.
 * Single-line and block comments are skipped, separators and comment markers within single or double quotes
 * are ignored, and backslash escapes are passed to the backend as is, if the backend supports POSIX escapes.
 */
public class DefaultScriptRunner implements ScriptRunner {

  private final Backend backend;
  private final Connection connection;
  private final boolean posixEscapeSyntaxSupported;
  private final AtomicBoolean canceled = new AtomicBoolean();
  private final AtomicReference<Statement> statementRef = new AtomicReference<>();

  private boolean escapeProcessing = true;              // as per JDBC specs enabled by default
  private int maxRows;                                  // optional max. number of rows in resultsets
  private int executionTimeout;                         // optional query timeout in seconds, 0 for none
  private boolean readOnly;                             // optional read-only flag for the connection
  private boolean transactional;                        // true if the script is enclosed in a transaction

  /** the listeners invoked with the SQL code of each statement before it is executed. */
  protected final List<Consumer<String>> executionListeners = new ArrayList<>();

  /** the listeners invoked with the result of each statement. */
  protected final List<Consumer<ScriptRunnerResult>> resultListeners = new ArrayList<>();

  /** the listeners invoked with the SQL code and the open result set of each query. */
  protected final List<BiConsumer<String, ResultSet>> resultSetListeners = new ArrayList<>();


  /**
   * The next SQL code to execute as a statement.
   *
   * @param sql        the SQL statement to execute
   * @param nextOffset the offset where to start searching for the next statement within the script
   */
  public record SQLCode(String sql, int nextOffset) {

    /**
     * Creates the SQL code.<br>
     * Leading whitespace is removed from the SQL statement.
     *
     * @param sql        the SQL statement to execute
     * @param nextOffset the offset where to start searching for the next statement within the script
     */
    public SQLCode {
      // remove leading whitespaces
      sql = sql.stripLeading();
    }
  }


  /**
   * Creates a script runner.
   *
   * @param backend    the backend
   * @param connection the SQL connection
   * @see AbstractBackend#createScriptRunner(Connection)
   */
  public DefaultScriptRunner(Backend backend, Connection connection) {
    this.backend = backend;
    this.connection = connection;
    this.posixEscapeSyntaxSupported = determinePosixEscapeSyntaxSupported();
  }

  @Override
  public Backend getBackend() {
    return backend;
  }

  @Override
  public Connection getConnection() {
    return connection;
  }

  @Override
  public void setEscapeProcessingEnabled(boolean enabled) {
    escapeProcessing = enabled;
  }

  @Override
  public boolean isEscapeProcessingEnabled() {
    return escapeProcessing;
  }

  @Override
  public void setMaxRows(int maxRows) {
    this.maxRows = maxRows;
  }

  @Override
  public int getMaxRows() {
    return maxRows;
  }

  @Override
  public void setExecutionTimeout(int executionTimeout) {
    this.executionTimeout = executionTimeout;
  }

  @Override
  public int getExecutionTimeout() {
    return executionTimeout;
  }

  @Override
  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public void setTransactional(boolean transactional) {
    this.transactional = transactional;
  }

  @Override
  public boolean isTransactional() {
    return transactional;
  }

  @Override
  public List<ScriptRunnerResult> run(String script) {
    canceled.set(false);
    SQLCode sqlCode = null;
    int offset = 0;
    List<ScriptRunnerResult> scriptRunnerResults = new ArrayList<>();

    boolean readOnlySet = false;          // true if the connection was switched to read-only by this runner
    boolean transactionStarted = false;   // true if the transaction started by this runner
    boolean commit = false;               // true if the transaction started by this runner must be committed

    try (Statement statement = createStatement(connection)) {

      if (readOnly && !connection.isReadOnly()) {
        connection.setReadOnly(true);
        readOnlySet = true;
      }
      if (transactional && connection.getAutoCommit()) {
        connection.setAutoCommit(false);
        transactionStarted = true;
      }

      statement.setEscapeProcessing(escapeProcessing);
      if (maxRows > 0) {
        statement.setMaxRows(maxRows);
      }
      if (executionTimeout > 0) {
        statement.setQueryTimeout(executionTimeout);
      }
      statementRef.set(statement);

      while (!canceled.get() && (sqlCode = determineNextSqlCode(script, offset)) != null) {

        if (!sqlCode.sql.isBlank()) {
          fireExecutionListeners(sqlCode.sql);
          long millis = System.currentTimeMillis();
          boolean isResultSet = statement.execute(sqlCode.sql);
          millis = System.currentTimeMillis() - millis;
          StringBuilder warnings = new StringBuilder();
          if (isResultSet) {
            // since we execute statement by statement, we should not create more than one result set,
            // but one never knows...
            do {
              ResultSet resultSet = statement.getResultSet();
              Results results = extractResults(resultSet, warnings);
              extractWarnings(statement.getWarnings(), warnings);
              statement.clearWarnings();
              ScriptRunnerResult result = new ScriptRunnerResult(sqlCode.sql, offset, warnings.toString(), millis, results.columnCount,
                                                                 results.columnTypes, results.columnNames, results.data.toArray());
              scriptRunnerResults.add(result);
              warnings.setLength(0);
              fireResultListeners(result);
              fireResultSetListeners(sqlCode.sql, resultSet);
            }
            while (statement.getMoreResults());   // this closes the current result set
          }
          int updateCount = statement.getUpdateCount();
          if (updateCount != -1) {
            extractWarnings(statement.getWarnings(), warnings);
            ScriptRunnerResult result = new ScriptRunnerResult(sqlCode.sql, offset, warnings.toString(), millis, updateCount);
            scriptRunnerResults.add(result);
            fireResultListeners(result);
          }
          statement.clearWarnings();
        }

        if (sqlCode.nextOffset > offset) {
          offset = sqlCode.nextOffset;
        }
        else {
          throw new BackendException("malformed SQL script");   // avoid endless loops
        }
      }

      StringBuilder warnings = new StringBuilder();
      extractWarnings(connection.getWarnings(), warnings);
      connection.clearWarnings();
      if (!warnings.isEmpty()) {
        ScriptRunnerResult result = new ScriptRunnerResult(warnings.toString());
        scriptRunnerResults.add(result);
        fireResultListeners(result);
      }
      // a canceled script is rolled back as well: transactional means all or nothing
      commit = !canceled.get();
      return scriptRunnerResults;
    }
    catch (SQLException ex) {
      if (sqlCode != null) {
        throw new ScriptRunnerException("script execution failed at line " +
                                        (StringHelper.countOccurrences(script.substring(0, sqlCode.nextOffset), '\n') + 1) + ": " +
                                        StringHelper.firstLine(sqlCode.sql) + " ...\n" +
                                        ExceptionHelper.concatenateMessages(ex),
                                        sqlCode.sql, scriptRunnerResults, ex);
      }
      throw new BackendException("script execution failed", ex);
    }
    finally {
      try {
        if (transactionStarted) {
          // Terminate the transaction explicitly. A failed statement does not roll back the
          // transaction on all backends, and setAutoCommit(true) would silently commit
          // whatever the script managed to execute before the error.
          if (commit) {
            connection.commit();
          }
          else {
            connection.rollback();
          }
          connection.setAutoCommit(true);
        }
        if (readOnlySet) {
          // switch back to read-write if read-only was set by this script runner
          connection.setReadOnly(false);
        }
      }
      catch (SQLException sx) {
        // this should not happen! We have no logger... Hmm...
        scriptRunnerResults.add(new ScriptRunnerResult(ExceptionHelper.concatenateMessages(sx)));
      }
    }
  }

  @Override
  public void cancel() {
    canceled.set(true);
    try {
      Statement statement = statementRef.get();
      if (statement != null && !statement.isClosed()) {
        statement.cancel();
      }
    }
    catch (SQLException sx) {
      throw new BackendException("canceling script failed", sx);
    }
  }

  @Override
  public boolean isCanceled() {
    return canceled.get();
  }

  @Override
  public void addExecutionListener(Consumer<String> listener) {
    executionListeners.add(listener);
  }

  @Override
  public void addResultListener(Consumer<ScriptRunnerResult> listener) {
    resultListeners.add(listener);
  }

  @Override
  public void addResultSetListener(BiConsumer<String, ResultSet> listener) {
    resultSetListeners.add(listener);
  }

  /**
   * Determines the next SQL code to execute.
   *
   * @param script the SQL script
   * @param offset the offset where to start in the script to find the next statement
   * @return the next SQL code to send to the database, null if the end of the script is reached
   */
  public SQLCode determineNextSqlCode(String script, int offset) {

    StringBuilder sql = new StringBuilder();
    boolean withinSingleQuotes = false;
    boolean withinDoubleQuotes = false;
    boolean withinPosixEscape = false;
    boolean withinBlockComment = false;

    // create SQL until we find the next statement separator, skipping comments
    while (offset < script.length()) {
      char c = script.charAt(offset);
      if (withinPosixEscape) {
        withinPosixEscape = false;
        if (!withinBlockComment) {
          sql.append(c);    // just send it to the backend "as is" -- no further check below
        }
      }
      else {
        if (posixEscapeSyntaxSupported && c == '\\') {
          withinPosixEscape = true;
          if (!withinBlockComment) {
            sql.append(c);    // leave escape handling up to the backend!
          }
        }
        else if (withinBlockComment) {
          boolean endFound = false;
          for (String blockEnd : backend.getBlockCommentEnds()) {
            if (script.startsWith(blockEnd, offset)) {
              offset += blockEnd.length();
              endFound = true;
            }
          }
          if (endFound) {
            withinBlockComment = false;
            continue;
          }
        }
        else {
          if (!withinDoubleQuotes && c == '\'') {
            withinSingleQuotes = !withinSingleQuotes;
          }
          else if (!withinSingleQuotes && c == '"') {
            withinDoubleQuotes = !withinDoubleQuotes;
          }

          if (!withinDoubleQuotes && !withinSingleQuotes) {
            if (script.startsWith(backend.getStatementSeparator(), offset)) {
              // found!
              return new SQLCode(sql.toString(), offset + backend.getStatementSeparator().length());
            }

            String match = StringHelper.startsWithAnyOf(script, offset, backend.getSingleLineComments());
            if (match != null) {
              offset += match.length();
              // skip to the next line
              int ndx = script.indexOf('\n', offset);
              if (ndx >= offset) {
                offset = ndx - 1;    // +1 will be added below
              }
              else {
                break;    // end of the script
              }
            }
            else if ((match = StringHelper.startsWithAnyOf(script, offset, backend.getBlockCommentBegins())) != null) {
              withinBlockComment = true;
              offset += match.length() - 1;   // +1 will be added below
            }
            else {
              sql.append(c);
            }
          }
          else {
            sql.append(c);
          }
        }
      }
      offset++;
    }

    // if some "within..." are still true at the end of the script,
    // just leave it up to the backend to deal with that and don't throw an exception.

    return sql.isEmpty() ? null : new SQLCode(sql.toString(), offset);
  }


  /**
   * Determines whether POSIX escape syntax is supported by the backend and/or connection.
   *
   * @return true if supported
   */
  protected boolean determinePosixEscapeSyntaxSupported() {
    return backend.isPosixEscapeSyntaxSupported();
  }

  /**
   * Fires execution listeners.
   *
   * @param sql the SQL statement
   */
  protected void fireExecutionListeners(String sql) {
    executionListeners.forEach(listener -> listener.accept(sql));
  }

  /**
   * Fires result listeners.
   *
   * @param result the result
   */
  protected void fireResultListeners(ScriptRunnerResult result) {
    resultListeners.forEach(listener -> listener.accept(result));
  }

  /**
   * Fires result set listeners.
   *
   * @param sql the SQL statement
   * @param resultSet the result set
   */
  protected void fireResultSetListeners(String sql, ResultSet resultSet) {
    resultSetListeners.forEach(listener -> listener.accept(sql, resultSet));
  }

  /**
   * Creates the statement.
   *
   * @param connection the connection to use for creating the statement
   * @return the statement
   * @throws SQLException if some backend error
   */
  protected Statement createStatement(Connection connection) throws SQLException {
    return resultSetListeners.isEmpty() ?
             connection.createStatement() :
             connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
  }


  private record Results(int columnCount, int[] columnTypes, String[] columnNames, List<Object> data) { }

  private Results extractResults(ResultSet resultSet, StringBuilder warnings) throws SQLException {
    List<Object> data = new ArrayList<>();
    ResultSetMetaData metaData = resultSet.getMetaData();
    int columnCount = metaData.getColumnCount();
    int[] columnTypes = new int[columnCount];
    String[] columnNames = new String[columnCount];
    for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
      columnTypes[columnIndex - 1] = metaData.getColumnType(columnIndex);
      columnNames[columnIndex - 1] = metaData.getColumnName(columnIndex);
    }
    while (resultSet.next()) {
      for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
        data.add(resultSet.getObject(columnIndex));
      }
      extractWarnings(resultSet.getWarnings(), warnings);
    }
    return new Results(columnCount, columnTypes, columnNames, data);
  }

  private void extractWarnings(SQLWarning warning, StringBuilder buf) {
    while (warning != null) {
      if (!buf.isEmpty()) {
        buf.append('\n');
      }
      buf.append(warning.getMessage());
      warning = warning.getNextWarning();
    }
  }

}

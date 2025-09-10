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

/**
 * Default implementation of a script runner.
 */
public class DefaultScriptRunner implements ScriptRunner {

  private final Backend backend;
  private final Connection connection;
  private final boolean posixEscapeSyntaxSupported;
  private final AtomicBoolean cancelled;
  private final AtomicReference<Statement> statementRef;

  private boolean escapeProcessing = true;              // as per JDBC specs enabled by default
  private int maxRows;                                  // optional max. number of rows in resultsets
  private int executionTimeout;                         // optional query timeout in seconds, 0 for none

  /**
   * The next SQL code to execute as a statement.
   *
   * @param sql        the SQL statement to execute
   * @param nextOffset the offset where to start searching for the next statement within the script
   */
  public record SQLCode(String sql, int nextOffset) {
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
    this.cancelled = new AtomicBoolean();
    this.statementRef = new AtomicReference<>();
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
  public List<ScriptRunnerResult> run(String script) {
    SQLCode sqlCode = null;
    int offset = 0;
    List<ScriptRunnerResult> scriptRunnerResults = new ArrayList<>();

    try (Statement statement = connection.createStatement()) {
      statement.setEscapeProcessing(escapeProcessing);
      if (maxRows > 0) {
        statement.setMaxRows(maxRows);
      }
      if (executionTimeout > 0) {
        statement.setQueryTimeout(executionTimeout);
      }
      statementRef.set(statement);

      while (!cancelled.get() && (sqlCode = determineNextSqlCode(script, offset)) != null) {

        if (!sqlCode.sql.isBlank()) {
            long millis = System.currentTimeMillis();
            boolean isResultSet = statement.execute(sqlCode.sql);
            millis = System.currentTimeMillis() - millis;
            StringBuilder warnings = new StringBuilder();
            if (isResultSet) {
              // since we execute statement by statement, we should not create more than one result set,
              // but one never knows...
              do {
                ResultSet resultSet = statement.getResultSet();
                List<Object> resultList = new ArrayList<>();
                int columnCount = extractResults(resultSet, warnings, resultList);
                extractWarnings(statement.getWarnings(), warnings);
                statement.clearWarnings();
                scriptRunnerResults.add(new ScriptRunnerResult(sqlCode.sql, offset, warnings.toString(), millis, columnCount, resultList.toArray()));
                warnings.setLength(0);
              }
              while (statement.getMoreResults());   // this closes the current result set
            }
            int updateCount = statement.getUpdateCount();
            if (updateCount != -1) {
              extractWarnings(statement.getWarnings(), warnings);
              scriptRunnerResults.add(new ScriptRunnerResult(sqlCode.sql, offset, warnings.toString(), millis, 0, updateCount));
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
        scriptRunnerResults.add(new ScriptRunnerResult("", 0, warnings.toString(), 0L, 0, "Connection Warnings"));
      }
      return scriptRunnerResults;
    }
    catch (SQLException ex) {
      if (sqlCode != null) {
        throw new ScriptRunnerException("script execution failed at offset " + offset + ": " +
                                        StringHelper.firstLine(sqlCode.sql) + " ...\n" +
                                        ExceptionHelper.concatenateMessages(ex),
                                        scriptRunnerResults, ex);
      }
      throw new ScriptRunnerException("script runner failed", scriptRunnerResults, ex);
    }
  }

  @Override
  public void cancel() {
    cancelled.set(true);
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

  /**
   * Determines the next SQL code to execute.
   *
   * @param script the SQL script
   * @param offset the offset where to start in the script to find the next statement
   * @return the next SQL code to send to the database, null if the end of the script found
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
   * Determines whether Posix escape syntax is supported by the backend and/or connection.
   *
   * @return true if supported
   */
  protected boolean determinePosixEscapeSyntaxSupported() {
    return backend.isPosixEscapeSyntaxSupported();
  }


  private int extractResults(ResultSet resultSet, StringBuilder warnings, List<Object> resultList) throws SQLException {
    ResultSetMetaData metaData = resultSet.getMetaData();
    int columnCount = metaData.getColumnCount();
    boolean first = true;
    while (resultSet.next()) {
      if (first) {
        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
          resultList.add(metaData.getColumnName(columnIndex));
        }
        first = false;
      }
      for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
        resultList.add(resultSet.getObject(columnIndex));
      }
      extractWarnings(resultSet.getWarnings(), warnings);
    }
    return columnCount;
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

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

/**
 * Default implementation of a script runner.
 */
public class DefaultScriptRunner implements ScriptRunner {

  private final Backend backend;
  private final Connection connection;
  private final boolean posixEscapeSyntaxSupported;

  private boolean escapeProcessing = true;              // as per JDBC specs enabled by default

  /**
   * The next SQL code to execute as a statement.
   *
   * @param sql the SQL statement to execute
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
   * @param backend the backend
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
  public List<ScriptRunnerResult> run(String script) {
    SQLCode sqlCode = null;
    int offset = 0;
    try (Statement statement = connection.createStatement()) {
      statement.setEscapeProcessing(escapeProcessing);
      List<ScriptRunnerResult> scriptRunnerResults = new ArrayList<>();
      while ((sqlCode = determineNextSqlCode(script, offset)) != null) {
        if (!sqlCode.sql.isBlank()) {
          List<Object> resultList = new ArrayList<>();
          int columnCount = 0;
          if (statement.execute(sqlCode.sql)) {
            ResultSet resultSet = statement.getResultSet();
            do {
              while (resultSet.next()) {
                columnCount = extractResults(resultSet, resultList);
              }
            }
            while (statement.getMoreResults());
            resultSet.close();
          }
          else {
            resultList.add(statement.getUpdateCount());
          }
          String statementWarnings = getWarnings(statement.getWarnings());
          statement.clearWarnings();
          scriptRunnerResults.add(new ScriptRunnerResult(sqlCode.sql, offset, statementWarnings, columnCount, resultList.toArray()));
        }
        if (sqlCode.nextOffset > offset) {
          offset = sqlCode.nextOffset;
        }
        else {
          throw new BackendException("malformed SQL script");   // avoid endless loops
        }
      }
      String connectionWarnings = getWarnings(connection.getWarnings());
      connection.clearWarnings();
      if (!connectionWarnings.isEmpty()) {
        scriptRunnerResults.add(new ScriptRunnerResult("", 0, connectionWarnings, 0,"Connection Warnings"));
      }
      return scriptRunnerResults;
    }
    catch (SQLException ex) {
      if (sqlCode != null) {
        throw new BackendException("script execution failed at offset " + offset + ": " + sqlCode.sql, ex);
      }
      throw new BackendException("script runner failed", ex);
    }
  }


  /**
   * Determines the next SQL code to execute.
   *
   * @param script the SQL script
   * @param offset the offset where to start in the script to find the next statement
   * @return the next SQL code to send to the database, null if end of script found
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
              // skip to next line
              int ndx = script.indexOf('\n', offset);
              if (ndx >= offset) {
                offset = ndx - 1;    // +1 will be added below
              }
              else {
                break;    // end of script
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

    // if some of the "within..."s are still true at the end of the script,
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


  private int extractResults(ResultSet resultSet, List<Object> resultList) throws SQLException {
    ResultSetMetaData metaData = resultSet.getMetaData();
    int columnCount = metaData.getColumnCount();
    if (resultList.isEmpty()) {
      for (int r=1; r <= columnCount; r++) {
        resultList.add(metaData.getColumnName(r));
      }
    }
    for (int r=1; r <= columnCount; r++) {
      resultList.add(resultSet.getObject(r));
    }
    return columnCount;
  }

  private String getWarnings(SQLWarning warning) {
    StringBuilder warnings = new StringBuilder();
    while (warning != null) {
      if (!warnings.isEmpty()) {
        warnings.append('\n');
      }
      warnings.append(warning.getMessage());
      warning = warning.getNextWarning();
    }
    return warnings.toString();
  }

}

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

/**
 * The result of a statement executed by a {@link ScriptRunner}.<br>
 * Results with warnings only (no SQL code) report the warnings of the connection or a failure
 * while terminating the script.
 *
 * @param sql the SQL code executed, null if warnings only
 * @param offset the offset within the SQL script
 * @param warnings optional warnings, empty string if none, never null
 * @param millis the number of milliseconds the SQL code took to execute
 * @param columnCount the number of columns, if result sets were returned, 0 for update count only
 * @param columnTypes the {@link java.sql.Types} of the columns, if a result set was returned, null if update count
 * @param columnNames the column names, if a result set was returned, null if update count
 * @param data the result objects returned from the statement, empty if the result set was empty, a single int for update count, never null
 */
public record ScriptRunnerResult(String sql, int offset, String warnings, long millis,
                                 int columnCount, int[] columnTypes, String[] columnNames,
                                 Object... data) {

  private static final String NULL_STRING = String.valueOf((Object) null);

  /**
   * Constructor for update count only.
   *
   * @param sql the SQL code executed
   * @param offset the offset within the SQL script
   * @param warnings optional warnings, empty string if none, never null
   * @param millis the number of milliseconds the SQL code took to execute
   * @param updateCount the update count
   */
  public ScriptRunnerResult(String sql, int offset, String warnings, long millis, int updateCount) {
    this(sql, offset, warnings, millis, 0, null, null, updateCount);
  }

  /**
   * Constructor for warnings only.
   *
   * @param warnings the warnings
   */
  public ScriptRunnerResult(String warnings) {
    this(null, 0, warnings, 0, 0, null, null);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(sql).append(" ->")
       .append(columnCount > 0 ? '\n' : ' ').append(resultToString());
    if (!warnings.isEmpty()) {
      buf.append("\nWarnings: ").append(warnings);
    }
    return buf.toString();
  }

  /**
   * Returns whether a result set was returned.
   *
   * @return true if a result set was returned, otherwise update count or warnings only
   */
  public boolean isResultSet() {
    return columnCount > 0;
  }

  /**
   * Gets the update count.
   *
   * @return the update count, -1 if a result set was returned or warnings only
   */
  public int getUpdateCount() {
    return columnCount == 0 && data.length > 0 ? (Integer) data[0] : -1;
  }

  /**
   * Converts the results to a string.
   *
   * @return the string representation
   */
  public String resultToString() {
    StringBuilder buf = new StringBuilder();
    if (isResultSet()) {
      // result set returned: align column widths
      int[] width = new int[columnCount];
      for (int i = 0; i < columnCount; i++) {
        width[i] = columnNames[i].length();
      }
      int r = 0;
      for (Object result : data) {
        if (result == null) {
          result = NULL_STRING;
        }
        int colWidth = result.toString().length();
        int colNdx = r++ % columnCount;
        if (width[colNdx] < colWidth) {
          width[colNdx] = colWidth;
        }
      }
      for (int colNdx = 0; colNdx < columnCount; colNdx++) {
        if (colNdx > 0) {
          buf.append(' ');
        }
        buf.append(String.format("%-" + width[colNdx] + "s", columnNames[colNdx]));
      }
      r = 0;
      for (Object result : data) {
        int colNdx = r++ % columnCount;
        if (!buf.isEmpty()) {
          buf.append(colNdx == 0 ? '\n' : ' ');
        }
        buf.append(String.format((result instanceof Number ? "%" : "%-") + width[colNdx] + "s", result));
      }
    }
    else {
      buf.append(getUpdateCount());
    }
    return buf.toString();
  }

}

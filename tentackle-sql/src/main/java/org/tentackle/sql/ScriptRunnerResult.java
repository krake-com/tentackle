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
 * The result of a statement executed by a {@link ScriptRunner}.
 *
 * @param sql the SQL code executed
 * @param offset the offset within the SQL script
 * @param warnings optional warnings, empty string if none, never null
 * @param columnCount the number of columns, if result sets were returned, 0 if count only
 * @param results the results returned from the statement, empty if none, never null
 */
public record ScriptRunnerResult(String sql, int offset, String warnings, int columnCount, Object... results) {

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(sql).append(" ->");

    if (columnCount > 0) {
      // result set returned: align column widths
      int[] width = new int[columnCount];
      int r = 0;
      for (Object result : results) {
        if (result == null) {
          result = "null";
        }
        int colWidth = result.toString().length();
        int colNdx = r++ % columnCount;
        if (width[colNdx] < colWidth) {
          width[colNdx] = colWidth;
        }
      }
      r = 0;
      for (Object result : results) {
        int colNdx = r++ % columnCount;
        if (colNdx == 0) {
          buf.append('\n');
        }
        buf.append(String.format(" %-" + width[colNdx] + "s", result));
      }
    }
    else {
      for (Object result : results) {
        buf.append(' ').append(result);
      }
    }

    if (!warnings.isEmpty()) {
      buf.append("\nWarnings: ").append(warnings);
    }
    return buf.toString();
  }

}

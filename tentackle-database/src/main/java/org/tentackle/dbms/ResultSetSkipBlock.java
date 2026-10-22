/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.dbms;

import org.tentackle.session.PersistenceException;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A subset of the columns returned by a {@link ResultSetWrapper}.
 *
 * @author harald
 */
public class ResultSetSkipBlock {

  private final ResultSetWrapper rs;                  // the rs wrapper
  private final int skippedColumns;                   // number of columns skipped
  private final Map<String,Integer> columnMap;        // the current map of columns
  private int maxColumnIndex;                         // the highest used column index so far

  /**
   * Creates a skip block.
   *
   * @param rs the result set
   * @param skippedColumns the number of columns skipped
   */
  public ResultSetSkipBlock(ResultSetWrapper rs, int skippedColumns) {
    this.rs = rs;
    this.skippedColumns = skippedColumns;
    this.maxColumnIndex = skippedColumns;
    // build the column map
    columnMap = new HashMap<>();
    int max = rs.getColumnCount();
    ResultSetMetaData meta = rs.getMetaData();
    for (int i = skippedColumns + 1; i <= max; i++) {
      try {
        // append only the first occurrence (from the left).
        // lowercase because column names are converted to lowercase in the model (see AttributeImpl)
        columnMap.putIfAbsent(meta.getColumnName(i).toLowerCase(Locale.ROOT), i);
      }
      catch (SQLException e) {
        throw new PersistenceException(rs.getSession(), e);
      }
    }
  }

  /**
   * Gets the number of columns skipped in the result set.
   *
   * @return the number of skipped columns, 0 if no column skipped
   */
  public int getSkippedColumns() {
    return skippedColumns;
  }

  /**
   * Gets the highest used column index.<br>
   * This will be the skippedColumns of the next skip block.
   *
   * @return the highest-used index
   */
  public int getMaxColumnIndex() {
    return maxColumnIndex;
  }

  /**
   * Maps the given column name to its column index.
   * <p>
   * Finds the first column with that name after the skipped columns of this block.
   * The column names of the result set are mapped in lowercase.
   *
   * @param name the column name
   * @return the index in the result set
   * @throws PersistenceException if no such column beyond the skipped columns
   */
  public int findColumn(String name) {
    Integer ndx = columnMap.get(name);
    if (ndx == null) {
      throw new PersistenceException(rs.getSession(),
              "no such column '" + name + "' beyond " + skippedColumns + " skipped columns in " + rs);
    }
    applyColumnIndex(ndx);
    return ndx;
  }

  /**
   * Applies the used column index to this skip block.
   *
   * @param ndx the used column index
   */
  public void applyColumnIndex(int ndx) {
    if (ndx > maxColumnIndex) {   // remember the highest configured column
      maxColumnIndex = ndx;
    }
  }

}

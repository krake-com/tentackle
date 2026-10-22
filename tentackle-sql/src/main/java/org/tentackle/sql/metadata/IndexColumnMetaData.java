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

package org.tentackle.sql.metadata;

import org.tentackle.common.StringHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;


/**
 * Metadata for a column of an index.
 *
 * @author harald
 */
public class IndexColumnMetaData implements Comparable<IndexColumnMetaData> {

  private final IndexMetaData indexMetaData;    // index column belongs to
  private String columnName;                    // column name
  private String functionName;                  // optional function name
  private boolean descending;                   // true = DESC, false = ASC
  private int position;                         // ordinal position within index


  /**
   * Creates an index column.
   *
   * @param indexMetaData the index this column belongs to
   */
  public IndexColumnMetaData(IndexMetaData indexMetaData) {
    this.indexMetaData = indexMetaData;
  }

  /**
   * Sets up the index column from the database metadata result.
   *
   * @param resultSet the column result set
   * @throws SQLException if processing the result set failed
   */
  public void setupIndexColumnFromMetaData(ResultSet resultSet) throws SQLException {
    columnName = StringHelper.toLower(resultSet.getString("COLUMN_NAME"));
    // check if functional index
    int ndx1 = columnName.indexOf('(');
    if (ndx1 >= 0) {
      int ndx2 = columnName.lastIndexOf(')');
      if (ndx2 > ndx1) {
        functionName = columnName.substring(0, ndx1).trim().toUpperCase(Locale.ROOT);
        columnName = columnName.substring(ndx1 + 1, ndx2).trim().toLowerCase(Locale.ROOT);
      }
    }
    String ascDesc = resultSet.getString("ASC_OR_DESC");
    descending = ascDesc != null && ascDesc.startsWith("D");
    position = resultSet.getShort("ORDINAL_POSITION");

    validate();
  }

  /**
   * Gets the index.
   *
   * @return the index this column belongs to
   */
  public IndexMetaData getIndexMetaData() {
    return indexMetaData;
  }

  /**
   * Returns whether the column is sorted in descending order.
   *
   * @return true if descending
   */
  public boolean isDescending() {
    return descending;
  }

  /**
   * Gets the column name.
   *
   * @return the index column name in lowercase
   */
  public String getColumnName() {
    return columnName;
  }

  /**
   * Gets the optional function name.
   *
   * @return the function in uppercase, null if none
   */
  public String getFunctionName() {
    return functionName;
  }

  /**
   * Gets the ordinal position within the index.
   *
   * @return the position
   */
  public int getPosition() {
    return position;
  }

  @Override
  public int compareTo(IndexColumnMetaData o) {
    return Integer.compare(position, o.position);
  }

  /**
   * Validates and post-processes the index column data.
   */
  public void validate() {
    // nothing to do
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(columnName);
    if (descending) {
      buf.append(" DESC");
    }
    return buf.toString();
  }

}

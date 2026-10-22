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
package org.tentackle.sql.metadata;

/**
 * Index column metadata for PostgreSQL.<br>
 * Strips optional typecasts and enclosing parentheses from the column expression.
 */
public class PostgresIndexColumnMetaData extends IndexColumnMetaData {

  private String colName;     // stripped column name

  /**
   * Creates an index column.
   *
   * @param indexMetaData the index this column belongs to
   */
  public PostgresIndexColumnMetaData(IndexMetaData indexMetaData) {
    super(indexMetaData);
  }

  @Override
  public String getColumnName() {
    if (colName == null) {
      colName = super.getColumnName();
      int ndx = colName.indexOf("::");
      if (ndx >= 0) {
        colName = colName.substring(0, ndx);
      }
      while (colName.startsWith("(")) {
        colName = colName.substring(1);
      }
      ndx = colName.indexOf(')');
      if (ndx >= 0) {
        colName = colName.substring(0, ndx);
      }
      colName = colName.trim();
    }
    return colName;
  }

}

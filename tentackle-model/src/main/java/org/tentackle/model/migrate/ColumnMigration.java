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

package org.tentackle.model.migrate;

import java.util.Objects;

/**
 * Explicit column migration for a given table.
 */
public class ColumnMigration {

  private final String columnName;        // the (old) column name

  private final String newColumnName;     // the new column name (if renamed)

  private final String sql;               // the SQL code

  /**
   * Creates an explicit migration for a column.
   *
   * @param columnName the column name
   * @param newColumnName the new column name if renamed, null if not renamed
   * @param sql the SQL code
   */
  public ColumnMigration(String columnName, String newColumnName, String sql) {
    this.columnName = columnName;
    this.newColumnName = newColumnName;
    this.sql = sql;
  }

  /**
   * Gets the (old) column name.
   *
   * @return the column name
   */
  public String getColumnName() {
    return columnName;
  }

  /**
   * Gets the new column name if column is renamed.
   *
   * @return the renamed column, null if no rename
   */
  public String getNewColumnName() {
    return newColumnName;
  }

  /**
   * Gets the SQL code to migrate this column.
   *
   * @return the SQL code
   */
  public String getSql() {
    return sql;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 79 * hash + Objects.hashCode(this.columnName);
    hash = 79 * hash + Objects.hashCode(this.newColumnName);
    hash = 79 * hash + Objects.hashCode(this.sql);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ColumnMigration other = (ColumnMigration) obj;
    if (!Objects.equals(this.columnName, other.columnName)) {
      return false;
    }
    if (!Objects.equals(this.newColumnName, other.newColumnName)) {
      return false;
    }
    return Objects.equals(this.sql, other.sql);
  }

}

/*
 * Tentackle - http://www.tentackle.org.
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
 * Holds non-SQL-standard common features supported by most backends.
 *
 * @author harald
 */
public class NonStandardCommons {

  /**
   * Creates a COMMENT ON TABLE statement.
   *
   * @param backend the backend
   * @param tableName the tablename with optional schema separated by a dot
   * @param comment optional comment, null if none
   * @return the SQL code including the closing bracket
   */
  public static String sqlCreateCommentOnTable(Backend backend, String tableName, String comment) {
    StringBuilder buf = new StringBuilder("COMMENT ON TABLE ");
    buf.append(tableName);
    buf.append(" IS ");
    buf.append(backend.toQuotedString(comment));
    buf.append(";\n");
    return buf.toString();
  }

  /**
   * Creates a COMMENT ON COLUMN statement.
   *
   * @param backend the backend
   * @param tableName the tablename with optional schema separated by a dot
   * @param columnName the column name
   * @param comment optional comment, null if none
   * @return the SQL code including the closing bracket
   */
  public static String sqlCreateCommentOnColumn(Backend backend, String tableName, String columnName, String comment) {
    StringBuilder buf = new StringBuilder("COMMENT ON COLUMN ");
    buf.append(tableName);
    buf.append(".");
    buf.append(columnName);
    buf.append(" IS ");
    if (comment == null) {
      buf.append("NULL");
    }
    else  {
      buf.append(backend.toQuotedString(comment));
    }
    buf.append(";\n");
    return buf.toString();
  }


  /**
   * don't instantiate.
   */
  private NonStandardCommons() {
  }

}

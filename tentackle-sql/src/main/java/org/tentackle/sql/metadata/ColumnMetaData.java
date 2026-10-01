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
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlType;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;


/**
 * Metadata for a column.
 *
 * @author harald
 */
public class ColumnMetaData {

  private final TableMetaData tableMetaData;    // the table this column belongs to
  private String columnName;                    // the column name
  private String comment;                       // the comment
  private int type;                             // the type code from java.sql.Types
  private String typeName;                      // backend-specific type name
  private int size;                             // the size / length (for ex. in VARCHAR)
  private int scale;                            // the scale (for ex. DECIMAL)
  private String defaultValue;                  // column default, null = none
  private boolean nullable;                     // true if column may contain nulls


  /**
   * Creates column metadata.
   *
   * @param tableMetaData the table metadata this column belongs to
   */
  public ColumnMetaData(TableMetaData tableMetaData) {
    this.tableMetaData = tableMetaData;
  }


  /**
   * Gets the table metadata.
   *
   * @return the table this column belongs to
   */
  public TableMetaData getTableMetaData() {
    return tableMetaData;
  }


  /**
   * Sets up the column from the database metadata result.
   *
   * @param resultSet the column result set
   * @throws SQLException if processing the result set failed
   */
  public void setupColumnFromMetaData(ResultSet resultSet) throws SQLException {
    columnName = StringHelper.toLower(resultSet.getString("COLUMN_NAME"));
    comment = resultSet.getString("REMARKS");
    size = resultSet.getInt("COLUMN_SIZE");
    scale = resultSet.getShort("DECIMAL_DIGITS");
    nullable = resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
    type = resultSet.getInt("DATA_TYPE");
    typeName = resultSet.getString("TYPE_NAME");
    defaultValue = resultSet.getString("COLUMN_DEF");
    validate();
  }


  /**
   * Gets the name of the column.
   *
   * @return the column name (always in lowercase)
   */
  public String getColumnName() {
    return columnName;
  }

  /**
   * Sets the column name.
   *
   * @param columnName the column name
   */
  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  /**
   * Gets the comment.
   *
   * @return the comment, null if none
   */
  public String getComment() {
    return comment;
  }

  /**
   * Sets the comment.
   *
   * @param comment the comment
   */
  public void setComment(String comment) {
    this.comment = comment;
  }

  /**
   * Gets the column's type.
   *
   * @return the type
   * @see java.sql.Types
   */
  public int getType() {
    return type;
  }

  /**
   * Sets the type.
   *
   * @param type the type
   */
  public void setType(int type) {
    this.type = type;
  }

  /**
   * Gets the backend-specific type name.
   *
   * @return the type name
   */
  public String getTypeName() {
    return typeName;
  }

  /**
   * Sets the type name.
   *
   * @param typeName the type name
   */
  public void setTypeName(String typeName) {
    this.typeName = typeName;
  }

  /**
   * Gets the column's size.
   *
   * @return the size
   */
  public int getSize() {
    return size;
  }

  /**
   * Sets the size.
   *
   * @param size the size
   */
  public void setSize(int size) {
    this.size = size;
  }

  /**
   * Gets the numeric scale.
   *
   * @return the scale
   */
  public int getScale() {
    return scale;
  }

  /**
   * Sets the scale.
   *
   * @param scale the scale
   */
  public void setScale(int scale) {
    this.scale = scale;
  }

  /**
   * Gets the column's default.
   *
   * @return the default value, null if none
   */
  public String getDefaultValue() {
    return defaultValue;
  }

  /**
   * Sets the default value.
   *
   * @param defaultValue the default value
   */
  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }


  /**
   * Returns whether column is nullable.
   *
   * @return true if nullable
   */
  public boolean isNullable() {
    return nullable;
  }

  /**
   * Sets whether column is nullable.
   *
   * @param nullable true if nullable
   */
  public void setNullable(boolean nullable) {
    this.nullable = nullable;
  }

  /**
   * Checks whether the column type matches the given SqlType according to the backend.
   *
   * @param sqlType the SQL type
   * @return true if matches
   */
  public boolean matchesSqlType(SqlType sqlType) {
    Backend backend = tableMetaData.getModelMetaData().getBackend();
    SqlType[] possibleSqlTypes = backend.jdbcTypeToSqlType(type, typeName, size, scale);
    for (SqlType possibleType: possibleSqlTypes) {
      if (sqlType == possibleType) {
        return true;
      }
    }
    return false;
  }

  /**
   * Validates and post-processes the column data.
   */
  public void validate() {
    if (StringHelper.isAllWhitespace(comment)) {
      comment = null;
    }
    if (defaultValue != null && defaultValue.isEmpty()) {
      defaultValue = null;
    }
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(columnName);
    buf.append(" ");
    buf.append(MetaDataUtilities.getInstance().jdbcTypeToString(type));
    if (size > 0) {
      buf.append("(");
      buf.append(size);
      if (scale > 0) {
        buf.append(",");
        buf.append(scale);
      }
      buf.append(")");
    }
    if (!nullable) {
      buf.append(" NOT NULL");
    }
    if (defaultValue != null) {
      buf.append(" DEFAULT ");
      buf.append(defaultValue);
    }
    if (comment != null) {
      buf.append(' ').append(tableMetaData.getModelMetaData().getBackend().getSingleLineComment()).append(' ').append(comment);
    }
    return buf.toString();
  }
}

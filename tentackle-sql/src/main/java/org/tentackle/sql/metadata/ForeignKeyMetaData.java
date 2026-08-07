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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Metadata for foreign keys.
 * <p>
 * Tentackle uses foreign keys only for simple columns (ID).
 *
 * @author harald
 */
public class ForeignKeyMetaData {

  private final TableMetaData tableMetaData;                  // the table the key belongs to
  private String foreignKeySchema;                            // the schema of the fk
  private String foreignKeyTable;                             // the table of the fk
  private String foreignKeyName;                              // the name of the foreign key (all TT-fk are named!)
  private String primaryKeySchema;                            // the schema of the pk
  private String primaryKeyTable;                             // the table of the pk
  private ForeignKeyAction updateRule;                        // the update-rule
  private ForeignKeyAction deleteRule;                        // the delete-rule
  private final Set<ForeignKeyColumnMetaData> columns;        // fk columns


  /**
   * Creates foreign key meta data.
   *
   * @param tableMetaData the table this foreign key belongs to
   */
  public ForeignKeyMetaData(TableMetaData tableMetaData) {
    this.tableMetaData = tableMetaData;
    this.columns = new TreeSet<>();
  }


  /**
   * Adds a column to the foreign key.
   *
   * @param column the column
   */
  public void addForeignKeyColumn(ForeignKeyColumnMetaData column) {
    columns.add(column);
  }

  /**
   * Gets the columns.
   *
   * @return the columns ordered by position
   */
  public List<ForeignKeyColumnMetaData> getForeignKeyColumns() {
    return new ArrayList<>(columns);
  }

  /**
   * Gets the table this foreign key belongs to.
   *
   * @return the table
   */
  public TableMetaData getTableMetaData() {
    return tableMetaData;
  }


  /**
   * Gets the name of the foreign key.
   *
   * @return the foreign key name
   */
  public String getForeignKeyName() {
    return foreignKeyName;
  }

  /**
   * Gets the schema of the referencing column.
   *
   * @return the referencing schema, null if default
   */
  public String getForeignKeySchema() {
    return foreignKeySchema;
  }

  /**
   * Gets the referencing table.
   *
   * @return the table name
   */
  public String getForeignKeyTable() {
    return foreignKeyTable;
  }

  /**
   * Gets the full foreign key tablename including optional schema.
   *
   * @return the full tablename
   */
  public String getFullForeignKeyTableName() {
    StringBuilder buf = new StringBuilder();
    if (foreignKeySchema != null) {
      buf.append(foreignKeySchema);
      buf.append('.');
    }
    buf.append(foreignKeyTable);
    return buf.toString();
  }

  /**
   * Gets the schema of the referenced column.
   *
   * @return the referenced schema, null if default
   */
  public String getPrimaryKeySchema() {
    return primaryKeySchema;
  }

  /**
   * Gets the referenced table.
   *
   * @return the table name
   */
  public String getPrimaryKeyTable() {
    return primaryKeyTable;
  }

  /**
   * Gets the full primary key tablename including optional schema.
   *
   * @return the full tablename
   */
  public String getFullPrimaryKeyTableName() {
    StringBuilder buf = new StringBuilder();
    if (primaryKeySchema != null) {
      buf.append(primaryKeySchema);
      buf.append('.');
    }
    buf.append(primaryKeyTable);
    return buf.toString();
  }

  /**
   * Gets the update-rule.
   *
   * @return the update-rule
   */
  public ForeignKeyAction getUpdateRule() {
    return updateRule;
  }

  /**
   * Gets the delete-rule.
   *
   * @return the delete-rule
   */
  public ForeignKeyAction getDeleteRule() {
    return deleteRule;
  }


  /**
   * Extracts the foreign key name from a row of the database metadata result.<br>
   * The name is converted to lowercase, and the optional schema prefix is cut off.
   * <p>
   * Since a foreign key spans one row per column, this is the value all rows
   * belonging to the same foreign key have in common.
   *
   * @param resultSet the foreign key column result set
   * @return the foreign key name, null if the backend does not provide one
   * @throws SQLException the processing the result set failed
   */
  public static String extractForeignKeyName(ResultSet resultSet) throws SQLException {
    String foreignKeyName = StringHelper.toLower(resultSet.getString("FK_NAME"));
    if (foreignKeyName != null) {
      // cut off schema name, if any
      int dotNdx = foreignKeyName.indexOf('.');
      if (dotNdx >= 0) {
        foreignKeyName = foreignKeyName.substring(dotNdx + 1);
      }
    }
    return foreignKeyName;
  }


  /**
   * Sets up the foreign key from the database metadata result.
   *
   * @param resultSet the foreign key column result set
   * @throws SQLException the processing the result set failed
   */
  public void setupForeignKeyFromMetaData(ResultSet resultSet) throws SQLException {
    foreignKeySchema = StringHelper.toLower(resultSet.getString("FKTABLE_SCHEM"));
    foreignKeyTable = StringHelper.toLower(resultSet.getString("FKTABLE_NAME"));
    foreignKeyName = extractForeignKeyName(resultSet);
    primaryKeySchema = StringHelper.toLower(resultSet.getString("PKTABLE_SCHEM"));
    primaryKeyTable = StringHelper.toLower(resultSet.getString("PKTABLE_NAME"));
    updateRule = ForeignKeyAction.createFromAction(resultSet.getShort("UPDATE_RULE"));
    deleteRule = ForeignKeyAction.createFromAction(resultSet.getShort("DELETE_RULE"));

    validate();
  }


  /**
   * Updates the primary table name.<br>
   * This is necessary, if a table was renamed. The metadata then still holds
   * the old tablename. However, after a RENAME TABLE the foreign keys will be
   * updated by the database automatically, so there's no need to drop and re-create
   * the FK.
   *
   * @param primaryKeyTable the new primary table name
   */
  public void setPrimaryKeyTable(String primaryKeyTable) {
    this.primaryKeyTable = primaryKeyTable;
  }


  /**
   * Updates the foreign table name.<br>
   * This is necessary, if a table was renamed. The metadata then still holds
   * the old tablename. However, after a RENAME TABLE the foreign keys will be
   * updated by the database automatically, so there's no need to drop and re-create
   * the FK.
   *
   * @param foreignKeyTable the foreign key table name
   */
  public void setForeignKeyTable(String foreignKeyTable) {
    this.foreignKeyTable = foreignKeyTable;
  }

  /**
   * Validates and post-processes the foreign key data.
   */
  public void validate() {
    String defaultSchema = getTableMetaData().getModelMetaData().getBackend().getDefaultSchema();
    if (defaultSchema != null) {
      if (defaultSchema.equalsIgnoreCase(foreignKeySchema)) {
        foreignKeySchema = null;
      }
      if (defaultSchema.equalsIgnoreCase(primaryKeySchema)) {
        primaryKeySchema = null;
      }
    }
  }


  @Override
  public int hashCode() {
    int hash = 7;
    hash = 79 * hash + Objects.hashCode(this.tableMetaData);
    hash = 79 * hash + Objects.hashCode(this.foreignKeyName);
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
    final ForeignKeyMetaData other = (ForeignKeyMetaData) obj;
    if (!Objects.equals(this.tableMetaData, other.tableMetaData)) {
      return false;
    }
    return Objects.equals(this.foreignKeyName, other.foreignKeyName);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("FOREIGN KEY ");
    buf.append(foreignKeyName);
    buf.append(" (");
    boolean needComma = false;
    for (ForeignKeyColumnMetaData column: columns) {
      if (needComma) {
        buf.append(", ");
      }
      else  {
        needComma = true;
      }
      buf.append(column);
    }
    buf.append(") REFERENCES ");
    if (primaryKeySchema != null) {
      buf.append(primaryKeySchema);
      buf.append(".");
    }
    buf.append(primaryKeyTable);
    buf.append(" (");
    needComma = false;
    for (ForeignKeyColumnMetaData column: columns) {
      if (needComma) {
        buf.append(", ");
      }
      else  {
        needComma = true;
      }
      buf.append(column.getPrimaryKeyColumn());
    }
    buf.append(")");
    if (updateRule != null) {
      buf.append(" ON UPDATE ");
      buf.append(updateRule);
    }
    if (deleteRule != null) {
      buf.append(" ON DELETE ");
      buf.append(deleteRule);
    }
    return buf.toString();
  }

}

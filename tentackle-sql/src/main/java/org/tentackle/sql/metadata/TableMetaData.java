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

import org.tentackle.sql.BackendException;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Database metadata for a table that is part of the model.
 *
 * @author harald
 */
public class TableMetaData {

  private final ModelMetaData modelMetaData;                  // the model containing this table
  private final String modelTableName;                        // the full table name including optional schema
  private String schemaName;                                  // the schema name, null if default or none
  private String tableName;                                   // the tablename without schema
  private String comment;                                     // the comment
  private final Collection<ColumnMetaData> columns;           // the columns
  private final Map<String,IndexMetaData> indexes;            // the indexes <index-name: index>
  private final Map<String,ForeignKeyMetaData> foreignKeys;   // the foreign keys <foreignkey-name: foreignkey>


  /**
   * Creates a table meta instance.
   *
   * @param modelMetaData the model this table belongs to
   * @param modelTableName the tablename used in the model
   */
  public TableMetaData(ModelMetaData modelMetaData, String modelTableName) {
    this.modelMetaData = modelMetaData;
    this.modelTableName = modelTableName;
    columns = new ArrayList<>();
    indexes = new TreeMap<>();
    foreignKeys = new TreeMap<>();
  }


  /**
   * Sets up the column from the database metadata result.
   * <p>
   * The method is idempotent: any state left over from a previous attempt is discarded.
   * This is essential because the caller retries with different name patterns and metadata
   * sources until the table is found (see {@code Backend#getTableMetaData}), and a failed
   * attempt may already have collected some columns.
   *
   * @param metaData the database metadata
   * @param schemas valid schemas to scan, null if all
   * @param schemaPattern the schema (null if none, "" if without schema)
   * @param tablePattern the table pattern
   * @throws SQLException the processing failed
   * @throws BackendException if logical meta data processing error
   */
  public void setupTableFromMetaData(DatabaseMetaData metaData, String[] schemas, String schemaPattern, String tablePattern) throws SQLException {
    schemaName = null;
    tableName = null;
    comment = null;
    columns.clear();
    indexes.clear();
    foreignKeys.clear();

    try (ResultSet resultSet = metaData.getTables(null, schemaPattern, tablePattern, null)) {
      boolean valid = false;    // true if valid table found
      while (resultSet.next()) {
        schemaName = resultSet.getString("TABLE_SCHEM");
        tableName = resultSet.getString("TABLE_NAME");
        comment = resultSet.getString("REMARKS");
        // restrict to optional schemas
        if (isValidSchema(schemaName, schemas)) {
          valid = true;
          break;
        }
      }
      if (!valid) {
        throw new BackendException("no metadata for schema " + schemaPattern + ", table " + tablePattern);
      }
    }

    try (ResultSet resultSet = metaData.getColumns(null, schemaName, tableName, null)) {
      while (resultSet.next()) {
        // check same schema, tablename
        String schema = resultSet.getString("TABLE_SCHEM");
        String table = resultSet.getString("TABLE_NAME");
        // check that we're getting the same results
        if (schema != null && schemaName != null && !schemaName.equals(schema)) {
            throw new BackendException("different schemas apply to '" + (schemaPattern == null ? "" : (schemaPattern + ".")) + tablePattern + "': " +
                                       schemaName + " != " + schema);
        }
        else if (schema == null && schemaName != null ||
                 schema != null && schemaName == null) {
          // schema is null??
          throw new BackendException("null- and non-null schemas found for '" + (schemaPattern == null ? "" : (schemaPattern + ".")) + tablePattern + "': " +
                                     (schemaName == null ? "<null>" : schemaName) + " != " +
                                     (schema == null ? "<null>" : schema));
        }
        if (table != null && tableName != null && !tableName.equals(table)) {
            throw new BackendException("different tablenames apply to '" + (schemaPattern == null ? "" : (schemaPattern + ".")) + tablePattern + "': " +
                                       tableName + " != " + table);
        }
        else if (table == null && tableName != null ||
                 table != null && tableName == null) {
          // table is null??
          throw new BackendException("null- and non-null tablenames found for '" + (schemaPattern == null ? "" : (schemaPattern + ".")) + tablePattern + "': " +
                                     (tableName == null ? "<null>" : tableName) + " != " +
                                     (table == null ? "<null>" : table));
        }

        ColumnMetaData columnMetaData = modelMetaData.getBackend().createColumnMetaData(this);
        columnMetaData.setupColumnFromMetaData(resultSet);
        if (columnMetaData.getColumnName() != null &&
            !modelMetaData.getBackend().isTemporaryName(columnMetaData.getColumnName())) {
          columns.add(columnMetaData);
        }
      }
    }

    // extract index information
    try (ResultSet resultSet = metaData.getIndexInfo(null, schemaName, tableName, false, false)) {
      while (resultSet.next()) {
        String indexName = resultSet.getString("INDEX_NAME");
        if (indexName != null && !modelMetaData.getBackend().isTemporaryName(indexName)) {
          // find index metadata, if new: create one
          IndexMetaData indexMetaData = indexes.get(indexName);
          if (indexMetaData == null) {
            // new: create it and add to tableMetaData
            indexMetaData = modelMetaData.getBackend().createIndexMetaData(this);
            indexMetaData.setupIndexFromMetaData(resultSet);
            indexes.put(indexName, indexMetaData);
          }
          indexMetaData.addIndexColumnFromMetaData(resultSet);
        }
      }
    }

    // Extract the foreign keys. A composite foreign key spans one row per column,
    // with KEY_SEQ holding the column's ordinal position starting at 1.
    // The JDBC ordering guarantee for getImportedKeys does not include the foreign key name,
    // so the rows of two foreign keys referencing the same table may be interleaved.
    // Therefore, the rows are grouped by name rather than by the position of a KEY_SEQ 1.
    Map<String, ForeignKeyMetaData> foreignKeysByName = new TreeMap<>();
    try (ResultSet resultSet = metaData.getImportedKeys(null, schemaName, tableName)) {
      while (resultSet.next()) {
        String foreignKeyName = ForeignKeyMetaData.extractForeignKeyName(resultSet);
        if (foreignKeyName == null) {
          throw new BackendException("unnamed foreign key in " + getFullTableName() +
                                     " referencing " + resultSet.getString("PKTABLE_NAME"));
        }
        ForeignKeyMetaData foreignKeyMetaData = foreignKeysByName.get(foreignKeyName);
        if (foreignKeyMetaData == null) {
          // first column of a foreign key not seen so far
          foreignKeyMetaData = new ForeignKeyMetaData(this);
          foreignKeyMetaData.setupForeignKeyFromMetaData(resultSet);
          foreignKeysByName.put(foreignKeyName, foreignKeyMetaData);
          if (!modelMetaData.getBackend().isTemporaryName(foreignKeyName)) {
            foreignKeys.put(foreignKeyName, foreignKeyMetaData);
          }
        }
        // else: another column of an already known foreign key.
        // setupForeignKeyColumnFromMetaData asserts that the row is consistent with the first one.
        ForeignKeyColumnMetaData column = new ForeignKeyColumnMetaData(foreignKeyMetaData);
        column.setupForeignKeyColumnFromMetaData(resultSet);
        foreignKeyMetaData.addForeignKeyColumn(column);
      }
    }

    validate();
  }


  /**
   * Gets the model this table belongs to.
   *
   * @return the model
   */
  public ModelMetaData getModelMetaData() {
    return modelMetaData;
  }

  /**
   * Gets the table name used in the model.
   *
   * @return the model's tablename
   */
  public String getModelTableName() {
    return modelTableName;
  }

  /**
   * Gets the database schema name.<br>
   * Always in lowercase.
   *
   * @return the schema, null if none
   */
  public String getSchemaName() {
    return schemaName;
  }

  /**
   * Gets the database table name.<br>
   * Always in lowercase.
   *
   * @return the table name (without schema)
   */
  public String getTableName() {
    return tableName;
  }

  /**
   * Gets the database table name with optional schemaname prepended.<br>
   * Always in lowercase.
   *
   * @return the full table name
   */
  public String getFullTableName() {
    if (schemaName != null) {
      return schemaName + "." + tableName;
    }
    return tableName;
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
   * @param comment the comment, null if none
   */
  public void setComment(String comment) {
    this.comment = comment;
  }

  /**
   * Gets the metadata for columns.
   *
   * @return the columns
   */
  public Collection<ColumnMetaData> getColumns() {
    return columns;
  }

  /**
   * Gets a column by its column name.
   *
   * @param columnName the column name
   * @return the column, null if no such column
   */
  public ColumnMetaData getColumnByName(String columnName) {
    columnName = columnName.toLowerCase(Locale.ROOT);
    for (ColumnMetaData column: columns) {
      if (column.getColumnName().equals(columnName)) {
        return column;
      }
    }
    return null;
  }

  /**
   * Gets the metadata for indexes.
   *
   * @return the indexes
   */
  public Collection<IndexMetaData> getIndexes() {
    return indexes.values();
  }

  /**
   * Gets the foreign keys.
   *
   * @return the foreign keys
   */
  public Collection<ForeignKeyMetaData> getForeignKeys() {
    return foreignKeys.values();
  }


  /**
   * Validates and post-processes the table data.
   */
  public void validate() {
    String defaultSchema = getModelMetaData().getBackend().getDefaultSchema();
    if (defaultSchema != null && defaultSchema.equalsIgnoreCase(schemaName)) {
      schemaName = null;
    }
    if (comment != null && comment.isEmpty()) {
      comment = null;
    }
  }


  @Override
  public int hashCode() {
    int hash = 5;
    hash = 71 * hash + Objects.hashCode(this.schemaName);
    hash = 71 * hash + Objects.hashCode(this.tableName);
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
    final TableMetaData other = (TableMetaData) obj;
    if (!Objects.equals(this.schemaName, other.schemaName)) {
      return false;
    }
    return Objects.equals(this.tableName, other.tableName);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("TABLE ");
    buf.append(getFullTableName());
    buf.append(" (");
    for (ColumnMetaData column: getColumns()) {
      buf.append("\n    ");
      buf.append(column);
    }
    buf.append("\n)");
    for (IndexMetaData index: getIndexes()) {
      buf.append("\n");
      buf.append(index);
    }
    for (ForeignKeyMetaData foreignKey: getForeignKeys()) {
      buf.append("\n");
      buf.append(foreignKey);
    }
    buf.append("\n");
    return buf.toString();
  }


  /**
   * Checks whether given schema name is valid.
   *
   * @param schema the schema name
   * @param schemas the (optional) schemas
   * @return true if schema belongs to optional given schemas (or no schemas given)
   */
  private boolean isValidSchema(String schema, String[] schemas) {
    boolean valid = false;
    if (schemas != null) {
      if (schema != null) {
        for (String s: schemas) {
          if (s.equalsIgnoreCase(schema)) {
            valid = true;
            break;
          }
        }
      }
    }
    else {
      valid = true;
    }
    return valid;
  }

}

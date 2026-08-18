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

import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.MigrationStrategy;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;

import java.util.Collection;

/**
 * Handles the migration of columns.
 *
 * @author harald
 */
public class ColumnMigrator {

  private final Entity entity;                // the entity to migrate
  private final Attribute attribute;          // the model's attribute
  private final Backend backend;              // the backend
  private final ColumnMetaData[] columns;     // the database's column (more than one if multi-column attribute)




  private ColumnMetaData column;        // current column, null if not in database yet
  private String columnName;            // the column name
  private String comment;               // the column's comment
  private SqlType sqlType;              // the SQL type
  private int columnSize;               // the column size/width
  private int columnScale;              // the scale/precision
  private Object defaultValue;          // the default


  /**
   * Creates a column migrator.
   *
   * @param entity the migrated entity
   * @param attribute the model's attribute, null if drop columns
   * @param backend the backend
   * @param columns the database's column(s), null if add attribute
   */
  public ColumnMigrator(Entity entity, Attribute attribute, Backend backend, ColumnMetaData... columns) {
    this.entity = entity;
    this.attribute = attribute;
    this.backend = backend;
    this.columns = columns;
  }

  /**
   * Creates a column migrator.
   *
   * @param entity the migrated entity
   * @param attribute the model's attribute, null if drop columns
   * @param backend the backend
   * @param columns the database's column(s), null or empty if add attribute
   */
  public ColumnMigrator(Entity entity, Attribute attribute, Backend backend, Collection<ColumnMetaData> columns) {
    this.entity = entity;
    this.attribute = attribute;
    this.backend = backend;
    this.columns = columns == null || columns.isEmpty() ? null : columns.toArray(new ColumnMetaData[0]);
  }

  /**
   * Gets the backend.
   *
   * @return the backend
   */
  public Backend getBackend() {
    return backend;
  }

  /**
   * Gets the migrated entity.
   *
   * @return the entity
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the model's attribute.
   *
   * @return the model's attribute, null if drop columns
   */
  public Attribute getAttribute() {
    return attribute;
  }

  /**
   * Gets the database's column meta data.
   *
   * @return the database's column(s)
   */
  public ColumnMetaData[] getColumns() {
    return columns;
  }


  /**
   * Migrates the column.
   *
   * @return the SQL code, empty string if nothing to migrate
   * @throws ModelException if migration failed
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public String migrate() throws ModelException {
    StringBuilder buf = new StringBuilder();
    if (attribute != null) {
      DataType dataType = attribute.getEffectiveDataType();

      /*
       * round 1: only add missing columns and migrate only those with exact name match
       * round 2: migrate the rest
       */
      int migratedColumnIndex = 0;
      boolean[] migrated = new boolean[dataType.getColumnCount(backend)];

      for (int round=0; round < 2; round++) {
        for (int columnIndex = 0; columnIndex < dataType.getColumnCount(backend); columnIndex++) {
          if (!migrated[columnIndex]) {
            columnName = attribute.getColumnName(backend, columnIndex);
            sqlType = dataType.getSqlType(backend, columnIndex);
            columnSize = dataType.getSize(backend, columnIndex, attribute.getSize());
            columnScale = dataType.getScale(backend, columnIndex, attribute.getScale());
            comment = attribute.getOptions().getComment();
            if (comment != null && !comment.isEmpty()) {
              comment += dataType.getCommentSuffix(backend, columnIndex).orElse("");
            }
            defaultValue = dataType.getColumnValue(backend, columnIndex, attribute.getOptions().getDefaultValue());
            column = columns == null || migratedColumnIndex >= columns.length ? null : columns[migratedColumnIndex];
            if (column == null) {
              buf.append(addColumn());
              migrated[columnIndex] = true;
            }
            else  {
              if (round == 1 || column.getColumnName().equals(columnName)) {
                buf.append(migrateColumn());
                migratedColumnIndex++;
                migrated[columnIndex] = true;
              }
            }
          }
        }
      }
    }
    else {
      for (ColumnMetaData col: columns) {
        buf.append(dropColumn(col));
      }
    }
    return buf.toString();
  }



  /**
   * Adds the current column.
   *
   * @return the sql code
   * @throws ModelException if failed
   */
  private String addColumn() throws ModelException {
    StringBuilder buf = new StringBuilder();
    buf.append(backend.sqlAddColumn(entity.getTableName(), columnName, comment,
               sqlType, columnSize, columnScale, attribute.isNullable() || defaultValue == null, defaultValue));
    if (!attribute.isNullable() && defaultValue == null) {
      buf.append(updateToNotNull(false));     // set default value before ...
      buf.append(alterNull());                // ... change to not nullable
    }
    if (comment != null) {
      buf.append(backend.sqlCreateColumnComment(entity.getTableName(), columnName, comment));
    }
    return buf.toString();
  }


  /**
   * Drops a column.
   *
   * @param column the column to drop
   * @return the sql code
   */
  private String dropColumn(ColumnMetaData column) {
    return backend.sqlDropColumn(entity.getTableName(), column.getColumnName());
  }


  /**
   * Migrates the current column.
   *
   * @return the sql code
   * @throws ModelException if failed
   */
  private String migrateColumn() throws ModelException {
    StringBuilder buf = new StringBuilder();

    MigrationStrategy[] strategies = backend.getMigrationStrategy(column, columnName, comment,
            sqlType, columnSize, columnScale, attribute.isNullable(), defaultValue);

    for (MigrationStrategy strategy: strategies) {
      switch(strategy) {
        case NAME:
          buf.append(alterName());
          break;

        case TYPE:
          buf.append(alterType());
          break;

        case TYPE_WARNING:
          buf.append(backend.getSingleLineComment()).append(' ').append(alterType());
          break;

        case NAME_AND_TYPE:
          buf.append(alterNameAndType());
          break;

        case NULL:
          if (!attribute.isNullable()) {
            // changed to NOT NULL
            buf.append(updateToNotNull(true));
          }
          buf.append(alterNull());
          break;

        case DEFAULT:
          buf.append(alterDefault());
          break;

        case COMMENT:
          buf.append(alterComment());
          break;
      }
    }

    return buf.toString();
  }



  /**
   * Alters the type, size and/or scale of current column.
   *
   * @return sql code
   */
  private String alterType() throws ModelException {
    return toNonNullString(backend.sqlAlterColumnType(
            entity.getTableName(), columnName, comment,
            sqlType, columnSize, columnScale, attribute.isNullable(),
            defaultValue));
  }

  /**
   * Updates the current column to a non-null value.
   *
   * @param migrated true if column is migrated, false if added
   * @return sql code
   */
  private String updateToNotNull(boolean migrated) {
    return toNonNullString(backend.sqlUpdateToNotNull(
            entity.getTableName(), columnName, sqlType, defaultValue, migrated));
  }

  /**
   * Alters the null constraint only.
   *
   * @return sql code
   * @throws ModelException if failed
   */
  private String alterNull() throws ModelException {
    return toNonNullString(backend.sqlAlterColumnNullConstraint(
            entity.getTableName(), columnName, sqlType, columnSize, columnScale,
            attribute.isNullable(), defaultValue));
  }

  /**
   * Renames a column.
   *
   * @return sql code
   */
  private String alterName() {
    return toNonNullString(backend.sqlRenameColumn(
            entity.getTableName(), column.getColumnName(), columnName));
  }

  private String alterNameAndType() throws ModelException {
    return toNonNullString(backend.sqlRenameAndAlterColumnType(
            entity.getTableName(), column.getColumnName(), columnName,
            comment, sqlType, columnSize, columnScale, attribute.isNullable(),
            defaultValue));
  }

  /**
   * Alter the comment.
   *
   * @return sql code
   */
  private String alterComment() {
    return toNonNullString(backend.sqlAlterColumnComment(
            entity.getTableName(), columnName, comment));
  }

  /**
   * Alters the default.
   */
  private String alterDefault() {
    return toNonNullString(backend.sqlAlterColumnDefault(
            entity.getTableName(), columnName, sqlType, defaultValue));
  }


  /**
   * Assert that SQL code from backend is not null.
   *
   * @param sql the sql code
   * @return the sql code
   */
  private String toNonNullString(String sql) {
    if (sql == null) {
      return "";
    }
    return sql;
  }
}

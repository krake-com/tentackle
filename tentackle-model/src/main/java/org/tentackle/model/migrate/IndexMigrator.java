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

package org.tentackle.model.migrate;

import org.tentackle.common.BasicStringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.IndexAttribute;
import org.tentackle.sql.Backend;
import org.tentackle.sql.metadata.IndexColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;

/**
 * Handles the migration of indexes.
 *
 * @author harald
 */
public class IndexMigrator {

  /**
   * Migration result.
   */
  public static class Result {

    private final String dropSql;
    private final String createSql;

    /**
     * Creates the migration result.
     *
     * @param dropSql the sql code to drop indexes
     * @param createSql the sql code to create indexes
     */
    public Result(String dropSql, String createSql) {
      this.dropSql = dropSql;
      this.createSql = createSql;
    }

    /**
     * Gets the sql code to drop indexex.
     *
     * @return the sql code, empty string if nothing to migrate
     */
    public String getDropSql() {
      return dropSql;
    }

    /**
     * Gets the sql code to create indexes.
     *
     * @return the sql code, empty string if nothing to migrate
     */
    public String getCreateSql() {
      return createSql;
    }

    /**
     * Builds the sum of this and another result.
     *
     * @param result the result to sum up
     * @return the sum
     */
    public Result sum(Result result) {
      return new Result(dropSql + result.dropSql, createSql + result.createSql);
    }
  }


  private final Backend backend;                  // the backend
  private final Entity entity;                    // the entity to migrate the index for
  private final Index index;                      // the index to migrate, null if no such index in model
  private final IndexMetaData indexMetaData;      // the index meta data, null if key does not exist


  /**
   * Creates a migrator.
   *
   * @param entity the entity
   * @param index the index to migrate, null if no such index in model
   * @param backend the backend
   * @param indexMetaData the foreign meta data, null if key does not exist
   */
  public IndexMigrator(Entity entity, Index index, Backend backend, IndexMetaData indexMetaData) {
    this.backend = backend;
    this.entity = entity;
    this.index = index;
    this.indexMetaData = indexMetaData;
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
   * Gets the index to migrate.
   *
   * @return the index, null if no such index in model
   */
  public Index getIndex() {
    return index;
  }

  /**
   * Gets the index metadata.
   *
   * @return the meta date, null if no such index in database
   */
  public IndexMetaData getIndexMetaData() {
    return indexMetaData;
  }


  /**
   * Migrates the index.
   *
   * @return the SQL code, empty string if nothing to migrate
   */
  public Result migrate() {
    StringBuilder dropSql = new StringBuilder();
    StringBuilder createSql = new StringBuilder();
    if (indexMetaData == null) {
      createSql.append(createIndex());
    }
    else if (index == null) {
      dropSql.append(dropIndex());
    }
    else {
      // check if index has changed in its configuration. If so -> drop and re-create
      boolean changed = index.isUnique() != indexMetaData.isUnique();
      if (!changed) {
        // check filter condition
        String modelFilter = index.getFilterCondition();
        if (BasicStringHelper.isAllWhitespace(modelFilter)) {
          modelFilter = "";
        }
        else {
          modelFilter = modelFilter.trim().toUpperCase();
          if (!modelFilter.startsWith("(")) {
            modelFilter = "(" + modelFilter + ")";
          }
          modelFilter = modelFilter.replace(" ", "");
        }
        String currentFilter = indexMetaData.getFilterCondition();
        if (BasicStringHelper.isAllWhitespace(currentFilter)) {
          currentFilter = "";
        }
        else {
          currentFilter = currentFilter.trim().toUpperCase();
          if (!currentFilter.startsWith("(")) {
            currentFilter = "(" + currentFilter + ")";
          }
          currentFilter = currentFilter.replace(" ", "");
        }
        changed = !modelFilter.equals(currentFilter);
      }
      if (!changed) {
        // check index columns
        if (index.getAttributes().size() == indexMetaData.getColumns().size()) {
          for (int position=0; position < index.getAttributes().size(); position++) {
            IndexAttribute indexAttribute = index.getAttributes().get(position);
            IndexColumnMetaData indexColumn = indexMetaData.getColumns().get(position);
            if (indexAttribute.isDescending() != indexColumn.isDescending() ||
                !indexAttribute.getAttribute().getColumnName().equals(indexColumn.getColumnName())) {
              changed = true;
              break;
            }
          }
        }
        else  {
          changed = true;
        }
      }

      if (changed) {
        dropSql.append(dropIndex());
        createSql.append(createIndex());
      }
    }

    return new Result(dropSql.toString(), createSql.toString());
  }


  /**
   * Creates the index.
   *
   * @return the sql
   */
  private String createIndex() {
    return index.sqlCreateIndex(backend, entity);
  }

  /**
   * Drops the index.
   *
   * @return the sql
   */
  private String dropIndex() {
    return backend.sqlDropIndex(indexMetaData.getTableMetaData().getSchemaName(),
                                indexMetaData.getTableMetaData().getTableName(),
                                indexMetaData.getIndexName());
  }

}

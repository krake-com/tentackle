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

import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.IndexAttribute;
import org.tentackle.sql.Backend;
import org.tentackle.sql.metadata.IndexColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;

import java.util.Locale;
import java.util.Objects;

/**
 * Handles the migration of indexes.
 *
 * @author harald
 */
public class IndexMigrator {

  /**
   * The migration result.
   *
   * @param dropSql the SQL code to drop or rename indexes
   * @param createSql the SQL code to create indexes
   */
  public record Result(String dropSql, String createSql) {

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
  private final Index index;                      // the index to migrate, null if no such index in the model
  private final IndexMetaData indexMetaData;      // the index metadata, null if the key does not exist


  /**
   * Creates a migrator.
   *
   * @param entity the entity
   * @param index the index to migrate, null if no such index in the model
   * @param backend the backend
   * @param indexMetaData the foreign metadata, null if key does not exist
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
   * Gets the migrated entity.
   *
   * @return the entity
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the index to migrate.
   *
   * @return the index, null if no such index in the model
   */
  public Index getIndex() {
    return index;
  }

  /**
   * Gets the index metadata.
   *
   * @return the meta-data, null if no such index in the database
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
      // check if the index has changed in its configuration. If so -> drop and re-create
      StringBuilder buf = new StringBuilder();
      if (isLogicallyDifferent(backend, index, indexMetaData, buf)) {
        dropSql.append(buf);
        dropSql.append(dropIndex());
        createSql.append(createIndex());
      }
      else {
        // check if only the comment has changed
        String dbComment = indexMetaData.getComment();
        if (dbComment == null || dbComment.isBlank()) {
          dbComment = "";
        }
        String modelComment = index.getComment();
        if (modelComment == null || modelComment.isBlank()) {
          modelComment = "";
        }
        if (!Objects.equals(dbComment, modelComment)) {
          createSql.append(backend.sqlAlterIndexComment(entity.getTableName(), index.createDatabaseIndexName(entity),
                                                        modelComment));
        }
      }
    }

    return new Result(dropSql.toString(), createSql.toString());
  }


  /**
   * Compares a model index with an existing database index.
   *
   * @param backend the backend
   * @param modelIndex the index as defined in the model
   * @param dbIndex the index existing in the database
   * @param commentBuf comment buffer to show the difference, null for none
   * @return true if they are different, false if logically equal
   */
  public static boolean isLogicallyDifferent(Backend backend, Index modelIndex, IndexMetaData dbIndex, StringBuilder commentBuf) {
    boolean different = modelIndex.isUnique() != dbIndex.isUnique() ||
                         !modelIndex.getDeferrability().name().equals(dbIndex.getDeferrability().name());
    if (!different) {
      // check filter condition
      String modelFilter = modelIndex.getFilterCondition();
      if (StringHelper.isAllWhitespace(modelFilter)) {
        modelFilter = "";
      }
      else {
        modelFilter = modelFilter.toUpperCase(Locale.ROOT);
      }
      String currentFilter = dbIndex.getFilterCondition();
      if (StringHelper.isAllWhitespace(currentFilter)) {
        currentFilter = "";
      }
      else {
        currentFilter = currentFilter.toUpperCase(Locale.ROOT);
      }
      different = !equalsIgnoreMatchingParenthesis(modelFilter, currentFilter);
      if (different && commentBuf != null) {
        commentBuf.append(backend.getSingleLineComment()).append(" filter in database = ").append(encloseInParenthesis(currentFilter))
                  .append('\n').append(backend.getSingleLineComment()).append("        in model    = ")
                  .append(encloseInParenthesis(modelFilter)).append('\n');
      }
    }
    if (!different) {
      // check index columns
      if (modelIndex.getAttributes().size() == dbIndex.getColumns().size()) {
        for (int position=0; position < modelIndex.getAttributes().size(); position++) {
          IndexAttribute indexAttribute = modelIndex.getAttributes().get(position);
          IndexColumnMetaData indexColumn = dbIndex.getColumns().get(position);
          if (indexAttribute.isDescending() != indexColumn.isDescending() ||
              !indexAttribute.getColumnName().equals(indexColumn.getColumnName()) ||
              !Objects.equals(indexAttribute.getFunctionName(), indexColumn.getFunctionName())) {
            different = true;
            break;
          }
        }
      }
      else  {
        different = true;
      }
    }
    return different;
  }

  /**
   * Creates the index.
   *
   * @return the SQL code
   */
  private String createIndex() {
    return index.sqlCreateIndex(backend, entity);
  }

  /**
   * Drops the index.
   *
   * @return the SQL code
   */
  private String dropIndex() {
    if (indexMetaData.getDeferrability() != org.tentackle.sql.metadata.Deferrability.NOT_DEFERRABLE) {
      // the index exists in the database as a unique constraint, not a plain index
      return backend.sqlDropConstraint(indexMetaData.getTableMetaData().getFullTableName(), indexMetaData.getIndexName());
    }
    return backend.sqlDropIndex(indexMetaData.getTableMetaData().getSchemaName(),
                                indexMetaData.getTableMetaData().getTableName(),
                                indexMetaData.getIndexName());
  }


  /**
   * Compares two expression strings ignoring extra matching parenthesis.<br>
   * The backend or the user may add extra parenthesis which would cause comparison to fail.
   *
   * @param s1 the first string
   * @param s2 the second string
   * @return true if s1 equals s2
   */
  private static boolean equalsIgnoreMatchingParenthesis(String s1, String s2) {
    s1 = s1.replace(" ", "");
    s2 = s2.replace(" ", "");
    int ndx1 = 0;
    int ndx2 = 0;
    int pending1 = 0;   // > 0 pending open parenthesis in s1
    int pending2 = 0;   // > 0 pending open parentheses in s2

    while (ndx1 < s1.length() || ndx2 < s2.length()) {    // one of both still pending chars
      char c1 = ndx1 < s1.length() ? s1.charAt(ndx1) : 0;
      char c2 = ndx2 < s2.length() ? s2.charAt(ndx2) : 0;
      if (c1 != c2) {
        if (c1 == '(') {
          pending1++;
          ndx1++;
          continue;
        }
        if (c1 == ')') {
          if (pending1 > 0) {
            pending1--;
            ndx1++;
            continue;
          }
          break;
        }

        if (c2 == '(') {
          pending2++;
          ndx2++;
          continue;
        }
        if (c2 == ')') {
          if (pending2 > 0) {
            pending2--;
            ndx2++;
            continue;
          }
          break;
        }

        break;
      }

      if (ndx1 < s1.length()) {
        ndx1++;
      }
      if (ndx2 < s2.length()) {
        ndx2++;
      }
    }

    return ndx1 == s1.length() && ndx2 == s2.length() && pending1 == 0 && pending2 == 0;
  }

  /**
   * Encloses a given string in parentheses if not already enclosed.
   *
   * @param s the string
   * @return the enclosed string
   */
  private static String encloseInParenthesis(String s) {
    return !s.startsWith("(") || !s.endsWith(")") ? "(" + s + ")" : s;
  }

}

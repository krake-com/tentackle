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
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Index;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelUtilities;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.ForeignKeyAction;
import org.tentackle.sql.metadata.ForeignKeyMetaData;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Handles the migration of tables.
 *
 * @author harald
 */
public class TableMigrator {

  /**
   * Migration result.<br>
   * Contains the migration parameters and the resulting SQL code.
   *
   * @param hints            hints to control the created output, null or empty if none
   * @param columnMigrations explicit column migrations, null or empty if none
   * @param renameColumns    explicit column rename hints, null or empty if none
   * @param tableSql         the SQL code to migrate the table
   * @param foreignKeySql    the SQL code to migrate the foreign keys
   * @param warningSql       generated SQL as comments
   */
  public record Result(Collection<Pattern> hints,
                       Collection<ColumnMigration> columnMigrations,
                       Map<String, String> renameColumns,
                       String tableSql, String foreignKeySql, String warningSql) {}



  private final Entity entity;                        // the model's entity
  private final Collection<ForeignKey> foreignKeys;   // the foreign keys
  private final Backend backend;                      // the backend
  private final TableMetaData table;                  // the database's table metadata, null if no such table


  /**
   * Creates a table migrator.
   *
   * @param entity the model's entity
   * @param foreignKeys the foreign keys related to the given entity
   * @param backend the backend
   * @param table the database's table metadata
   */
  public TableMigrator(Entity entity, Collection<ForeignKey> foreignKeys, Backend backend, TableMetaData table) {
    this.entity = entity;
    this.foreignKeys = foreignKeys;
    this.backend = backend;
    this.table = table;
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
   * Gets the model's entity.
   *
   * @return the model's entity
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the database's table metadata.
   *
   * @return the database's table metadata
   */
  public TableMetaData getTable() {
    return table;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(entity.getName());
    buf.append(" -> ");
    if (table == null) {
      buf.append("table missing");
    }
    else {
      buf.append(table.getFullTableName());
    }
    return buf.toString();
  }




  /**
   * Create SQL code to migrate the table.
   *
   * @param hints hints to control the created output, null or empty if none
   * @param columnMigrations explicit column migrations, null or empty if none
   * @param renameColumns  explicit column rename hints, null or empty if none
   * @return the migration result
   * @throws ModelException if migration failed
   */
  public Result migrate(Collection<Pattern> hints, Collection<ColumnMigration> columnMigrations, Map<String, String> renameColumns) throws ModelException {

    StringBuilder tableSql = new StringBuilder();
    StringBuilder foreignKeySql = new StringBuilder();
    StringBuilder warningSql = new StringBuilder();

    if (table == null) {
      tableSql.append(createTable());
      foreignKeySql.append(createForeignKeys());
    }
    else  {
      IndexMigrator.Result indexResult = migrateIndexes();
      tableSql.append(indexResult.dropSql());
      tableSql.append(migrateForeignKeysToDrop());
      ColumnsResult columnsResult = migrateColumns(hints, columnMigrations, renameColumns);
      tableSql.append(columnsResult.tableSql);
      warningSql.append(columnsResult.warningSql);
      tableSql.append(migrateTable());
      tableSql.append(indexResult.createSql());
      foreignKeySql.append(migrateForeignKeysToAdd());
    }

    if (!tableSql.isEmpty()) {
      // insert a blank line if something to migrate
      tableSql.insert(0, '\n');
    }

    return new Result(hints, columnMigrations, renameColumns, tableSql.toString(), foreignKeySql.toString(), warningSql.toString());
  }



  /**
   * Creates the table.
   *
   * @return the SQL code
   */
  private String createTable() throws ModelException {
    StringBuilder buf = new StringBuilder();
    buf.append(entity.sqlCreateTable(backend));
    for (Index index: entity.getTableIndexes()) {
      buf.append(index.sqlCreateIndex(backend, entity));
    }
    return buf.toString();
  }

  /**
   * Creates the foreign keys.
   *
   * @return the SQL code
   */
  private String createForeignKeys() {
    StringBuilder buf = new StringBuilder();
    for (ForeignKey foreignKey: foreignKeys) {
      buf.append(foreignKey.sqlCreateForeignKey(backend));
    }
    return buf.toString();
  }


  /**
   * Gets the migration for an existing (old) column.
   *
   * @param columName the old column name
   * @param columnMigrations the migrations
   * @return the migration, null if no explicit migration
   */
  private ColumnMigration getColumnMigrationForColumn(String columName, Collection<ColumnMigration> columnMigrations) {
    if (columnMigrations != null && columName != null) {
      for (ColumnMigration migration: columnMigrations) {
        if (StringHelper.equalsIgnoreCase(columName, migration.getColumnName())) {
          return migration;
        }
      }
    }
    return null;
  }

  /**
   * Gets the migration for a new (renamed) column.
   *
   * @param newColumName the new column name
   * @param columnMigrations the migrations
   * @return the migration, null if no explicit migration
   */
  private ColumnMigration getColumnMigrationForNewColumn(String newColumName, Collection<ColumnMigration> columnMigrations) {
    if (columnMigrations != null && newColumName != null) {
      for (ColumnMigration migration: columnMigrations) {
        if (StringHelper.equalsIgnoreCase(newColumName, migration.getNewColumnName())) {
          return migration;
        }
      }
    }
    return null;
  }


  /**
   * Migrates the table definition.
   *
   * @return the SQL code
   */
  private String migrateTable() {
    if (!Objects.equals(table.getComment(), entity.getOptions().getComment())) {
      String sql = backend.sqlAlterTableComment(entity.getTableName(), entity.getOptions().getComment());
      if (sql != null && !sql.isEmpty()) {
        return sql;
      }
    }
    return "";
  }


  private record ColumnsResult(String tableSql, String warningSql) {}

  /**
   * Migrates the columns.
   *
   * @param hints the migration hints, null if none
   * @param columnMigrations explicit column migrations, null if none
   * @param renameColumns  explicit column rename hints, null if none
   * @return the SQL code
   * @throws ModelException if failed
   */
  private ColumnsResult migrateColumns(Collection<Pattern> hints, Collection<ColumnMigration> columnMigrations, Map<String, String> renameColumns)
          throws ModelException {

    StringBuilder sqlBuf = new StringBuilder();
    StringBuilder warnBuf = new StringBuilder();

    Map<String, ColumnMetaData> columnsToDrop = new LinkedHashMap<>();                      // columns to drop in database
    Set<Attribute> attributesToAdd = new LinkedHashSet<>();                                 // attributes to create in database
    Map<Attribute,Collection<ColumnMetaData>> attributesToMigrate = new LinkedHashMap<>();  // attributes to migrate in database

    // add all old columns to the drop list first
    for (ColumnMetaData column: table.getColumns()) {
      columnsToDrop.put(column.getColumnName(), column);
    }

    // first guess by column name.
    for (Attribute attribute: entity.getTableAttributes()) {
      Collection<ColumnMetaData> matchingColumns = new ArrayList<>();
      DataType<?> dataType = attribute.getEffectiveDataType();
      for (int columnIndex = 0; columnIndex < dataType.getColumnCount(backend); columnIndex++) {
        ColumnMetaData column = table.getColumnByName(attribute.getColumnName(backend, columnIndex));
        if (column != null) {
          matchingColumns.add(column);
        }
      }
      if (matchingColumns.isEmpty()) {
        if (renameColumns != null) {
          for (int columnIndex = 0; columnIndex < dataType.getColumnCount(backend); columnIndex++) {
            String oldColumnName = renameColumns.get(attribute.getColumnName(backend, columnIndex));
            if (oldColumnName != null) {
              // matching rename hint!
              ColumnMetaData oldColumn = columnsToDrop.get(oldColumnName);
              if (oldColumn != null) {
                ColumnMetaData column = table.getColumnByName(oldColumnName);
                if (column != null) {
                  matchingColumns.add(column);
                }
              }
            }
          }
          if (matchingColumns.size() == dataType.getColumnCount(backend)) {    // all hints must match for multi-column types
            attributesToMigrate.put(attribute, matchingColumns);
            for (ColumnMetaData column : matchingColumns) {
              columnsToDrop.remove(column.getColumnName());
            }
            continue;
          }
        }
        attributesToAdd.add(attribute);
      }
      else  {
        for (ColumnMetaData matchingColumn: matchingColumns) {
          columnsToDrop.remove(matchingColumn.getColumnName());
        }
        attributesToMigrate.put(attribute, matchingColumns);
      }
    }

    /*
     * attributesToAdd and columnsToDrop may contain columns that just need to be renamed.
     * rule: if a column is to drop and there is _one_ _exact_ type match in attributes to add,
     * this column is probably just renamed. Generate the rename-statement, but keep the drop- or add-
     * statements as comments (so they can be used if guess was wrong)
     */
    for (Iterator<Attribute> addAttributeIterator = attributesToAdd.iterator(); addAttributeIterator.hasNext(); ) {

      Attribute addAttribute = addAttributeIterator.next();
      DataType<?> addDataType = addAttribute.getEffectiveDataType();

      String addSql = new ColumnMigrator(entity, addAttribute, backend).migrate();
      Pattern addHint = sqlMatchesHint(addSql, hints);

      ColumnMetaData typeMatchingColumn = null;
      for (ColumnMetaData dropColumn: columnsToDrop.values()) {
        if (dropColumn.matchesSqlType(addDataType.getSqlType(backend, 0))) {
          if (typeMatchingColumn == null) {
            typeMatchingColumn = dropColumn;
          }
          else  {
            typeMatchingColumn = null;  // more than one type match
            break;
          }
        }
      }

      Collection<ColumnMetaData> matchingColumns = new ArrayList<>();

      if (typeMatchingColumn != null) {
        // check multi-column exists with same name
        for (int columnIndex = 0; columnIndex < addDataType.getColumnCount(backend); columnIndex++) {
          String columnName = typeMatchingColumn.getColumnName() + addDataType.getColumnSuffix(backend, columnIndex).orElse("");
          ColumnMetaData dropColumn = columnsToDrop.get(columnName);
          if (dropColumn != null) {
            matchingColumns.add(dropColumn);
          }
        }
      }

      if (matchingColumns.size() == addDataType.getColumnCount(backend)) {
        // generate alternate statements and check against hints
        String renameSql = new ColumnMigrator(entity, addAttribute, backend, matchingColumns).migrate();
        Pattern renameHint = sqlMatchesHint(renameSql, hints);

        String dropSql = new ColumnMigrator(entity, null, backend, matchingColumns).migrate();
        Pattern dropHint = sqlMatchesHint(dropSql, hints);

        if (renameHint != null) {
          if (addHint != null) {
            // two hints apply to both variants -> fix hints!
            throw new ModelException("migration hints collision:\n'" +
                    addHint + "' applies to '" + addSql + "'\n'" +
                    renameHint + "' applies to '" + renameSql + "'\n-> fix hints!");
          }
          if (dropHint != null) {
            // two hints apply to both variants -> fix hints!
            throw new ModelException("migration hints collision:\n'" +
                    dropHint + "' applies to '" + dropSql + "'\n'" +
                    renameHint + "' applies to '" + renameSql + "'\n-> fix hints!");
          }

          // rename applies
        }
        else  {
          // no rename hint
          if (addHint != null || dropHint != null) {
            sqlBuf.append(backend.sqlComment(renameSql));
            sqlBuf.append(backend.getSingleLineComment()).append(" disabled by hint '");
            if (addHint != null) {
              sqlBuf.append(addHint);
            }
            if (dropHint != null) {
              sqlBuf.append(dropHint);
            }
            sqlBuf.append("'\n");
            continue;   // next attribute to add
          }
          else  {
            // else: no add- or drop hint either -> default to rename
            // create alternate commands as comment
            sqlBuf.append(backend.sqlComment(addSql));
            sqlBuf.append(backend.sqlComment(dropSql));
          }
        }

        // remove from attributesToAdd & columnsToDrop and add to attributesToMigrate
        addAttributeIterator.remove();
        for (ColumnMetaData dropColumn: matchingColumns) {
          columnsToDrop.remove(dropColumn.getColumnName());
        }
        attributesToMigrate.put(addAttribute, matchingColumns);
      }
    }

    /*
     * for all columns to drop and add:
     * some may be just renamed.
     * Check all permutations whether a hint applies.
     */
    if (!columnsToDrop.isEmpty() && hints != null && !hints.isEmpty()) {
      for (Iterator<Attribute> addAttributeIterator = attributesToAdd.iterator(); addAttributeIterator.hasNext(); ) {
        Attribute addAttribute = addAttributeIterator.next();
        DataType<?> addDataType = addAttribute.getEffectiveDataType();
        for (ColumnMetaData dropColumn: new ArrayList<>(columnsToDrop.values())) {
          Collection<ColumnMetaData> columns = new ArrayList<>();
          // check multi-column exists with same name
          for (int columnIndex = 0; columnIndex < addDataType.getColumnCount(backend); columnIndex++) {
            String columnName = dropColumn.getColumnName() + addDataType.getColumnSuffix(backend, columnIndex).orElse("");
            ColumnMetaData column = columnsToDrop.get(columnName);
            if (column != null) {
              columns.add(column);
            }
          }
          if (columns.size() == addDataType.getColumnCount(backend)) {
            // generate alternate statements and check against hints
            String renameSql = new ColumnMigrator(entity, addAttribute, backend, columns).migrate();
            Pattern renameHint = sqlMatchesHint(renameSql, hints);
            if (renameHint != null) {
              // remove from attributesToAdd & columnsToDrop and add to attributesToMigrate
              addAttributeIterator.remove();
              for (ColumnMetaData column: columns) {
                columnsToDrop.remove(column.getColumnName());
              }
              attributesToMigrate.put(addAttribute, columns);
              break;
            }
          }
        }
      }
    }

    // last round: find the best match among same types with string distance calculations.
    // rename goes before drop/add to avoid data loss!
    for (Attribute addAttribute :
         // start with the longest names: they have the highest number of distance combinations and thus compare best when first
         attributesToAdd.stream().sorted((o1, o2) -> o2.getColumnName().length() - o1.getColumnName().length()).toList()) {

      String addSql = new ColumnMigrator(entity, addAttribute, backend).migrate();
      Pattern addHint = sqlMatchesHint(addSql, hints);
      if (addHint == null) {
        DataType<?> addDataType = addAttribute.getEffectiveDataType();
        List<ColumnMetaData> matchingColumns = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < addDataType.getColumnCount(backend); columnIndex++) {
          String columnName = addAttribute.getColumnName() + addDataType.getColumnSuffix(backend, columnIndex).orElse("");
          List<String> possibleNames = new ArrayList<>();
          for (ColumnMetaData dropColumn : columnsToDrop.values()) {
            if (dropColumn.matchesSqlType(addDataType.getSqlType(backend, columnIndex))) {
              possibleNames.add(dropColumn.getColumnName());
            }
          }
          String matchingName = ModelUtilities.getInstance().bestMatch(columnName, possibleNames);
          if (matchingName != null) {
            matchingColumns.add(columnsToDrop.get(matchingName));   // must exist!
          }
        }
        if (matchingColumns.size() == addDataType.getColumnCount(backend)) {
          String dropSql = new ColumnMigrator(entity, null, backend, matchingColumns).migrate();
          Pattern dropHint = sqlMatchesHint(dropSql, hints);
          if (dropHint == null) {
            // found a match for all columns of the added attribute
            attributesToAdd.remove(addAttribute);
            attributesToMigrate.put(addAttribute, matchingColumns);
            for (ColumnMetaData matchingColumn : matchingColumns) {
              columnsToDrop.remove(matchingColumn.getColumnName());
            }
            sqlBuf.append(backend.sqlComment(addSql));
            sqlBuf.append(backend.sqlComment(dropSql));
          }
        }
      }
    }

    // create migration code
    for (ColumnMetaData dropColumn: columnsToDrop.values()) {
      if (getColumnMigrationForColumn(dropColumn.getColumnName(), columnMigrations) == null) {
        sqlBuf.append(new ColumnMigrator(entity, null, backend, dropColumn).migrate());
      }
    }

    for (Attribute addAttribute: attributesToAdd) {
      if (getColumnMigrationForNewColumn(addAttribute.getColumnName(), columnMigrations) == null) {
        sqlBuf.append(new ColumnMigrator(entity, addAttribute, backend).migrate());
      }
    }

    for (Map.Entry<Attribute,Collection<ColumnMetaData>> renameEntry: attributesToMigrate.entrySet()) {
      String columnName = renameEntry.getValue().iterator().next().getColumnName();   // multi-column does not work for explicit migrations
      ColumnMigration explicitMigration = getColumnMigrationForColumn(columnName, columnMigrations);
      String migrationCode = new ColumnMigrator(entity, renameEntry.getKey(), backend, renameEntry.getValue()).migrate();
      if (migrationCode.isEmpty()) {
        // column has not changed: don't run the explicit migration code
        if (explicitMigration != null) {
          columnMigrations.remove(explicitMigration);
        }
      }
      else {
        if (StringHelper.startsWithAnyOf(migrationCode, backend.getSingleLineComments()) != null) {
          warnBuf.append(migrationCode);
        }
        else {
          if (explicitMigration == null) {
            sqlBuf.append(migrationCode);
          }
          else {
            sqlBuf.append(backend.sqlComment(migrationCode));    // show as comment
          }
        }
      }
    }

    // add explicit migrations (if there is anything to migrate at all)
    if (!sqlBuf.isEmpty() && columnMigrations != null) {
      for (ColumnMigration migration: columnMigrations) {
        sqlBuf.append(migration.getSql()).append('\n');
      }
    }

    if (!sqlBuf.isEmpty() && hints != null && !hints.isEmpty()) {
      StringBuilder hintsBuf = new StringBuilder();
      hintsBuf.append(backend.getSingleLineComment()).append(" hints for ").append(entity).append('\n');
      for (Pattern hint: hints) {
        hintsBuf.append(backend.getSingleLineComment()).append(' ').append(hint).append('\n');
      }
      sqlBuf.insert(0, hintsBuf);
    }

    return new ColumnsResult(sqlBuf.toString(), warnBuf.toString());
  }


  /**
   * Check whether a hint applies to SQL code.
   *
   * @param sql the SQL statement
   * @param hints the hints, null if none
   * @return the matching hint, null if none
   */
  private Pattern sqlMatchesHint(String sql, Collection<Pattern> hints) {
    Pattern matchingHint = null;
    if (hints != null) {
      for (Pattern hint: hints) {
        if (hint.matcher(sql).matches()) {
          matchingHint = hint;
          break;
        }
      }
    }
    return matchingHint;
  }


  /**
   * Migrates the indexes.
   *
   * @return the SQL code
   */
  private IndexMigrator.Result migrateIndexes() {
    IndexMigrator.Result result = new IndexMigrator.Result("", "");

    Collection<IndexMetaData> existingIndexes = table.getIndexes();
    List<Index> tableIndexes = entity.getTableIndexes();
    List<IndexMetaData> renamedIndexes = new ArrayList<>();

    for (Index index: tableIndexes) {
      boolean found = false;
      for (IndexMetaData existingIndex: existingIndexes) {
        String indexName = index.createDatabaseIndexName(entity);
        if (existingIndex.getIndexName().equalsIgnoreCase(indexName)) {
          // found -> migrate, if necessary
          result = result.sum(new IndexMigrator(entity, index, backend, existingIndex).migrate());
          found = true;
          break;
        }
        else if (!IndexMigrator.isLogicallyDifferent(backend, index, existingIndex, null)) {
          // different name, but logically equivalent -> rename it if backend supports that.
          // however, check that this is the only equivalent (special case if
          // duplicate indexes already exist in the database)
          int count = 0;
          for (IndexMetaData exNdx: existingIndexes) {
            if (!IndexMigrator.isLogicallyDifferent(backend, index, exNdx, null)) {
              count++;
            }
          }
          if (count == 1) {
            String existingName = entity.getSchemaName() == null || entity.getModel().isSchemaNameMapped() ?
                                  existingIndex.getIndexName() :
                                  (entity.getSchemaName() + "." + existingIndex.getIndexName());
            String sql = backend.sqlRenameIndex(entity.getTableName(), existingName, indexName);
            if (sql != null) {
              result = result.sum(new IndexMigrator.Result(sql, ""));
              found = true;
              renamedIndexes.add(existingIndex);
              break;
            }
          }
          // else: drop and create
        }
      }
      if (!found) {
        // create index
        result = result.sum(new IndexMigrator(entity, index, backend, null).migrate());
      }
    }

    for (IndexMetaData existingIndex: existingIndexes) {
      if (!existingIndex.isPrimaryIdKey() && !renamedIndexes.contains(existingIndex)) {
        boolean found = false;
        for (Index index: tableIndexes) {
          if (index.createDatabaseIndexName(entity).equalsIgnoreCase(existingIndex.getIndexName())) {
            found = true;
            break;
          }
        }
        if (!found) {
          // remove index
          result = result.sum(new IndexMigrator(entity, null, backend, existingIndex).migrate());
        }
      }
    }

    return result;
  }


  /**
   * Creates the SQL code to add foreign keys constraints.
   *
   * @return the SQL code
   */
  private String migrateForeignKeysToAdd() throws ModelException {
    StringBuilder buf = new StringBuilder();

    for (ForeignKey foreignKey: foreignKeys) {
      boolean found = false;
      for (ForeignKeyMetaData existingForeignKey: table.getForeignKeys()) {
        if (foreignKeysMatch(foreignKey, existingForeignKey)) {
          found = true;   // match -> nothing to do
          break;
        }
      }
      if (!found) {
        // create
        buf.append(new ForeignKeyMigrator(backend, foreignKey, null).migrate());
      }
    }

    return buf.toString();
  }

  /**
   * Creates the SQL code to drop foreign keys constraints.
   *
   * @return the SQL code
   */
  private String migrateForeignKeysToDrop() throws ModelException {
    StringBuilder buf = new StringBuilder();

    for (ForeignKeyMetaData existingForeignKey: table.getForeignKeys()) {
      boolean found = false;
      for (ForeignKey foreignKey: foreignKeys) {
        if (foreignKeysMatch(foreignKey, existingForeignKey)) {
          found = true;
          break;
        }
      }
      if (!found) {
        // drop
        buf.append(new ForeignKeyMigrator(backend, null, existingForeignKey).migrate());
      }
    }

    return buf.toString();
  }


  /**
   * Returns whether foreign keys from model matches with the database.
   *
   * @param foreignKey the foreign key
   * @param existingForeignKey the meta data
   * @return true if match
   */
  private boolean foreignKeysMatch(ForeignKey foreignKey, ForeignKeyMetaData existingForeignKey) {

    Entity referencingTableProvidingEntity = foreignKey.getReferencingTableProvidingEntity();
    Entity referencedTableProvidingEntity = foreignKey.getReferencedTableProvidingEntity();

    boolean mapSchemas = entity.getModel().isSchemaNameMapped();

    return existingForeignKey.getForeignKeyColumns().size() == 1 &&
           // the TT-model uses only foreign keys on object-ID columns -> cannot match

           (existingForeignKey.getDeleteRule() == ForeignKeyAction.CASCADE) == foreignKey.isComposite() &&

           foreignKey.getDeferrability().name().equals(existingForeignKey.getDeferrability() != null ?
               existingForeignKey.getDeferrability().name() : "NOT_DEFERRABLE") &&

           foreignKey.getReferencingAttribute().getColumnName().equalsIgnoreCase(existingForeignKey.getForeignKeyColumns().get(0).getForeignKeyColumn()) &&
           (mapSchemas || StringHelper.equalsIgnoreCase(referencingTableProvidingEntity.getSchemaName(), existingForeignKey.getForeignKeySchema())) &&
           referencingTableProvidingEntity.getTableNameWithoutSchema().equalsIgnoreCase(existingForeignKey.getForeignKeyTable()) &&

           foreignKey.getReferencedAttribute().getColumnName().equalsIgnoreCase(existingForeignKey.getForeignKeyColumns().get(0).getPrimaryKeyColumn()) &&
           (mapSchemas || StringHelper.equalsIgnoreCase(referencedTableProvidingEntity.getSchemaName(), existingForeignKey.getPrimaryKeySchema())) &&
           referencedTableProvidingEntity.getTableNameWithoutSchema().equalsIgnoreCase(existingForeignKey.getPrimaryKeyTable());
  }

}

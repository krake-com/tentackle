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

package org.tentackle.maven.plugin.sql;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.wurbelizer.misc.Settings;

import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.migrate.TableMigrator;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.metadata.DatabaseMetaDataTableHeader;
import org.tentackle.sql.metadata.ForeignKeyMetaData;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Generates an SQL-script to migrate the database tables according to the model.
 *
 * @author harald
 */
@Mojo(name = "migrate", aggregator = true)
public class MigrateSqlMojo extends AbstractSqlMojo {

  private static final String DEFAULT_SCHEMA = "<default>";     // default schema name

  private static final String STR_CHANGES_DETECTED = "database meta data differs from object model";
  private static final String STR_NO_CHANGES_DETECTED = "database meta data matches object model";


  /**
   * The name of the created sql file.
   */
  @Parameter(defaultValue = "migratemodel.sql",
             property = "tentackle.migrateSqlFile")
  private String sqlFileName;

  /**
   * Optionally, the generated migration file is passed to freemarker as a template.
   */
  @Parameter(defaultValue = "false",
             property = "tentackle.migrateSqlAsTemplate")
  private boolean asTemplate;

  /**
   * The generated SQL file can be splitted into one file per entity.<br>
   * This is especially useful if the migrations are processed any further,
   * for example by shell scripts.
   * <p>
   * The main migration file will just contain statistic comments.<br>
   * Each migrated table gets its own directory which is a number starting at 1
   * and as much leading zeros as necessary to keep the directory names in numberic order.<br>
   * If there is a before-all SQL, it will go into the folder zero, i.e. 0 or 00, etc...<br>
   * Any after-all SQL will go into the folder with the highest number.
   */
  @Parameter
  protected boolean split;

  /**
   * Fail if the model differs from the database meta data.<br>
   * Useful to validate that no migrations are necessary.
   * <p>
   * Same as {@code validate} goal.
   */
  @Parameter
  protected boolean validate;

  /**
   * Fail if unexpected tables are found in the database.
   */
  @Parameter(defaultValue = "false")
  protected boolean failOnUnexpectedTables;


  /**
   * The database meta data retrieved from the backend.
   */
  private ModelMetaData modelMetaData;

  /**
   * The migration hints for current backend.
   */
  private MigrationHints backendMigrationHints;

  /**
   * The generated SQL code.
   */
  private StringBuilder sqlCode;

  /**
   * Number of processed tables per schema.
   */
  private Map<String,Integer> tableStats;

  /**
   * Number of processed entities.
   */
  private int processedEntites;

  /**
   * Number of skipped entities.
   */
  private int skippedEntites;

  /**
   * Map of splitted migrations.
   */
  private Map<String, String> splittedMigrations;   // <tablename:SQL-code>

  /**
   * Migration hints per jdbc-Url.
   */
  private Map<String, MigrationHints> migrationHints;

  /**
   * Map of all entities, migrated or not.
   */
  private Map<String, Boolean> migratedEntitiesMap;   // <entity-name>:TRUE|FALSE

  /**
   * All other remaining tables in the database, except the alien tables.
   */
  private Map<String, DatabaseMetaDataTableHeader> otherTablesMap;

  /**
   * Found alien tables.
   */
  private Map<String, DatabaseMetaDataTableHeader> alienTablesMap;

  @Override
  protected void openResources(BackendInfo backendInfo) throws MojoExecutionException {
    if (backendInfo.isConnectable()) {
      super.openResources(backendInfo);
      try {
        DatabaseMetaData[] metaData = backendInfo.getBackend().getMetaData(backendInfo);
        modelMetaData = new ModelMetaData(backendInfo.getBackend(), metaData, backendInfo.getSchemas());
        // collect other tables (not in model)
        otherTablesMap = new TreeMap<>();
        alienTablesMap = new HashMap<>();
        String[] schemas = backendInfo.getSchemas();
        for (DatabaseMetaDataTableHeader header: modelMetaData.getAllTablesInDatabase()) {
          if (!backendInfo.getBackend().isReservedTableName(header.getName()) &&
              header.isUserTable() && (!checkReservedTables || !header.isReserved())) {
            if (header.getSchema() != null && !header.getSchema().isEmpty()) {
              if (backendInfo.getBackend().isReservedSchemaName(header.getSchema())) {
                continue;
              }
              if (schemas != null) {
                boolean inSchemas = false;
                for (String schema : schemas) {
                  if (schema.equalsIgnoreCase(header.getSchema())) {
                    inSchemas = true;
                    break;
                  }
                }
                if (!inSchemas) {
                  continue;
                }
              }
            }
            if (isAlienTable(header.getName())) {
              alienTablesMap.put(header.getName(), header);
            }
            else {
              DatabaseMetaDataTableHeader oldHeader = otherTablesMap.put(header.getName(), header);
              if (oldHeader != null) {
                getLog().warn("table '" + header.getName() + "' found in more than one schema: " +
                              header.getSchema() + ", " + oldHeader.getSchema());
              }
            }
          }
        }
      }
      catch (SQLException sqx) {
        throw new MojoExecutionException("could not retrieve metadata from " + backendInfo, sqx);
      }
      getLog().info("migration hint filter: " + VersionFileFilter.getInstance().getName());
      backendMigrationHints = migrationHints.get(backendInfo.getUrl());
      if (backendMigrationHints != null) {
        if (backendMigrationHints.getHintFileNames().isEmpty()) {
          getLog().info("no migration hints applied");
        }
        else {
          StringBuilder buf = new StringBuilder();
          buf.append("migration hints: ");
          boolean needComma = false;
          for (String hintFileName : backendMigrationHints.getHintFileNames()) {
            if (needComma) {
              buf.append(", ");
            }
            else {
              needComma = true;
            }
            buf.append(hintFileName);
            if (buf.length() > 80) {
              getLog().info(buf.toString());
              buf.setLength(0);
              needComma = false;
            }
          }
          if (needComma) {
            getLog().info(buf.toString());
          }
        }
      }
      sqlCode = new StringBuilder();
      splittedMigrations = new LinkedHashMap<>();
      migratedEntitiesMap = new HashMap<>();
      if (useDropIfExists) {
        Backend backend = backendInfo.getBackend();
        backend.setDropIfExistsEnabled(true);
        if (!backend.isDropIfExistsEnabled()) {
          getLog().warn("backend " + backend + " does not support DROP IF EXISTS");
        }
      }
    }
    else  {
      getLog().debug("no connection parameters available for " + backendInfo + " -> skipped");
    }
  }

  @Override
  protected void closeResources(BackendInfo backendInfo) throws MojoExecutionException, MojoFailureException {

    boolean migrationNecessary = false;

    try {
      if (generateHeaderComment) {
        sqlWriter.append(printTableStats(backendInfo.getBackend().getDefaultSchema()));
        sqlWriter.append("-- entities in model: processed=")
            .append(Integer.toString(processedEntites))
            .append(", skipped=")
            .append(Integer.toString(skippedEntites)).append("\n");

        if (!otherTablesMap.isEmpty()) {
          sqlWriter.append("-- unexpected tables in database not belonging to the model: ").append(Integer.toString(otherTablesMap.size())).append("\n");
          for (DatabaseMetaDataTableHeader header : otherTablesMap.values()) {
            if (failOnUnexpectedTables) {
              sqlWriter.append(backendInfo.getBackend().sqlDropTable(header.getSchema(), header.getName()));
            }
            else {
              sqlWriter.append("--     ").append(header.toString()).append("\n");
            }
          }
          if (!failOnUnexpectedTables) {
            sqlWriter.append("-- please add to <alienTables> or consider removal\n");
          }
        }
      }

      if (!split) {
        String sql = backendMigrationHints.getAlwaysBefore();
        if (!sql.isEmpty()) {
          sqlWriter.append("\n-- always before\n").append(sql).append("\n\n--\n");
        }
      }

      if (sqlCode.length() > 0 || !splittedMigrations.isEmpty()) {
        if (split) {
          sqlWriter.append("-- migrated entities in subfolders: ").append(String.valueOf(splittedMigrations.size())).append("\n");
        }
        else {
          String sql = backendMigrationHints.getBeforeAll();
          if (!sql.isEmpty()) {
            sqlWriter.append("\n-- before all\n").append(sql).append("\n\n--\n");
          }

          sqlWriter.append(sqlCode.toString());

          sql = backendMigrationHints.getAfterAll();
          if (!sql.isEmpty()) {
            sqlWriter.append("\n\n-- after all\n").append(sql).append("\n\n--\n");
          }
        }
        migrationNecessary = true;
      }
      else if (generateHeaderComment) {
        sqlWriter.append("-- database meta data matches object model -> no migration necessary\n");
      }

      if (!split) {
        String sql = backendMigrationHints.getAlwaysAfter();
        if (!sql.isEmpty()) {
          sqlWriter.append("\n\n-- always after\n").append(sql).append("\n\n--\n");
        }
      }
    }
    catch (IOException iox) {
      throw new MojoExecutionException("cannot write to sql file " + sqlFile.getAbsolutePath(), iox);
    }

    super.closeResources(backendInfo);

    if (modelMetaData != null) {
      for (DatabaseMetaData metaData: modelMetaData.getMetaData()) {
        Connection con = null;
        try {
          con = metaData.getConnection();
          if (con != null && !con.isClosed()) {
            con.close();
          }
        }
        catch (SQLException sqx) {
          getLog().warn("could not close connection " + con, sqx);
        }
      }
    }

    if (asTemplate) {
      new MigrateGenerator(this, sqlFile, backendInfo, migratedEntitiesMap).generate(sqlFileName);
    }

    if (migrationNecessary) {
      if (validate) {
        throw new MojoFailureException(STR_CHANGES_DETECTED);
      }
      getLog().warn(STR_CHANGES_DETECTED);
    }
    else {
      getLog().info(STR_NO_CHANGES_DETECTED);
    }

    if (failOnUnexpectedTables && !otherTablesMap.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      buf.append(otherTablesMap.size()).append(" unexpected tables found in database:");
      for (DatabaseMetaDataTableHeader header: otherTablesMap.values()) {
        buf.append('\n').append(header);
      }
      throw new MojoFailureException(buf.toString());
    }
  }

  @Override
  protected boolean validate() throws MojoExecutionException {
    migrationHints = new HashMap<>();
    return super.validate();
  }

  @Override
  protected void processBackend(BackendInfoParameter backendParameter, BackendInfo backendInfo) throws MojoExecutionException {
    MigrationHints hints = backendParameter.loadMigrationHints(backendInfo, this, verbosityLevel.isDebug(), resourceDirs);
    migrationHints.put(backendInfo.getUrl(), hints);
    getLog().info("backend: " + backendInfo);
    getLog().info("version: " + backendParameter.minVersion);
  }

  @Override
  protected String getSqlFileName() {
    if (asTemplate) {
      // generate into ftl file first
      return sqlFileName.replace('.', '-') + ".ftl";    // e.g. migratemodel-sql.ftl
    }
    return sqlFileName;
  }

  @Override
  protected Collection<BackendInfo> getBackendInfosToExecute() {
    return connectableBackendInfos.values();
  }

  @Override
  protected void processFileSet(BackendInfo backendInfo, FileSet fileSet) throws MojoExecutionException {
    if (backendInfo.isConnectable()) {

      int errorCount = 0;

      if (fileSet.getDirectory() == null) {
        // directory missing: use sourceDir as default
        fileSet.setDirectory(modelDir.getAbsolutePath());
      }

      File modelDir = new File(fileSet.getDirectory());
      String modelDirName = getCanonicalPath(modelDir);
      if (verbosityLevel.isDebug()) {
        getLog().info("processing files in " + modelDirName);
      }

      writeModelIntroComment(backendInfo, modelDirName);
      MigrationHints hints = migrationHints.get(backendInfo.getUrl());
      ModelDefaults defaults = getModelDefaults();
      EntityAliases aliases = getEntityAliases();

      Map<String, String> renamedTables;      // map <new:old> tablename
      Map<String, String> revRenamedTables;   // map <old:new> tablename

      Collection<ForeignKey> foreignKeys;
      try {
        Model model = Model.getInstance();
        if (mapSchemas) {
          model.setSchemaNameMapped(true);
        }
        model.loadModel(modelDirName, defaults, aliases);
        foreignKeys = Model.getInstance().getForeignKeys();
        // build table rename maps if hints given
        renamedTables = determineRenamedTables(hints);
        revRenamedTables = new HashMap<>();
        for (Map.Entry<String, String> entry: renamedTables.entrySet()) {
          String dup = revRenamedTables.put(entry.getValue(), entry.getKey());
          if (dup != null) {
            getLog().error("rename of '" + entry.getValue() + "' not unique! Found '" + entry.getKey() + "' and '" + dup + "'");
            errorCount++;
          }
        }
      }
      catch (ModelException mex) {
        throw new MojoExecutionException("parsing model failed in directory " + modelDirName, mex);
      }

      Set<String> migratedTablenames = new HashSet<>();          // to check for dependencies on other tables
      Map<String,String> pendingMigrations = new HashMap<>();    // pending migrations <tablename:sql>

      StringBuilder foreignKeySql = new StringBuilder();    // collected foreignkey migration code
      String[] fileNames = new FileSetManager(getLog(), verbosityLevel.isDebug()).getIncludedFiles(fileSet);

      if (fileNames.length > 0) {
        Arrays.sort(fileNames);
        for (String filename : fileNames) {
          // check if file exists
          File modelFile = new File(modelDirName, filename);
          if (!modelFile.exists()) {
            getLog().error("no such modelfile: " + filename);
            errorCount++;
          }
          else  {
            if (verbosityLevel.isDebug()) {
              getLog().info("processing " + modelFile);
            }
            try {
              Entity entity = Model.getInstance().getByFilePath(modelFile.getAbsolutePath());
              if (entity == null) {
                throw new MojoExecutionException("no entity for path " + modelFile.getAbsolutePath());
              }
              if (entity.getTableProvidingEntity() != entity || entity.getOptions().isProvided()) {
                continue;   // skip
              }

              // check schema, if set
              if (!mapSchemas && backendInfo.getSchemas() != null) {
                boolean add = false;
                for (String schema: backendInfo.getSchemas()) {
                  if (entity.getSchemaName() != null && schema.equalsIgnoreCase(entity.getSchemaName())) {
                    add = true;
                    break;
                  }
                }
                if (!add) {
                  getLog().debug(entity + " skipped because of wrong schema " + entity.getSchemaName());
                  skippedEntites++;
                  continue;   // skip
                }
              }

              processedEntites++;
              StringBuilder tableSql = new StringBuilder();
              String tableName = entity.getTableName();
              String plainTableName = entity.getTableNameWithoutSchema();
              String plainOldTableName = renamedTables.get(plainTableName);
              String oldTableName = entity.getSchemaName() != null && plainOldTableName != null ?
                oldTableName = entity.getSchemaName() + "." + plainOldTableName : plainOldTableName;

              TableMetaData table = backendInfo.getBackend().getTableMetaData(modelMetaData, oldTableName != null ? oldTableName : tableName);

              if (table != null) {
                // verify that this is a user-defined table we can migrate
                try {
                  DatabaseMetaDataTableHeader tableHeader = modelMetaData.getTableInDatabase(tableName);
                  if (tableHeader != null && !tableHeader.isUserTable()) {
                    getLog().error("'" + entity.getTableName() + "' already exists as " + tableHeader.getType());
                    errorCount++;
                    continue;
                  }
                }
                catch (SQLException sqx) {
                  getLog().warn("could not retrieve table header info for table " + tableName, sqx);
                }

                if (isAlienTable(plainTableName)) {
                  DatabaseMetaDataTableHeader header = alienTablesMap.get(plainTableName);
                  StringBuilder buf = new StringBuilder();
                  buf.append("table '").append(entity.getTableName()).append("' is reserved for other uses");
                  if (header != null && !StringHelper.isAllWhitespace(header.getComment())) {
                    buf.append(": ").append(header.getComment());
                  }
                  getLog().error(buf.toString());
                  errorCount++;
                  continue;
                }
                else {
                  modelMetaData.addTableMetaData(table);
                  countTableStats(table);
                }

                if (oldTableName != null) {
                  // generate RENAME TABLE first
                  otherTablesMap.remove(plainOldTableName);
                  tableSql.append('\n').append(backendInfo.getBackend().sqlRenameTable(oldTableName, plainTableName));
                }
              }
              otherTablesMap.remove(plainTableName);

              // filter foreign keys
              Collection<ForeignKey> relatedForeignKeys = new ArrayList<>();
              if (foreignKeys != null) {
                for (ForeignKey key : foreignKeys) {
                  if (key.getReferencingEntity().equals(entity)) {
                    relatedForeignKeys.add(key);
                  }
                }
              }

              // migrate foreign key metadata (since RENAME TABLE migrates the FK as well and we don't need explicit migrations)
              if (table != null) {
                for (ForeignKeyMetaData foreignKeyMetaData : table.getForeignKeys()) {
                  String oldPrimaryKeyTable = revRenamedTables.get(foreignKeyMetaData.getPrimaryKeyTable());
                  if (oldPrimaryKeyTable != null) {
                    foreignKeyMetaData.setPrimaryKeyTable(oldPrimaryKeyTable);
                  }
                }
              }

              TableMigrator tableMigrator = new TableMigrator(entity, relatedForeignKeys, backendInfo.getBackend(), table);
              TableMigrator.Result migrationResult = tableMigrator.migrate(
                  hints.getHints(tableName), hints.getColumnMigrations(tableName), hints.getRenameColumns(tableName));

              // table will have to be migrated?
              boolean migrated = !migrationResult.getTableSql().isEmpty();

              migratedEntitiesMap.put(entity.getName(), migrated);

              if (migrated) {
                // check if explicit migration code is available
                String sql = backendMigrationHints.getMigrateTable(tableName);
                if (sql != null) {
                  tableSql.append("\n-- manual migration of ");
                  tableSql.append(tableName);
                  tableSql.append(":\n");
                  tableSql.append(backendInfo.getBackend().sqlComment(migrationResult.getTableSql() + "\n"));
                  tableSql.append(sql);
                  tableSql.append('\n');
                }
                else {
                  // run the generated migration
                  sql = backendMigrationHints.getBeforeTable(tableName);
                  if (sql != null) {
                    tableSql.append('\n');
                    tableSql.append(sql);
                  }
                  tableSql.append(migrationResult.getTableSql());
                  sql = backendMigrationHints.getAfterTable(tableName);
                  if (sql != null) {
                    tableSql.append(sql);
                    tableSql.append('\n');
                  }
                }
              }

              // foreign key migration is separate at end of migration
              foreignKeySql.append(migrationResult.getForeignKeySql());

              // check if migration needs to be delayed
              boolean migrate = true;
              Collection<String> dependencies = backendMigrationHints.getDependencies(tableName);
              if (dependencies != null && !dependencies.isEmpty() &&
                  isMigrationPending(dependencies, migratedTablenames)) {
                migrate = false;
                pendingMigrations.put(tableName, tableSql.toString());
              }

              if (migrate) {  // no dependencies
                migratedTablenames.add(tableName);
                flushSqlForTable(tableName, tableSql.toString());

                // check if we can now migrate some pending table
                for (Iterator<Map.Entry<String, String>> iter = pendingMigrations.entrySet().iterator(); iter.hasNext(); ) {
                  Map.Entry<String, String> entry = iter.next();
                  String pendingTablename = entry.getKey();
                  if (!isMigrationPending(backendMigrationHints.getDependencies(pendingTablename), migratedTablenames)) {
                    migratedTablenames.add(pendingTablename);
                    flushSqlForTable(pendingTablename, entry.getValue());
                    iter.remove();
                  }
                }
              }
            }

            catch (BackendException | ModelException ex) {
              getLog().error("parsing model failed for " + filename, ex);
              errorCount++;
            }
          }
        }
      }

      // process any still pending migration just one after the other.
      // obviously the dependencies are misordered, incomplete or whatever
      StringBuilder depErrors = new StringBuilder();
      for (Map.Entry<String,String> entry: pendingMigrations.entrySet()) {
        String pendingTablename = entry.getKey();
        if (isMigrationPending(backendMigrationHints.getDependencies(pendingTablename), migratedTablenames)) {
          String msg = "dependency loop detected for " + pendingTablename + "! migration order may be not as expected!";
          getLog().error(msg);
          depErrors.append("-- ");
          depErrors.append(msg);
          depErrors.append('\n');
          errorCount++;
        }
        flushSqlForTable(pendingTablename, entry.getValue());
      }

      if (foreignKeySql.length() > 0) {
        sqlCode.append("\n");
        sqlCode.append(foreignKeySql.toString());
      }

      if (depErrors.length() > 0) {
        sqlCode.append("\n\n-- ************* CAUTION *************\n");
        sqlCode.append(depErrors);
      }

      if (split) {
        writeSplittedMigrations(backendInfo);
      }

      getLog().info(getPathRelativeToBasedir(modelDirName) + ": " +
                    fileNames.length + " files processed, " +
                    errorCount + " errors, " +
                    getPathRelativeToBasedir(sqlDirName) + File.separator + sqlFileName + " created");

      totalErrors += errorCount;
    }
  }

  /**
   * Applies the rename table hints to determine the renamed tables.
   *
   * @param hints the migration hints
   * @return the map of tablenames (without schema) mapped to their old names
   * @throws ModelException if model is inconsistent
   */
  private Map<String, String> determineRenamedTables(MigrationHints hints) throws ModelException {
    Map<String, String> renameMap = new HashMap<>();
    for (Entity entity: Model.getInstance().getAllEntitites()) {
      String name = entity.getTableNameWithoutSchema();
      String oldName = hints.getRenameTable(name);
      if (oldName != null && otherTablesMap.get(name) == null) {
        // old name is in database and current name not in database -> rename hint applies!
        int ndx = oldName.indexOf('.');
        if (ndx >= 0) {
          oldName = oldName.substring(ndx + 1);
        }
        renameMap.put(name, oldName);
      }
    }
    return renameMap;
  }

  /**
   * Flushes the SQL statements for a table.
   *
   * @param tableName the tablename
   * @param sql the SQL statements
   */
  private void flushSqlForTable(String tableName, String sql) {
    if (!StringHelper.isAllWhitespace(sql)) {
      if (split) {
        splittedMigrations.put(tableName + ".sql", sql);
      }
      else {
        sqlCode.append(sql);
      }
    }
  }

  /**
   * Creates the splitted migrations.
   *
   * @param backendInfo the backend info
   * @throws MojoExecutionException if IO error
   */
  private void writeSplittedMigrations(BackendInfo backendInfo) throws MojoExecutionException {

    int digits = 1;

    String sql = backendMigrationHints.getAlwaysBefore();
    if (!sql.isEmpty()) {
      writeSplittedMigration(0, digits, "_always_before.sql", sql, backendInfo);
    }

    if (!splittedMigrations.isEmpty()) {
      sql = backendMigrationHints.getAfterAll();
      if (!sql.isEmpty()) {
        splittedMigrations.put("_after_all.sql", sql);
      }

      int tableCount = splittedMigrations.size() + (backendMigrationHints.getAlwaysAfter().isEmpty() ? 0 : 1);
      while ((tableCount /= 10) > 0) {
        digits++;
      }

      int splitCounter = 1;
      for (Map.Entry<String, String> entry : splittedMigrations.entrySet()) {
        writeSplittedMigration(splitCounter++, digits, entry.getKey(), entry.getValue(), backendInfo);
      }

      sql = backendMigrationHints.getBeforeAll();
      if (!sql.isEmpty()) {
        writeSplittedMigration(0, digits, "_before_all.sql", sql, backendInfo);
      }
    }

    sql = backendMigrationHints.getAlwaysAfter();
    if (!sql.isEmpty()) {
      writeSplittedMigration(splittedMigrations.size() + 1, digits, "_always_after.sql", sql, backendInfo);
    }
  }

  /**
   * Writes a single migration file into its own subfolder.
   *
   * @param subDirCount the folder count
   * @param digits the number of digits for the folder name
   * @param fileName the sql filename in the folder
   * @param sql the SQL code
   * @param backendInfo the backend info
   * @throws MojoExecutionException if IO error
   */
  private void writeSplittedMigration(int subDirCount, int digits, String fileName, String sql, BackendInfo backendInfo) throws MojoExecutionException {
    StringBuilder subDirName = new StringBuilder(Integer.toString(subDirCount));
    while (subDirName.length() < digits) {
      subDirName.insert(0, '0');
    }
    File dir = new File(sqlFile.getParent(), subDirName.toString());
    dir.mkdirs();

    File file = new File(dir, asTemplate ? fileName.replace('.', '-') + ".ftl" : fileName);
    try (Writer writer = Files.newBufferedWriter(file.toPath(), Settings.getEncodingCharset(),
                                                                StandardOpenOption.CREATE,
                                                                StandardOpenOption.TRUNCATE_EXISTING)) {
      writer.write(sql);
    }
    catch (IOException e) {
      throw new MojoExecutionException("writing to " + file + " failed", e);
    }
    getLog().debug(file + " created");

    if (asTemplate) {
      new MigrateGenerator(this, file, backendInfo, migratedEntitiesMap).generate(fileName);
    }
  }


  /**
   * Checks whether migration needs to be delayed.
   *
   * @param dependencies the dependencies to check
   * @param migratedTablenames the already migrated tables
   * @return true if still pending
   */
  private boolean isMigrationPending(Collection<String> dependencies, Set<String> migratedTablenames) {
    boolean pending = false;
    for (String dependency: dependencies) {
      if (!migratedTablenames.contains(dependency)) {
        pending = true;
        break;
      }
    }
    return pending;
  }

  /**
   * Counts a processed table.
   *
   * @param table the table meta data
   */
  private void countTableStats(TableMetaData table) {
    if (tableStats == null) {
      tableStats = new TreeMap<>();    // sorted by schema name
    }
    String schema = StringHelper.isAllWhitespace(table.getSchemaName()) ? DEFAULT_SCHEMA : table.getSchemaName();
    Integer count = tableStats.get(schema);
    if (count == null) {
      count = 0;
    }
    tableStats.put(schema, ++count);
  }

  private String printTableStats(String defaultSchema) {
    StringBuilder buf = new StringBuilder();
    if (tableStats == null) {
      buf.append("-- no tables found in database!\n");
    }
    else {
      buf.append("-- schemas/tables in database: ");
      boolean needComma = false;
      int total = 0;
      for (Map.Entry<String, Integer> entry : tableStats.entrySet()) {
        String schema = entry.getKey();
        if (defaultSchema != null && DEFAULT_SCHEMA.equals(schema)) {
          schema = defaultSchema;
        }
        if (needComma) {
          buf.append(", ");
        }
        buf.append(schema).append('=').append(entry.getValue());
        needComma = true;
        total += entry.getValue();
      }
      if (tableStats.size() > 1) {
        buf.append(", total=").append(total);
      }
      buf.append('\n');
    }
    return buf.toString();
  }

}

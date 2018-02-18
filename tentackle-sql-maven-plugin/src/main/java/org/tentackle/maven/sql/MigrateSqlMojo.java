/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.maven.sql;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.migrate.TableMigrator;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

/**
 * Generate sql script to migrate the tables.
 *
 * @author harald
 */
@Mojo(name = "migrate")
public class MigrateSqlMojo extends AbstractSqlMojo {

  private static final String DEFAULT_SCHEMA = "<default>";     // default schema name

  /**
   * The name of the created sql file.
   */
  @Parameter(defaultValue = "migratemodel.sql",
             property = "tentackle.migrateSqlFile")
  private String sqlFileName;

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


  @Override
  protected void openResources(BackendInfo backendInfo) throws MojoExecutionException {
    if (backendInfo.isConnectable()) {
      super.openResources(backendInfo);
      try {
        DatabaseMetaData[] metaData = backendInfo.getBackend().getMetaData(backendInfo);
        modelMetaData = new ModelMetaData(backendInfo.getBackend(), metaData, backendInfo.getSchemas());
      }
      catch (SQLException sqx) {
        throw new MojoExecutionException("could not retrieve metadata from " + backendInfo, sqx);
      }
      backendMigrationHints = migrationHints.get(backendInfo.getUrl());
      sqlCode = new StringBuilder();
    }
    else  {
      getLog().debug("no connection parameters available for " + backendInfo + " -> skipped");
    }
  }

  @Override
  protected void closeResources(BackendInfo backendInfo) throws MojoExecutionException {
    try {
      sqlWriter.append(printTableStats(backendInfo.getBackend().getDefaultSchema()));
      sqlWriter.append("-- entities in model: processed=")
               .append(Integer.toString(processedEntites))
               .append(", skipped=")
               .append(Integer.toString(skippedEntites)).append("\n");

      if (sqlCode.length() > 0) {
        String sql = backendMigrationHints.getBeforeAll();
        if (!sql.isEmpty()) {
          sqlWriter.append("\n-- before all:\n");
          sqlWriter.append(sql);
          sqlWriter.append("\n\n");
        }

        sqlWriter.append(sqlCode.toString());

        sql = backendMigrationHints.getAfterAll();
        if (!sql.isEmpty()) {
          sqlWriter.append("\n\n-- after all:\n");
          sqlWriter.append(sql);
          sqlWriter.append('\n');
        }
      }
      else {
        sqlWriter.append("-- relational model matches object model -> no migration necessary\n");
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
  }




  @Override
  protected String getSqlFileName() {
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

      File dir = new File(fileSet.getDirectory());
      String modelDirName = getCanonicalPath(dir);
      if (verbosityLevel.isDebug()) {
        getLog().info("processing files in " + modelDirName);
      }

      writeModelIntroComment(backendInfo, modelDirName);
      ModelDefaults defaults = getModelDefaults();
      EntityAliases aliases = getEntityAliases();

      Collection<ForeignKey> foreignKeys = null;
      try {
        Model model = Model.getInstance();
        if (mapSchemas) {
          model.setSchemaNameMapped(true);
        }
        model.loadModel(modelDirName, defaults, aliases);
        foreignKeys = Model.getInstance().getForeignKeys();
      }
      catch (ModelException mex) {
        getLog().error("parsing model failed in directory " + modelDirName, mex);
        errorCount++;
      }

      MigrationHints hints = migrationHints.get(backendInfo.getUrl());

      Set<String> migratedTablenames = new HashSet<>();          // to check for dependencies on other tables
      Map<String,String> pendingMigrations = new HashMap<>();    // pending migrations <tablename:sql>

      StringBuilder foreignKeySql = new StringBuilder();    // collected foreignkey migration code
      String[] fileNames = new FileSetManager(getLog(), verbosityLevel.isDebug()).getIncludedFiles(fileSet);

      if (fileNames.length > 0) {
        Arrays.sort(fileNames);
        for (String filename : fileNames) {
          // check if file exists
          File modelFile = new File(modelDirName + "/" + filename);
          if (!modelFile.exists()) {
            getLog().error("no such modelfile: " + filename);
            errorCount++;
          }
          else  {
            if (verbosityLevel.isDebug()) {
              getLog().info("processing " + modelFile);
            }
            try {
              Entity entity = Model.getInstance().loadByFileName(modelFile.getPath(), defaults, aliases);
              if (entity.getTableProvidingEntity() != entity) {
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

              TableMetaData table = backendInfo.getBackend().getTableMetaData(modelMetaData, entity.getTableName());
              if (table != null) {
                modelMetaData.addTableMetaData(table);
                countTableStats(table);
              }

              StringBuilder tableSql = new StringBuilder();

              String tableName = entity.getTableName();

              // filter foreign keys
              Collection<ForeignKey> relatedForeignKeys = new ArrayList<>();
              if (foreignKeys != null) {
                for (ForeignKey key : foreignKeys) {
                  if (key.getReferencingEntity().equals(entity)) {
                    relatedForeignKeys.add(key);
                  }
                }
              }
              TableMigrator tableMigrator = new TableMigrator(entity, relatedForeignKeys, backendInfo.getBackend(), table);
              TableMigrator.Result migrationResult = tableMigrator.migrate(
                      hints.getHints(tableName), hints.getColumnMigrations(tableName));

              // table will have to be migrated?
              boolean migrated = !migrationResult.getTableSql().isEmpty();

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
                else  {
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
                sqlCode.append(tableSql);

                // check if we can now migrate some pending table
                for (Iterator<Map.Entry<String,String>> iter = pendingMigrations.entrySet().iterator(); iter.hasNext(); ) {
                  Map.Entry<String,String> entry = iter.next();
                  String pendingTablename = entry.getKey();
                  if (!isMigrationPending(backendMigrationHints.getDependencies(pendingTablename), migratedTablenames)) {
                    migratedTablenames.add(pendingTablename);
                    sqlCode.append(entry.getValue());
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
        sqlCode.append(entry.getValue());
      }

      if (foreignKeySql.length() > 0) {
        sqlCode.append("\n");
        sqlCode.append(foreignKeySql.toString());
      }

      if (depErrors.length() > 0) {
        sqlCode.append("\n\n-- ************* CAUTION *************\n");
        sqlCode.append(depErrors);
      }

      getLog().info(getPathRelativeToBasedir(modelDirName) + ": " +
                    fileNames.length + " files processed, " +
                    errorCount + " errors, " +
                    getPathRelativeToBasedir(sqlDirName) + File.separator + sqlFile.getName() + " created");

      totalErrors += errorCount;
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

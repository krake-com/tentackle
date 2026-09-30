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

import org.tentackle.common.Settings;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Index;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates an SQL script to create the database tables according to the model.
 *
 * @author harald
 */
@Mojo(name = "create", aggregator = true, threadSafe = true)
public class CreateSqlMojo extends AbstractSqlMojo {

  /**
   * The name of the created sql file.
   */
  @Parameter(defaultValue = "createmodel.sql",
             property = "tentackle.createSqlFile")
  private String sqlFileName;

  /**
   * Generate SQL to create schemas.<br>
   * If the model contains table definitions with schemas and the database doesn't support it,
   * the {@link #mapSchemas} flag must be set.
   */
  @Parameter(defaultValue = "true")
  private boolean createSchemas;

  /**
   * Prepends the SQL code for the model found in the resources of the classpath.<br>
   * Useful to create the database tables for dependencies, for example, the Tentackle model.<br>
   * Requires that the plugin dependencies contain the model resources
   * (see {@link #dumpModelIndex}) amd {@link #dumpModelSource}).
   */
  @Parameter
  private boolean prependModelFromClassPath;

  /**
   * Same as {@link #prependModelFromClassPath}, but appends the SQL to the generated script.
   */
  @Parameter
  private boolean appendModelFromClassPath;

  /**
   * Optional list of sequences to create.
   */
  @Parameter
  private List<SequenceParameter> sequences;

  /**
   * Optional filesets to prepend to the generated sql file.
   */
  @Parameter
  private List<FileSet> prependFilesets;

  /**
   * Optional filesets to append to the generated sql file.
   */
  @Parameter
  private List<FileSet> appendFilesets;


  /**
   * Set of created schemas so far.
   */
  private final Set<String> createdSchemas = new HashSet<>();

  /**
   * Set of backends processed so far.
   * Used if {@link #prependModelFromClassPath} or {@link #appendModelFromClassPath} is true to
   * generate the classpath model only once per backend.
   */
  private final Set<BackendInfo> processedBackendInfos = new HashSet<>();


  @Override
  protected String getSqlFileName() {
    return sqlFileName;
  }

  @Override
  protected Collection<BackendInfo> getBackendInfosToExecute() {
    return backendInfos.values();
  }

  @Override
  protected void createSqlFile(BackendInfo backendInfo) throws MojoExecutionException {
    super.createSqlFile(backendInfo);
    copyFileSets(prependFilesets);
  }

  @Override
  protected void closeResources(BackendInfo backendInfo) throws MojoExecutionException, MojoFailureException {
    createSequences(backendInfo.getBackend());
    copyFileSets(appendFilesets);
    super.closeResources(backendInfo);
  }

  @Override
  protected void processFileSet(BackendInfo backendInfo, FileSet fileSet) throws MojoExecutionException {

    int errorCount = 0;
    Backend backend = backendInfo.getBackend();

    if (prependModelFromClassPath && processedBackendInfos.add(backendInfo)) {
      if (createSqlForClassPath(backendInfo)) {
        errorCount++;
      }
    }

    if (fileSet.getDirectory() == null) {
      // directory missing: use modelDir as default
      fileSet.setDirectory(modelDir.getAbsolutePath());
    }

    File modelDir = new File(fileSet.getDirectory());
    String modelDirName = getCanonicalPath(modelDir);
    if (verbosityLevel.isDebug()) {
      getLogger().info("processing files in {}", modelDirName);
    }

    writeModelIntroComment(backendInfo, modelDirName);

    Set<Entity> generatedEntities = new HashSet<>();    // entities code was generated for
    Collection<ForeignKey> foreignKeys = null;
    try {
      Model model = getModel();
      model.setModelDefaults(getModelDefaults());
      model.setEntityAliases(getEntityAliases());
      model.setSchemaNameMapped(mapSchemas);
      model.loadFromDirectory(modelDirName, true);
      foreignKeys = model.getForeignKeys();
      warnIfDeferredNotSupported(backend, model.getAllEntities(), foreignKeys);
    }
    catch (ModelException mex) {
      getLogger().error("parsing model failed in directory {}:\n{}", modelDirName, mex.getMessage());
      errorCount++;
    }

    String[] fileNames = getIncludedFiles(fileSet);
    if (fileNames.length > 0) {
      Arrays.sort(fileNames);
      for (String filename : fileNames) {
        // check if file exists
        File modelFile = new File(modelDirName, filename);
        if (!modelFile.exists()) {
          getLogger().error("no such model file: {}", filename);
          errorCount++;
        }
        else  {
          if (verbosityLevel.isDebug()) {
            getLogger().info("processing {}", modelFile);
          }
          try {
            URL url = modelFile.toURI().toURL();
            Entity entity = getModel().getByURL(url);
            if (entity == null) {
              throw new MojoExecutionException("no entity for " + url);
            }

            if (entity.getTableProvidingEntity() == entity && !entity.getOptions().isProvided()) {
              String plainTableName = entity.getTableNameWithoutSchema();
              if (isAlienTable(plainTableName)) {
                // let the goal fail, but generate SQL despite the error (to see what it would look like)
                getLogger().error("table '{}' is reserved for other uses", entity.getTableName());
                errorCount++;
              }
              if (checkReservedTables && checkAndLogReservedTable(entity)) {
                errorCount++;
              }

              if (entity.getSchemaName() != null && backend.isReservedSchemaName(entity.getSchemaName())) {
                getLogger().error("schema '{}' is a reserved word for backend {}", entity.getSchemaName(), backend);
                errorCount++;
              }

              generatedEntities.add(entity);    // for foreign key filtering below
              createTableSql(entity, backend);
            }
          }
          catch (IOException iox) {
            throw new MojoExecutionException("cannot write table definitions to sql file " + sqlFile.getAbsolutePath(), iox);
          }
          catch (ModelException wex) {
            getLogger().error("parsing model failed for {}", filename, wex);
            errorCount++;
          }
        }
      }
    }

    try {
      // create referential integrity
      if (foreignKeys != null) {
        createForeignKeySql(
            // only for generated entities
            foreignKeys.stream().filter(fk -> generatedEntities.contains(fk.getReferencingEntity()) ||
                                              generatedEntities.contains(fk.getReferencedEntity())).toList(),
            backend);
      }
    }
    catch (IOException iox) {
      throw new MojoExecutionException("cannot write constraints to sql file " + sqlFile.getAbsolutePath(), iox);
    }

    if (appendModelFromClassPath && processedBackendInfos.add(backendInfo)) {
      if (createSqlForClassPath(backendInfo)) {
        errorCount++;
      }
    }

    getLogger().info("{}: {} files processed, {} errors, {}{}{} created",
                     getPathRelativeToBasedir(modelDirName), fileNames.length, errorCount,
                     getPathRelativeToBasedir(sqlDirName), File.separator, sqlFile.getName());

    totalErrors += errorCount;
  }


  /**
   * Copies the contents of the given filesets to the generated sql file.
   *
   * @param fileSets the filesets, null if none
   */
  protected void copyFileSets(List<FileSet> fileSets) throws MojoExecutionException {
    if (fileSets != null) {
      for (FileSet fileSet: fileSets) {
        if (fileSet.getDirectory() == null) {
          // relative to basedir if not given
          fileSet.setDirectory(getBaseDir().getAbsolutePath());
        }
        for (String filename : getIncludedFiles(fileSet)) {
          String fullName = fileSet.getDirectory() + File.separator + filename;
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fullName), Settings.getEncodingCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
              sqlWriter.append(line).append('\n');
            }
          }
          catch (IOException e) {
            throw new MojoExecutionException("cannot copy " + fullName, e);
          }
        }
      }
    }
  }


  /**
   * Creates the SQL code for sequences.
   *
   * @param backend the backend
   * @throws MojoExecutionException if failed
   */
  protected void createSequences(Backend backend) throws MojoExecutionException {
    try {
      if (sequences != null && !sequences.isEmpty()) {
        if (backend.isSequenceSupported()) {
          sqlWriter.append('\n').append(backend.getSingleLineComment()).append(" sequences\n");
          for (SequenceParameter sequence : sequences) {
            if (sequence.name != null) {
              sqlWriter.append(backend.sqlCreateSequence(sequence.name, sequence.start, sequence.increment)).append(backend.getStatementSeparator());
              if (!StringHelper.isAllWhitespace(sequence.comment)) {
                String sqlComment = backend.sqlCreateSequenceComment(sequence.name, sequence.comment);
                if (!StringHelper.isAllWhitespace(sqlComment)) {
                  sqlWriter.append('\n').append(sqlComment);
                }
                else {
                  sqlWriter.append(' ').append(backend.getSingleLineComment()).append(' ').append(sequence.comment).append('\n');
                }
              }
              else {
                sqlWriter.append('\n');
              }
            }
          }
        }
        else {
          sqlWriter.append('\n').append(backend.getSingleLineComment()).append(" sequences not supported by backend ")
                   .append(String.valueOf(backend)).append("!\n");
        }
      }
    }
    catch (IOException e) {
      throw new MojoExecutionException("cannot create sequence", e);
    }
  }


  private boolean createSqlForClassPath(BackendInfo backendInfo) throws MojoExecutionException {
    boolean failed = false;
    Backend backend = backendInfo.getBackend();
    try {
      Model model = getModel();
      model.clearModel();
      model.loadFromResources(true);
      Collection<Entity> allEntities = model.getAllEntities();
      if (!allEntities.isEmpty()) {
        writeModelIntroComment(backendInfo, "<classpath>");
        for (Entity entity : allEntities) {
          createTableSql(entity, backend);
        }
        createForeignKeySql(model.getForeignKeys(), backend);
      }
      model.clearModel();
    }
    catch (IOException iox) {
      throw new MojoExecutionException("cannot write classpath model's table definitions to sql file " + sqlFile.getAbsolutePath(), iox);
    }
    catch (ModelException mex) {
      getLogger().error("parsing model from classpath failed:\n{}", mex.getMessage());
      failed = true;
    }
    return failed;
  }

  private void createTableSql(Entity entity, Backend backend) throws IOException, ModelException {
    if (createSchemas && !mapSchemas) {
      String schemaName = entity.getSchemaName();
      if (!StringHelper.isAllWhitespace(schemaName) && createdSchemas.add(schemaName)) {
        sqlWriter.append('\n').append(backend.sqlCreateSchema(schemaName)).append(backend.getStatementSeparator()).append('\n');
      }
    }

    // create table
    sqlWriter.append('\n');
    sqlWriter.append(entity.sqlCreateTable(backend));

    // create indexes
    for (Index index : entity.getTableIndexes()) {
      sqlWriter.append(index.sqlCreateIndex(backend, entity));
    }
  }

  private void createForeignKeySql(Collection<ForeignKey> foreignKeys, Backend backend) throws IOException {
    if (!foreignKeys.isEmpty()) {
      sqlWriter.append('\n').append(backend.getSingleLineComment()).append(" referential integrity\n");
      for (ForeignKey foreignKey : foreignKeys) {
        sqlWriter.append(foreignKey.sqlCreateForeignKey(backend));
      }
    }
  }

}

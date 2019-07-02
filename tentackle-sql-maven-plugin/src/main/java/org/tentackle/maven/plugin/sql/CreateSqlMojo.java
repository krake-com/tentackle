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
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import org.tentackle.common.Settings;
import org.tentackle.model.Entity;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Index;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.sql.BackendInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Generate sql script to create the tables.
 *
 * @author harald
 */
@Mojo(name = "create")
public class CreateSqlMojo extends AbstractSqlMojo {

  /**
   * The name of the created sql file.
   */
  @Parameter(defaultValue = "createmodel.sql",
             property = "tentackle.createSqlFile")
  private String sqlFileName;

  /**
   * Optional filesets to prepend to the generated sql file.
   */
  @Parameter
  protected List<FileSet> prependFilesets;

  /**
   * Optional filesets to append to the generated sql file.
   */
  @Parameter
  protected List<FileSet> appendFilesets;


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
  protected void closeResources(BackendInfo backendInfo) throws MojoExecutionException {
    copyFileSets(appendFilesets);
    super.closeResources(backendInfo);
  }

  @Override
  protected void processFileSet(BackendInfo backendInfo, FileSet fileSet) throws MojoExecutionException {

    int errorCount = 0;

    if (fileSet.getDirectory() == null) {
      // directory missing: use modelDir as default
      fileSet.setDirectory(modelDir.getAbsolutePath());
    }

    File modelDir = new File(fileSet.getDirectory());
    String modelDirName = getCanonicalPath(modelDir);
    if (verbosityLevel.isDebug()) {
      getLog().info("processing files in " + modelDirName);
    }

    writeModelIntroComment(backendInfo, modelDirName);

    Collection<ForeignKey> foreignKeys = null;
    try {
      Model model = Model.getInstance();
      if (mapSchemas) {
        model.setSchemaNameMapped(true);
      }
      model.loadModel(modelDirName, getModelDefaults(), getEntityAliases());
      foreignKeys = Model.getInstance().getForeignKeys();
    }
    catch (ModelException mex) {
      getLog().error("parsing model failed in directory " + modelDirName + ":\n" + mex.getMessage());
      errorCount++;
    }

    String[] fileNames = new FileSetManager(getLog(), verbosityLevel.isDebug()).getIncludedFiles(fileSet);
    if (fileNames.length > 0) {
      Arrays.sort(fileNames);
      for (String filename : fileNames) {
        // check if file exists
        File modelFile = new File(modelDir, filename);
        if (!modelFile.exists()) {
          getLog().error("no such modelfile: " + filename);
          errorCount++;
        }
        else  {
          if (verbosityLevel.isDebug()) {
            getLog().info("processing " + modelFile);
          }
          try {
            Entity entity = Model.getInstance().getByFileName(modelFile.getPath());
            if (entity == null) {
              throw new MojoExecutionException("no entity for path " + modelFile.getPath());
            }
            if (entity.getTableProvidingEntity() == entity && !entity.getOptions().isProvided()) {
              // create table
              sqlWriter.append('\n');
              sqlWriter.append(entity.sqlCreateTable(backendInfo.getBackend()));

              // create indexes
              for (Index index: entity.getTableIndexes()) {
                sqlWriter.append(index.sqlCreateIndex(backendInfo.getBackend(), entity));
              }
            }
          }
          catch (IOException iox) {
            throw new MojoExecutionException("cannot write table definitions to sql file " + sqlFile.getAbsolutePath(), iox);
          }
          catch (ModelException wex) {
            getLog().error("parsing model failed for " + filename, wex);
            errorCount++;
          }
        }
      }
    }

    try {
      // create referential integrity
      if (foreignKeys != null && !foreignKeys.isEmpty()) {
        sqlWriter.append("\n-- referential integrity\n");
        for (ForeignKey foreignKey: foreignKeys) {
          sqlWriter.append(foreignKey.sqlCreateForeignKey(backendInfo.getBackend()));
        }
      }
    }
    catch (IOException iox) {
      throw new MojoExecutionException("cannot write constraints to sql file " + sqlFile.getAbsolutePath(), iox);
    }

    getLog().info(getPathRelativeToBasedir(modelDirName) + ": " +
                  fileNames.length + " files processed, " +
                  errorCount + " errors, " +
                  getPathRelativeToBasedir(sqlDirName) + File.separator + sqlFile.getName() + " created");

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
          fileSet.setDirectory(getMavenProject().getBasedir().getAbsolutePath());
        }
        for (String filename : new FileSetManager(getLog(), verbosityLevel.isDebug()).getIncludedFiles(fileSet)) {
          String fullName = fileSet.getDirectory() + File.separator + filename;
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fullName), Settings.getEncodingCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
              sqlWriter.append(line).append('\n');
            }
          }
          catch (IOException e) {
            throw new MojoExecutionException("cannot read from " + fullName, e);
          }
        }
      }
    }
  }

}

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
import java.util.Arrays;
import java.util.Collection;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.tentackle.model.Entity;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Index;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.sql.BackendInfo;

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


  @Override
  protected String getSqlFileName() {
    return sqlFileName;
  }

  @Override
  protected Collection<BackendInfo> getBackendInfosToExecute() {
    return backendInfos.values();
  }

  @Override
  protected void processFileSet(BackendInfo backendInfo, FileSet fileSet) throws MojoExecutionException {

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

}

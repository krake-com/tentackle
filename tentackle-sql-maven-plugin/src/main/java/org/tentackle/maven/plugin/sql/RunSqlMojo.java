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

import org.tentackle.common.Settings;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.ScriptRunnerResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * Runs an SQL script against every configured connectable backend.<br>
 * The statements are executed in autocommit mode, i.e. without a transaction.
 * Each statement's result is logged.<br>
 * Example:
 * <pre>
 *   mvn tentackle-sql:run -Dscript=src/sql/myscript.sql
 * </pre>
 */
@Mojo(name = "run", aggregator = true, threadSafe = true)
public class RunSqlMojo extends AbstractSqlMojo {
  // extending AbstractSqlMojo to inherit the backend configuration which is usually the same as for the migrate-goal

  /**
   * The SQL script to execute.<br>
   * Read with the project's encoding.
   */
  @Parameter(required = true, property = "script")
  private File script;


  @Override
  public void executeImpl() throws MojoExecutionException {

    Path scriptPath = script.toPath();

    for (BackendInfo backendInfo : getBackendInfosToExecute()) {
      getLogger().info("connecting to {}", backendInfo);
      try (Connection connection = backendInfo.connect()) {
        for (ScriptRunnerResult result : backendInfo.getBackend().createScriptRunner(connection)
                                                    .run(Files.readString(scriptPath, Settings.getEncodingCharset()))) {
          getLogger().info(result.toString());
        }
      }
      catch (SQLException | IOException | BackendException ex) {
        throw new MojoExecutionException("SQL script execution failed", ex);
      }
    }
  }

  @Override
  protected Collection<BackendInfo> getBackendInfosToExecute() {
    return connectableBackendInfos.values();
  }

  @Override
  protected String getSqlFileName() {
    // not invoked (executeImpl is overridden)
    return null;
  }

  @Override
  protected void processFileSet(BackendInfo backendInfo, FileSet fileSet) throws MojoExecutionException {
    // not invoked (executeImpl is overridden)
  }

}

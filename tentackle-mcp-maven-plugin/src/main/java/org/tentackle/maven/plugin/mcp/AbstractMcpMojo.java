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

package org.tentackle.maven.plugin.mcp;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;

import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base mojo for the MCP server.
 * <p>
 * Declares the same model parameters as the wizard- and SQL plugins, with the same defaults and user properties.
 * Pointing the server at exactly the model the build sees is the whole point of hosting it in Maven, so an
 * application usually needs no configuration beyond the properties it already sets for the wurbelizer.
 *
 * @author harald
 */
public abstract class AbstractMcpMojo extends AbstractTentackleMojo {

  /**
   * Serializes the executions in parallel builds.<br>
   * The model held by the {@code ModelManager} is a singleton of the plugin's class realm, which is shared
   * by all projects of a build. Concurrent executions would load and reload the same model.
   */
  private static final Lock EXECUTION_LOCK = new ReentrantLock();

  @Override
  protected Lock getExecutionLock() {
    return EXECUTION_LOCK;
  }


  /** The model name. */
  @Parameter(defaultValue = ModelManager.DEFAULT_MODEL_NAME,
             property = "tentackle.modelName")
  protected String modelName;

  /**
   * Directory holding the model files to be processed.<br>
   * Ignored if filesets explicitly given.
   */
  @Parameter(defaultValue = "${project.build.directory}/wurbel/model", property = "tentackle.modelDir")
  protected File modelDir;

  /**
   * The model defaults.<br>
   * Without them the model is loaded exactly as written, which usually differs from what the build sees.
   */
  @Parameter(property = "tentackle.modelDefaults")
  protected String modelDefaults;

  /** The entity aliases. */
  @Parameter(property = "tentackle.entityAliases")
  protected String entityAliases;

  /** True if schema names are mapped. */
  @Parameter(property = "tentackle.mapSchemas")
  protected boolean mapSchemas;

  /**
   * The list of file sets containing the model.<br>
   * If set, the modelDir is ignored.
   */
  @Parameter
  protected List<FileSet> filesets;

  /** Load the model from the plugin dependencies on the classpath as well. */
  @Parameter(defaultValue = "false")
  protected boolean loadModelFromDependencies;

  /**
   * The comma-separated list of database backends the model is validated against.<br>
   * Without them names, types, and sizes are not verified. {@code all} selects every non-deprecated backend.
   */
  @Parameter(property = "tentackle.backends")
  protected String backends;


  /**
   * Gets the model defaults.
   *
   * @return the defaults, null if none
   * @throws MojoExecutionException if parsing the model defaults failed
   */
  protected ModelDefaults getModelDefaults() throws MojoExecutionException {
    if (modelDefaults == null) {
      return null;
    }
    try {
      return new ModelDefaults(modelDefaults);
    }
    catch (ModelException mex) {
      throw new MojoExecutionException(mex.getMessage(), mex);
    }
  }

  /**
   * Gets the entity aliases.
   *
   * @return the aliases, null if none
   * @throws MojoExecutionException if parsing the aliases failed
   */
  protected EntityAliases getEntityAliases() throws MojoExecutionException {
    if (entityAliases == null) {
      return null;
    }
    try {
      return new EntityAliases(entityAliases);
    }
    catch (ModelException mex) {
      throw new MojoExecutionException(mex.getMessage(), mex);
    }
  }

  /**
   * Gets the directories holding the model, in load order.
   * <p>
   * Either the configured filesets or, if none, the model directory.
   *
   * @return the directories, never null
   */
  protected List<String> getModelDirectories() {
    List<String> dirs = new ArrayList<>();
    if (filesets != null && !filesets.isEmpty()) {
      for (FileSet fileSet : filesets) {
        dirs.add(fileSet.getDirectory() == null ? modelDir.getAbsolutePath() : fileSet.getDirectory());
      }
    }
    else {
      dirs.add(modelDir.getAbsolutePath());
    }
    return dirs;
  }

  /**
   * Creates the configuration for the model session.
   *
   * @return the configuration
   * @throws MojoExecutionException if the configuration is malformed
   */
  protected ModelConfiguration createModelConfiguration() throws MojoExecutionException {
    return new ModelConfiguration(modelName, getModelDirectories(), loadModelFromDependencies,
                                  getModelDefaults(), getEntityAliases(), mapSchemas, backends);
  }

}

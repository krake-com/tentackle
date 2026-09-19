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

import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Renders the model as a set of cross-linked HTML pages, one per entity, plus an index page.<br>
 * Unlike the {@code ModelComment} wurblet's Javadoc comment block, entity names mentioned on a page
 * are hyperlinks to that entity's own page, so the whole model can be browsed by following
 * references, composite trees and inheritance in a browser.
 *
 * @author harald
 */
@Mojo(name = "document", aggregator = true)
public class DocumentSqlMojo extends AbstractTentackleMojo {

  /**
   * The model name.
   */
  @Parameter(defaultValue = ModelManager.DEFAULT_MODEL_NAME,
             property = "tentackle.modelName")
  private String modelName;

  /**
   * Directory holding the model files to be processed.<br>
   * Ignored if {@link #filesets} explicitly given.
   */
  @Parameter(defaultValue = "${project.build.directory}/wurbel/model",
             property = "tentackle.modelDir")
  private File modelDir;

  /**
   * Explicit filesets given instead of the model directory.<br>
   * Only each fileset's {@code directory} is used to load the model from - include/exclude
   * patterns are not applied, since this goal always documents the entire loaded model.
   */
  @Parameter
  private List<FileSet> filesets;

  /**
   * The model defaults.
   */
  @Parameter(property = "tentackle.modelDefaults")
  private String modelDefaults;

  /**
   * The entity aliases.
   */
  @Parameter(property = "tentackle.entityAliases")
  private String entityAliases;

  /**
   * Also loads the model provided by the classpath (e.g. dependencies shipping their own model),
   * so entities from dependencies that are referenced or embedded show up as well.
   */
  @Parameter(property = "tentackle.loadModelFromClassPath")
  private boolean loadModelFromClassPath;

  /**
   * Directory to create the HTML pages in.
   */
  @Parameter(defaultValue = "${project.build.directory}/model-doc",
             property = "tentackle.htmlDir")
  private File htmlDir;

  /**
   * The heading shown on the index page.<br>
   * Defaults to the maven project's name.
   */
  @Parameter(property = "tentackle.htmlTitle")
  private String title;

  /**
   * Optional project-specific stylesheet, copied verbatim to {@code style.css}.<br>
   * Overrides the default theme (colors, fonts) generated otherwise. The structural rules (tables,
   * lists, spacing) live in the separate, always-generated {@code base.css} and are not affected -
   * a custom stylesheet only needs to redefine the {@code --tentackle-doc-*} CSS variables it wants
   * to change, or override selectors outright for full control.
   */
  @Parameter(property = "tentackle.cssFile")
  private File cssFile;

  /**
   * Comma-separated list of backend names to render the SQL type of each attribute for.<br>
   * One self-contained HTML page tree is generated per backend, nested under {@link #htmlDir} by
   * backend name - same layout convention as {@code create}'s {@code sqlDir/<backend-name>/} -
   * since the SQL type shown in the attributes table can differ between backends.
   * <p>
   * The following special names are recognized:
   * <ul>
   *   <li>all: all non-deprecated backends</li>
   *   <li>none: does not match any backend</li>
   *   <li>deprecated: all deprecated backends</li>
   * </ul>
   */
  @Parameter(property = "tentackle.backendNames")
  private String backendNames;


  private Model model;
  private Collection<Backend> backends;


  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (modelDir == null) {
        throw new MojoExecutionException("missing tentackle.modelDir");
      }
      if (modelDir.getPath().contains("${")) {
        throw new MojoExecutionException("undefined variable(s) in modelDir: " + modelDir.getPath());
      }
      if (htmlDir == null) {
        throw new MojoExecutionException("missing tentackle.htmlDir");
      }
      if (htmlDir.getPath().contains("${")) {
        throw new MojoExecutionException("undefined variable(s) in htmlDir: " + htmlDir.getPath());
      }
      if (cssFile != null && !cssFile.isFile()) {
        throw new MojoExecutionException("no such cssFile: " + cssFile.getAbsolutePath());
      }
      backends = BackendFactory.getInstance().getBackends(backendNames);
      return true;
    }
    return false;
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {

    Model model = getModel();
    try {
      model.setModelDefaults(getModelDefaults());
      model.setEntityAliases(getEntityAliases());

      if (filesets != null && !filesets.isEmpty()) {
        for (FileSet fileSet : filesets) {
          String dirName = fileSet.getDirectory() != null ? fileSet.getDirectory() : modelDir.getPath();
          loadDirectory(model, dirName);
        }
      }
      else if (modelDir.isDirectory()) {
        String[] files = modelDir.list();
        if (files != null && files.length > 0) {
          loadDirectory(model, modelDir.getPath());
        }
        else {
          getLog().warn("empty modelDir " + modelDir.getAbsolutePath());
        }
      }
      else {
        getLog().warn("no modelDir " + modelDir.getAbsolutePath());
      }

      if (loadModelFromClassPath) {
        model.loadFromResources(true);
      }
    }
    catch (ModelException mex) {
      throw new MojoExecutionException("loading the model failed", mex);
    }

    Collection<Entity> entities;
    try {
      entities = model.getAllEntities();
    }
    catch (ModelException mex) {
      throw new MojoExecutionException("resolving the model failed", mex);
    }

    if (backends.isEmpty()) {
      getLog().warn("no backends configured (see tentackle.backendNames) - nothing to document");
      return;
    }

    String pageTitle = title != null ? title : getMavenProject().getName();

    for (Backend backend : backends) {
      File backendHtmlDir = new File(htmlDir, backend.getName());
      if (!backendHtmlDir.isDirectory() && !backendHtmlDir.mkdirs()) {
        throw new MojoExecutionException("cannot create htmlDir " + backendHtmlDir.getAbsolutePath());
      }

      try {
        new ModelHtmlGenerator(entities, backendHtmlDir, pageTitle, cssFile, backend).generate();
      }
      catch (IOException iox) {
        throw new MojoExecutionException("writing the HTML pages for backend " + backend.getName() + " failed", iox);
      }

      getLog().info(entities.size() + " entity page(s) created in " + getPathRelativeToBasedir(backendHtmlDir.getAbsolutePath()));
    }
  }


  private void loadDirectory(Model model, String dirName) throws MojoExecutionException, ModelException {
    String canonicalDirName = getCanonicalPath(new File(dirName));
    if (verbosityLevel.isDebug()) {
      getLog().info("processing files in " + canonicalDirName);
    }
    model.loadFromDirectory(canonicalDirName, true);
  }

  private Model getModel() {
    if (model == null) {
      model = ModelManager.getInstance().getModel(modelName);
    }
    return model;
  }

  private ModelDefaults getModelDefaults() throws MojoExecutionException {
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

  private EntityAliases getEntityAliases() throws MojoExecutionException {
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

}

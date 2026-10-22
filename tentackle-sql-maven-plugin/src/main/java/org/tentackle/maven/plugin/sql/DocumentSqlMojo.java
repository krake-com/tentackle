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

import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Renders the model as a set of cross-linked HTML or Markdown pages, one per entity, plus an index page.<br>
 * Unlike the {@code ModelComment} wurblet's Javadoc comment block, entity names mentioned on a page
 * are hyperlinks to that entity's own page, so the whole model can be browsed by following
 * references, composite trees, and inheritance in a browser or Markdown viewer.
 *
 * @author harald
 */
@Mojo(name = "document", aggregator = true, threadSafe = true)
public class DocumentSqlMojo extends AbstractSqlMojo {

  private static final String FORMAT_HTML = "html";
  private static final String FORMAT_MARKDOWN = "markdown";


  /**
   * The output format.<br>
   * Either {@code html} (the default) or {@code markdown} (alias {@code md}), case-insensitive.
   * Both formats provide the same content and the same navigation between the pages;
   * Markdown pages are named {@code *.md} and use GitHub-flavored pipe tables and fenced code blocks.
   */
  @Parameter(defaultValue = FORMAT_HTML,
             property = "tentackle.docFormat")
  private String format;

  /**
   * Directory to create the pages in (HTML or Markdown).
   */
  @Parameter(defaultValue = "${project.build.directory}/model-doc",
             property = "tentackle.docDir")
  private File docDir;

  /**
   * The heading shown on the index page.<br>
   * Defaults to the maven project's name.
   */
  @Parameter(property = "tentackle.htmlTitle")
  private String title;

  /**
   * Optional project-specific stylesheet, copied verbatim to {@code style.css}.<br>
   * Applies only to the {@code html} format.<br>
   * Overrides the default theme (colors, fonts) generated otherwise. The structural rules (tables,
   * lists, spacing) live in the separate, always-generated {@code base.css} and are not affected -
   * a custom stylesheet only needs to redefine the {@code --tentackle-doc-*} CSS variables it wants
   * to change or override selectors outright for full control.
   */
  @Parameter(property = "tentackle.cssFile")
  private File cssFile;


  private boolean markdown;


  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (docDir == null) {
        throw new MojoExecutionException("missing tentackle.docDir");
      }
      if (docDir.getPath().contains("${")) {
        throw new MojoExecutionException("undefined variable(s) in docDir: " + docDir.getPath());
      }
      String fmt = format == null ? FORMAT_HTML : format.trim().toLowerCase(Locale.ROOT);
      switch (fmt) {
        case FORMAT_HTML -> markdown = false;
        case FORMAT_MARKDOWN, "md" -> markdown = true;
        default -> throw new MojoExecutionException("unsupported format '" + format + "' (must be " +
                                                    FORMAT_HTML + " or " + FORMAT_MARKDOWN + ")");
      }
      if (cssFile != null) {
        if (markdown) {
          getLogger().warn("cssFile is ignored for format {}", FORMAT_MARKDOWN);
        }
        else if (!cssFile.isFile()) {
          throw new MojoExecutionException("no such cssFile: " + cssFile.getAbsolutePath());
        }
      }
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
      model.setSchemaNameMapped(mapSchemas);

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
          getLogger().warn("empty modelDir {}", modelDir.getAbsolutePath());
        }
      }
      else {
        getLogger().warn("no modelDir {}", modelDir.getAbsolutePath());
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

    if (backendInfos.isEmpty()) {
      getLogger().warn("no backends configured (see tentackle.backendNames) - nothing to document");
      return;
    }

    String pageTitle = title != null ? title : getMavenProject().getName();

    for (BackendInfo backendInfo : backendInfos.values()) {
      Backend backend = backendInfo.getBackend();
      File backendDocDir = new File(docDir, backend.getName());
      if (!backendDocDir.isDirectory() && !backendDocDir.mkdirs()) {
        throw new MojoExecutionException("cannot create docDir " + backendDocDir.getAbsolutePath());
      }

      AbstractModelDocGenerator generator = markdown ?
          new ModelMarkdownGenerator(entities, backendDocDir, pageTitle, backend, mapSchemas) :
          new ModelHtmlGenerator(entities, backendDocDir, pageTitle, cssFile, backend, mapSchemas);
      try {
        generator.generate();
      }
      catch (IOException iox) {
        throw new MojoExecutionException("writing the " + (markdown ? "Markdown" : "HTML") +
                                         " pages for backend " + backend.getName() + " failed", iox);
      }

      getLogger().info("{} entity page(s) created in {}",
                       entities.size(), getPathRelativeToBasedir(backendDocDir.getAbsolutePath()));
    }
  }

  @Override
  protected boolean isSqlDirRequired() {
    return false;
  }

  @Override
  protected String getSqlFileName() {
    return "";
  }

  @Override
  protected void processFileSet(BackendInfo backendInfo, FileSet fileSet) throws MojoExecutionException {

  }

  @Override
  protected Collection<BackendInfo> getBackendInfosToExecute() {
    return List.of();
  }


  private void loadDirectory(Model model, String dirName) throws MojoExecutionException, ModelException {
    String canonicalDirName = getCanonicalPath(new File(dirName));
    if (verbosityLevel.isDebug()) {
      getLogger().info("processing files in {}", canonicalDirName);
    }
    model.loadFromDirectory(canonicalDirName, true);
  }

}

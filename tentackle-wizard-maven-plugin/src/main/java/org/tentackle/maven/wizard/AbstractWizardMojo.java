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


package org.tentackle.maven.wizard;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import org.tentackle.common.FileHelper;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Index;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;

import javax.lang.model.element.NestingKind;
import javax.swing.ListModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Base wizard mojo.
 *
 * @author harald
 */
public abstract class AbstractWizardMojo extends AbstractMojo {

  private static final String CATEGORY_PDO = "pdo";
  private static final String CATEGORY_OPERATION = "operation";

  /**
   * The Maven Project.
   */
  @Parameter(defaultValue = "${project}",
  readonly = true,
  required = true)
  private MavenProject mavenProject;

  /**
   * Directory holding the model files to be processed.<br>
   * Ignored if fileset explicitly given.
   */
  @Parameter(defaultValue = "${project.build.directory}/wurbel/model",
  property = "tentackle.modelDir")
  protected File modelDir;

  /**
   * The model defaults.
   */
  @Parameter(property = "tentackle.modelDefaults")
  protected String modelDefaults;

  /**
   * The entity aliases.
   */
  @Parameter(property = "tentackle.entityAliases")
  protected String entityAliases;

  /**
   * The list of file sets containing the model.<br>
   * If set, the modelDir is ignored.
   */
  @Parameter
  protected List<FileSet> filesets;

  /**
   * The profiles.
   */
  @Parameter(required = true)
  private List<Profile> profiles;


  /**
   * The directory holding the templates.
   */
  @Parameter(defaultValue = "${project.basedir}/templates")
  private File templateDir;


  /**
   * Gets the profiles.
   *
   * @param clazz the concrete profile class
   * @return the list of profiles
   */
  @SuppressWarnings("unchecked")
  public <T extends Profile> List<T> getProfiles(Class<T> clazz) {
    List<T> list = new ArrayList<>();
    for (Profile profile : profiles) {
      if (clazz.isAssignableFrom(profile.getClass())) {
        list.add((T) profile);
      }
    }
    return list;
  }


  /**
   * Gets the template directory.
   *
   * @return the directory holding the wizard templates
   */
  public File getTemplateDir() {
    return templateDir;
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    validate();
    loadModel();
  }


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
   * Loads the model.
   *
   * @throws MojoFailureException if the model is inconsistent
   * @throws MojoExecutionException if loading failed due to some other error
   */
  protected void loadModel() throws MojoFailureException, MojoExecutionException {
    int errors = 0;

    if (filesets != null && !filesets.isEmpty()) {
      // explicit filesets given instead of model dir
      for (FileSet fileSet : filesets) {
        errors += processFileSet(fileSet);
      }
    }
    else {
      // all from model dir
      String[] files = modelDir.isDirectory() ? modelDir.list() : null;
      if (files != null && files.length > 0) {
        final FileSet fs = new FileSet();
        fs.setDirectory(modelDir.getPath());
        errors += processFileSet(fs);
      }
      else {
        getLog().warn((modelDir.exists() ? "empty modelDir " : "no modelDir ") + modelDir.getAbsolutePath());
      }
    }

    if (errors > 0) {
      throw new MojoFailureException(errors + " model errors");
    }

    try {
      getLog().info(Model.getInstance().getAllEntitites().size() + " entities loaded");
    }
    catch (ModelException e) {
      throw new MojoExecutionException("cannot determine entities", e);
    }
  }


  /**
   * Processes a fileset to load the model.
   *
   * @param fileSet the fileset containing the model
   * @return the number of errors
   */
  protected int processFileSet(FileSet fileSet) throws MojoExecutionException {
    int errors = 0;

    if (fileSet.getDirectory() == null) {
      // directory missing: use modelDir as default
      fileSet.setDirectory(modelDir.getAbsolutePath());
    }

    File dir = new File(fileSet.getDirectory());
    String modelDirName = dir.getPath();

    try {
      Model.getInstance().loadModel(modelDirName, getModelDefaults(), getEntityAliases());
    }
    catch (ModelException mex) {
      getLog().error("parsing model failed in directory " + modelDirName + ":\n" + mex.getMessage());
      errors++;
    }

    return errors;
  }


  /**
   * Validates the configuration.
   *
   * @throws MojoExecutionException if project not set or misconfigured
   */
  protected void validate() throws MojoExecutionException {
    if (mavenProject == null) {
      throw new MojoExecutionException("missing project");
    }
    if (mavenProject.getBasedir() == null) {
      throw new MojoExecutionException("missing project.baseDir");
    }
    if (templateDir.exists()) {
      if (!templateDir.isDirectory()) {
        throw new MojoExecutionException(templateDir.getPath() + " is not a directory");
      }
    }
    else {
      templateDir.mkdirs();
      getLog().info("template directory created: " + templateDir.getPath());
      installTemplates();
    }
  }


  /**
   * Copies the templates to the template directory.<br>
   * Overwrites any existing templates.
   *
   * @throws MojoExecutionException if failed
   */
  protected void installTemplates() throws MojoExecutionException {
    installTemplate(CATEGORY_PDO, "DomainImplementation.ftl");
    installTemplate(CATEGORY_PDO, "DomainInterface.ftl");
    installTemplate(CATEGORY_PDO, "PdoInterface.ftl");
    installTemplate(CATEGORY_PDO, "PersistenceImplementation.ftl");
    installTemplate(CATEGORY_PDO, "PersistenceInterface.ftl");
    // TODO: add operation templates
  }

  private void installTemplate(String category, String template) throws MojoExecutionException {
    File dir = new File(templateDir, category);
    dir.mkdirs();
    File file = new File(dir, template);
    String path = "/templates/" + category + "/" + template;
    String text = loadResourceFileIntoString(path);
    try (PrintStream ps = new PrintStream(new FileOutputStream(file))) {
      ps.print(text);
      getLog().info("installed " + category + "-template " + template);
    }
    catch (IOException e) {
      throw new MojoExecutionException("cannot install template " + path, e);
    }
  }

  private String loadResourceFileIntoString(String path) throws MojoExecutionException {
    InputStream inputStream = getClass().getResourceAsStream(path);
    if (inputStream == null) {
      throw new MojoExecutionException("no such resource: " + path);
    }
    BufferedReader buffer = new BufferedReader(new InputStreamReader(inputStream));
    return buffer.lines().collect(Collectors.joining(System.getProperty("line.separator")));
  }

  /**
   * Creates a map of package names to package infos.
   *
   * @return the packages
   * @throws MojoExecutionException if split package detected
   */
  protected Map<String, PackageInfo> createPackageMap() throws MojoExecutionException {
    Map<String, PackageInfo> map = new HashMap<>();
    for (MavenProject project : mavenProject.getCollectedProjects()) {
      for (String path : project.getCompileSourceRoots()) {
        for (PackageInfo info : getPackages(project, path)) {
          PackageInfo splitPkg = map.put(info.getName(), info);
          if (splitPkg != null) {
            throw new MojoExecutionException("split package detected: " + info.getName() + " in " + project.getName() +
                                             " and " + info.getProject().getName());
          }
        }
      }
    }
    return map;
  }

  private List<PackageInfo> getPackages(MavenProject project, String compileSourceRoot) {
    List<PackageInfo> packages = new ArrayList<>();
    checkDir(project, new File(compileSourceRoot), packages, "");
    return packages;
  }

  private void checkDir(MavenProject project, File dir, List<PackageInfo> packages, String packageName) {
    if (dir.isDirectory()) {
      boolean containsJavaFiles = false;
      for (File file : dir.listFiles()) {
        if (file.isDirectory()) {
          checkDir(project, file, packages, packageName.isEmpty() ? file.getName() : packageName + "." + file.getName());
        }
        else if (file.getName().endsWith(".java")) {
          containsJavaFiles = true;
        }
      }
      if (containsJavaFiles && !packageName.isEmpty()) {
        packages.add(new PackageInfo(packageName, project, dir));
      }
    }
  }

}

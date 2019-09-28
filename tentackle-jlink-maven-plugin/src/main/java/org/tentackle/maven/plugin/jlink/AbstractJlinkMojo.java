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


package org.tentackle.maven.plugin.jlink;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.languages.java.jpms.LocationManager;

import org.tentackle.common.FileHelper;
import org.tentackle.common.StringHelper;
import org.tentackle.maven.AbstractTentackleMojo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Base jlink mojo.
 *
 * @author harald
 */
public abstract class AbstractJlinkMojo extends AbstractTentackleMojo {

  /**
   * The name of the module holding the main class.<br>
   * If none, the plugin will build an application running on the classpath.
   */
  @Parameter
  private String mainModule;

  /**
   * The name of the main class.
   */
  @Parameter(required = true)
  private String mainClass;

  /**
   * Dependencies that should go to the classpath instead of modulepath.
   */
  @Parameter
  private List<ClasspathDependency> classpathDependencies;

  /**
   * JPMS support.
   */
  @Component
  private LocationManager locationManager;

  /**
   * To attach artifacts with classifier.
   */
  @Component
  private MavenProjectHelper projectHelper;

  /**
   * The directory holding the script templates.
   */
  @Parameter(defaultValue = "${project.basedir}/templates")
  private File templateDir;

  /**
   * The name of the template to generate the name of the runner script.<br>
   * For *nixes this is usually {@code run.sh}, for windozes {@code run.cmd} or {@code run.bat}.
   */
  @Parameter(defaultValue = "name.ftl")
  private String nameTemplate;

  /**
   * The name of the runner script template.
   */
  @Parameter(defaultValue = "run.ftl")
  private String runTemplate;

  /**
   * The name of the update script template.<br>
   * Empty to disable.
   */
  @Parameter(defaultValue = "update.ftl")
  private String updateTemplate;

  /**
   * File of the jdeps tool.
   */
  @Parameter
  private File jdepsTool;

  /**
   * The directory holding extra resources, such as logger configurations or database
   * credentials, that will be copied to the <code>conf</code> directory.<br>
   * Defaults to the maven-resources-plugin's destination directory, usually <code>target/classes</code>.<br>
   * If no resources must be copied, for example in server-images for production, set this to any
   * non-existent directory, for example <code>"none"</code>. This will also add <code>"."</code> to
   * the classpath, which is not overridden when the image is unzipped.
   */
  @Parameter(defaultValue = "${project.build.directory}/classes")
  private File resourcesDirectory;

  /**
   * Extra jre or jdk modules to add to the image.<br>
   * Useful to add tools in bin. Example:
   *
   * <pre>
   * &lt;addModules&gt;
   *   &lt;addModule&gt;jdk.jcmd&lt;/addModule&gt;
   * &lt;/addModules&gt;
   * </pre>
   */
  @Parameter
  private List<String> addModules;

  /**
   * Modules to exclude.<br>
   * Necessary only if a dependency refers to removed api even if not used at all at runtime.
   *
   * <pre>
   * &lt;excludeModules&gt;
   *   &lt;excludeModule&gt;jdk8internals&lt;/excludeModule&gt;
   * &lt;/excludeModules&gt;
   * </pre>
   */
  @Parameter
  private List<String> excludeModules;

  /**
   * Extra variables for template or artifact generation.
   */
  @Parameter
  private Map<String, Object> variables;

  /**
   * Defines the precedence of properties and variables.<br>
   * By default, system properties are overridden by maven properties which are overridden by the extra template variables.
   * <p>
   * The case insensitive keywords are:
   * <ul>
   *   <li>{@code system, sys}: for system properties</li>
   *   <li>{@code maven, mvn, properties, props, pom}: maven properties</li>
   *   <li>{@code variables, vars, var, extras, extra, template}: extra template variables</li>
   * </ul>
   */
  @Parameter(defaultValue = "system, maven, variables", required = true)
  private String variablesPrecedence;

  /**
   * Gets the template directory.
   *
   * @return the directory holding the wizard templates
   */
  public File getTemplateDir() {
    return templateDir;
  }

  /**
   * Gets the directory created by jlink holding the image.
   *
   * @return the image dir, never null
   */
  abstract public File getImageDirectory();

  /**
   * Gets the name of the module holding the main class.
   *
   * @return the main module, null to build an application running on the classpath
   */
  public String getMainModule() {
    return mainModule;
  }

  /**
   * Gets the name of the main class.
   *
   * @return the main class, never null
   */
  public String getMainClass() {
    return mainClass;
  }

  /**
   * Gets the location manager to extract module information.
   *
   * @return the JPMS manager
   */
  public LocationManager getLocationManager() {
    return locationManager;
  }

  /**
   * Gets the project helper to attach artifacts.
   *
   * @return the project helper
   */
  public MavenProjectHelper getProjectHelper() {
    return projectHelper;
  }

  /**
   * Gets the jdeps tool.
   *
   * @return the file, never null
   */
  public File getJdepsTool() {
    return jdepsTool;
  }


  /**
   * Gets the name of the name template.
   *
   * @return the template to generate the name of the runner script
   */
  public String getNameTemplate() {
    return nameTemplate;
  }

  /**
   * Gets the name of the runner template.
   *
   * @return the template to generate the runner script
   */
  public String getRunTemplate() {
    return runTemplate;
  }

  /**
   * Gets the name of the update template.
   *
   * @return the template to generate the update script, null if none
   */
  public String getUpdateTemplate() {
    return StringHelper.isAllWhitespace(updateTemplate) ? null : updateTemplate;
  }

  /**
   * Gets extra jre or jdk modules to add to the image.
   *
   * @return extra module names, never null
   */
  public List<String> getAddModules() {
    return addModules == null ? Collections.emptyList() : addModules;
  }

  /**
   * Gets modules to exclude.
   *
   * @return excluded module names, never null
   */
  public List<String> getExcludeModules() {
    return excludeModules == null ? Collections.emptyList() : excludeModules;
  }

  /**
   * Gets the extra variables for templates or artifact generation.
   *
   * @return the variables, never null
   */
  public Map<String, Object> getVariables() {
    return variables == null ? Collections.emptyMap() : variables;
  }

  /**
   * Gets the order how properties and variables override each other.
   *
   * @return default is system, maven, variables
   */
  public String getVariablesPrecedence() {
    return variablesPrecedence;
  }


  @Override
  public void prepareExecute() throws MojoExecutionException, MojoFailureException {
    if (jdepsTool == null) {
      jdepsTool = getToolFinder().find("jdeps");
    }
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    // analyze project, determine jlink strategy
    JlinkResolver.Result result = new JlinkResolver(this, getMavenProject().getArtifacts()).resolve();

    // and run jlink
    createImage(result);

    // copy artifacts not covered by jlink to modulepath and classpath subdirectories
    copyArtifacts(result);

    // copy resources such as backend.properties or logging config to the conf subdirectory
    if (resourcesDirectory != null && resourcesDirectory.isDirectory()) {
      int resCount = copyResources(resourcesDirectory, new File(getApplicationDirectory(), "conf"));
      if (resCount > 0) {
        result.addToClasspath("conf");
      }
    }
    else {
      result.addToClasspath(".");
    }

    // generate the script to run the application in the bin subdirectory
    new ScriptGenerator(this, result).generate();
  }

  /**
   * Creates the application image.
   *
   * @param result the resolver info
   * @throws MojoExecutionException if building the JPMS info failed
   * @throws MojoFailureException if jlink returned an error code
   */
  abstract protected void createImage(JlinkResolver.Result result) throws MojoExecutionException, MojoFailureException;

  /**
   * Gets the application directory.
   *
   * @return the destination base directory for the application
   */
  protected File getApplicationDirectory() {
    return getImageDirectory();
  }

  /**
   * Copies artifacts not already in the created jlink image to module- or classpath.
   *
   * @param result the resolver info
   * @throws MojoExecutionException if copy failed
   */
  protected void copyArtifacts(JlinkResolver.Result result) throws MojoExecutionException {
    try {
      // module path, if any
      if (result.isModular() && !result.getModulePath().isEmpty()) {
        File mpDir = new File(getApplicationDirectory(), "mp");
        mpDir.mkdir();
        for (ModularArtifact artifact : result.getModulePath()) {
          File dest = new File(mpDir, artifact.getFileName());
          getLog().debug("copying " + artifact.getPath() + " to " + mpDir);
          FileHelper.copy(artifact.getFile(), dest);
        }
      }
      // classpath, if any
      if (!result.getClassPath().isEmpty()) {
        File cpDir = new File(getApplicationDirectory(), "cp");
        cpDir.mkdir();
        for (Artifact artifact : result.getClassPath()) {
          File dest = new File(cpDir, artifact.getFile().getName());
          getLog().debug("copying " + artifact.getFile() + " to " + cpDir);
          FileHelper.copy(artifact.getFile(), dest);
        }
      }
    }
    catch (IOException ex) {
      throw new MojoExecutionException("copying artifacts failed", ex);
    }
  }

  /**
   * Copies the resources.
   *
   * @return number of resource files copied
   * @throws MojoExecutionException if copy failed
   */
  private int copyResources(File srcDir, File dstDir) throws MojoExecutionException {
    try {
      int count = 0;
      File[] files = srcDir.listFiles();
      if (files != null) {
        for (File file: files) {
          if (file.isFile()) {
            getLog().debug("copying " + file + " to " + dstDir);
            File dest = new File(dstDir, file.getName());
            FileHelper.copy(file, dest);
            ++count;
          }
          else if (file.isDirectory()) {
            File subDir = new File(dstDir, file.getName());
            subDir.mkdir();
            count += copyResources(file, subDir);
          }
          else {
            getLog().debug("skipping " + file);
          }
        }
      }
      return count;
    }
    catch (IOException ex) {
      throw new MojoExecutionException("copying resources failed", ex);
    }
  }


  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (jdepsTool == null) {
        throw new MojoExecutionException("jdeps tool not found");
      }
      getLog().debug("using " + jdepsTool);

      if (templateDir.exists()) {
        if (!templateDir.isDirectory()) {
          throw new MojoExecutionException(templateDir.getPath() + " is not a directory");
        }
      }
      else {
        getLog().info("template directory created: " + templateDir.getPath());
        installTemplates();
      }

      if (mainClass == null) {
        throw new MojoExecutionException("mainClass missing");
      }

      if (nameTemplate == null) {
        throw new MojoExecutionException("nameTemplate missing");
      }

      if (runTemplate == null) {
        throw new MojoExecutionException("runTemplate missing");
      }

      return true;
    }
    return false;
  }



  /**
   * Returns whether an artifact should be moved to the classpath explicitly.
   *
   * @param artifact the artifact
   * @return true if move to classpath, else modulepath
   */
  public boolean isClasspathDependency(Artifact artifact) {
    if (classpathDependencies != null) {
      for (ClasspathDependency classpathDependency: classpathDependencies) {
        if (Objects.equals(classpathDependency.getGroupId(), artifact.getGroupId()) &&
            Objects.equals(classpathDependency.getArtifactId(), artifact.getArtifactId()) &&
            Objects.equals(classpathDependency.getClassifier(), artifact.getClassifier())) {
          return true;
        }
      }
    }
    return false;
  }


  /**
   * Copies the templates to the template directory.<br>
   * Overwrites any existing templates.
   *
   * @throws MojoExecutionException if failed
   */
  protected void installTemplates() throws MojoExecutionException {
    installTemplate(nameTemplate);
    installTemplate(runTemplate);
    if (getUpdateTemplate() != null) {
      installTemplate(updateTemplate);
    }
  }

  private void installTemplate(String template) throws MojoExecutionException {
    templateDir.mkdirs();
    File file = new File(templateDir, template);
    String path = "/templates/" + template;
    String text = loadResourceFileIntoString(path);
    try (PrintStream ps = new PrintStream(new FileOutputStream(file))) {
      ps.print(text);
      getLog().info("installed template " + template);
    }
    catch (IOException e) {
      throw new MojoExecutionException("cannot install template " + path, e);
    }
  }

}

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
import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.maven.ToolRunner;

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
   * If missing, the plugin will build an application running on the classpath.
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
   * Extra directories to copy to the image.<br>
   * Relative to the <code>src</code> directory.
   */
  @Parameter
  private List<String> extraDirectories;

  /**
   * Extra classpath elements.<br>
   * Pathnames are relative to the image root.
   */
  @Parameter
  private List<String> extraClasspathElements;

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
   * Explicit path to the <code>jdeps</code> tool.
   */
  @Parameter(property = "jdepsTool")
  private File jdepsTool;

  /**
   * Explicit path to the <code>jlink</code> tool.
   */
  @Parameter(property = "jlinkTool")
  private File jlinkTool;

  /**
   * The optional file holding the jlink options.<br>
   * Corresponds to the <code>jlink</code> option <code>--save-opts</code>.
   */
  @Parameter
  private File saveOpts;

  /**
   * Strip debug information.<br>
   * Corresponds to the <code>jlink</code> option <code>--strip-debug</code>.
   */
  @Parameter(defaultValue = "false")
  private boolean stripDebug;

  /**
   * Compression of resources.<br>
   * Corresponds to the <code>jlink</code> option <code>--compress</code>.
   */
  @Parameter
  private Integer compress;

  /**
   * Suppresses a fatal error when signed modular JARs are linked in the runtime image.<br>
   * Corresponds to the <code>jlink</code> option <code>--ignore-signing-information</code>
   */
  @Parameter(defaultValue = "false")
  private boolean ignoreSigningInformation;

  /**
   * Suppress the <code>includes</code> directory.<br>
   * Corresponds to the <code>jlink</code> option <code>--no-header-files</code>.
   */
  @Parameter(defaultValue = "false")
  private boolean noHeaderFiles;

  /**
   * Suppress the <code>man</code> directory.<br>
   * Corresponds to the <code>jlink</code> option <code>--no-man-pages</code>
   */
  @Parameter(defaultValue = "false")
  private boolean noManPages;

  /**
   * The directory holding extra resources, such as logger configurations or database
   * credentials, that will be copied to the <code>conf</code> directory.<br>
   * Defaults to the maven-resources-plugin's destination directory, usually <code>target/classes</code>.<br>
   * If no resources must be copied, for example in server-images for production, set this to any
   * non-existent directory, for example <code>"none"</code>.
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
   * The directory created by jlink holding the image.
   */
  @Parameter(defaultValue = "${project.build.directory}/jlink", required = true)
  private File imageDirectory;

  /**
   * Additional classifier to add to deployed artifacts.
   */
  @Parameter
  private String extraClassifier;


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
  public File getImageDirectory() {
    return imageDirectory;
  }

  /**
   * Gets the name of the module holding the main class.
   *
   * @return the main module, null or empty string to build an application running on the classpath
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

  /**
   * Gets the optional extra deployment classifier.
   *
   * @return the extra classifier
   */
  public String getExtraClassifier() {
    return extraClassifier;
  }

  /**
   * Gets optional extra classpath elements.<br>
   *
   * @return pathnames relative to the image root, null if none
   */
  public List<String> getExtraClasspathElements() {
    return extraClasspathElements;
  }

  /**
   * Extra directories to copy to the image.
   *
   * @return pathnames relative to the <code>src</code> directory, null if none
   */
  public List<String> getExtraDirectories() {
    return extraDirectories;
  }

  @Override
  public void prepareExecute() throws MojoExecutionException, MojoFailureException {
    if (jdepsTool == null) {
      jdepsTool = getToolFinder().find("jdeps");
    }
    if (jlinkTool == null) {
      jlinkTool = getToolFinder().find("jlink");
    }
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    // analyze project, determine jlink strategy
    JlinkResolver.Result result = new JlinkResolver(this, getMavenProject().getArtifacts()).resolve();
    // generate image
    createImage(result);
    // and the shell scripts
    generateFiles(result);
  }


  /**
   * Gets the prefix to the jars and dirs in the runtime image.
   *
   * @return the prefix, empty string if none (never null)
   */
  public String getImagePathPrefix() {
    return "";
  }


  /**
   * Invokes the jlink tool and copies other artifacts and resources.
   *
   * @param result the resolver result
   */
  protected void createImage(JlinkResolver.Result result) throws MojoFailureException, MojoExecutionException {
    // run jlink
    createJLinkImage(result);

    // copy artifacts not covered by jlink to modulepath and classpath subdirectories
    copyArtifacts(result);

    // copy resources such as backend.properties or logging config to the conf subdirectory
    copyResources(result);

    // extra directories, if any
    copyExtraDirectories();
  }


  /**
   * Generates additional files such as shell scripts or command files.
   *
   * @param result the resolver result
   * @throws MojoExecutionException if generation failed
   */
  abstract protected void generateFiles(JlinkResolver.Result result) throws MojoExecutionException;


  /**
   * Creates the jlink image.
   *
   * @param result the resolver info
   * @throws MojoExecutionException if building the JPMS info failed
   * @throws MojoFailureException if jlink returned an error code
   */
  protected void createJLinkImage(JlinkResolver.Result result) throws MojoExecutionException, MojoFailureException {
    getLog().info("creating jlink image for a " +
                  (result.isPlainModular() ? "plain " : "") +
                  (result.isModular() ? "modular" : "classpath") + " application");
    getLog().debug(result.toString());

    ToolRunner jlinkRunner = new ToolRunner(jlinkTool);
    if (saveOpts != null) {
      jlinkRunner.arg("--save-opts").arg(saveOpts);
    }
    jlinkRunner.arg("--output").arg(getImageDirectory());
    if (stripDebug) {
      jlinkRunner.arg("--strip-debug");
    }
    if (compress != null) {
      jlinkRunner.arg("--compress=" + compress);
    }
    if (ignoreSigningInformation) {
      jlinkRunner.arg("--ignore-signing-information");
    }
    if (noHeaderFiles) {
      jlinkRunner.arg("--no-header-files");
    }
    if (noManPages) {
      jlinkRunner.arg("--no-man-pages");    // is this implemented in jlink at all???
    }
    result.generateJlinkModulePath(jlinkRunner);
    result.generateJlinkModules(jlinkRunner);

    getLog().debug(jlinkRunner.toString());

    int errCode = jlinkRunner.run().getErrCode();
    if (errCode != 0) {
      throw new MojoFailureException("jlink failed: " + errCode + "\n" + jlinkRunner.getErrorsAsString() +
                                     "\n" + jlinkRunner.getOutputAsString());
    }
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
      if (result.isModular()) {
        List<ModularArtifact> modulePath = result.getModulePath();
        if (!modulePath.isEmpty()) {
          File mpDir = new File(getImageDirectory(), "mp");
          mpDir.mkdir();
          for (ModularArtifact artifact : modulePath) {
            File dest = new File(mpDir, artifact.getFileName());
            getLog().debug("copying " + artifact.getPath() + " to " + mpDir);
            FileHelper.copy(artifact.getFile(), dest);
          }
        }
      }
      // classpath, if any
      List<Artifact> classPath = result.getClassPath();
      if (!classPath.isEmpty()) {
        File cpDir = new File(getImageDirectory(), "cp");
        cpDir.mkdir();
        for (Artifact artifact : classPath) {
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
   * Copies the resources to the <code>conf</code> directory.
   *
   * @param result the resolver info
   * @throws MojoExecutionException if copy failed
   */
  protected void copyResources(JlinkResolver.Result result) throws MojoExecutionException {
    // copy resources such as backend.properties or logging config to the conf subdirectory
    if (resourcesDirectory != null && resourcesDirectory.isDirectory()) {
      int resCount = copyResources(resourcesDirectory, new File(getImageDirectory(), "conf"));
      if (resCount > 0) {
        result.addToClasspath("conf");
      }
    }
  }

  /**
   * Copies optional extra directories to the image directory.
   *
   * @throws MojoExecutionException if failed
   */
  protected void copyExtraDirectories() throws MojoExecutionException {
    // extra directories, if any
    try {
      if (getExtraDirectories() != null) {
        String src = getMavenProject().getBuild().getSourceDirectory();
        if (src != null) {
          File srcDir = new File(src);
          while (srcDir != null && !"src".equals(srcDir.getName())) {   // step up to "src"
            srcDir = srcDir.getParentFile();
          }
          if (srcDir != null) {
            for (String name : getExtraDirectories()) {
              File fromDir = new File(srcDir, name);
              File toDir = new File(getImageDirectory(), fromDir.getName());
              FileHelper.copyDirectory(fromDir, toDir, true, null);
            }
          }
        }
      }
    }
    catch (IOException ex) {
      throw new MojoExecutionException("copying extra directories failed", ex);
    }
  }
  

  /**
   * Copies the resources.
   *
   * @param srcDir the resource directory
   * @param dstDir the destination directory
   * @return number of resource files copied
   * @throws MojoExecutionException if copy failed
   */
  private int copyResources(File srcDir, File dstDir) throws MojoExecutionException {
    try {
      return FileHelper.copyDirectory(srcDir, dstDir,true, null);
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

      if (jlinkTool == null) {
        throw new MojoExecutionException("jlink tool not found");
      }
      getLog().debug("using " + jlinkTool);

      if (templateDir.exists()) {
        if (!templateDir.isDirectory()) {
          throw new MojoExecutionException(templateDir.getPath() + " is not a directory");
        }
      }
      else {
        getLog().info("template directory created: " + templateDir.getPath());
      }
      installTemplates(false);    // install any missing template

      if (mainClass == null) {
        throw new MojoExecutionException("mainClass missing");
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
   * @param overwrite true if overwrite existing templates, false if install only missing
   * @throws MojoExecutionException if installation failed
   */
  abstract protected void installTemplates(boolean overwrite) throws MojoExecutionException;

  /**
   * Installs the template with the given name.
   *
   * @param template the name
   * @param overwrite true if overwrite existing template, false if leave unchanged
   * @throws MojoExecutionException if installation failed
   */
  protected void installTemplate(String template, boolean overwrite) throws MojoExecutionException {
    templateDir.mkdirs();
    File file = new File(templateDir, template);
    if (overwrite || !file.exists()) {
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

  /**
   * Logs the toolrunner's output at info level.
   *
   * @param runner the toolrunner
   * @throws MojoExecutionException if runner not invoked so far
   */
  protected void logToolOutput(ToolRunner runner) throws MojoExecutionException {
    String out = runner.getOutputAsString();
    if (!out.isEmpty()) {
      getLog().info(out);
    }
  }

}

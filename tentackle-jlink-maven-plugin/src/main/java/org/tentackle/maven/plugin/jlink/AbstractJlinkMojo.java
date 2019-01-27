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
import org.codehaus.plexus.languages.java.jpms.LocationManager;

import org.tentackle.common.FileHelper;
import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.maven.ToolRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
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
   * Dependencies that should go to the classpath.
   */
  @Parameter
  private List<ClasspathDependency> classpathDependencies;

  /**
   * JPMS support.
   */
  @Component
  private LocationManager locationManager;

  /**
   * The directory holding the script templates.
   */
  @Parameter(defaultValue = "${project.basedir}/templates")
  private File templateDir;

  /**
   * File of the jdeps tool.
   */
  @Parameter
  private File jdepsTool;

  /**
   * File of the jlink tool.
   */
  @Parameter
  private File jlinkTool;

  /**
   * The directory holding extra resources that go to conf.
   */
  @Parameter(defaultValue = "${project.build.directory}/classes", required = true)
  private File resourcesDirectory;

  /**
   * The directory created by jlink holding the image.
   */
  @Parameter(defaultValue = "${project.build.directory}/jlink", required = true)
  private File imageDirectory;

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
  @Parameter( defaultValue = "false" )
  private boolean stripDebug;

  /**
   * Compression of resources.<br>
   * Corresponds to the <code>jlink</code> option <code>--compress</code>.
   */
  @Parameter
  private Integer compress;

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
   * Suppresses a fatal error when signed modular JARs are linked in the runtime image.<br>
   * Corresponds to the <code>jlink</code> option <code>--ignore-signing-information</code>
   */
  @Parameter( defaultValue = "false" )
  private boolean ignoreSigningInformation;

  /**
   * Suppress the <code>includes</code> directory.<br>
   * Corresponds to the <code>jlink</code> option <code>--no-header-files</code>.
   */
  @Parameter( defaultValue = "false" )
  private boolean noHeaderFiles;

  /**
   * Suppress the <code>man</code> directory.<br>
   * Corresponds to the <code>jlink</code> option <code>--no-man-pages</code>
   */
  @Parameter( defaultValue = "false" )
  private boolean noManPages;


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
   * Gets the jdeps tool.
   *
   * @return the file, never null
   */
  public File getJdepsTool() {
    return jdepsTool;
  }

  /**
   * Gets the jlink tool.
   *
   * @return the file, never null
   */
  public File getJlinkTool() {
    return jlinkTool;
  }

  /**
   * Optionally strip debug information.<br>
   * Corresponds to the <code>jlink</code> option <code>--strip-debug</code>.
   *
   * @return true to strip debug information, default is false
   */
  public boolean isStripDebug() {
    return stripDebug;
  }

  /**
   * Gets the compression of resources.<br>
   * Corresponds to the <code>jlink</code> option <code>--compress</code>.
   *
   * @return the compression level, null if none (default)
   */
  public Integer getCompress() {
    return compress;
  }

  /**
   * Gets extra jre or jdk modules to add to the image.
   *
   * @return extra module names, never null
   */
  public List<String> getAddModules() {
    return addModules == null ? new ArrayList<>() : addModules;
  }

  /**
   * Gets whether to suppress fatal errors when signed modular JARs are linked in the runtime image.
   *
   * @return true to ignore this error, false is default
   */
  public boolean isIgnoreSigningInformation() {
    return ignoreSigningInformation;
  }

  /**
   * Returns whether to uppress the <code>includes</code> directory.
   *
   * @return true to suppress, false is default
   */
  public boolean isNoHeaderFiles() {
    return noHeaderFiles;
  }

  /**
   * Suppress the <code>man</code> directory.
   *
   * @return true to suppress, false is default
   */
  public boolean isNoManPages() {
    return noManPages;
  }



  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (jdepsTool == null) {
      jdepsTool = getToolFinder().find("jdeps");
    }
    if (jlinkTool == null) {
      jlinkTool = getToolFinder().find("jlink");
    }
    if (validate()) {
      // analyze project, determine jlink strategy and run jlink
      JlinkResolver.Result result = runJlink();

      // copy artifacts not covered by jlink to modulepath and classpath subdirectories
      copyArtifacts(result);

      // copy resources such as backend.properties or logging config to the conf subdirectory
      int resCount = copyResources(resourcesDirectory, new File(imageDirectory, "conf"));
      if (resCount > 0) {
        result.setConfAddedToClasspath(true);
      }

      // generate the script to run the application in the bin subdirectory
      new RunGenerator(this, result).generate();
    }
  }

  /**
   * Runs the jlink tool.
   *
   * @return the resolver info
   * @throws MojoExecutionException if building the JPMS info failed
   * @throws MojoFailureException if jlink returned an error code
   */
  private JlinkResolver.Result runJlink() throws MojoExecutionException, MojoFailureException {
    JlinkResolver.Result result = new JlinkResolver(this, getMavenProject().getArtifacts()).resolve();
    getLog().info("\n" + result.toString());

    ToolRunner jlinkRunner = new ToolRunner(jlinkTool);
    if (saveOpts != null) {
      jlinkRunner.arg("--save-opts").arg(saveOpts);
    }
    jlinkRunner.arg("--output").arg(imageDirectory);
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

    int errCode = jlinkRunner.run().getErrCode();
    if (errCode != 0) {
      throw new MojoFailureException("jlink failed: " + errCode + "\n" + jlinkRunner.getOutputAsString());
    }
    return result;
  }

  /**
   * Copies artifacts not already in the created jlink image to module- or classpath.
   *
   * @param result the resolver info
   * @throws MojoExecutionException if copy failed
   */
  private void copyArtifacts(JlinkResolver.Result result) throws MojoExecutionException {
    try {
      // module path, if any
      if (!result.getModulePath().isEmpty()) {
        File mpDir = new File(imageDirectory, "mp");
        mpDir.mkdir();
        for (ModularArtifact artifact : result.getModulePath()) {
          File dest = new File(mpDir, artifact.getFileName());
          getLog().debug("copying " + artifact.getPath() + " to " + mpDir);
          FileHelper.copy(artifact.getFile(), dest);
        }
      }
      // classpath, if any
      if (!result.getClassPath().isEmpty()) {
        File cpDir = new File(imageDirectory, "cp");
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
        installTemplates();
      }

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
   * @throws MojoExecutionException if failed
   */
  protected void installTemplates() throws MojoExecutionException {
    installTemplate(Constants.PLATFORM_LINUX + Constants.TEMPLATE_EXTENSION);
    installTemplate(Constants.PLATFORM_WINDOWS + Constants.TEMPLATE_EXTENSION);
    installTemplate(Constants.PLATFORM_MACOS + Constants.TEMPLATE_EXTENSION);
    installTemplate(Constants.PLATFORM_SOLARIS + Constants.TEMPLATE_EXTENSION);
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

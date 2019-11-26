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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

import org.tentackle.common.StringHelper;
import org.tentackle.maven.ToolRunner;

import java.io.File;
import java.io.IOException;

/**
 * Creates a self-contained java application zip-file with <code>jlink</code>.<br>
 * This mojo works for modular, non-modular and even mixed applications.
 * It does so by analyzing the dependencies and finding the best strategy to invoke <code>jlink</code>, which
 * creates a directory containing an application-specific <code>jimage</code> module.<br>
 * Artifacts not processed by <code>jlink</code> are copied to separate folders. Next, a platform-specific run-script will be generated according to
 * a template provided by the project. Finally, the created directory is packed into a zip file.
 * <p>
 * Basically, applications fall into one of 3 categories:
 * <ol>
 *   <li>full-blown modular applications: all module-infos must only require real modules. Jlink is used to create
 *   an image from the application modules. Optional (but not required!) real and automatic modules are allowed and will be added
 *   to the module path via the generated run-script.</li>
 *   <li>modular applications with non-modular dependencies: jlink is used to create an image from java's runtime modules only,
 *   which are determined by the plugin either from the module-infos or via <code>jdeps</code>.
 *   The application's dependencies are placed on the module-path via the generated run-script.</li>
 *   <li>non-modular applications: same as 2, but all dependencies are placed on the classpath.</li>
 * </ol>
 *
 * The minimum plugin configuration is very simple:
 *
 * <pre>
 *       &lt;plugin&gt;
 *         &lt;groupId&gt;org.tentackle&lt;/groupId&gt;
 *         &lt;artifactId&gt;tentackle-jlink-maven-plugin&lt;/artifactId&gt;
 *         &lt;version&gt;${tentackle.version}&lt;/version&gt;
 *         &lt;extensions&gt;true&lt;/extensions&gt;
 *         &lt;configuration&gt;
 *           &lt;mainModule&gt;com.example&lt;/mainModule&gt;
 *           &lt;mainClass&gt;com.example.MyApp&lt;/mainClass&gt;
 *         &lt;/configuration&gt;
 *       &lt;/plugin&gt;
 * </pre>
 *
 * The freemarker templates are copied to the project's template folder, if it does not exist, i.e.
 * when the goal is invoked the first time.
 * They become part of the project and can be changed easily according to project specific needs (for example by adding runtime arguments).
 * <p>
 * The template model provides the following variables:
 *
 * <ul>
 *   <li><code>mainModule</code>: the name of the main module. Empty if classpath application.</li>
 *   <li><code>mainClass</code>: the name of the main class.</li>
 *   <li><code>modulePath</code>: the module path.</li>
 *   <li><code>classPath</code>: the class path</li>
 *   <li><code>phase</code>: the mojo lifecycle phase</li>
 *   <li><code>goal</code>: the plugin goal (jlink or jpackage)</li>
 *   <li><code>id</code>: the execution id</li>
 *   <li>all system properties (dots in property names translated to camelCase, e.g. "os.name" becomes "osName"</li>
 *   <li>all maven properties (translated to camelCase as well)</li>
 *   <li>the optional variables from the plugin configuration</li>
 * </ul>
 *
 * Modules not passed to <code>jlink</code> and automatic modules are copied to the <code>mp</code> folder
 * and added to the <code>modulePath</code> template variable. If no such modules are detected, no folder is created.<br>
 * Non-modular classpath artifacts are copied to the <code>cp</code> folder and added to the <code>classPath</code> template variable.
 * Again, the folder is only created if necessary.<br>
 * Additional resources, such as property files or logger configurations, are copied to the <code>conf</code> directory and
 * this directory is added to the classpath.
 * <p>
 * The generation of the ZIP-file and attachment of the artifact for installation and deployment can be replaced by an
 * application-specific implementation. This allows further modification of the generated image or files in the jlink target
 * directory. It is also possible to add a more than one artifact, for example, each with a different configuration.
 * To do so, you simply provide a plugin dependency that contains a class annotated with {@code @Service(ArtifactCreator.class)}.
 */
@Mojo(name = "jlink",
      requiresDependencyResolution = ResolutionScope.RUNTIME,
      defaultPhase = LifecyclePhase.PACKAGE,
      requiresProject = true)

public class JlinkMojo extends AbstractJlinkMojo {

  /** file extension for ZIP files. */
  public static final String ZIP_EXTENSION = "zip";

  /**
   * File of the jlink tool.
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
   * The archiver to zip the image.
   */
  @Component(role = Archiver.class, hint = ZIP_EXTENSION)
  private ZipArchiver zipArchiver;

  /**
   * The directory where to store the ZIP file.
   */
  @Parameter(defaultValue = "${project.build.directory}")
  private File zipDirectory;

  /**
   * Disables the generation of a ZIP file and does not add an artifact for installation and deployment.<br>
   * If {@code true}, no {@link ArtifactCreator} will be invoked.
   */
  @Parameter(defaultValue = "false")
  private boolean noArtifact;

  /**
   * The generated ZIP file name.
   */
  @Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
  private String finalName;

  /**
   * The directory created by jlink holding the image.
   */
  @Parameter(defaultValue = "${project.build.directory}/jlink", required = true)
  private File imageDirectory;


  /**
   * Gets the directory where to store the ZIP-file.
   *
   * @return the ZIP target folder
   */
  public File getZipDirectory() {
    return zipDirectory;
  }

  /**
   * Gets the ZIP file's name without extension.
   *
   * @return the final name
   */
  public String getFinalName() {
    return finalName;
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
   * Creates the zip file.
   *
   * @param name the filename without extension
   * @return the created file
   * @throws MojoExecutionException if some archiver or IO errors occurred
   */
  public File createZipFile(String name) throws MojoExecutionException {
    File zipFile = new File(zipDirectory, name + "." + ZIP_EXTENSION);
    zipArchiver.addDirectory(getImageDirectory());
    zipArchiver.setDestFile(zipFile);
    try {
      zipArchiver.createArchive();
    }
    catch (ArchiverException | IOException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    return zipFile;
  }

  /**
   * The zip file will be installed and deployed with an additional classifier
   * showing the operating system and the platform.
   *
   * @return the classifier
   * @throws MojoExecutionException if no os.name
   */
  public String getClassifier() throws MojoExecutionException {
    try {
      return StringHelper.getPlatform() + "-" + StringHelper.getArchitecture();
    }
    catch (RuntimeException ex) {
      throw new MojoExecutionException(ex.getMessage());
    }
  }

  @Override
  public void prepareExecute() throws MojoExecutionException, MojoFailureException {
    super.prepareExecute();
    if (jlinkTool == null) {
      jlinkTool = getToolFinder().find("jlink");
    }
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    super.executeImpl();

    if (!noArtifact) {
      ArtifactCreator.getInstance().createAndAttachArtifact(this);
    }
  }

  /**
   * Creates the jlink image.
   *
   * @param result the resolver info
   * @throws MojoExecutionException if building the JPMS info failed
   * @throws MojoFailureException if jlink returned an error code
   */
  protected void createImage(JlinkResolver.Result result) throws MojoExecutionException, MojoFailureException {
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

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (jlinkTool == null) {
        throw new MojoExecutionException("jlink tool not found");
      }
      getLog().debug("using " + jlinkTool);
      return true;
    }
    return false;
  }

}

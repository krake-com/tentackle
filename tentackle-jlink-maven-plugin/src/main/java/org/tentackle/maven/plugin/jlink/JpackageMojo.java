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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.tentackle.common.FileHelper;
import org.tentackle.maven.ToolRunner;

import java.io.File;
import java.io.IOException;

/**
 * Mojo to create an application installer package.<br>
 * Still WIP. Will be cont'd when Java 14 RC is out.
 * <p>
 * Notice that with java 14 the package tool still does not support invoking a script,
 * but we can leave off the java-module/class without getting an error.
 * As a result, the following command will now work (in target):
 * <pre>
 * jpackage -d installer --runtime-image jlink
 * </pre>
 * This will simply read all files created by the jlink-mojo in target/jlink and create an
 * installer in target/installer. On linux, the files will be installed in a subfolder in /opt.
 * The subfolder contains exactly the same files as in target/jlink. Not much gained, at least on linux.
 * <p>
 * The following problems/questions still exist:
 * <ul>
 *   <li>linux: the log files cannot remain in the installer dir, since it is owned by root and not writable for normal users
 *   (another config for logback.xml, as a filtered resource pointing to user's home?)</li>
 *   <li>what is executed when clicking on the icon, if no java-module/class is given?
 *   Maybe, we should provide a wrapper java class that simply invokes the script?</li>
 *   <li>auto update wouldn't work as non-root user as well. However, this is already covered by
 *   apt update/uprade / yum update (assumed, we provide a repo)</li>
 *   <li>same applies to macs</li>
 *   <li>on the other hand: windows is the main target for installers and that should work, hopefully</li>
 * </ul>
 */
@Mojo(name = "jpackage",
      requiresDependencyResolution = ResolutionScope.RUNTIME,
      defaultPhase = LifecyclePhase.PACKAGE,
      requiresProject = true)

public class JpackageMojo extends AbstractJlinkMojo {

  /**
   * File of the jpackage tool.
   */
  @Parameter
  private File jpackageTool;

  /**
   * The optional package type.<br>
   * One of "msi", "exe", "rpm", "deb", "dmg", "pkg" or "pkg-app-store", depending on the platform.
   * If missing, all supported types for the platform are generated.
   */
  @Parameter
  private String packageType;

  /**
   * The application name.
   */
  @Parameter(defaultValue = "${project.artifactId}", required = true)
  private String applicationName;

  /**
   * The name of the main jar holding the main class.<br>
   * Only necessary for classpath applications and only if not the first dependency is the main jar.<br>
   * A unique substring is sufficient, e.g. "myapp-server".
   */
  @Parameter
  private String mainJar;

  /**
   * The directory created by jpackage holding the image.
   */
  @Parameter(defaultValue = "${project.build.directory}/jpackage", required = true)
  private File imageDirectory;

  /**
   * The directory holding the classpath artifacts as input dir for jpackage.
   */
  @Parameter(defaultValue = "${project.build.directory}/artifacts", required = true)
  private File artifactDirectory;

  private File applicationDirectory;    // lazy jpackage/<application-name>/app

  /**
   * Gets the directory created by jpackage holding the image.
   *
   * @return the image dir, never null
   */
  public File getImageDirectory() {
    return imageDirectory;
  }

  @Override
  public File getApplicationDirectory() {
    if (applicationDirectory == null) {
      applicationDirectory = new File(new File(imageDirectory, applicationName), "runtime");
    }
    return applicationDirectory;
  }

  @Override
  public void prepareExecute() throws MojoExecutionException, MojoFailureException {
    super.prepareExecute();
    if (jpackageTool == null) {
      jpackageTool = getToolFinder().find("jpackage");
    }
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {

    // this creates only the image and copies artifacts, resources and the run script
    super.executeImpl();

    // create the installer from the image
    ToolRunner jpackageRunner = new ToolRunner(jpackageTool);
    jpackageRunner.arg("create-installer");
    if (packageType != null) {
      jpackageRunner.arg(packageType);
    }
    jpackageRunner.arg("-o").arg(getImageDirectory())
                  .arg("-n").arg(applicationName)
                  .arg("--app-image").arg(new File(getImageDirectory(), applicationName));
    getLog().debug(jpackageRunner.toString());
    getLog().info("creating installation package");
    int errCode = jpackageRunner.run().getErrCode();
    if (errCode != 0) {
      throw new MojoFailureException("jpackage failed: " + errCode + "\n" + jpackageRunner.getErrorsAsString() +
                                         "\n" + jpackageRunner.getOutputAsString());
    }
  }

  /**
   * Creates the application image.
   *
   * @param result the resolver info
   * @throws MojoExecutionException if building the JPMS info failed
   * @throws MojoFailureException if jlink returned an error code
   */
  protected void createImage(JlinkResolver.Result result) throws MojoExecutionException, MojoFailureException {
    getLog().info("creating jpackage image for a " +
                      (result.isPlainModular() ? "plain " : "") +
                      (result.isModular() ? "modular" : "classpath") + " application");
    getLog().debug(result.toString());

    // create the image first
    ToolRunner jpackageRunner = new ToolRunner(jpackageTool);
    jpackageRunner.arg("create-image")
        .arg("-o").arg(imageDirectory)
        .arg("-n").arg(applicationName)
        .arg("-c").arg(getMainClass());

    if (result.isModular()) {
      if (result.isPlainModular()) {
        jpackageRunner.arg("-m").arg(getMainModule());
        result.generateJlinkModulePath(jpackageRunner);
        result.generateJlinkModules(jpackageRunner);
      }
      else {
        throw new MojoFailureException("jpackage does not support modular applications with non-modular dependencies");
      }
    }
    else {
      // copy all artifacts to a source directory (jpackage cannot handle the classpath directly)
      copyClasspathArtifacts(result);
      jpackageRunner.arg("-i").arg(artifactDirectory);
      jpackageRunner.arg("-j").arg(determineMainJar(result));
    }

    getLog().debug(jpackageRunner.toString());

    int errCode = jpackageRunner.run().getErrCode();
    if (errCode != 0) {
      throw new MojoFailureException("jpackage failed: " + errCode + "\n" + jpackageRunner.getErrorsAsString() +
                                         "\n" + jpackageRunner.getOutputAsString());
    }
  }

  @Override
  protected void copyArtifacts(JlinkResolver.Result result) throws MojoExecutionException {
    // all artifacts are in the app-folder
    super.copyArtifacts(result);
    // TODO
    //   jpackage stores artifacts in the app folder. We copy non-modules to runtime/cp and runtime/mp,
    //   i.e. some are stored twice in the package.
    //   Create symbolic links in the generated run-script?
  }

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (jpackageTool == null) {
        throw new MojoExecutionException("jpackageTool tool not found");
      }
      getLog().debug("using " + jpackageTool);
      if (applicationName.contains(" ") || applicationName.contains(File.separator)) {
        throw new MojoExecutionException("applicationName must not contain blanks or file separators");
      }
      return true;
    }
    return false;
  }

  private void copyClasspathArtifacts(JlinkResolver.Result result) throws MojoExecutionException {
    try {
      artifactDirectory.mkdirs();
      for (Artifact artifact : result.getClassPath()) {
        FileHelper.copy(artifact.getFile(), new File(artifactDirectory, artifact.getFile().getName()));
      }
    }
    catch (IOException io) {
      throw new MojoExecutionException("copying classpath artifacts failed", io);
    }
  }

  private String determineMainJar(JlinkResolver.Result result) throws MojoFailureException {
    String jarName = null;
    if (mainJar == null) {
      jarName = result.getClassPath().iterator().next().getFile().getName();
    }
    else {
      for (Artifact artifact : result.getClassPath()) {
        if (artifact.getFile().getName().contains(mainJar)) {
          jarName = artifact.getFile().getName();
          break;
        }
      }
    }
    if (jarName == null) {
      throw new MojoFailureException("cannot determine main jar");
    }
    return jarName;
  }
}

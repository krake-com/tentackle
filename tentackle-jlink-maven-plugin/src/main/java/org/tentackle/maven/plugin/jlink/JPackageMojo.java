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

import org.tentackle.common.Settings;
import org.tentackle.common.StringHelper;
import org.tentackle.maven.ToolRunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a java application installer with the <code>jpackage</code> tool.
 * <p>
 * The mojo works in 4 phases:
 * <ol>
 *   <li>Invokes the <code>jlink</code> tool as described in {@link JlinkMojo}. This will generate
 *   a directory holding the runtime impage. However, no run or update scripts and no zip file will be created.</li>
 *   <li>Invokes the <code>jpackage</code> tool to generate the application image from the previously
 *   created runtime image. Application specific options can be configured via a <code>package-image</code> template.</li>
 *   <li>If the runtime image contains extra classpath- or modulepath-elements, the generated config files
 *   will be patched.</li>
 *   <li>Finally, the installer will be generated from the application image. Again, a <code>package-installer</code>
 *   template can be used to provide additional options to the <code>jpackage</code> tool.</li>
 * </ol>
 *
 * Notice that applications installed by such an installer are maintained by the platform specific
 * package manager. As a result, Tentackle's auto update feature cannot be used in such cases. If auto update
 * is required, {@link JlinkMojo} must be used instead.
 * <p>
 * Important: needs at least Java 14,
 */
@Mojo(name = "jpackage",
      requiresDependencyResolution = ResolutionScope.RUNTIME,
      defaultPhase = LifecyclePhase.PACKAGE)

public class JPackageMojo extends AbstractJlinkMojo {

  /** filename of the options template to create the application image. */
  public static final String PACKAGE_IMAGE_TEMPLATE = "package-image.ftl";

  /** filename of the options template to create the installer. */
  public static final String PACKAGE_INSTALLER_TEMPLATE = "package-installer.ftl";

  /** filename of the generated jpackage options to create the application image. */
  public static final String OPTIONS_IMAGE = "jpackage-image.options";

  /** filename of the generated jpackage options to create the installer. */
  public static final String OPTIONS_INSTALLER = "jpackage-installer.options";

  /** extension of the config files (platform independent). */
  private static final String CONFIG_EXTENSION = ".cfg";



  /**
   * File of the jpackage tool.
   */
  @Parameter(property = "jpackageTool")
  private File jpackageTool;

  /**
   * The filename of the jpackage options template to create the application image.
   */
  @Parameter(defaultValue = PACKAGE_IMAGE_TEMPLATE)
  private String packageImageTemplate;

  /**
   * The filename of the jpackage options template to create the installer.
   */
  @Parameter(defaultValue = PACKAGE_INSTALLER_TEMPLATE)
  private String packageInstallerTemplate;

  /**
   * The directory where to create the installers.
   */
  @Parameter(defaultValue = "${project.build.directory}/jpackage")
  private File packageDirectory;

  /**
   * The name of the main jar holding the main class.<br>
   * Only necessary for classpath applications and only if not the first dependency is the main jar.<br>
   * A unique substring is sufficient, e.g. "myapp-server".
   */
  @Parameter
  private String mainJar;



  private String imagePathPrefix;

  @Override
  public String getImagePathPrefix() {
    if (imagePathPrefix == null) {
      // lazy at runtime!
      imagePathPrefix = "$ROOTDIR" + File.separator + "lib" + File.separator + "runtime" + File.separator;
    }
    return imagePathPrefix;
  }

  /**
   * Gets the name of the package template to create the application image.
   *
   * @return the template file name
   */
  public String getPackageImageTemplate() {
    return packageImageTemplate;
  }

  /**
   * Gets the name of the package template to create the installer.
   *
   * @return the template file name
   */
  public String getPackageInstallerTemplate() {
    return packageInstallerTemplate;
  }

  /**
   * Gets the directory where to create the installers.
   *
   * @return the installer target directory
   */
  public File getPackageDirectory() {
    return packageDirectory;
  }

  @Override
  public void prepareExecute() throws MojoExecutionException, MojoFailureException {
    super.prepareExecute();
    if (jpackageTool == null) {
      jpackageTool = getToolFinder().find("jpackage");
    }
  }

  /**
   * The installers will be installed and deployed with an additional classifier
   * showing platform's architecture.
   *
   * @return the classifier
   * @throws MojoExecutionException if no os.name
   */
  public String getClassifier() throws MojoExecutionException {
    try {
      // we don't need the platform since it is already defined by the installer's extension
      String classifier = StringHelper.getArchitecture();
      if (getExtraClassifier() != null) {
        classifier += "-" + getExtraClassifier();
      }
      return classifier;
    }
    catch (RuntimeException ex) {
      throw new MojoExecutionException(ex.getMessage());
    }
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    long startTime = System.currentTimeMillis();    // to detect generated installers for deployment
    super.executeImpl();
    ArtifactCreator.getInstance().createAndAttachInstallers(this, startTime);
  }

  @Override
  protected void createImageAndArtifacts(JlinkResolver.Result result) throws MojoFailureException, MojoExecutionException {
    if (getImageDirectory().exists()) {
      getLog().info("using already existing jlink image in " + getImageDirectory().getPath());
      copyResources(result);    // to add the "conf" directory to the classpath, if resources found
    }
    else {
      super.createImageAndArtifacts(result);
    }
  }

  @Override
  protected void generateFiles(JlinkResolver.Result result) throws MojoExecutionException {

    // generate the options file for the jpackage tool
    new JPackageOptionsGenerator(this, result).generate();

    // create app-image from the runtime-image previously generated by jlink
    ToolRunner jpackageRunner = new ToolRunner(jpackageTool);
    jpackageRunner.arg("--type").arg("app-image")
                  .arg("-d").arg(packageDirectory)
                  .arg("--runtime-image").arg(getImageDirectory());
    if (result.isModular()) {
      jpackageRunner.arg("-m").arg(getMainModule() + "/" + getMainClass());
    }
    else {
      jpackageRunner.arg("--main-jar").arg(StringHelper.isAllWhitespace(mainJar) ? determineMainJar(result) : mainJar)
                    .arg("--main-class").arg(getMainClass());
    }
    jpackageRunner.arg("@" + getMavenProject().getBuild().getDirectory() + File.separator + OPTIONS_IMAGE);
    if (verbosityLevel.isDebug()) {
      jpackageRunner.arg("--verbose");
    }
    getLog().debug(jpackageRunner.toString());
    getLog().info("creating application image");
    int errCode = jpackageRunner.run().getErrCode();
    if (errCode != 0) {
      throw new MojoExecutionException("creating application image with jpackage failed: " + errCode + "\n" + jpackageRunner.getErrorsAsString() +
                                     "\n" + jpackageRunner.getOutputAsString());
    }
    logToolOutput(jpackageRunner);

    String[] imageDirNames = getPackageDirectory().list();
    if (imageDirNames == null || imageDirNames.length != 1) {
      throw new MojoExecutionException("cannot locate application image directory in " + getPackageDirectory().getPath());
    }
    String applicationName = imageDirNames[0];
    File appImageDir = new File(getPackageDirectory(), applicationName);
    getLog().debug("application image found in " + appImageDir.getPath());
    if (result.isClassPathRequired() || result.isModulePathRequired()) {
      // cfg file must be modified.
      // Notice that we cannot add the classpath via -cp in [JavaOptions] section, since it is ignored there -- for whatever reason.
      // Instead, we must set app.classpath in the [Application] section. Notice further, that -p works, but there
      // is no app.modulepath. Hmm...
      List<File> configFiles = locateConfigFiles(appImageDir);
      if (configFiles.isEmpty()) {
        throw new MojoExecutionException("no application configuration files found");
      }
      for (File configFile : configFiles) {
        patchConfigFile(configFile, result);
      }
    }
    // run jpackage again to create the installer(s) from the app-image
    jpackageRunner = new ToolRunner(jpackageTool);
    jpackageRunner.arg("-n").arg(applicationName)
                  .arg("--app-image").arg(appImageDir)
                  .arg("-d").arg(getMavenProject().getBuild().getDirectory());
    jpackageRunner.arg("@" + getMavenProject().getBuild().getDirectory() + File.separator + OPTIONS_INSTALLER);
    if (verbosityLevel.isDebug()) {
      jpackageRunner.arg("--verbose");
    }
    getLog().debug(jpackageRunner.toString());
    getLog().info("creating installer");
    errCode = jpackageRunner.run().getErrCode();
    if (errCode != 0) {
      throw new MojoExecutionException("creating installer with jpackage failed: " + errCode + "\n" + jpackageRunner.getErrorsAsString() +
                                       "\n" + jpackageRunner.getOutputAsString());
    }
    logToolOutput(jpackageRunner);

    // let's see what we've
  }


  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (jpackageTool == null) {
        throw new MojoExecutionException("jpackageTool tool not found");
      }
      getLog().debug("using " + jpackageTool);
      if (packageImageTemplate == null) {
        throw new MojoExecutionException("packageTemplate missing");
      }
      return true;
    }
    return false;
  }

  @Override
  protected void installTemplates(boolean overwrite) throws MojoExecutionException {
    installTemplate(packageImageTemplate, overwrite);
    installTemplate(packageInstallerTemplate, overwrite);
  }


  private void patchConfigFile(File configFile, JlinkResolver.Result result) throws MojoExecutionException {
    boolean patchClassPath = result.isClassPathRequired();
    boolean classPathPatched = false;
    boolean patchModulePath = result.isModulePathRequired();
    boolean modulePathPatched = false;

    getLog().debug("patching configfile " + configFile);
    StringBuilder buf = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), Settings.getEncodingCharset()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        buf.append(line);
        if (patchClassPath && line.startsWith("app.classpath=")) {
          if (classPathPatched) {
            throw new MojoExecutionException("more than one 'app.classpath' found in " + configFile);
          }
          if (!line.endsWith("=")) {
            // not empty
            buf.append(File.pathSeparatorChar);
          }
          buf.append(result.generateClassPath());
          classPathPatched = true;
        }
        if (patchModulePath && line.equals("[JavaOptions]")) {
          if (modulePathPatched) {
            throw new MojoExecutionException("more than one [JavaOptions] sections found in " + configFile);
          }
          buf.append("\n-p\n").append(result.generateModulePath());
          modulePathPatched = true;
        }
        buf.append('\n');
      }
    }
    catch (IOException e) {
      throw new MojoExecutionException("cannot read from config file " + configFile, e);
    }

    if (patchClassPath && !classPathPatched) {
      throw new MojoExecutionException("could not patch classpath");
    }
    if (patchModulePath && !modulePathPatched) {
      throw new MojoExecutionException("could not patch modulepath");
    }

    try (Writer configWriter = Files.newBufferedWriter(configFile.toPath(), Settings.getEncodingCharset(),
                                                       StandardOpenOption.TRUNCATE_EXISTING)) {
      configWriter.append(buf);
    }
    catch (IOException e) {
      throw new MojoExecutionException("could not patch config file " + configFile, e);
    }
  }

  private List<File> locateConfigFiles(File appImageDir) {
    List<File> configFiles = new ArrayList<>();
    // the cfg files are located in a subdirectory called "app". Depending on the platform, it can be
    // found in lib, Contents or without a further subdir.
    File configDir = locateConfigDirectory(appImageDir);    // windoze?
    if (configDir == null) {
      // linux or mac
      configDir = locateConfigDirectory(new File(appImageDir, "lib"));
      if (configDir == null) {
        // mac?
        configDir = locateConfigDirectory(new File(appImageDir, "Contents"));
      }
    }
    if (configDir != null) {
      String[] configNames = configDir.list((d, n) -> n.toLowerCase().endsWith(CONFIG_EXTENSION));
      if (configNames != null) {
        for (String configName : configNames) {
          configFiles.add(new File(configDir, configName));
        }
      }
    }
    return configFiles;
  }

  private File locateConfigDirectory(File dir) {
    // the cfg files are located in a subdirectory called "app". Depending on the platform, it can be
    // found in lib, Contents or without a further subdir.
    String[] names = dir.list();
    if (names != null) {
      for (String name : names) {
        if ("app".equalsIgnoreCase(name)) {
          return new File(dir, name);
        }
      }
    }
    return null;
  }




  private String determineMainJar(JlinkResolver.Result result) throws MojoExecutionException {
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
      throw new MojoExecutionException("cannot determine main jar");
    }
    return jarName;
  }
}

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
 * The minimum plugin configuration is very simple:
 *
 * <pre>
 *   ...
 *   &lt;packaging&gt;jpackage&lt;/packaging&gt;
 *   ...
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
 * The freemarker templates are copied to the project's template folder, if missing.
 * They become part of the project and can be changed easily according to project specific needs (for example by adding runtime arguments).
 * To install and edit the templates before running jpackage (or jlink, see {@link JlinkMojo}), use {@link InitMojo} first.
 * <p>
 * Notice that applications deployed by an installer are maintained by the platform specific
 * package manager. As a result, Tentackle's auto update feature cannot be used in such cases. If auto update
 * is required, consider using {@link JlinkMojo} instead.
 * <p>
 * If both jlink zip-files and jpackage installers are required, change the packaging type to <code>jar</code>
 * and add executions, like this:
 * <pre>
 *         &lt;executions&gt;
 *           &lt;execution&gt;
 *             &lt;id&gt;both&lt;/id&gt;
 *             &lt;goals&gt;
 *               &lt;goal&gt;jlink&lt;/goal&gt;
 *               &lt;goal&gt;jpackage&lt;/goal&gt;
 *             &lt;/goals&gt;
 *           &lt;/execution&gt;
 *         &lt;/executions&gt;
 * </pre>
 * The jpackage goal will re-use the jlink image.
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

  /** config variable for the classpath. */
  private static final String TAG_APP_CLASSPATH = "app.classpath=";

  /** config variable for the main jar. */
  private static final String TAG_APP_MAINJAR = "app.mainjar=";

  /** config section for the java options. */
  private static final String TAG_JAVA_OPTIONS = "[JavaOptions]";



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
   * Only necessary for classpath applications and only if the top-level artifact of the maven dependency tree is not the main jar.<br>
   * A unique substring is sufficient, e.g. "myapp-server".
   */
  @Parameter
  private String mainJar;



  private String imagePathPrefix;

  @Override
  public String getImagePathPrefix() {
    if (imagePathPrefix == null) {
      String platform = StringHelper.getPlatform();
      if (platform.contains("win")) {
        imagePathPrefix = "$ROOTDIR" + File.separator + "runtime" + File.separator;
      }
      else if (platform.contains("mac")) {
        imagePathPrefix = "$ROOTDIR/Contents/runtime/";
      }
      else {    // linux
        imagePathPrefix = "$ROOTDIR/lib/runtime/";
      }
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
      if (!StringHelper.isAllWhitespace(getExtraClassifier())) {
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
  protected void createImage(JlinkResolver.Result result) throws MojoFailureException, MojoExecutionException {
    if (getImageDirectory().exists()) {
      getLog().info("using already existing jlink image in " + getImageDirectory().getPath());
      copyResources(result);    // to add the "conf" directory to the classpath, if resources found
    }
    else {
      super.createImage(result);
    }
  }

  @Override
  protected void generateFiles(JlinkResolver.Result result) throws MojoExecutionException {

    // generate the options file for the jpackage tool
    new JPackageOptionsGenerator(this, result).generate();

    String mainJarName = null;    // only for classpath apps

    // create app-image from the runtime-image previously generated by jlink
    ToolRunner jpackageRunner = new ToolRunner(jpackageTool);
    jpackageRunner.arg("--type").arg("app-image")
                  .arg("-d").arg(packageDirectory)
                  .arg("--runtime-image").arg(getImageDirectory());
    if (result.isModular()) {
      jpackageRunner.arg("-m").arg(getMainModule() + "/" + getMainClass());
    }
    else {
      mainJarName = StringHelper.isAllWhitespace(mainJar) ? determineMainJar(result) : mainJar;
      jpackageRunner.arg("--main-jar").arg(mainJarName)
                    .arg("--main-class").arg(getMainClass())
                    .arg("--input").arg(new File(getImageDirectory(), "cp"));
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

    File configDir = locateAppDirectory(appImageDir);

    if (!result.isModular()) {
      // remove jars from "app" directory. They have been copied from runtime dir (see --input above)
      String[] names = configDir.list((dir, name) -> !name.toLowerCase().endsWith(CONFIG_EXTENSION));
      if (names != null) {
        for (String name : names) {
          new File(configDir, name).delete();
        }
      }
    }

    if (configDir != null && (result.isClassPathRequired() || result.isModulePathRequired())) {
      // cfg file must be modified.
      // Notice that we cannot add the classpath via -cp in [JavaOptions] section, since it is ignored there -- for whatever reason.
      // Instead, we must set app.classpath in the [Application] section. Notice further, that -p works, but there
      // is no app.modulepath. Hmm...
      List<File> configFiles = loadConfigFiles(configDir);
      if (configFiles.isEmpty()) {
        throw new MojoExecutionException("no application configuration files found");
      }
      for (File configFile : configFiles) {
        patchConfigFile(configFile, result, mainJarName);
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


  /**
   * Patches a configuration file.<br>
   * For modular apps the patch is necessary to add the module path and set the classpath.
   * For non-modular apps the mainjar and the classpath must be replaced in order to
   * point to the runtime image and not the app directory and to provide the correct
   * classpath order according to the maven dependency tree. The latter cannot be guessed
   * by the jpackage tool, especially when classes need to be replaced via ServiceLoader
   * or ServiceFinde (META-INF).
   *
   * @param configFile the config file
   * @param result the resolver result
   * @param mainJarName the name of the mainjar (only if classpath application)
   * @throws MojoExecutionException if patch failed
   */
  private void patchConfigFile(File configFile, JlinkResolver.Result result, String mainJarName) throws MojoExecutionException {
    final boolean patchClassPath = result.isClassPathRequired();
    boolean classPathPatched = false;
    final boolean patchModulePath = result.isModulePathRequired();
    boolean modulePathPatched = false;
    final boolean patchMainJar = !result.isModular();
    boolean mainJarPatched = false;

    getLog().debug("patching configfile " + configFile);
    StringBuilder buf = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), Settings.getEncodingCharset()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        buf.append(line);
        if (patchClassPath && line.startsWith(TAG_APP_CLASSPATH)) {
          if (classPathPatched) {
            throw new MojoExecutionException("more than one '" + TAG_APP_CLASSPATH + "' found in " + configFile);
          }
          // replace the classpath
          buf.setLength(buf.length() - line.length() + TAG_APP_CLASSPATH.length());
          buf.append(result.generateClassPath());
          classPathPatched = true;
        }
        if (patchMainJar && line.startsWith(TAG_APP_MAINJAR)) {
          if (mainJarPatched) {
            throw new MojoExecutionException("more than one '" + TAG_APP_MAINJAR + "' found in " + configFile);
          }
          // replace mainjar
          buf.setLength(buf.length() - line.length() + TAG_APP_MAINJAR.length());
          buf.append(getImagePathPrefix() + mainJarName);
          mainJarPatched = true;
        }
        if (patchModulePath && line.equals(TAG_JAVA_OPTIONS)) {
          if (modulePathPatched) {
            throw new MojoExecutionException("more than one '" + TAG_JAVA_OPTIONS + "' found in " + configFile);
          }
          // insert the module path
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

  /**
   * Loads the config files from the given directory.
   *
   * @param configDir the directory holding the config files
   * @return the list of files, never null
   */
  private List<File> loadConfigFiles(File configDir) {
    List<File> configFiles = new ArrayList<>();
    // the cfg files are located in a subdirectory called "app". Depending on the platform, it can be
    // found in lib, Contents or without a further subdir.

      String[] configNames = configDir.list((d, n) -> n.toLowerCase().endsWith(CONFIG_EXTENSION));
      if (configNames != null) {
        for (String configName : configNames) {
          configFiles.add(new File(configDir, configName));
        }
      }
    return configFiles;
  }

  /**
   * Locates the app directory within the application image directory.<br>
   * Tries the different locations of the known platforms.
   *
   * @param appImageDir the application image directory
   * @return the app directory, null if not found
   */
  private File locateAppDirectory(File appImageDir) {
    File appDir = loadAppDirectory(appImageDir);    // windoze?
    if (appDir == null) {
      // linux or mac
      appDir = loadAppDirectory(new File(appImageDir, "lib"));
      if (appDir == null) {
        // mac?
        appDir = loadAppDirectory(new File(appImageDir, "Contents"));
      }
    }
    return appDir;
  }

  /**
   * Loads the "app" directory if it is a subdir of given directory.
   *
   * @param dir the directory to look for the app directory
   * @return the app directory, null if not in dir
   */
  private File loadAppDirectory(File dir) {
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

  /**
   * Determines the main jar of the application.
   *
   * @param result the resolver result
   * @return the name of the main jar, never null
   * @throws MojoExecutionException if main jar could not be determined
   */
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

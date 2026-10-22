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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.toolchain.Toolchain;
import org.eclipse.aether.artifact.Artifact;

import org.tentackle.common.FileHelper;
import org.tentackle.common.LayerBootstrap;
import org.tentackle.common.Settings;
import org.tentackle.common.StringHelper;
import org.tentackle.common.ToolRunner;
import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.maven.JavaToolFinder;
import org.tentackle.maven.ProjectDependencies;

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
import java.util.Locale;
import java.util.Map;

/**
 * Creates a java application installer with the <code>jpackage</code> tool.
 * <p>
 * The mojo works in 4 phases:
 * <ol>
 *   <li>Invokes the <code>jlink</code> tool as described in {@link JLinkMojo}. This will generate
 *   a directory holding the runtime image. However, no run or update scripts and no zip file will be created.</li>
 *   <li>Invokes the <code>jpackage</code> tool to generate the application image from the previously
 *   created runtime image. Application- and platform specific options can be configured via the {@link #packageImageTemplate}.</li>
 *   <li>If the runtime image contains extra classpath- or modulepath-elements, the generated config files
 *   will be patched. This is especially necessary to provide the correct
 *   classpath order according to the maven/module dependency tree, which usually differs
 *   from the one determined by <code>jpackage</code>, because <code>jpackage</code> has no idea about the maven project structure
 *   and does its own guess according to the packages referenced from within the jars.
 *   This may become an issue if the classpath order is critical, such as configurations overridden in META-INF.</li>
 *   <li>Finally, the installer will be generated from the application image. The {@link #packageInstallerTemplate}
 *   is used to provide additional options to the <code>jpackage</code> tool.</li>
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
 * To install and edit the templates before running jpackage (or jlink, see {@link JLinkMojo}), use {@link InitMojo} first.
 * In addition to the template variables defined by the {@link JLinkMojo}, the variable <code>runtimeDir</code> is provided
 * pointing to the runtime image directory (which is platform specific).
 * <p>
 * The application image passes the system properties <code>ROOTDIR</code>, <code>APPDIR</code> and <code>RUNTIMEDIR</code>
 * to the application. If {@link AbstractJLinkMojo#getLayers()} are configured, the launcher starts
 * <code>org.tentackle.common.LayerBootstrap</code> instead of the main class and additionally passes
 * <code>tentackle.layers</code> and <code>tentackle.layers.basedir</code>, since the native launcher's working
 * directory is not the runtime image.
 * <p>
 * If the application is built with Tentackle's update feature, please keep in mind that
 * applications deployed by an installer are maintained by a platform specific
 * package manager. If the installation is system-wide, the installation files cannot be changed by
 * a regular user.
 * Some platforms, however, also provide per-user installations that can be updated. For Windows, the <code>jpackage</code>
 * tool provides the option <code>--win-per-user-install</code>. MacOS allows the user to decide whether to install
 * system-wide or for the current user only. See {@link AbstractJLinkMojo#isWithUpdater()} for more details.
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
 * The jpackage goal will then re-use the previously created jlink image.
 * <p>
 * The contents of the application image and attachment of the artifacts for installation and deployment can be customized by an
 * application-specific implementation.
 * To do so, provide a plugin dependency that contains a class annotated with {@code @Service(}{@link ArtifactCreator}).
 * <p>
 * Important: the <code>jpackage</code> tool is available since Java 14.
 * <p>
 * Notice that you can create an image for a different java version than the one used by the maven build process
 * via {@link AbstractTentackleMojo#getToolchain()}. Furthermore, you can select the <code>jpackage</code> tool explicitly from another JDK
 * via {@link #jpackageToolchain} or {@link #jpackageTool}.
 */
@Mojo(name = "jpackage",
      requiresDependencyResolution = ResolutionScope.RUNTIME,
      defaultPhase = LifecyclePhase.PACKAGE,
      threadSafe = true)

public class JPackageMojo extends AbstractJLinkMojo {

  /** filename of the options template to create the application image. */
  public static final String PACKAGE_IMAGE_TEMPLATE = "package-image.ftl";

  /** filename of the options template to create the installer. */
  public static final String PACKAGE_INSTALLER_TEMPLATE = "package-installer.ftl";

  /** filename of the template to create the package updater script. */
  public static final String PACKAGE_UPDATE_TEMPLATE = "package-update.ftl";

  /** filename of the generated jpackage options to create the application image. */
  public static final String OPTIONS_IMAGE = "jpackage-image.options";

  /** filename of the generated jpackage options to create the installer. */
  public static final String OPTIONS_INSTALLER = "jpackage-installer.options";

  /** extension of the config files (platform independent). */
  private static final String CONFIG_EXTENSION = ".cfg";

  /** extension added by macOS to the application folder's name. */
  private static final String MAC_APPNAME_EXTENSION = ".app";

  /** config variable for the classpath. */
  private static final String APP_CLASSPATH = "app.classpath=";

  /** config variable for the main jar. */
  private static final String APP_MAINJAR = "app.mainjar=";

  /** config section for the java options. */
  private static final String JAVA_OPTIONS = "[JavaOptions]";

  /** name of the subdir jpackage >= 15 puts the module path jars into. */
  private static final String MODS_DIR = "mods";

  /** config for the module path (>= java 15). */
  private static final String APPDIR_MODS = "java-options=$APPDIR/" + MODS_DIR;

  /** config for the module path (>= java 15 on windows) */
  private static final String APPDIR_MODS_WINDOWS = "java-options=$APPDIR\\" + MODS_DIR;


  /**
   * Explicit path to the <code>jpackage</code> tool.<br>
   * Only if toolchains cannot be used.
   * @see #jpackageToolchain
   */
  @Parameter(property = "jpackageTool")
  private File jpackageTool;

  /**
   * Extra jpackage options.<br>
   * Provided for platform-specific or hidden options.
   */
  @Parameter
  private List<String> jpackageOptions;

  /**
   * Toolchain for invocation of the <code>jpackage</code> tool only.<br>
   * Allows to create installers for older java runtime versions not supporting the jpackage tool yet.
   * <p>
   * Example:
   * <pre>
   *   &lt;jpackageToolchain&gt;
   *     &lt;version&gt;14&lt;/version&gt;
   *   &lt;/jpackageToolchain&gt;
   * </pre>
   * To deselect the toolchain configured by the maven-toolchain-plugin:
   * <pre>
   *   &lt;jpackageToolchain&gt;&lt;/jpackageToolchain&gt;
   * </pre>
   */
  @Parameter
  private Map<String, String> jpackageToolchain;

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
   * The name of the update script template.
   */
  @Parameter(defaultValue = PACKAGE_UPDATE_TEMPLATE)
  private String packageUpdateTemplate;

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


  /**
   * Major version of the jpackage tool.
   */
  private int jpackageMajorRuntimeVersion;

  /**
   * Lazily created image path prefix according to the platform.
   */
  private String imagePathPrefix;


  @Override
  public String getImagePathPrefix() {
    if (imagePathPrefix == null) {
      String platform = StringHelper.getPlatform();
      if (platform.contains("win")) {
        imagePathPrefix = "$ROOTDIR/runtime";    // no backslashes!
      }
      else if (platform.contains("mac")) {
        imagePathPrefix = "$ROOTDIR/runtime/Contents/Home";
      }
      else {    // linux
        if (jpackageMajorRuntimeVersion >= 15) {
          // since 15 the ROOTDIR is no more defined (see linux/native/applauncher/Package.cpp).
          // However, if we move the installation to another directory, so that dpkg can't find it anymore,
          // ROOTDIR will be set properly. Bug or feature?
          // anyway... -> using APPDIR instead fixes this issue
          // TODO: check whether fixed in future versions (JDK 15 RC2 at time of writing)
          imagePathPrefix = "$APPDIR/../runtime";
        }
        else {
          imagePathPrefix = "$ROOTDIR/lib/runtime";
        }
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
   * Gets the name of the package updater template.
   *
   * @return the template file name, null if update feature disabled
   */
  public String getPackageUpdateTemplate() {
    return isWithUpdater() ? packageUpdateTemplate : null;
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
      JavaToolFinder toolFinder;
      if (jpackageToolchain == null) {
        toolFinder = getToolFinder();
      }
      else {
        if (jpackageToolchain.isEmpty()) {
          toolFinder = new JavaToolFinder();    // explicitly no toolchain!
        }
        else {
          Toolchain toolchain = getToolchain(JDK_TOOLCHAIN, jpackageToolchain);
          if (toolchain == null) {
            throw new MojoExecutionException("no matching toolchain for the jpackage tool: " + jpackageToolchain);
          }
          toolFinder = new JavaToolFinder(toolchain);
        }
      }
      jpackageTool = toolFinder.find("jpackage");
    }
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    long startTime = System.currentTimeMillis();    // to detect generated installers for deployment
    super.executeImpl();
    ArtifactCreator.getInstance().attachInstallers(this, startTime);
  }


  @Override
  protected void createImage(JLinkResolver.Result result) throws MojoFailureException, MojoExecutionException {
    if (getImageDirectory().exists()) {
      getLogger().info("using already existing jlink image in {}", getImageDirectory().getPath());
      copyResources(result);    // to add the "conf" directory to the classpath, if resources found
    }
    else {
      super.createImage(result);
    }
  }

  @Override
  protected void generateFiles(JLinkResolver.Result result) throws MojoExecutionException {

    // generate the options file for the jpackage tool
    JPackageGenerator generator = new JPackageGenerator(this, result);
    generator.generateOptions();

    String mainJarName = null;    // only for classpath apps
    File cpDir = null;            // the classpath artifact directory (only for classpath apps)

    // create app-image from the runtime-image previously generated by jlink
    ToolRunner jpackageRunner = new ToolRunner(jpackageTool);
    jpackageRunner.arg("--type").arg("app-image")
                  .arg("-d").arg(packageDirectory)
                  .arg("--runtime-image").arg(getImageDirectory());
    if (result.isModular()) {
      // a layered application is launched via the bootstrap instead of its own main module/class,
      // exactly like the jlink goal's run.ftl - see org.tentackle.common.LayerBootstrap
      String effectiveMainModule = isLayered() ? JLinkResolver.BOOTSTRAP_MODULE_NAME : getMainModule();
      String effectiveMainClass = isLayered() ? JLinkResolver.BOOTSTRAP_CLASS_NAME : getMainClass();
      ModularArtifact mainArtifact = result.getModuleArtifact(effectiveMainModule);   // just check before
      if (mainArtifact == null) {
        throw new MojoExecutionException("no such main module: " + effectiveMainModule);
      }
      if (result.isModulePathRequired() && jpackageMajorRuntimeVersion >= 15) {
        // jpackage >= 15 needs a module path if built with --runtime-image
        // we will remove the artifacts copied to the app directory below...
        jpackageRunner.arg("-p").arg(getImageDirectory() + "/" + DEST_MODULEPATH);
      }
      jpackageRunner.arg("-m").arg(effectiveMainModule + "/" + effectiveMainClass);
    }
    else {
      mainJarName = determineMainJar(result);
      cpDir = new File(getImageDirectory(), DEST_CLASSPATH);
      jpackageRunner.arg("--main-jar").arg(mainJarName)
                    .arg("--main-class").arg(getMainClass())
                    .arg("--input").arg(cpDir);
    }
    jpackageRunner.arg("--java-options").arg("-DROOTDIR=$ROOTDIR")
                  .arg("--java-options").arg("-DAPPDIR=$APPDIR")
                  .arg("--java-options").arg("-DRUNTIMEDIR=" + getImagePathPrefix());
    if (isLayered()) {
      // the native launcher does not run in the runtime image: the relative layer directories of
      // layers.properties must be resolved against the runtime image explicitly
      jpackageRunner.arg("--java-options").arg("-D" + LayerBootstrap.LAYERS_PROPERTY + "=" +
              getImagePathPrefix() + "/" + DEST_RESOURCES + "/" + LayerResolver.LAYERS_PROPERTIES)
                    .arg("--java-options").arg("-D" + LayerBootstrap.LAYERS_BASEDIR_PROPERTY + "=" + getImagePathPrefix());
    }
    jpackageRunner.arg("@" + getMavenProject().getBuild().getDirectory() + File.separator + OPTIONS_IMAGE);
    if (verbosityLevel.isDebug()) {
      jpackageRunner.arg("--verbose");
    }
    if (jpackageOptions != null) {
      jpackageOptions.forEach(jpackageRunner::arg);
    }
    getLogger().debug(jpackageRunner.toString());
    getLogger().info("creating application image");
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
    getLogger().debug("application image found in {}", appImageDir.getPath());

    ArtifactCreator.getInstance().processApplicationImage(this, appImageDir);

    File configDir = locateAppDirectory(appImageDir);

    if (cpDir != null) {    // classpath app
      // remove all jars in the classpath (cpDir) directory from the "app" directory.
      // They have been copied from cpDir to appDir (see --input above)
      String[] names = cpDir.list();
      if (names != null) {
        for (String name : names) {
          new File(configDir, name).delete();
        }
      }
    }

    if (configDir != null && (result.isClassPathRequired() || result.isModulePathRequired())) {
      // cfg file must be modified.
      // Notice that we cannot add the classpath via -cp in [JavaOptions] section, since it is ignored there -- for whatever reason.
      // Instead, we must set app.classpath in the [Application] section. Notice further that -p works and there
      // is no app.modulepath.
      List<File> configFiles = loadConfigFiles(configDir);
      if (configFiles.isEmpty()) {
        throw new MojoExecutionException("no application configuration files found");
      }
      for (File configFile : configFiles) {
        patchConfigFile(configFile, result, mainJarName);
      }
      if (jpackageMajorRuntimeVersion >= 15) {
        // remove duplicate jars (see above)
        File modsDir = new File(configDir, MODS_DIR);
        if (modsDir.isDirectory()) {
          try {
            getLogger().debug("removing {}", modsDir);
            FileHelper.removeDirectory(modsDir.getPath());
          }
          catch (IOException e) {
            throw new MojoExecutionException("cannot remove obsolete module directory " + modsDir);
          }
        }
      }
    }

    // run jpackage again to create the installer(s) from the app-image
    jpackageRunner = new ToolRunner(jpackageTool);
    // remove ".app" from application name, if on MAC
    if (applicationName.endsWith(MAC_APPNAME_EXTENSION)) {
      applicationName = applicationName.substring(0, applicationName.length() - MAC_APPNAME_EXTENSION.length());
    }
    jpackageRunner.arg("-n").arg(applicationName)   // can be overridden via options file below
                  .arg("--app-image").arg(appImageDir);
    // version must only contain digits and dots (else error "Version ... contains invalid component ...", at least on windows)
    StringBuilder appVersion = new StringBuilder();
    String projectVersion = getMavenProject().getVersion();
    for (int i=0; i < projectVersion.length(); i++) {
      char c = projectVersion.charAt(i);
      if (c == '.' || Character.isDigit(c)) {
        appVersion.append(c);
      }
      else {
        break;
      }
    }
    if (!appVersion.isEmpty()) {
      jpackageRunner.arg("--app-version").arg(appVersion);  // can be overridden via options file below
    }
    jpackageRunner.arg("-d").arg(getMavenProject().getBuild().getDirectory())
                  .arg("@" + getMavenProject().getBuild().getDirectory() + File.separator + OPTIONS_INSTALLER);
    if (verbosityLevel.isDebug()) {
      jpackageRunner.arg("--verbose");
    }
    getLogger().debug(jpackageRunner.toString());
    getLogger().info("creating installer");
    errCode = jpackageRunner.run().getErrCode();
    if (errCode != 0) {
      throw new MojoExecutionException("creating installer with jpackage failed: " + errCode + "\n" + jpackageRunner.getErrorsAsString() +
                                       "\n" + jpackageRunner.getOutputAsString());
    }
    logToolOutput(jpackageRunner);

    if (isWithUpdater()) {
      getLogger().info("creating updater");
      generator.generateUpdateScript(appImageDir);
      ArtifactCreator.getInstance().createAndAttachArtifact(this, appImageDir);
    }
  }

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (jpackageTool == null) {
        throw new MojoExecutionException("jpackage tool not found");
      }
      getLogger().debug("using {}", jpackageTool);
      jpackageMajorRuntimeVersion = getMajorVersion(determineJavaToolVersion(jpackageTool));
      getLogger().debug("major version of jpackage is {}", jpackageMajorRuntimeVersion);
      if (jpackageMajorRuntimeVersion != getJavaMajorRuntimeVersion()) {
        getLogger().warn("jpackage tool's major version {} differs from jlink/jdeps version {}",
                         jpackageMajorRuntimeVersion, getJavaMajorRuntimeVersion());
      }
      return true;
    }
    return false;
  }

  @Override
  protected void validateTemplates() throws MojoExecutionException {
    if (StringHelper.isAllWhitespace(packageImageTemplate)) {
      throw new MojoExecutionException("packageImageTemplate missing");
    }
    if (StringHelper.isAllWhitespace(packageInstallerTemplate)) {
      throw new MojoExecutionException("packageInstallerTemplate missing");
    }
    if (isWithUpdater() && StringHelper.isAllWhitespace(packageUpdateTemplate)) {
      throw new MojoExecutionException("packageUpdateTemplate missing");
    }
  }

  @Override
  protected void installTemplates(boolean overwrite) throws MojoExecutionException {
    installTemplate(PACKAGE_IMAGE_TEMPLATE, packageImageTemplate, overwrite);
    installTemplate(PACKAGE_INSTALLER_TEMPLATE, packageInstallerTemplate, overwrite);
    if (getPackageUpdateTemplate() != null) {
      installTemplate(PACKAGE_UPDATE_TEMPLATE, packageUpdateTemplate, overwrite);
    }
  }


  /**
   * Patches a configuration file.<br>
   * For modular apps it adds the modulepath via the <code>[JavaOptions]</code> section
   * and sets the classpath via the <code>app.classpath</code> variable.<br>
   * For non-modular apps the mainjar and the classpath are replaced in order to
   * point to the runtime image and not the app directory and to provide the correct
   * classpath order according to the maven/module dependency tree. The order usually differs
   * from the one determined by jpackage, because jpackage has no idea about the maven project structure
   * (which may become an issue when classpath order is critical, such as configuration overrides in META-INF).
   *
   * @param configFile the config file
   * @param result the resolver result
   * @param mainJarName the name of the mainjar (only if classpath application)
   * @throws MojoExecutionException if patch failed
   */
  private void patchConfigFile(File configFile, JLinkResolver.Result result, String mainJarName) throws MojoExecutionException {
    final boolean patchClassPath = result.isClassPathRequired();
    boolean classPathPatched = false;
    final boolean patchModulePath = result.isModulePathRequired();
    boolean modulePathPatched = false;
    final boolean patchMainJar = !result.isModular();
    boolean mainJarPatched = false;

    getLogger().debug("patching configfile {}", configFile);
    StringBuilder buf = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), Settings.getEncodingCharset()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        buf.append(line);
        if (patchClassPath && line.startsWith(APP_CLASSPATH)) {
          if (classPathPatched && jpackageMajorRuntimeVersion >= 15) {
            // starting with Java 15 app.classpath is used once per artifact: just ignore succeeding lines
            getLogger().debug("removed line: {}", line);
            buf.setLength(buf.length() - line.length());
            continue;
          }
          // replace the classpath
          getLogger().debug("old classpath = {}", line.substring(APP_CLASSPATH.length()));
          buf.setLength(buf.length() - line.length() + APP_CLASSPATH.length());
          buf.append(result.generateClassPath());
          classPathPatched = true;
        }
        if (patchMainJar && line.startsWith(APP_MAINJAR)) {
          if (mainJarPatched) {
            throw new MojoExecutionException("more than one '" + APP_MAINJAR + "' found in " + configFile);
          }
          // replace mainjar
          getLogger().debug("old mainjar = {}", line.substring(APP_MAINJAR.length()));
          buf.setLength(buf.length() - line.length() + APP_MAINJAR.length());
          buf.append(getImagePathPrefix()).append('/').append(DEST_CLASSPATH).append('/').append(mainJarName);
          mainJarPatched = true;
        }
        if (patchModulePath) {
          if (line.equals(JAVA_OPTIONS)) {
            if (modulePathPatched) {
              throw new MojoExecutionException("more than one '" + JAVA_OPTIONS + "' found in " + configFile);
            }
            if (jpackageMajorRuntimeVersion >= 15) {
              if (patchClassPath && !classPathPatched) {
                // missing app.classpath (beginning with java 15): insert it before the [JavaOptions] section
                buf.setLength(buf.length() - JAVA_OPTIONS.length() - 2);
                buf.append('\n').append(APP_CLASSPATH).append(result.generateClassPath())
                   .append("\n\n").append(JAVA_OPTIONS);
                classPathPatched = true;
              }
            }
            else {
              // insert the module path
              buf.append("\n-p\n").append(result.generateModulePath());
              modulePathPatched = true;
            }
          }
          else if ((line.equals(APPDIR_MODS) || line.equals(APPDIR_MODS_WINDOWS)) && jpackageMajorRuntimeVersion >= 15) {
            if (modulePathPatched) {
              throw new MojoExecutionException("more than one module path option found in " + configFile);
            }
            // java options end with module path: replace it
            buf.setLength(buf.length() - APPDIR_MODS.length());
            buf.append("java-options=").append(result.generateModulePath()).append('\n');
            modulePathPatched = true;
          }
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
    String[] configNames = configDir.list((_, n) -> n.toLowerCase(Locale.ROOT).endsWith(CONFIG_EXTENSION));
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
   * Determines the main jar of the application.<br>
   * This is the first classpath artifact, unless {@link #mainJar} is configured, which may be a unique
   * substring of the jar's filename.
   *
   * @param result the resolver result
   * @return the name of the main jar, never null
   * @throws MojoExecutionException if main jar could not be determined
   */
  private String determineMainJar(JLinkResolver.Result result) throws MojoExecutionException {
    String jarName = null;
    if (StringHelper.isAllWhitespace(mainJar)) {
      jarName = result.getClassPath().isEmpty() ? null : ProjectDependencies.getFile(result.getClassPath().getFirst()).getName();
    }
    else {
      for (Artifact artifact : result.getClassPath()) {
        if (ProjectDependencies.getFile(artifact).getName().contains(mainJar)) {
          jarName = ProjectDependencies.getFile(artifact).getName();
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

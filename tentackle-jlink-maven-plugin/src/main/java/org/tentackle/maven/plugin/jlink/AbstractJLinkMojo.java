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

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.DependencyResolutionException;

import javax.inject.Inject;
import javax.inject.Named;

import org.tentackle.common.FileHelper;
import org.tentackle.common.StringHelper;
import org.tentackle.common.ToolRunner;
import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.maven.ProjectDependencies;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Base class for all mojos of the <code>tentackle-jlink-maven-plugin</code>.
 *
 * @author harald
 */
public abstract class AbstractJLinkMojo extends AbstractTentackleMojo {

  /**
   * Name of the destination subfolder containing the classpath artifacts.
   */
  public static final String DEST_CLASSPATH = "cp";

  /**
   * Name of the destination subfolder containing the module-path artifacts.
   */
  public static final String DEST_MODULEPATH = "mp";

  /**
   * Name of the destination subfolder containing the resources.
   */
  public static final String DEST_RESOURCES = "conf";

  /**
   * File extension for ZIP files.
   */
  public static final String ZIP_EXTENSION = "zip";



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
   * Dependencies that should go to the classpath instead of the module-path.
   */
  @Parameter
  private List<ClasspathDependency> classpathDependencies;

  /**
   * Named, isolated {@code ModuleLayer}s for dependencies that must run isolated from each other -
   * typically because two of them need different, otherwise conflicting versions of the same module.
   * <p>
   * If empty (the default), the application is built exactly as without this feature at all: a single,
   * unlayered boot {@code ModuleLayer}. If not empty, {@link #mainClass} is launched by
   * {@code org.tentackle.common.LayerBootstrap} instead of directly, which builds the configured
   * {@code ModuleLayer} graph at startup and then hands off to the real main class. See the
   * {@code layers.md} documentation.
   *
   * @see #leafLayer
   */
  @Parameter
  private List<Layer> layers;

  /**
   * The name of the layer holding the application's own {@link #mainModule}, used as the {@code leaf}
   * entry of the generated {@code layers.properties}. Required if {@link #layers} is not empty, and must
   * not collide with any of their names.
   */
  @Parameter
  private String leafLayer;

  /**
   * Controls the behavior if an automatic module is required.
   *
   * <ul>
   *   <li>default: log such modules as warnings</li>
   *   <li>true: log as an error and let the build fail</li>
   *   <li>false: log as info</li>
   * </ul>
   *
   * This flag is ignored, if a classpath application is built,
   * i.e., no {@link #mainModule} is given.
   */
  @Parameter
  protected Boolean failOnAutomaticRequired;

  /**
   * Extra directories to copy to the image.
   */
  @Parameter
  private List<File> extraDirectories;

  /**
   * Extra classpath elements.<br>
   * Pathnames are relative to the image root.
   */
  @Parameter
  private List<String> extraClasspathElements;

  /**
   * The directory holding the script templates.
   */
  @Parameter(defaultValue = "${project.basedir}/templates")
  private File templateDir;

  /**
   * Explicit path to the <code>jdeps</code> tool.<br>
   * Only if toolchains cannot be used.
   * @see AbstractTentackleMojo#getToolchain()
   */
  @Parameter(property = "jdepsTool")
  private File jdepsTool;

  /**
   * Explicit path to the <code>jlink</code> tool.<br>
   * Only if toolchains cannot be used.
   * @see AbstractTentackleMojo#getToolchain()
   */
  @Parameter(property = "jlinkTool")
  private File jlinkTool;

  /**
   * Extra jlink options.<br>
   * Provided for platform-specific or hidden options, such as {@code --upgrade-files}.
   */
  @Parameter
  private List<String> jlinkOptions;

  /**
   * The optional file holding the jlink options.<br>
   * Corresponds to the <code>jlink</code> option <code>--save-opts</code>.
   */
  @Parameter
  private File saveOpts;

  /**
   * Enables the auto-update feature.
   * <p>
   * For the <code>jlink</code> goal an extra update script will be generated
   * via the {@link JLinkMojo#getUpdateTemplate()}.
   * The update script will be named <code>bin/update.&lt;extension&gt;</code> where
   * the extension is derived from the platform (<code>cmd</code> on Windows,
   * otherwise <code>sh</code> for unixes).
   * <p>
   * For the <code>jpackage</code> goal an extra ZIP-file is created holding
   * the jpackage image's files and an update script generated via
   * {@link JPackageMojo#getPackageUpdateTemplate()}.
   * <p>
   * For an implementation of the auto-update feature see the Tentackle modules
   * <code>tentackle-update</code> and <code>tentackle-fx-rdc-update</code>.
   * Whereas <code>tentackle-update</code> can be used by any kind of application,
   * the frontend part <code>tentackle-fx-rdc-update</code> requires a Tentackle FX
   * application.
   */
  @Parameter
  private boolean withUpdater;

  /**
   * List of full-blown modules that should be moved to the module path explicitly.<br>
   * By default, all full-blown modules are passed to the jlink tool. If a module is listed
   * for the module path only, the module will be moved to the <code>mp</code> folder instead.<br>
   * This may become necessary if a 3rd-party full-blown module (illegally!) requires automatic modules, which
   * would cause all artifacts to be moved to the module path by this plugin automatically, since the
   * jlink tool can only handle full-blown modules.
   * <p>
   * Please notice that this workaround is only available for non-required modules, i.e., modules
   * that "plug in" via the ServiceFinder or some other dependency injection mechanism.
   * <p>
   * The main module of a layered application (see {@link #layers}) is always module-path-only,
   * without being listed here.
   * <p>
   * If all modules must be moved to the <code>mp</code> folder, the single values <code>true</code>,
   * <code>all</code> or <code>*</code> can be used as a shortcut.
   * <p>
   * Examples:
   * <pre>
   *    &lt;modulePathOnly&gt;
   *        &lt;!-- kwik 0.10.9 illegally requires automatic module io.whitfin:siphash --&gt;
   *        &lt;module&gt;org.tentackle.quic&lt;/module&gt;
   *        &lt;module&gt;tech.kwik.core&lt;/module&gt;
   *    &lt;/modulePathOnly&gt;
   * </pre>
   * or
   * <pre>
   *   &lt;modulePathOnly&gt;all&lt;/modulePathOnly&gt;
   * </pre>
   */
  @Parameter
  private List<String> modulePathOnly;

  /**
   * The directory where to store the ZIP files.
   */
  @Parameter(defaultValue = "${project.build.directory}")
  private File zipDirectory;

  /**
   * Strip debug information.<br>
   * Corresponds to the <code>jlink</code> option <code>--strip-debug</code>.
   */
  @Parameter(defaultValue = "false")
  private boolean stripDebug;

  /**
   * Compression of resources.<br>
   * Corresponds to the <code>jlink</code> option <code>--compress</code>, i.e. <code>zip-0</code> to
   * <code>zip-9</code> (the legacy values <code>0</code>, <code>1</code> and <code>2</code> are deprecated
   * since Java 21).
   */
  @Parameter
  private String compress;

  /**
   * Suppresses a fatal error when signed modular JARs are linked in the runtime image.<br>
   * Corresponds to the <code>jlink</code> option <code>--ignore-signing-information</code>
   */
  @Parameter(defaultValue = "false")
  private boolean ignoreSigningInformation;

  /**
   * Suppress the <code>include</code> directory.<br>
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
   * If no resources must be copied, for example, in server-images for production, set this to any
   * non-existent directory, for example <code>"none"</code>.
   */
  @Parameter(defaultValue = "${project.build.directory}/classes")
  private File resourcesDirectory;

  /**
   * Extra jre or jdk modules to add to the image.<br>
   * Useful to add the runtime localization module or extra tools in the bin-directory.
   * <p>
   * Example:
   *
   * <pre>
   * &lt;addModules&gt;
   *   &lt;addModule&gt;jdk.localedata&lt;/addModule&gt;
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
   * <p>
   * For example, the subproject's basedir:
   * <pre>
   *  &lt;variables&gt;
   *    &lt;project&gt;${project.basedir}&lt;/project&gt;
   *  &lt;/variables&gt;
   * </pre>
   * And then use it in <code>package-image.ftl</code> for file paths, such as:
   * <pre>
   *   --icon ${project}/src/pkg/myapp.ico
   * </pre>
   */
  @Parameter
  private Map<String, Object> variables;

  /**
   * Defines the precedence of properties and variables.<br>
   * By default, system properties are overridden by maven properties which are overridden by the extra template variables.
   * <p>
   * The case-insensitive keywords are:
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
   * The generated ZIP file name.
   */
  @Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
  private String finalName;


  /**
   * JPMS support.
   */
  @Inject
  private LocationManager locationManager;

  /**
   * To attach artifacts with classifier.
   */
  @Inject
  private MavenProjectHelper projectHelper;

  /**
   * The archiver to zip the image.
   */
  @Inject
  @Named(ZIP_EXTENSION)
  private ZipArchiver zipArchiver;


  /**
   * The java runtime version used by the application.
   */
  private String javaRuntimeVersion;

  /**
   * The java major version as a comparable integer.
   */
  private int javaMajorRuntimeVersion;



  /**
   * Gets the ZIP file's name without extension.<br>
   * Invoked by the {@link ArtifactCreator}.
   *
   * @return the final name
   */
  public String getFinalName() {
    return finalName;
  }

  /**
   * Returns whether the update feature is enabled.
   *
   * @return true if generate artifacts with the auto-update feature
   */
  public boolean isWithUpdater() {
    return withUpdater;
  }

  /**
   * Returns the policy if an automatic module is required.
   *
   * @return the policy, null if not configured
   * @see #failOnAutomaticRequired
   */
  public Boolean getFailOnAutomaticRequired() {
    return failOnAutomaticRequired;
  }

  /**
   * Returns whether all application modules should go to the module path and
   * not the image (modules) file.
   *
   * @return true if forced to mp folder
   */
  public boolean isModulePathOnly() {
    if (modulePathOnly != null && modulePathOnly.size() == 1) {
      String arg = modulePathOnly.get(0);
      return "true".equals(arg) || "all".equals(arg) || "*".equals(arg);
    }
    return false;
  }

  /**
   * Returns whether the given module name should go to the module path and
   * not the image (modules) file.
   *
   * @param moduleName the name of the full-blown module
   * @return true if forced to mp folder
   */
  public boolean isModulePathOnly(String moduleName) {
    if (isLayered() && moduleName.equals(getMainModule())) {
      // a layered application's own main module can never be linked into the jimage: ModuleLayerBuilder
      // needs to find it later as a loose file on disk, not baked into the runtime image
      return true;
    }
    return modulePathOnly != null && modulePathOnly.contains(moduleName);
  }

  /**
   * Gets the configured isolated module layers.
   *
   * @return the layers, never null
   */
  public List<Layer> getLayers() {
    return layers == null ? Collections.emptyList() : layers;
  }

  /**
   * Returns whether this application uses isolated module layers.
   *
   * @return true if {@link #getLayers()} is not empty
   */
  public boolean isLayered() {
    return !getLayers().isEmpty();
  }

  /**
   * Gets the name of the layer holding the application's own main module.
   *
   * @return the leaf layer name, null if not {@link #isLayered()}
   */
  public String getLeafLayer() {
    return leafLayer;
  }

  /**
   * Gets the directory where to store the ZIP-file.
   *
   * @return the ZIP target folder
   */
  public File getZipDirectory() {
    return zipDirectory;
  }

  /**
   * Gets the template directory.
   *
   * @return the directory holding the script and option templates
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
   * @return the file, never null once {@link #validate()} succeeded
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
   * Gets the modules to exclude.
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
   * @return the pathnames relative to the image root, null for none
   */
  public List<String> getExtraClasspathElements() {
    return extraClasspathElements;
  }

  /**
   * Extra directories to copy to the image.
   *
   * @return the directories, null if none
   */
  public List<File> getExtraDirectories() {
    return extraDirectories;
  }

  /**
   * Gets the java runtime version used by the application.
   *
   * @return the java version
   */
  public String getJavaRuntimeVersion() {
    return javaRuntimeVersion;
  }

  /**
   * Gets the java major runtime version as a comparable integer.
   *
   * @return the major version
   */
  public int getJavaMajorRuntimeVersion() {
    return javaMajorRuntimeVersion;
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
    Set<Artifact> artifacts;
    try {
      artifacts = new LinkedHashSet<>(ProjectDependencies.resolve(getRepositorySystem(), getMavenSession().getRepositorySession(),
                                                                  getMavenProject(), ProjectDependencies.RUNTIME_SCOPES));
    }
    catch (DependencyResolutionException ex) {
      throw new MojoExecutionException("resolving the runtime dependencies of " + getMavenProject().getId() + " failed", ex);
    }
    JLinkResolver.Result result = new JLinkResolver(this, artifacts).resolve();
    // generate image
    createImage(result);
    // and the shell scripts
    generateFiles(result);

    // this does the trick for https://issues.apache.org/jira/browse/MINSTALL-151
    getMavenProject().setArtifact(new ProjectArtifact(getMavenProject()));    // set pom.xml as the main artifact, just as pom packaging would do
    getMavenProject().setPackaging("pom");                                    // don't install pom.xml twice
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
      String classifier = StringHelper.getPlatform() + "-" + StringHelper.getArchitecture();
      if (!StringHelper.isAllWhitespace(getExtraClassifier())) {
        classifier += "-" + getExtraClassifier();
      }
      return classifier;
    }
    catch (RuntimeException ex) {
      throw new MojoExecutionException(ex.getMessage());
    }
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
   * Gets the archiver to zip the image.
   *
   * @return the archiver, never null
   */
  public ZipArchiver getZipArchiver() {
    return zipArchiver;
  }


  /**
   * Returns whether an artifact should be moved to the classpath explicitly.
   *
   * @param artifact the artifact
   * @return true if move to classpath, else module-path
   */
  public boolean isClasspathDependency(Artifact artifact) {
    if (classpathDependencies != null) {
      for (ClasspathDependency classpathDependency: classpathDependencies) {
        if (Objects.equals(classpathDependency.getGroupId(), artifact.getGroupId()) &&
            Objects.equals(classpathDependency.getArtifactId(), artifact.getArtifactId()) &&
            // the resolver's artifact has an empty classifier if none, but the configuration may be null
            Objects.equals(Objects.toString(classpathDependency.getClassifier(), ""), artifact.getClassifier())) {
          return true;
        }
      }
    }
    return false;
  }



  /**
   * Generates additional files such as shell scripts or command files.
   *
   * @param result the resolver result
   * @throws MojoExecutionException if generation failed
   */
  abstract protected void generateFiles(JLinkResolver.Result result) throws MojoExecutionException;


  /**
   * Checks the configured template names.<br>
   * Invoked before {@link #installTemplates(boolean)}, since the names are used as filenames there.
   *
   * @throws MojoExecutionException if a template name is missing
   */
  abstract protected void validateTemplates() throws MojoExecutionException;


  /**
   * Copies the templates to the template directory.
   *
   * @param overwrite true if overwrite existing templates, false if install only missing
   * @throws MojoExecutionException if installation failed
   */
  abstract protected void installTemplates(boolean overwrite) throws MojoExecutionException;



  /**
   * Invokes the jlink tool and copies other artifacts and resources.
   *
   * @param result the resolver result
   */
  protected void createImage(JLinkResolver.Result result) throws MojoFailureException, MojoExecutionException {
    // run jlink
    createJLinkImage(result);

    // copy remaining artifacts to the module-path and classpath subdirectories
    copyArtifacts(result);

    // copy resources such as backend.properties or logging config to the conf subdirectory
    copyResources(result);

    // extra directories, if any
    copyExtraDirectories();

    // isolated module layers, if any
    if (isLayered()) {
      LayerResolver layerResolver = new LayerResolver(this);
      layerResolver.copyLeafModulePath(getImageDirectory(), result);
      layerResolver.resolveAndCopyLayers(new File(getImageDirectory(), DEST_MODULEPATH));
      layerResolver.writeLayersProperties(new File(getImageDirectory(), DEST_RESOURCES));
    }
  }

  /**
   * Creates the jlink image.
   *
   * @param result the resolver info
   * @throws MojoExecutionException if building the JPMS info failed
   * @throws MojoFailureException if jlink returned an error code
   */
  protected void createJLinkImage(JLinkResolver.Result result) throws MojoExecutionException, MojoFailureException {
    getLogger().info("creating jlink image for a {}{} application with Java {}",
                     result.isPlainModular() ? "plain " : "", result.isModular() ? "modular" : "classpath", javaRuntimeVersion);
    getLogger().debug(result.toString());

    ToolRunner jlinkRunner = new ToolRunner(jlinkTool);
    if (saveOpts != null) {
      jlinkRunner.arg("--save-opts").arg(saveOpts);
    }
    jlinkRunner.arg("--output").arg(getImageDirectory());
    if (stripDebug) {
      jlinkRunner.arg("--strip-debug");
    }
    if (!StringHelper.isAllWhitespace(compress)) {
      jlinkRunner.arg("--compress=" + compress);
    }
    if (ignoreSigningInformation) {
      jlinkRunner.arg("--ignore-signing-information");
    }
    if (noHeaderFiles) {
      jlinkRunner.arg("--no-header-files");
    }
    if (noManPages) {
      jlinkRunner.arg("--no-man-pages");
    }
    if (jlinkOptions != null) {
      jlinkOptions.forEach(jlinkRunner::arg);
    }
    result.generateJlinkModulePath(jlinkRunner);
    result.generateJlinkModules(jlinkRunner);

    getLogger().debug(jlinkRunner.toString());

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
  protected void copyArtifacts(JLinkResolver.Result result) throws MojoExecutionException {
    try {
      // module path, if any
      if (result.isModular()) {
        List<ModularArtifact> modulePath = result.getModulePath();
        if (!modulePath.isEmpty()) {
          File mpDir = new File(getImageDirectory(), DEST_MODULEPATH);
          mpDir.mkdir();
          for (ModularArtifact artifact : modulePath) {
            File dest = new File(mpDir, artifact.getFileName());
            getLogger().debug("copying {} to {}", artifact.getPath(), mpDir);
            FileHelper.copy(artifact.getFile(), dest);
          }
        }
      }
      // classpath, if any
      List<Artifact> classPath = result.getClassPath();
      if (!classPath.isEmpty()) {
        File cpDir = new File(getImageDirectory(), DEST_CLASSPATH);
        cpDir.mkdir();
        for (Artifact artifact : classPath) {
          File dest = new File(cpDir, ProjectDependencies.getFile(artifact).getName());
          getLogger().debug("copying {} to {}", ProjectDependencies.getFile(artifact), cpDir);
          FileHelper.copy(ProjectDependencies.getFile(artifact), dest);
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
  protected void copyResources(JLinkResolver.Result result) throws MojoExecutionException {
    // copy resources such as backend.properties or logging config to the conf subdirectory
    if (resourcesDirectory != null && resourcesDirectory.isDirectory()) {
      int resCount = copyResources(resourcesDirectory, new File(getImageDirectory(), DEST_RESOURCES));
      if (resCount > 0) {
        result.addToClasspath(DEST_RESOURCES);
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
        for (File fromDir : getExtraDirectories()) {
          File toDir = new File(getImageDirectory(), fromDir.getName());
          FileHelper.copyDirectory(fromDir, toDir, true, null);
        }
      }
    }
    catch (IOException ex) {
      throw new MojoExecutionException("copying extra directories failed", ex);
    }
  }

  /**
   * Returns whether this goal builds an image.<br>
   * Only such goals need the <code>jdeps</code> and <code>jlink</code> tools, the templates and the main class.
   *
   * @return true if the tools, templates and main class are validated, false if only the common and layer settings
   */
  protected boolean isBuildingImage() {
    return true;
  }

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (isBuildingImage()) {
        validateImageSettings();
      }
      validateLayers();
      return true;
    }
    return false;
  }

  /**
   * Validates the tools, templates and the main class of goals building an image.
   *
   * @throws MojoExecutionException if invalid
   */
  private void validateImageSettings() throws MojoExecutionException {
    if (jdepsTool == null) {
      throw new MojoExecutionException("jdeps tool not found");
    }
    getLogger().debug("using {}", jdepsTool);

    javaRuntimeVersion = determineJavaToolVersion(jdepsTool);
    javaMajorRuntimeVersion = getMajorVersion(javaRuntimeVersion);

    if (jlinkTool == null) {
      throw new MojoExecutionException("jlink tool not found");
    }
    getLogger().debug("using {}", jlinkTool);

    if (templateDir.exists()) {
      if (!templateDir.isDirectory()) {
        throw new MojoExecutionException(templateDir.getPath() + " is not a directory");
      }
    }
    else {
      getLogger().info("template directory created: {}", templateDir.getPath());
    }
    validateTemplates();        // the names must be valid before they are used as filenames
    installTemplates(false);    // install any missing template

    if (mainClass == null) {
      throw new MojoExecutionException("mainClass missing");
    }
  }

  /**
   * Validates the {@link #layers}/{@link #leafLayer} configuration, if any.
   *
   * @throws MojoExecutionException if invalid
   */
  private void validateLayers() throws MojoExecutionException {
    if (!isLayered()) {
      return;
    }
    if (StringHelper.isAllWhitespace(getMainModule())) {
      throw new MojoExecutionException("layered applications must be modular: mainModule missing");
    }
    if (StringHelper.isAllWhitespace(leafLayer)) {
      throw new MojoExecutionException("leafLayer missing: required if layers are configured");
    }
    Set<String> names = new HashSet<>();
    for (Layer layer : getLayers()) {
      if (StringHelper.isAllWhitespace(layer.getName())) {
        throw new MojoExecutionException("a layer without a name");
      }
      if (layer.getName().equals(leafLayer)) {
        throw new MojoExecutionException("layer name '" + layer.getName() + "' collides with leafLayer");
      }
      if (!names.add(layer.getName())) {
        throw new MojoExecutionException("duplicate layer name '" + layer.getName() + "'");
      }
      if (layer.getDependencies() == null || layer.getDependencies().isEmpty()) {
        throw new MojoExecutionException("layer '" + layer.getName() + "' has no dependencies");
      }
      for (Dependency dependency : layer.getDependencies()) {
        if (StringHelper.isAllWhitespace(dependency.getGroupId()) ||
            StringHelper.isAllWhitespace(dependency.getArtifactId()) ||
            StringHelper.isAllWhitespace(dependency.getVersion())) {
          throw new MojoExecutionException("layer '" + layer.getName() +
                                           "' has a dependency without groupId, artifactId or version: " + dependency);
        }
      }
    }
  }

  /**
   * Installs the template with the given name.
   *
   * @param pluginTemplate the plugin's name of the template
   * @param projectTemplate the project's name of the template
   * @param overwrite true if overwrite the existing template, false if leave unchanged
   * @throws MojoExecutionException if installation failed
   */
  protected void installTemplate(String pluginTemplate, String projectTemplate, boolean overwrite) throws MojoExecutionException {
    templateDir.mkdirs();
    File file = new File(templateDir, projectTemplate);
    if (overwrite || !file.exists()) {
      String path = "/templates/" + pluginTemplate;
      String text = loadResourceFileIntoString(path);
      try (PrintStream ps = new PrintStream(new FileOutputStream(file))) {
        ps.print(text);
        getLogger().info("installed template {}", projectTemplate);
      }
      catch (IOException e) {
        throw new MojoExecutionException("cannot install template " + path, e);
      }
    }
  }

  /**
   * Logs the {@link ToolRunner}'s output at info level.
   *
   * @param runner the ToolRunner
   */
  protected void logToolOutput(ToolRunner runner) {
    String out = runner.getOutputAsString();
    if (!out.isEmpty()) {
      getLogger().info(out);
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

}

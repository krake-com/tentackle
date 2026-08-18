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

package org.tentackle.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.java.JavaToolchainImpl;
import org.slf4j.LoggerFactory;
import org.wurbelizer.misc.Verbosity;

import org.tentackle.common.Settings;
import org.tentackle.common.ToolRunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

/**
 * Base tentackle mojo.
 *
 * @author harald
 */
public abstract class AbstractTentackleMojo extends AbstractMojo {

  /** Name of the default JDK toolchain. */
  public static final String JDK_TOOLCHAIN = "jdk";


  /**
   * The Maven Project.
   */
  @Parameter(defaultValue = "${project}",
             readonly = true,
             required = true)
  private MavenProject mavenProject;

  /**
   * The mojo execution.
   */
  @Parameter(defaultValue = "${mojoExecution}",
             readonly = true,
             required = true)
  private MojoExecution mojoExecution;

  /**
   * The settings.<br>
   * From settings.xml.
   */
  @Parameter(defaultValue = "${settings}", readonly = true)
  private org.apache.maven.settings.Settings settings;

  @Component
  private SettingsDecrypter settingsDecrypter;

  /**
   * The current build session instance.<br>
   * Used for toolchain manager API calls.
   */
  @Parameter(defaultValue = "${session}",
      readonly = true,
      required = true)
  private MavenSession mavenSession;

  /**
   * Toolchain for invocation of external tools.<br>
   * Explicitly specifies the <code>"jdk"</code> toolchain for this plugin only.
   * Overrides the toolchain selected by the maven-toolchain-plugin, if any.
   * <p>
   * Example:
   * <pre>
   *   &lt;jdkToolchain&gt;
   *     &lt;version&gt;14&lt;/version&gt;
   *   &lt;/jdkToolchain&gt;
   * </pre>
   * To deselect the toolchain configured by the maven-toolchain-plugin:
   * <pre>
   *   &lt;jdkToolchain&gt;&lt;/jdkToolchain&gt;
   * </pre>
   */
  @Parameter
  private Map<String, String> jdkToolchain;

  /**
   * Toolchain manager to locate tools.
   */
  @Component
  private ToolchainManager toolchainManager;


  /**
   * The verbosity.<br>
   * One of "default", "info" or "debug".
   * Debug is also turned on (if not set explicitly) by Maven's global debug flag (see command line switch <code>-X</code>).
   */
  @Parameter(defaultValue = "${tentackle.verbosity}")
  protected String verbosity;

  /**
   * Skips processing.<br>
   * Defaults to true if packaging is "pom".
   */
  @Parameter
  private Boolean skip;

  /**
   * The encoding to read and write files.
   */
  @Parameter(defaultValue = "${project.build.sourceEncoding}")
  protected String charset;   // don't use the name "encoding" as maven handles this


  /**
   * The minimum logging {@link Level} to redirect java.util logging to the maven logger.
   */
  @Parameter(defaultValue = "WARNING")
  protected String minLogLevel;

  /**
   * mapped verbosity level.
   */
  protected Verbosity verbosityLevel;

  /**
   * List of resource dirs.
   */
  private List<String> resourceDirs;

  /**
   * List of test resource dirs.
   */
  private List<String> testResourceDirs;

  /**
   * The toolfinder.
   */
  private JavaToolFinder toolFinder;


  /**
   * Installs a java.util log handler that redirects to the maven logger.
   */
  public void installJavaLoggingHandler() {
    LogManager.getLogManager().reset();
    LogManager.getLogManager().getLogger("").addHandler(new MavenLogHandler(getLog(), Level.parse(minLogLevel)));
  }

  /**
   * Gets the maven project.
   *
   * @return the project, never null
   */
  public MavenProject getMavenProject() {
    return mavenProject;
  }

  /**
   * Gets the mojo execution.
   *
   * @return the execution
   */
  public MojoExecution getMojoExecution() {
    return mojoExecution;
  }

  /**
   * Gets the maven settings.
   *
   * @return the settings from settings.xml
   */
  public org.apache.maven.settings.Settings getSettings() {
    return settings;
  }

  /**
   * Decrypter for credentials in settings.xml.
   *
   * @return the settings decrypter
   */
  public SettingsDecrypter getSettingsDecrypter() {
    return settingsDecrypter;
  }

  /**
   * Gets the maven session.
   *
   * @return the session, never null
   */
  public MavenSession getMavenSession() {
    return mavenSession;
  }

  /**
   * Gets the toolchain manager.
   *
   * @return the manager, never null
   */
  public ToolchainManager getToolchainManager() {
    return toolchainManager;
  }

  /**
   * Gets the <code>"jdk"</code> toolchain.
   *
   * @return the toolchain, null if none
   * @throws MojoExecutionException if toolchain specified but cannot be loaded
   */
  public Toolchain getToolchain() throws MojoExecutionException {
    return getToolchain(JDK_TOOLCHAIN);
  }

  /**
   * Gets a toolchain for the given type and selector.
   *
   * @param type the toolchain type
   * @param selector the selector map
   * @return the toolchain, null if no match found
   * @throws MojoExecutionException if loading the toolchain failed
   */
  public Toolchain getToolchain(String type, Map<String, String> selector) throws MojoExecutionException {
    try {
      Method method = toolchainManager.getClass().getMethod("getToolchains", MavenSession.class, String.class, Map.class);
      @SuppressWarnings("unchecked")
      List<Toolchain> toolchains = (List<Toolchain>) method.invoke(toolchainManager, mavenSession, type, selector);
      return toolchains != null && !toolchains.isEmpty() ? toolchains.get(0) : null;
    }
    catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new MojoExecutionException("loading toolchain failed for type=" + type + ", selector=" + selector, e);
    }
  }

  /**
   * Gets the toolchain for a given type.
   *
   * @param type the toolchain type
   * @return the toolchain, null if none
   * @throws MojoExecutionException if toolchain specified but cannot be loaded
   */
  public Toolchain getToolchain(String type) throws MojoExecutionException {
    Toolchain toolchain;

    if (jdkToolchain != null) {
      if (jdkToolchain.isEmpty()) {
        toolchain = null;
      }
      else {
        toolchain = getToolchain(type, jdkToolchain);
        if (toolchain == null) {
          throw new MojoExecutionException("no matching '" + type + "' toolchain for: " + jdkToolchain);
        }
      }
    }
    else {
      toolchain = toolchainManager.getToolchainFromBuildContext(type, mavenSession);
    }

    if (getLog().isDebugEnabled()) {
      StringBuilder buf = new StringBuilder();
      buf.append("using ");
      if (toolchain == null) {
        buf.append("no ");
      }
      buf.append("toolchain for type ").append(type);
      if (toolchain != null) {
        buf.append(": ").append(toolchain);
      }
      getLog().debug(buf);
    }

    return toolchain;
  }

  /**
   * Gets the java home directory from a toolchain.
   *
   * @param toolchain the toolchain
   * @return the java home directory, null if not a {@link JavaToolchainImpl} or undefined by the toolchain
   * @throws MojoExecutionException if the given directory does not exist or is not a directory
   */
  public File getJavaHome(Toolchain toolchain) throws MojoExecutionException {
    if (toolchain instanceof JavaToolchainImpl javaToolchain) {
      String javaHome = javaToolchain.getJavaHome();
      if (javaHome != null && !javaHome.isBlank()) {
        File javaHomeDir = new File(javaHome);
        if (javaHomeDir.isDirectory()) {
          return javaHomeDir;
        }
        else {
          throw new MojoExecutionException("JAVA_HOME=" + javaHomeDir.getAbsolutePath() +
                                           " given by toolchain " + toolchain.getType() +
                                           " does not exist or is not a directory");
        }
      }
    }
    return null;
  }

  /**
   * Gets the hostname.
   *
   * @return the hostname
   */
  public String getHostName() {
    String hostName;
    try {
      // may fail if host is misconfigured
      hostName = InetAddress.getLocalHost().getHostName();
    }
    catch (UnknownHostException ex) {
      // return localhost
      hostName = InetAddress.getLoopbackAddress().getHostAddress();
    }
    return hostName;
  }

  @Override
  public final void execute() throws MojoExecutionException, MojoFailureException {
    installJavaLoggingHandler();
    prepareExecute();
    if (validate()) {
      executeImpl();
      finishExecute();
    }
  }

  /**
   * Prepares the execution.<br>
   * Invoked before validate().
   *
   * @throws MojoExecutionException if an unexpected problem occurs.
   * @throws MojoFailureException if an expected problem occurs.
   */
  public void prepareExecute() throws MojoExecutionException, MojoFailureException {
    // default does nothing
  }

  /**
   * Implements the execution.<br>
   * Tentackle mojos must override this method instead of {@link #execute()}. This method
   * is only invoked if validation succeeds.
   *
   * @throws MojoExecutionException if an unexpected problem occurs
   * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs
   * @see #prepareExecute()
   */
  public abstract void executeImpl() throws MojoExecutionException, MojoFailureException;

  /**
   * Finishes the execution.<br>
   * The method is invoked after {@link #executeImpl()}.
   *
   * @throws MojoExecutionException if an unexpected problem occurs.
   * @throws MojoFailureException if an expected problem occurs.
   */
  public void finishExecute() throws MojoExecutionException, MojoFailureException {
    // default does nothing
  }

  /**
   * Determines the encoding charset.
   */
  public void determineEncoding() {
    if (charset != null) {
      Charset cs = Charset.forName(charset);
      Settings.setEncodingCharset(cs);
      org.wurbelizer.misc.Settings.setEncodingCharset(cs);
    }
  }

  /**
   * sets the verbosity.
   */
  public void determineVerbosity() {
    if (verbosity != null) {
      try {
        verbosityLevel = Verbosity.valueOf(verbosity.toUpperCase(Locale.ROOT));
      }
      catch (RuntimeException ex) {
        verbosityLevel = Verbosity.DEFAULT;
      }
    }
    else  {
      // use maven's global setting
      verbosityLevel = getLog().isDebugEnabled() ? Verbosity.DEBUG : Verbosity.DEFAULT;
    }
  }

  /**
   * Gets the path relative to the basedir.<br>
   * Parent dirs of the basedir will also be tried.
   *
   * @param path the absolute path
   * @return the shortened path
   */
  public String getPathRelativeToBasedir(String path) {
    String basePath = mavenProject.getBasedir().getAbsolutePath();
    while (!basePath.isEmpty()) {
      if (path.startsWith(basePath)) {
        int len = basePath.lastIndexOf(File.separatorChar);
        if (len > 0) {
          len++;    // include the last dir of the basepath
        }
        else  {
          len = basePath.length() + 1;
        }
        return path.substring(len);
      }
      // continue with the parent dir
      int ndx = basePath.lastIndexOf(File.separatorChar);
      if (ndx > 0) {
        basePath = basePath.substring(0, ndx);
      }
      else  {
        break;
      }
    }
    return path;
  }

  /**
   * Gets the names of all resource directories.
   *
   * @param test true if load test resource directories
   * @return the names provided by the maven project model, never null
   */
  public List<String> getResourceDirs(boolean test) {
    if (test && testResourceDirs == null || !test && resourceDirs == null) {
      List<String> dirs = new ArrayList<>();
      List<Resource> resources = test ? mavenProject.getBuild().getTestResources() : mavenProject.getBuild().getResources();
      for (Resource resource : resources) {
        if (resource != null) {
          String name = resource.getDirectory();
          if (name != null) {
            getLog().debug("found resource directory " + name);
            dirs.add(name);
          }
        }
      }
      if (test) {
        testResourceDirs = dirs;
      }
      else {
        resourceDirs = dirs;
      }
    }
    return test ? testResourceDirs : resourceDirs;
  }

  /**
   * Checks if the given file resides in a resource directory.
   *
   * @param file the file
   * @param test true if load test resource directories
   * @return the resource dir, null if not a resource
   * @throws MojoExecutionException if calculating a canonical pathname failed
   */
  public File getResourceDir(File file, boolean test) throws MojoExecutionException {
    String filePath = getCanonicalPath(file);
    for (String resourceDirName: getResourceDirs(test)) {
      File resourceDir = new File(resourceDirName);
      String resourceDirPath = getCanonicalPath(resourceDir);
      if (filePath.startsWith(resourceDirPath)) {
        return resourceDir;
      }
    }
    return null;
  }

  /**
   * Gets the canonical path of the diven directory.
   *
   * @param dir the directory
   * @return the path
   * @throws MojoExecutionException if failed
   */
  public String getCanonicalPath(File dir) throws MojoExecutionException {
    try {
      return dir.getCanonicalPath();
    }
    catch (IOException ex) {
      throw new MojoExecutionException("cannot determine canonical path of " + dir, ex);
    }
  }

  /**
   * Gets the default toolfinder to locate tools like java, jdeps, jlink, etc.
   *
   * @return the toolfinder, never null
   * @throws MojoExecutionException if the toolchain cannot be loaded
   */
  public JavaToolFinder getToolFinder() throws MojoExecutionException {
    if (toolFinder == null) {
      toolFinder = new JavaToolFinder(getToolchain());
    }
    return toolFinder;
  }

  /**
   * Determines the version number string of a given java tool.<br>
   * Invokes the tool with <code>--version</code>.<br>
   * Skips noise like <code>WARNING: Using incubator modules</code>.
   *
   * @param javaTool the java tool
   * @return the version string
   * @throws MojoExecutionException if the tool's output does not contain a java version
   */
  public String determineJavaToolVersion(File javaTool) throws MojoExecutionException {
    String output = new ToolRunner(javaTool).arg("--version").run().getOutputAsString();
    StringTokenizer stok = new StringTokenizer(output);
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      if (Character.isDigit(token.charAt(0)) &&
          (token.length() == 2 || (token.length() > 2 && (token.charAt(2) == '.' || token.charAt(2) == '-')))) {
        // 14, 14.0.2, 17-ea, etc... but not 100, 8, 7.3
        // this should work up to java 99 ;)
        return token;
      }
    }
    throw new MojoExecutionException("cannot determine version string from tool's output:\n" + output);
  }

  /**
   * Determines the major version number integer of a given version string.
   *
   * @param version the version string
   * @return the version number
   */
  public int getMajorVersion(String version) {
    int ndx = version.indexOf('.');
    if (ndx < 0) {
      ndx = version.indexOf('-');   // 17-ea?
    }
    return Integer.parseInt(ndx >= 0 ? version.substring(0, ndx) : version);
  }

  /**
   * Loads a resource file into a string.
   *
   * @param path the path to the resource file
   * @return the contents
   * @throws MojoExecutionException if no such resource
   */
  public String loadResourceFileIntoString(String path) throws MojoExecutionException {
    InputStream inputStream = getClass().getResourceAsStream(path);
    if (inputStream == null) {
      throw new MojoExecutionException("no such resource: " + path);
    }
    BufferedReader buffer = new BufferedReader(new InputStreamReader(inputStream));
    return buffer.lines().collect(Collectors.joining(System.lineSeparator()));
  }

  /**
   * Creates a descriptor name from an artifact file.
   *
   * @param file the artifact file
   * @return the descriptor name
   */
  public String toDescriptorName(File file) {
    String name = file.getName();
    int ndx = name.lastIndexOf('.');
    if (ndx > 0 && ndx < name.length() - 1 && !Character.isDigit(name.charAt(ndx + 1))) {
      name = name.substring(0, ndx); // cut extension .jar
    }

    StringBuilder buf = new StringBuilder();
    StringTokenizer stok = new StringTokenizer(name, "-");
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      if (!token.isBlank() &&                           // skip "- -" and other pathological tokens
          !"SNAPSHOT".equals(token)) {                  // skip "-SNAPSHOT"
        if (!buf.isEmpty()) {
          if (Character.isDigit(token.charAt(0))) {     // stop at "<version>", rest is classifier for platform, architecture etc...
            break;
          }
          buf.append('-');
        }
        buf.append(token);
      }
    }

    name = buf.toString();
    if (name.startsWith(".")) {
      // pathological case ".hidden"
      name = name.substring(1);
    }

    return name;
  }

  /**
   * Creates a fileset manager.
   *
   * @return the fileset manager.
   */
  public FileSetManager createFileSetManager(boolean verbose) {
    return new FileSetManager(LoggerFactory.getLogger(FileSetManager.class), verbose);
  }

  /**
   * Gets the filenames included by the given fileset.
   *
   * @param fileSet the fileset
   * @return the filenames
   */
  public String[] getIncludedFiles(FileSet fileSet) {
    return createFileSetManager(verbosityLevel.isDebug()).getIncludedFiles(fileSet);
  }


  /**
   * Returns whether reactor execution is recursive.
   *
   * @return true if recursive, false if non-recursive (--non-recursive or -N option set, or plugin is an aggregator)
   */
  protected boolean isExecutionRecursive() {
    return mavenSession.getRequest().isRecursive() && !mojoExecution.getMojoDescriptor().isAggregator();
  }

  /**
   * Determines whether mojo should be skipped by default.
   *
   * @return true if mojo should be skipped by default
   */
  protected boolean isSkippedByDefault() {
    return "pom".equals(mavenProject.getPackaging()) && isExecutionRecursive();
  }

  /**
   * Checks the configuration.<br>
   *
   * @return true if continue with execution, false to skip
   * @throws MojoExecutionException if validation failed
   */
  protected boolean validate() throws MojoExecutionException {
    determineEncoding();
    determineVerbosity();
    if (mavenProject.getBasedir() == null) {
      throw new MojoExecutionException("missing project.baseDir");
    }
    if (skip == null && isSkippedByDefault()) {
      skip = true;    // nothing to do here
    }
    if (Boolean.TRUE.equals(skip)) {
      getLog().info("skipped");
      return false;
    }
    return true;
  }

  /**
   * Creates a map of package names to package infos.
   *
   * @param resourceRoots true if collect all resource roots, else all source roots
   * @return the packages
   * @throws MojoExecutionException if split package detected
   */
  protected Map<String, PackageInfo> createPackageMap(boolean resourceRoots) throws MojoExecutionException {
    Map<String, PackageInfo> map = new HashMap<>();
    StringBuilder buf = new StringBuilder();
    List<MavenProject> projects;
    if (isExecutionRecursive()) {
      projects = new ArrayList<>();
      projects.add(mavenProject);
    }
    else {
      projects = mavenProject.getCollectedProjects();
    }
    for (MavenProject project : projects) {
      List<String> paths;
      if (resourceRoots) {
        paths = project.getResources().stream().filter(resource -> !resource.isFiltering()).map(resource -> resource.getDirectory()).toList();
      }
      else {
        paths = project.getCompileSourceRoots();
      }
      for (String path : paths) {
        for (PackageInfo info : getPackages(project, path)) {
          boolean infoContainingFiles = info.isContainingFiles();
          PackageInfo splitPkg = map.get(info.getName());
          if (splitPkg != null) {
            if (splitPkg.isContainingFiles()) {
              if (infoContainingFiles) {
                buf.append("\nsplit package detected: ").append(info.getName()).
                    append(" in ").append(project.getName()).
                       append(" and ").append(splitPkg.getProject().getName());
              }
              // the other package is empty: keep existing with java files
              continue;
            }
            else if (!infoContainingFiles) {
              // more than one empty package found: add it to the suspects until we know whether a non-empty exists
              List<PackageInfo> dups = splitPkg.getEmptyDuplicates();
              if (dups == null) {
                dups = new ArrayList<>();
                splitPkg.setEmptyDuplicates(dups);
              }
              dups.add(info);
              continue;
            }
            // else: split is empty and info contains java files -> replace empty
          }
          // add new or replace empty package
          map.put(info.getName(), info);
        }
      }
    }

    if (!buf.isEmpty()) {
      // at least one split package found.
      // this isn't okay whether used in profiles or not.
      buf.deleteCharAt(0);    // remove leading newline
      throw new MojoExecutionException(buf.toString());
    }

    return map;
  }


  private List<PackageInfo> getPackages(MavenProject project, String dirName) {
    List<PackageInfo> packages = new ArrayList<>();
    checkDir(project, new File(dirName), packages, "");
    return packages;
  }

  private void checkDir(MavenProject project, File dir, List<PackageInfo> packages, String packageName) {
    if (dir.isDirectory()) {
      boolean containsFiles = false;
      boolean empty = true;
      File[] files = dir.listFiles();
      if (files != null) {
        for (File file : files) {
          if (!PackageInfo.isHidden(file)) {
            empty = false;
            if (file.isDirectory()) {
              checkDir(project, file, packages, packageName.isEmpty() ? file.getName() : packageName + "." + file.getName());
            }
            else if (file.isFile()) {
              containsFiles = true;
            }
          }
        }
      }
      if ((empty || containsFiles) && !packageName.isEmpty()) {
        packages.add(new PackageInfo(packageName, project, dir));
      }
    }
  }
}

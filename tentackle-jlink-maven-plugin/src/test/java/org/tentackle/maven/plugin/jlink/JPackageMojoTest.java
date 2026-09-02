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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJPackageMojo;

import java.io.File;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the configuration of the {@code jpackage} goal and the patching of the config files the
 * jpackage tool generates.
 * <p>
 * The patching is what makes a jpackage installer work at all for a Tentackle application: the
 * jpackage tool derives its classpath from the packages it finds in the jars, which is neither the
 * maven dependency order nor aware of the artifacts living in the runtime image.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class JPackageMojoTest {

  /** the config file jpackage generates for a modular application (Java >= 15). */
  private static final String MODULAR_CONFIG = """
      [Application]
      app.mainmodule=com.example.app/com.example.Main

      [JavaOptions]
      java-options=-DROOTDIR=$ROOTDIR
      java-options=$APPDIR/mods
      """;

  /** the config file jpackage generates for a classpath application (Java >= 15). */
  private static final String CLASSPATH_CONFIG = """
      [Application]
      app.mainjar=app-1.0.jar
      app.mainclass=com.example.Main
      app.classpath=$APPDIR/app-1.0.jar
      app.classpath=$APPDIR/lib-1.0.jar

      [JavaOptions]
      java-options=-DROOTDIR=$ROOTDIR
      """;

  /** the runtime image prefix jpackage uses on linux since Java 15. */
  private static final String LINUX_PREFIX = "$APPDIR/../runtime";

  private File tempDir;
  private File templateDir;
  private TestJPackageMojo mojo;
  private RecordingLog log;
  private String savedOsName;


  @BeforeMethod
  public void setUp() {
    JLinkTestSupport.clearJdepsCache();
    savedOsName = System.getProperty("os.name");
    tempDir = JLinkTestSupport.createTempDir("jpackagegoal");
    templateDir = new File(tempDir, "templates");
    log = new RecordingLog();

    mojo = new TestJPackageMojo();
    mojo.setLog(log);
    JLinkTestSupport.setField(mojo, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(mojo, "mainClass", "com.example.Main");
    JLinkTestSupport.setField(mojo, "templateDir", templateDir);
    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(mojo, "jlinkTool", new File("jlink"));
    JLinkTestSupport.setField(mojo, "jpackageTool", new File("jpackage"));
    // the @Parameter defaults are applied by maven, not by the mojo (see PluginDescriptorTest)
    JLinkTestSupport.setField(mojo, "packageImageTemplate", JPackageMojo.PACKAGE_IMAGE_TEMPLATE);
    JLinkTestSupport.setField(mojo, "packageInstallerTemplate", JPackageMojo.PACKAGE_INSTALLER_TEMPLATE);
    JLinkTestSupport.setField(mojo, "packageUpdateTemplate", JPackageMojo.PACKAGE_UPDATE_TEMPLATE);
  }

  @AfterMethod
  public void tearDown() {
    System.setProperty("os.name", savedOsName);
    JLinkTestSupport.deleteRecursively(tempDir);
  }


  /**
   * Validating the goal installs the two option templates, but not the updater's.
   */
  @Test
  public void validationInstallsTheOptionTemplates() throws Exception {
    assertTrue(JLinkTestSupport.callValidate(mojo));

    assertEquals(JLinkTestSupport.fileNames(templateDir),
                 List.of(JPackageMojo.PACKAGE_IMAGE_TEMPLATE, JPackageMojo.PACKAGE_INSTALLER_TEMPLATE));
    assertNull(mojo.getPackageUpdateTemplate());
  }

  /**
   * The update script is generated only for applications using the auto-update feature.
   */
  @Test
  public void theUpdateTemplateIsInstalledOnlyWithTheUpdater() throws Exception {
    JLinkTestSupport.setField(mojo, "withUpdater", true);

    assertEquals(mojo.getPackageUpdateTemplate(), JPackageMojo.PACKAGE_UPDATE_TEMPLATE);
    assertTrue(JLinkTestSupport.callValidate(mojo));
    assertEquals(JLinkTestSupport.fileNames(templateDir),
                 List.of(JPackageMojo.PACKAGE_IMAGE_TEMPLATE, JPackageMojo.PACKAGE_INSTALLER_TEMPLATE,
                         JPackageMojo.PACKAGE_UPDATE_TEMPLATE));
  }

  /**
   * Every template the goal needs must be named.<br>
   * The names are checked before the templates are installed, or a missing name would surface as
   * a {@code NullPointerException} from the file handling instead.
   */
  @Test
  public void missingTemplateNamesFailTheBuild() {
    JLinkTestSupport.setField(mojo, "packageImageTemplate", null);
    assertEquals(expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo)).getMessage(),
                 "packageImageTemplate missing");

    JLinkTestSupport.setField(mojo, "packageImageTemplate", " ");
    assertEquals(expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo)).getMessage(),
                 "packageImageTemplate missing");

    JLinkTestSupport.setField(mojo, "packageImageTemplate", JPackageMojo.PACKAGE_IMAGE_TEMPLATE);
    JLinkTestSupport.setField(mojo, "packageInstallerTemplate", null);
    assertEquals(expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo)).getMessage(),
                 "packageInstallerTemplate missing");

    assertEquals(JLinkTestSupport.fileNames(templateDir), List.of(),
                 "nothing may be installed before the names are known to be valid");
  }

  /**
   * An application using the auto-update feature needs a name for its update script.
   */
  @Test
  public void aBlankUpdateTemplateFailsTheBuild() throws Exception {
    JLinkTestSupport.setField(mojo, "packageUpdateTemplate", " ");

    assertTrue(JLinkTestSupport.callValidate(mojo), "without the updater the name is not needed");

    JLinkTestSupport.setField(mojo, "withUpdater", true);
    assertEquals(expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo)).getMessage(),
                 "packageUpdateTemplate missing");
  }

  /**
   * A jpackage tool from another JDK than jlink is legal, but worth a warning.
   */
  @Test
  public void differingToolVersionsAreWarnedAbout() throws Exception {
    TestJPackageMojo olderJPackage = new TestJPackageMojo() {
      @Override
      public String determineJavaToolVersion(File javaTool) {
        return javaTool.getName().startsWith("jpackage") ? "21" : "25";
      }
    };
    olderJPackage.setLog(log);
    JLinkTestSupport.setField(olderJPackage, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(olderJPackage, "mainClass", "com.example.Main");
    JLinkTestSupport.setField(olderJPackage, "templateDir", templateDir);
    JLinkTestSupport.setField(olderJPackage, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(olderJPackage, "jlinkTool", new File("jlink"));
    JLinkTestSupport.setField(olderJPackage, "jpackageTool", new File("jpackage"));
    JLinkTestSupport.setField(olderJPackage, "packageImageTemplate", JPackageMojo.PACKAGE_IMAGE_TEMPLATE);
    JLinkTestSupport.setField(olderJPackage, "packageInstallerTemplate", JPackageMojo.PACKAGE_INSTALLER_TEMPLATE);

    assertTrue(JLinkTestSupport.callValidate(olderJPackage));

    assertTrue(RecordingLog.contains(log.getWarnMessages(),
                                     "jpackage tool's major version 21 differs from jlink/jdeps version 25"));
  }


  /**
   * The path to the runtime image within the installed application is platform specific.
   *
   * @return the os name, the jpackage major version and the expected prefix
   */
  @DataProvider(name = "platforms")
  public Object[][] platforms() {
    return new Object[][] {
      { "Windows 11", 25, "$ROOTDIR/runtime" },
      { "Mac OS X", 25, "$ROOTDIR/runtime/Contents/Home" },
      { "Linux", 25, "$APPDIR/../runtime" },
      { "Linux", 14, "$ROOTDIR/lib/runtime" }
    };
  }

  @Test(dataProvider = "platforms")
  public void imagePathPrefixIsPlatformSpecific(String osName, int jpackageVersion, String expected) {
    System.setProperty("os.name", osName);
    TestJPackageMojo platformMojo = new TestJPackageMojo();
    JLinkTestSupport.setField(platformMojo, "jpackageMajorRuntimeVersion", jpackageVersion);

    assertEquals(platformMojo.getImagePathPrefix(), expected);
    assertEquals(platformMojo.getImagePathPrefix(), expected, "must be cached, not recomputed");
  }


  /**
   * For a modular application the module path replaces the {@code mods} directory jpackage put
   * into the java options.
   */
  @Test
  public void modularConfigFileGetsTheModulePath() throws Exception {
    File configFile = JLinkTestSupport.writeFile(tempDir, "app.cfg", MODULAR_CONFIG);
    JLinkTestSupport.setField(mojo, "jpackageMajorRuntimeVersion", 25);

    patchConfigFile(configFile, result(true, List.of("mp/app-1.0.jar"), List.of()), null);

    String patched = JLinkTestSupport.readString(configFile);
    assertTrue(patched.contains("java-options=mp/app-1.0.jar"), patched);
    assertTrue(!patched.contains("$APPDIR/mods"), patched);
  }

  /**
   * For a modular application with resources the classpath is inserted, because jpackage does not
   * write an {@code app.classpath} line for a modular application at all.
   */
  @Test
  public void modularConfigFileGetsTheClassPathInserted() throws Exception {
    File configFile = JLinkTestSupport.writeFile(tempDir, "app.cfg", MODULAR_CONFIG);
    JLinkTestSupport.setField(mojo, "jpackageMajorRuntimeVersion", 25);
    System.setProperty("os.name", "Linux");

    patchConfigFile(configFile, result(true, List.of("mp/app-1.0.jar"), List.of("conf")), null);

    String patched = JLinkTestSupport.readString(configFile);
    assertTrue(patched.contains("app.classpath=" + LINUX_PREFIX + "/conf"), patched);
    assertTrue(patched.indexOf("app.classpath=") < patched.indexOf("[JavaOptions]"),
               "the classpath belongs into the application section: " + patched);
  }

  /**
   * For a classpath application the main jar and the classpath are redirected into the runtime
   * image, and the classpath order becomes the maven dependency order.
   */
  @Test
  public void classpathConfigFileIsRedirectedIntoTheImage() throws Exception {
    File configFile = JLinkTestSupport.writeFile(tempDir, "app.cfg", CLASSPATH_CONFIG);
    JLinkTestSupport.setField(mojo, "jpackageMajorRuntimeVersion", 25);
    System.setProperty("os.name", "Linux");

    patchConfigFile(configFile, result(false, List.of(), List.of("conf")), "app-1.0.jar");

    String patched = JLinkTestSupport.readString(configFile);
    assertTrue(patched.contains("app.mainjar=" + LINUX_PREFIX + "/cp/app-1.0.jar"), patched);
    assertTrue(patched.contains("app.classpath=" + LINUX_PREFIX + "/conf"), patched);
    assertEquals(patched.lines().filter(line -> line.startsWith("app.classpath=")).count(), 1,
                 "the second app.classpath line must be dropped: " + patched);
  }

  /**
   * A config file without the expected entries means jpackage changed its format: better to fail
   * than to ship an installer that cannot start.
   */
  @Test
  public void unpatchableConfigFileFailsTheBuild() throws Exception {
    File configFile = JLinkTestSupport.writeFile(tempDir, "app.cfg", "[Application]\napp.mainmodule=com.example.app\n");
    JLinkTestSupport.setField(mojo, "jpackageMajorRuntimeVersion", 25);
    System.setProperty("os.name", "Linux");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () ->
        patchConfigFile(configFile, result(true, List.of(), List.of("conf")), null));

    assertEquals(ex.getMessage(), "could not patch classpath");
  }

  /**
   * The {@code app} directory sits at a different depth on each platform.
   */
  @Test
  public void theAppDirectoryIsFoundOnEveryPlatform() throws Exception {
    File windows = JLinkTestSupport.createDir(tempDir, "windows");
    File windowsApp = JLinkTestSupport.createDir(windows, "app");
    assertEquals(locateAppDirectory(windows), windowsApp);

    File linux = JLinkTestSupport.createDir(tempDir, "linux");
    File linuxApp = JLinkTestSupport.createDir(new File(linux, "lib"), "app");
    assertEquals(locateAppDirectory(linux), linuxApp);

    File mac = JLinkTestSupport.createDir(tempDir, "mac");
    File macApp = JLinkTestSupport.createDir(new File(mac, "Contents"), "app");
    assertEquals(locateAppDirectory(mac), macApp);

    assertNull(locateAppDirectory(JLinkTestSupport.createDir(tempDir, "empty")));
  }

  /**
   * By default the main jar is the first artifact of the dependency tree, which is the project's
   * own artifact.
   */
  @Test
  public void mainJarDefaultsToTheFirstClasspathArtifact() throws Exception {
    JLinkResolver.Result result = classPathResult("app-1.0.jar", "lib-1.0.jar");

    assertEquals(determineMainJar(result), "app-1.0.jar");
  }

  /**
   * An explicit main jar is matched as a substring, so the version need not be spelled out.
   */
  @Test
  public void mainJarCanBeSelectedBySubstring() throws Exception {
    JLinkResolver.Result result = classPathResult("app-1.0.jar", "myapp-server-1.0.jar");
    JLinkTestSupport.setField(mojo, "mainJar", "myapp-server");

    assertEquals(determineMainJar(result), "myapp-server-1.0.jar");
  }

  /**
   * A main jar that matches nothing is a configuration error.
   */
  @Test
  public void unknownMainJarFailsTheBuild() {
    JLinkTestSupport.setField(mojo, "mainJar", "nosuch");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                            () -> determineMainJar(classPathResult("app-1.0.jar")));

    assertEquals(ex.getMessage(), "cannot determine main jar");
  }


  // ---------------- fixture ----------------

  private void patchConfigFile(File configFile, JLinkResolver.Result result, String mainJarName) throws Exception {
    JLinkTestSupport.invoke(mojo, "patchConfigFile",
                            new Class<?>[] { File.class, JLinkResolver.Result.class, String.class },
                            configFile, result, mainJarName);
  }

  private File locateAppDirectory(File appImageDir) throws Exception {
    return JLinkTestSupport.invoke(mojo, "locateAppDirectory", new Class<?>[] { File.class }, appImageDir);
  }

  private String determineMainJar(JLinkResolver.Result result) throws Exception {
    return JLinkTestSupport.invoke(mojo, "determineMainJar", new Class<?>[] { JLinkResolver.Result.class }, result);
  }

  /**
   * Creates a resolver result with canned paths.
   *
   * @param modular true for a modular application
   * @param modulePathEntries the module path entries of the generated launcher
   * @param classPathEntries the extra class path entries of the generated launcher
   * @return the result
   * @throws Exception if the result could not be filled in
   */
  private JLinkResolver.Result result(boolean modular, List<String> modulePathEntries,
                                      List<String> classPathEntries) throws Exception {
    JLinkResolver.Result result = new JLinkResolver(mojo, java.util.Set.of()).new Result() {
      @Override
      public String generateModulePath() {
        return String.join(File.pathSeparator, modulePathEntries);
      }

      @Override
      public boolean isModulePathRequired() {
        return !modulePathEntries.isEmpty();
      }
    };
    JLinkTestSupport.invoke(result, "setModular", new Class<?>[] { boolean.class }, modular);
    classPathEntries.forEach(result::addToClasspath);
    return result;
  }

  /**
   * Creates a classpath result for the given jar names.
   *
   * @param jarNames the names of the jar files
   * @return the result
   */
  private JLinkResolver.Result classPathResult(String... jarNames) {
    JLinkResolver.Result result = new JLinkResolver(mojo, java.util.Set.of()).new Result();
    for (String jarName : jarNames) {
      Artifact artifact = JLinkTestSupport.createArtifact(jarName.substring(0, jarName.indexOf("-1.0.jar")),
                                                          new File(tempDir, jarName));
      try {
        JLinkTestSupport.invoke(result, "addToClassPath", new Class<?>[] { Artifact.class }, artifact);
      }
      catch (Exception ex) {
        throw new IllegalStateException("cannot fill in the class path", ex);
      }
    }
    return result;
  }

}

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
import org.eclipse.aether.artifact.Artifact;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.ToolRunner;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.FakeLocationManager;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJLinkMojo;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the command lines and paths the resolver result generates.
 * <p>
 * These strings end up verbatim in the jlink command line, in the generated launch scripts and in
 * the patched jpackage config files, so they are part of what the plugin produces.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class ResolverResultTest {

  /** Path separator of the platform the test runs on ({@code :} or {@code ;}). */
  private static final char SEP = File.pathSeparatorChar;

  private File tempDir;
  private File fakeTool;
  private FakeLocationManager locationManager;
  private TestJLinkMojo mojo;
  private Set<Artifact> artifacts;
  private String imagePathPrefix;


  @BeforeMethod
  public void setUp() {
    JLinkTestSupport.clearJdepsCache();
    if (tempDir == null) {
      tempDir = JLinkTestSupport.createTempDir("jlinkresult");
      // the tool runner insists on an existing executable, but the tool is never run here
      fakeTool = JLinkTestSupport.writeFile(tempDir, "jlink", "#!/bin/sh\n");
      fakeTool.setExecutable(true);
    }
    locationManager = new FakeLocationManager();
    artifacts = new LinkedHashSet<>();
    imagePathPrefix = "";

    mojo = new TestJLinkMojo() {
      @Override
      public String getImagePathPrefix() {
        return imagePathPrefix;
      }
    };
    mojo.setLogger(new RecordingLog());
    JLinkTestSupport.setField(mojo, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(mojo, "locationManager", locationManager);
    JLinkTestSupport.setField(mojo, "mainClass", "com.example.Main");
  }

  @AfterClass
  public void tearDown() {
    JLinkTestSupport.clearJdepsCache();
    JLinkTestSupport.deleteRecursively(tempDir);
  }


  /**
   * The jlink module path lists the artifact files, the modules are added by name.
   */
  @Test
  public void jlinkOptionsListTheModulesToLink() throws Exception {
    module("app", "com.example.app", "org.example.lib", "java.sql");
    module("lib", "org.example.lib");
    mainModule("com.example.app");

    ToolRunner runner = new ToolRunner(fakeTool);
    JLinkResolver.Result result = resolve();
    result.generateJlinkModulePath(runner);
    result.generateJlinkModules(runner);

    assertEquals(args(runner),
                 List.of("--module-path",
                         new File(tempDir, "app-1.0.jar").getPath() + SEP + new File(tempDir, "lib-1.0.jar").getPath(),
                         "--add-modules",
                         "com.example.app,org.example.lib"));
  }

  /**
   * Runtime modules are linked by name only, without a module path.
   */
  @Test
  public void jlinkOptionsOfARuntimeOnlyImageHaveNoModulePath() throws Exception {
    module("app", "com.example.app", "java.sql");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "modulePathOnly", List.of("all"));

    ToolRunner runner = new ToolRunner(fakeTool);
    JLinkResolver.Result result = resolve();
    result.generateJlinkModulePath(runner);
    result.generateJlinkModules(runner);

    assertEquals(args(runner), List.of("--add-modules", "java.sql"));
  }

  /**
   * A jlink image without any modules at all must not generate empty options.
   */
  @Test
  public void jlinkOptionsAreOmittedIfThereIsNothingToAdd() throws Exception {
    automatic("legacy", "org.example.legacy");

    ToolRunner runner = new ToolRunner(fakeTool);
    JLinkResolver.Result result = resolve();
    result.generateJlinkModulePath(runner);
    result.generateJlinkModules(runner);

    assertTrue(args(runner).isEmpty(), "got " + args(runner));
  }

  /**
   * The module path of the launch script points into the image's {@code mp} folder.
   */
  @Test
  public void modulePathPointsIntoTheImage() throws Exception {
    module("app", "com.example.app", "org.example.legacy");
    automatic("legacy", "org.example.legacy");
    mainModule("com.example.app");

    JLinkResolver.Result result = resolve();

    assertEquals(result.generateModulePath(), "mp/legacy-1.0.jar" + SEP + "mp/app-1.0.jar");
  }

  /**
   * For a jpackage image the paths are prefixed with the platform specific runtime directory.
   */
  @Test
  public void modulePathIsPrefixedForAPackageImage() throws Exception {
    module("app", "com.example.app", "org.example.legacy");
    automatic("legacy", "org.example.legacy");
    mainModule("com.example.app");
    imagePathPrefix = "$ROOTDIR/runtime";    // no trailing slash: the result adds it

    JLinkResolver.Result result = resolve();

    assertEquals(result.generateModulePath(),
                 "$ROOTDIR/runtime/mp/legacy-1.0.jar" + SEP + "$ROOTDIR/runtime/mp/app-1.0.jar");
  }

  /**
   * A classpath application has no module path, even though the artifacts were copied.
   */
  @Test
  public void classpathApplicationHasNoModulePath() throws Exception {
    module("app", "com.example.app");

    JLinkResolver.Result result = resolve();

    assertFalse(result.isModulePathRequired());
    assertEquals(result.generateModulePath(), "");
  }

  /**
   * The extra elements come first, the artifacts follow in dependency order.
   */
  @Test
  public void classPathKeepsTheExtraElementsInFront() throws Exception {
    module("app", "com.example.app");
    module("lib", "org.example.lib");

    JLinkResolver.Result result = resolve();
    result.addToClasspath(AbstractJLinkMojo.DEST_RESOURCES);

    assertEquals(result.generateClassPath(),
                 "conf" + SEP + "cp/app-1.0.jar" + SEP + "cp/lib-1.0.jar");
  }

  /**
   * A classpath element is added only once, no matter how often it is requested.
   */
  @Test
  public void classPathElementsAreNotDuplicated() throws Exception {
    module("app", "com.example.app");

    JLinkResolver.Result result = resolve();
    result.addToClasspath(AbstractJLinkMojo.DEST_RESOURCES);
    result.addToClasspath(AbstractJLinkMojo.DEST_RESOURCES);

    assertEquals(result.getExtraClassPathElements(), List.of("conf"));
  }

  /**
   * The image directory itself is expressed as a dot, which must not become {@code <prefix>/.}
   * in a prefixed image.
   */
  @Test
  public void dotClassPathElementBecomesTheImageDirectory() throws Exception {
    module("app", "com.example.app");
    imagePathPrefix = "$APPDIR/../runtime";

    JLinkResolver.Result result = resolve();
    result.addToClasspath(".");

    assertEquals(result.generateClassPath(),
                 "$APPDIR/../runtime" + SEP + "$APPDIR/../runtime/cp/app-1.0.jar");
  }

  /**
   * Without a prefix the dot stays a dot.
   */
  @Test
  public void dotClassPathElementStaysADotWithoutPrefix() throws Exception {
    module("app", "com.example.app");

    JLinkResolver.Result result = resolve();
    result.addToClasspath(".");

    assertEquals(result.generateClassPath(), "." + SEP + "cp/app-1.0.jar");
  }

  /**
   * The description in the build log names what went where.
   */
  @Test
  public void toStringDescribesTheStrategy() throws Exception {
    module("app", "com.example.app", "org.example.legacy", "java.sql");
    automatic("legacy", "org.example.legacy");
    mainModule("com.example.app");

    String description = resolve().toString();

    assertTrue(description.contains("runtime modules passed to jlink: java.sql"), description);
    assertTrue(description.contains("module-path passed to bin/java: org.example.legacy, com.example.app"), description);
  }


  // ---------------- fixture ----------------

  private JLinkResolver.Result resolve() throws MojoExecutionException {
    return new JLinkResolver(mojo, artifacts).resolve();
  }

  private void mainModule(String moduleName) {
    JLinkTestSupport.setField(mojo, "mainModule", moduleName);
  }

  private void module(String artifactId, String moduleName, String... requires) {
    Artifact artifact = JLinkTestSupport.createArtifact(artifactId, new File(tempDir, artifactId + "-1.0.jar"));
    locationManager.addModule(artifact, moduleName, requires);
    artifacts.add(artifact);
  }

  private void automatic(String artifactId, String moduleName) {
    Artifact artifact = JLinkTestSupport.createArtifact(artifactId, new File(tempDir, artifactId + "-1.0.jar"));
    locationManager.addAutomaticModule(artifact, moduleName);
    JLinkTestSupport.seedJdepsCache(artifact);
    artifacts.add(artifact);
  }

  /**
   * Gets the arguments of a tool runner without the tool itself.
   *
   * @param runner the tool runner
   * @return the arguments
   */
  private List<String> args(ToolRunner runner) {
    List<String> command = JLinkTestSupport.getField(runner, "command");
    return command.subList(1, command.size());
  }

}

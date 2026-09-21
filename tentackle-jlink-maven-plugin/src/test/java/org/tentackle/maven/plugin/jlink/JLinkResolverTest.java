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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.jlink.JLinkTestSupport.FakeLocationManager;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJLinkMojo;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the strategy the resolver derives from the project's dependencies.
 * <p>
 * This is the heart of the plugin: it decides which artifacts jlink gets to see, which ones end up
 * on the module path of the generated launcher, and which ones on its class path. The decision
 * depends only on the JPMS descriptors of the dependencies and on a handful of mojo parameters,
 * both of which are provided here without touching a real jar file or forking a real tool
 * (see {@link JLinkTestSupport}).
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class JLinkResolverTest {

  private File tempDir;
  private FakeLocationManager locationManager;
  private RecordingLog log;
  private TestJLinkMojo mojo;
  private Set<Artifact> artifacts;


  @BeforeMethod
  public void setUp() {
    JLinkTestSupport.clearJdepsCache();
    if (tempDir == null) {
      tempDir = JLinkTestSupport.createTempDir("jlinkresolver");
    }
    locationManager = new FakeLocationManager();
    log = new RecordingLog();
    artifacts = new LinkedHashSet<>();

    mojo = new TestJLinkMojo();
    mojo.setLog(log);
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
   * All dependencies are full-blown modules: jlink can link the application itself.
   */
  @Test
  public void allFullBlownModulesAreLinkedIntoTheImage() throws Exception {
    module("app", "com.example.app", "org.example.lib", "java.sql");
    module("lib", "org.example.lib");
    mainModule("com.example.app");

    JLinkResolver.Result result = resolve();

    assertTrue(result.isModular());
    assertTrue(result.isPlainModular());
    assertEquals(moduleNames(result.getJlinkModules()), List.of("com.example.app", "org.example.lib"));
    assertTrue(result.getModulePath().isEmpty());
    assertTrue(result.getClassPath().isEmpty());
    assertFalse(result.isModulePathRequired());
    assertFalse(result.isClassPathRequired());
  }

  /**
   * A required automatic module cannot be linked by jlink. The whole application therefore moves
   * to the module path and jlink only builds the runtime.
   */
  @Test
  public void aRequiredAutomaticModuleDegradesTheImageToTheRuntime() throws Exception {
    module("app", "com.example.app", "org.example.legacy");
    automatic("legacy", "org.example.legacy");
    mainModule("com.example.app");

    JLinkResolver.Result result = resolve();

    assertTrue(result.isModular());
    assertFalse(result.isPlainModular());
    assertTrue(result.getJlinkModules().isEmpty());
    assertEquals(moduleNames(result.getModulePath()), List.of("org.example.legacy", "com.example.app"));
    assertTrue(result.isModulePathRequired());
    assertTrue(RecordingLog.contains(log.getWarnMessages(), "required org.example.legacy is not a full-blown module"));
    assertTrue(RecordingLog.contains(log.getWarnMessages(), "linking runtime modules only"));
  }

  /**
   * {@code failOnAutomaticRequired} turns the warning above into a build failure.
   */
  @Test
  public void requiredAutomaticModuleFailsTheBuildIfConfigured() {
    module("app", "com.example.app", "org.example.legacy");
    automatic("legacy", "org.example.legacy");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "failOnAutomaticRequired", Boolean.TRUE);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, this::resolve);

    assertEquals(ex.getMessage(), "required automatic modules found");
    assertTrue(RecordingLog.contains(log.getErrorMessages(), "required org.example.legacy is not a full-blown module"));
    assertTrue(log.getWarnMessages().isEmpty(), "must not warn as well: " + log.getWarnMessages());
  }

  /**
   * Explicitly setting the flag to false demotes the warning to an info message.
   */
  @Test
  public void requiredAutomaticModuleIsOnlyLoggedIfConfigured() throws Exception {
    module("app", "com.example.app", "org.example.legacy");
    automatic("legacy", "org.example.legacy");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "failOnAutomaticRequired", Boolean.FALSE);

    resolve();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "required org.example.legacy is not a full-blown module"));
    assertFalse(RecordingLog.contains(log.getWarnMessages(), "is not a full-blown module"));
  }

  /**
   * A dependency listed as a classpath dependency goes to the class path, even though it is a
   * full-blown module and even though it is required by the main module.
   */
  @Test
  public void classpathDependencyGoesToTheClassPath() throws Exception {
    module("app", "com.example.app", "org.example.lib");
    Artifact lib = module("lib", "org.example.lib");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "classpathDependencies", List.of(classpathDependency(lib)));

    JLinkResolver.Result result = resolve();

    assertFalse(result.isPlainModular());
    assertEquals(fileNames(result.getClassPath()), List.of("lib-1.0.jar"));
    assertEquals(moduleNames(result.getModulePath()), List.of("com.example.app"));
    assertTrue(result.isClassPathRequired());
  }

  /**
   * {@code modulePathOnly} with a single wildcard value moves everything to the module path.
   */
  @Test
  public void modulePathOnlyWildcardMovesEverythingToTheModulePath() throws Exception {
    module("app", "com.example.app", "org.example.lib");
    module("lib", "org.example.lib");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "modulePathOnly", List.of("all"));

    JLinkResolver.Result result = resolve();

    assertTrue(result.isModular());
    assertFalse(result.isPlainModular());
    assertTrue(result.getJlinkModules().isEmpty());
    assertEquals(moduleNames(result.getModulePath()), List.of("com.example.app", "org.example.lib"));
  }

  /**
   * A single named module can be moved to the module path, which is the documented workaround for
   * a third-party module requiring automatic modules.
   */
  @Test
  public void namedModulePathOnlyModuleIsTreatedLikeAnAutomaticOne() throws Exception {
    module("app", "com.example.app");
    module("plugin", "org.example.plugin");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "modulePathOnly", List.of("org.example.plugin"));

    JLinkResolver.Result result = resolve();

    // the plugin module is not required by anyone, hence the application is still plain modular
    assertTrue(result.isPlainModular());
    assertEquals(moduleNames(result.getJlinkModules()), List.of("com.example.app"));
    assertEquals(moduleNames(result.getModulePath()), List.of("org.example.plugin"));
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "org.example.plugin moved to the module-path"));
  }

  /**
   * Without a main module the application is a classpath application: every artifact goes to the
   * class path, modular or not.
   */
  @Test
  public void withoutMainModuleEverythingGoesToTheClassPath() throws Exception {
    module("app", "com.example.app", "java.sql");
    automatic("legacy", "org.example.legacy");

    JLinkResolver.Result result = resolve();

    assertFalse(result.isModular());
    assertFalse(result.isPlainModular());
    assertTrue(result.getJlinkModules().isEmpty());
    assertTrue(result.getModulePath().isEmpty());
    assertEquals(fileNames(result.getClassPath()), List.of("app-1.0.jar", "legacy-1.0.jar"));
    assertFalse(result.isModulePathRequired());
    assertTrue(result.isClassPathRequired());
  }

  /**
   * A main module that is not among the dependencies is a configuration error.
   */
  @Test
  public void unknownMainModuleFailsTheBuild() {
    module("app", "com.example.app");
    mainModule("com.example.other");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, this::resolve);

    assertEquals(ex.getMessage(), "no such main module: com.example.other");
  }

  /**
   * A module requiring something that is not among the dependencies at all cannot be resolved.
   */
  @Test
  public void missingRequiredModuleFailsTheBuild() {
    module("app", "com.example.app", "org.example.missing");
    mainModule("com.example.app");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, this::resolve);

    assertEquals(ex.getMessage(), "required module not found in dependencies: org.example.missing");
  }

  /**
   * An optional dependency does not have to be there.
   */
  @Test
  public void staticRequiresIsNotResolved() throws Exception {
    module("app", "com.example.app", "static org.example.optional");
    mainModule("com.example.app");

    JLinkResolver.Result result = resolve();

    assertTrue(result.isPlainModular());
    assertEquals(moduleNames(result.getJlinkModules()), List.of("com.example.app"));
  }

  /**
   * BOMs and other pom artifacts carry no code and must be ignored.
   */
  @Test
  public void pomArtifactsAreIgnored() throws Exception {
    module("app", "com.example.app");
    mainModule("com.example.app");
    artifacts.add(JLinkTestSupport.createArtifact("org.example", "bom", "1.0", "pom", null,
                                                  new File(tempDir, "bom-1.0.pom")));

    JLinkResolver.Result result = resolve();

    assertEquals(moduleNames(result.getJlinkModules()), List.of("com.example.app"));
    assertTrue(result.getClassPath().isEmpty());
  }

  /**
   * The empty per-platform javafx dummy artifacts must not show up anywhere.
   */
  @Test
  public void emptyJavafxDummyArtifactsAreSkipped() throws Exception {
    module("app", "com.example.app");
    mainModule("com.example.app");
    automatic("javafx-base", "javafx.baseEmpty");

    JLinkResolver.Result result = resolve();

    assertEquals(moduleNames(result.getJlinkModules()), List.of("com.example.app"));
    assertTrue(result.getModulePath().isEmpty());
    assertTrue(result.getClassPath().isEmpty());
    assertTrue(RecordingLog.contains(log.getDebugMessages(), "javafx.baseEmpty is an automatic dummy javafx module"));
  }

  /**
   * A plain modular image needs no explicit runtime modules: jlink resolves them from the module
   * graph of the application modules it links.
   */
  @Test
  public void aPlainModularImageNeedsNoExplicitRuntimeModules() throws Exception {
    module("app", "com.example.app", "java.sql", "jdk.unsupported");
    mainModule("com.example.app");

    JLinkResolver.Result result = resolve();

    assertTrue(result.isPlainModular());
    assertTrue(runtimeModuleNames(result).isEmpty(), "got " + runtimeModuleNames(result));
  }

  /**
   * As soon as jlink only builds the runtime, the {@code java.*} and {@code jdk.*} requirements
   * have to be passed to it by name.
   */
  @Test
  public void runtimeModulesArePassedToJlinkByName() throws Exception {
    module("app", "com.example.app", "java.sql", "jdk.unsupported");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "modulePathOnly", List.of("all"));

    JLinkResolver.Result result = resolve();

    assertFalse(result.isPlainModular());
    assertEquals(Set.copyOf(runtimeModuleNames(result)), Set.of("java.sql", "jdk.unsupported"));
  }

  /**
   * An old-school dependency named like a runtime module (such as {@code java.mail}) is a plain
   * dependency and must be moved back from the runtime modules.
   */
  @Test
  public void dependencyNamedLikeARuntimeModuleIsTreatedAsADependency() throws Exception {
    module("app", "com.example.app", "java.mail");
    automatic("mail", "java.mail");
    mainModule("com.example.app");

    JLinkResolver.Result result = resolve();

    assertTrue(runtimeModuleNames(result).isEmpty());
    assertEquals(moduleNames(result.getModulePath()), List.of("java.mail", "com.example.app"));
  }

  /**
   * Excluded modules are dropped from the requirements, so that a dependency referring to removed
   * api does not have to be present.
   */
  @Test
  public void excludedModulesAreNotRequired() throws Exception {
    module("app", "com.example.app", "jdk8internals");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "excludeModules", List.of("jdk8internals"));

    JLinkResolver.Result result = resolve();

    assertTrue(result.isPlainModular());
    assertTrue(RecordingLog.contains(log.getDebugMessages(), "module jdk8internals excluded"));
  }

  /**
   * The extra modules are added to the image although nothing requires them - in both strategies.
   */
  @Test
  public void addModulesAreAppendedToTheRuntimeModules() throws Exception {
    module("app", "com.example.app", "java.sql");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "addModules", List.of("jdk.localedata", "jdk.jcmd"));

    assertEquals(runtimeModuleNames(resolve()), List.of("jdk.localedata", "jdk.jcmd"));

    JLinkTestSupport.setField(mojo, "modulePathOnly", List.of("all"));
    assertEquals(runtimeModuleNames(resolve()), List.of("java.sql", "jdk.localedata", "jdk.jcmd"));
  }

  /**
   * The dependencies of a non-modular artifact are determined by jdeps.
   */
  @Test
  public void jdepsDeterminesTheRequirementsOfNonModularArtifacts() throws Exception {
    module("app", "com.example.app", "org.example.legacy");
    Artifact legacy = automatic("legacy", "org.example.legacy");
    JLinkTestSupport.seedJdepsCache(legacy, "java.sql", "java.desktop/sun.awt", "not found");
    mainModule("com.example.app");

    JLinkResolver.Result result = resolve();

    // the package of a qualified export is cut off, the "not found" line ignored
    assertEquals(Set.copyOf(runtimeModuleNames(result)), Set.of("java.desktop", "java.sql"));
  }

  /**
   * An artifact the JPMS analysis cannot make sense of gets a default automatic descriptor derived
   * from its file name.
   */
  @Test
  public void artifactWithoutDescriptorGetsADefaultOne() throws Exception {
    module("app", "com.example.app");
    mainModule("com.example.app");
    Artifact old = JLinkTestSupport.createArtifact("ancient", new File(tempDir, "ancient-1.0.jar"));
    locationManager.addUnresolvable(old);
    JLinkTestSupport.seedJdepsCache(old);
    artifacts.add(old);

    JLinkResolver.Result result = resolve();

    assertEquals(moduleNames(result.getModulePath()), List.of("ancient"));
    assertTrue(RecordingLog.contains(log.getWarnMessages(), "creating default descriptor 'ancient'"));
  }

  /**
   * An artifact the JPMS analysis does not return at all is still listed and passed to jdeps.<br>
   * If it is the only one, no module name is known and the log's name column has no width.
   */
  @Test
  public void artifactMissingFromTheJpmsAnalysisIsStillListed() throws Exception {
    Artifact ancient = JLinkTestSupport.createArtifact("ancient", new File(tempDir, "ancient-1.0.jar"));
    JLinkTestSupport.seedJdepsCache(ancient, "java.base");
    artifacts.add(ancient);    // deliberately not known to the location manager

    JLinkResolver.Result result = resolve();

    assertFalse(result.isModular());
    assertEquals(fileNames(result.getClassPath()), List.of("ancient-1.0.jar"));
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "artifact  "), log.getInfoMessages().toString());
  }

  /**
   * The extra classpath elements are relative to the image root and are prepended to the artifacts.
   */
  @Test
  public void extraClasspathElementsAreAdded() throws Exception {
    module("app", "com.example.app");
    mainModule("com.example.app");
    JLinkTestSupport.setField(mojo, "extraClasspathElements", List.of("lib/extra.jar"));

    JLinkResolver.Result result = resolve();

    assertEquals(result.getExtraClassPathElements(), List.of("lib/extra.jar"));
    assertTrue(result.isClassPathRequired());
    assertEquals(result.generateClassPath(), "lib/extra.jar");
  }

  /**
   * The main module is never moved to the module path, not even if nothing requires it.
   */
  @Test
  public void theMainModuleIsAlwaysLinked() throws Exception {
    module("app", "com.example.app");
    module("other", "com.example.other");
    mainModule("com.example.app");

    JLinkResolver.Result result = resolve();

    assertTrue(result.isPlainModular());
    assertEquals(moduleNames(result.getJlinkModules()), List.of("com.example.app", "com.example.other"));
    assertNull(result.getModuleArtifact("com.example.missing"));
    assertNotNull(result.getModuleArtifact("com.example.app"));
  }


  /**
   * A layered application's own main module is never linked into the jimage, even if it would
   * otherwise qualify as plain modular: it must remain a loose file so {@code ModuleLayerBuilder}
   * can find it later, and it must never share a directory with whatever boots {@code LayerBootstrap}
   * (see the {@code JLinkResolver#BOOTSTRAP_MODULE_NAME} exclusion), so it goes to its own,
   * dedicated leaf module-path instead of the regular (shared, boot-visible) one.
   */
  @Test
  public void layeredMainModuleGoesToTheLeafModulePathNotTheImage() throws Exception {
    module("app", "com.example.app", "org.example.lib");
    module("lib", "org.example.lib");
    mainModule("com.example.app");
    layered("app");

    JLinkResolver.Result result = resolve();

    assertTrue(result.isModular());
    assertTrue(result.isPlainModular(), "the rest of the app is still plain modular");
    assertEquals(moduleNames(result.getJlinkModules()), List.of("org.example.lib"));
    assertTrue(result.getModulePath().isEmpty());
    assertEquals(moduleNames(result.getLeafModulePath()), List.of("com.example.app"));
  }

  /**
   * In mixed mode (some dependency is automatic), everything that would normally share the boot
   * layer's module-path is diverted to the leaf's own module-path instead - except the bootstrap
   * module itself, which is what boots the leaf layer's {@code ModuleLayer} in the first place and
   * must therefore stay visible to the boot layer.
   */
  @Test
  public void layeredMixedModeDivertsEverythingExceptTheBootstrapModule() throws Exception {
    module("app", "com.example.app", "org.example.legacy", "org.tentackle.common");
    automatic("legacy", "org.example.legacy");
    module("common", "org.tentackle.common");
    mainModule("com.example.app");
    layered("app");

    JLinkResolver.Result result = resolve();

    assertTrue(result.isModular());
    assertFalse(result.isPlainModular());
    assertEquals(Set.copyOf(moduleNames(result.getLeafModulePath())),
                Set.of("com.example.app", "org.example.legacy"));
    assertEquals(moduleNames(result.getModulePath()), List.of("org.tentackle.common"));
  }

  // ---------------- fixture ----------------

  private JLinkResolver.Result resolve() throws MojoExecutionException {
    return new JLinkResolver(mojo, artifacts).resolve();
  }

  private void mainModule(String moduleName) {
    JLinkTestSupport.setField(mojo, "mainModule", moduleName);
  }

  /**
   * Configures the mojo as layered, with a single dummy library layer (its contents are irrelevant
   * to {@link JLinkResolver}, which never looks at {@link AbstractJLinkMojo#getLayers()} itself -
   * only {@link AbstractJLinkMojo#isLayered()}/{@link AbstractJLinkMojo#isModulePathOnly(String)}
   * matter to the resolver; the layers' own dependency resolution is {@link LayerResolver}'s job).
   *
   * @param leafLayerName the leaf layer name
   */
  private void layered(String leafLayerName) {
    Layer layer = new Layer();
    layer.setName("lib-layer");
    org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
    dependency.setGroupId("org.example");
    dependency.setArtifactId("some-lib");
    dependency.setVersion("1.0");
    layer.setDependencies(List.of(dependency));
    JLinkTestSupport.setField(mojo, "layers", List.of(layer));
    JLinkTestSupport.setField(mojo, "leafLayer", leafLayerName);
  }

  private Artifact module(String artifactId, String moduleName, String... requires) {
    Artifact artifact = JLinkTestSupport.createArtifact(artifactId, new File(tempDir, artifactId + "-1.0.jar"));
    locationManager.addModule(artifact, moduleName, requires);
    artifacts.add(artifact);
    return artifact;
  }

  private Artifact automatic(String artifactId, String moduleName) {
    Artifact artifact = JLinkTestSupport.createArtifact(artifactId, new File(tempDir, artifactId + "-1.0.jar"));
    locationManager.addAutomaticModule(artifact, moduleName);
    JLinkTestSupport.seedJdepsCache(artifact);
    artifacts.add(artifact);
    return artifact;
  }

  private ClasspathDependency classpathDependency(Artifact artifact) {
    ClasspathDependency dependency = new ClasspathDependency();
    dependency.setGroupId(artifact.getGroupId());
    dependency.setArtifactId(artifact.getArtifactId());
    dependency.setClassifier(artifact.getClassifier());
    return dependency;
  }

  private List<String> moduleNames(List<ModularArtifact> modules) {
    List<String> names = new ArrayList<>();
    modules.forEach(module -> names.add(module.getModuleName()));
    return names;
  }

  private List<String> fileNames(List<Artifact> artifactList) {
    List<String> names = new ArrayList<>();
    artifactList.forEach(artifact -> names.add(artifact.getFile().getName()));
    return names;
  }

  /**
   * Gets the runtime module names of a result.<br>
   * They are not exposed by the API, but only via the jlink command line.
   *
   * @param result the resolver result
   * @return the module names
   */
  private List<String> runtimeModuleNames(JLinkResolver.Result result) {
    return JLinkTestSupport.getField(result, "runtimeModuleNames");
  }

}

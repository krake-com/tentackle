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
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.eclipse.aether.artifact.Artifact;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.StringHelper;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJLinkMojo;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the configuration handling and the file copying shared by all goals.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class AbstractJLinkMojoTest {

  private File tempDir;
  private File templateDir;
  private File imageDir;
  private RecordingLog log;
  private TestJLinkMojo mojo;


  @BeforeMethod
  public void setUp() {
    tempDir = JLinkTestSupport.createTempDir("jlinkmojo");
    templateDir = new File(tempDir, "templates");
    imageDir = JLinkTestSupport.createDir(tempDir, "jlink");    // jlink would have created it
    log = new RecordingLog();

    mojo = new TestJLinkMojo();
    mojo.setLogger(log);
    JLinkTestSupport.setField(mojo, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(mojo, "mainClass", "com.example.Main");
    JLinkTestSupport.setField(mojo, "templateDir", templateDir);
    JLinkTestSupport.setField(mojo, "imageDirectory", imageDir);
    // the @Parameter defaults are applied by maven, not by the mojo (see PluginDescriptorTest)
    JLinkTestSupport.setField(mojo, "nameTemplate", JLinkMojo.NAME_TEMPLATE);
    JLinkTestSupport.setField(mojo, "runTemplate", JLinkMojo.RUN_TEMPLATE);
    JLinkTestSupport.setField(mojo, "updateTemplate", JLinkMojo.UPDATE_TEMPLATE);
  }

  @AfterMethod
  public void tearDown() {
    JLinkTestSupport.deleteRecursively(tempDir);
  }


  /**
   * The optional list parameters must never force the caller to check for null.
   */
  @Test
  public void unconfiguredListsAreEmptyNotNull() {
    assertEquals(mojo.getAddModules(), List.of());
    assertEquals(mojo.getExcludeModules(), List.of());
    assertEquals(mojo.getVariables(), Map.of());
    assertFalse(mojo.isModulePathOnly());
    assertFalse(mojo.isModulePathOnly("com.example.app"));
    assertFalse(mojo.isClasspathDependency(artifact("org.example", "lib", null)));
  }

  /**
   * All three documented spellings switch every module to the module path.
   */
  @Test
  public void modulePathOnlyAcceptsTheDocumentedShortcuts() {
    for (String shortcut : List.of("true", "all", "*")) {
      JLinkTestSupport.setField(mojo, "modulePathOnly", List.of(shortcut));
      assertTrue(mojo.isModulePathOnly(), shortcut + " must switch all modules to the module path");
    }
  }

  /**
   * A list of module names is not a shortcut, not even a single one.
   */
  @Test
  public void modulePathOnlyWithNamesIsNotAShortcut() {
    JLinkTestSupport.setField(mojo, "modulePathOnly", List.of("org.tentackle.quic"));

    assertFalse(mojo.isModulePathOnly());
    assertTrue(mojo.isModulePathOnly("org.tentackle.quic"));
    assertFalse(mojo.isModulePathOnly("tech.kwik.core"));
  }

  /**
   * A classpath dependency is matched by group, artifact and classifier, but not by version.
   */
  @Test
  public void classpathDependenciesAreMatchedByCoordinates() {
    JLinkTestSupport.setField(mojo, "classpathDependencies",
                              List.of(classpathDependency("org.example", "lib", null),
                                      classpathDependency("org.example", "native", "linux")));

    assertTrue(mojo.isClasspathDependency(artifact("org.example", "lib", null)));
    assertTrue(mojo.isClasspathDependency(artifact("org.example", "native", "linux")));
    assertFalse(mojo.isClasspathDependency(artifact("org.example", "native", "windows")));
    assertFalse(mojo.isClasspathDependency(artifact("org.example", "native", null)));
    assertFalse(mojo.isClasspathDependency(artifact("com.example", "lib", null)));
    assertFalse(mojo.isClasspathDependency(artifact("org.example", "other", null)));
  }

  /**
   * The deployment classifier names the platform the image was built for.
   */
  @Test
  public void classifierNamesThePlatform() throws Exception {
    String expected = StringHelper.getPlatform() + "-" + StringHelper.getArchitecture();

    assertEquals(mojo.getClassifier(), expected);

    JLinkTestSupport.setField(mojo, "extraClassifier", "server");
    assertEquals(mojo.getClassifier(), expected + "-server");

    JLinkTestSupport.setField(mojo, "extraClassifier", "   ");
    assertEquals(mojo.getClassifier(), expected, "a blank extra classifier must be ignored");
  }

  /**
   * A template is only installed if it is missing, so that the project's own version survives.
   */
  @Test
  public void existingTemplatesAreNotOverwritten() throws Exception {
    JLinkTestSupport.writeFile(templateDir, JLinkMojo.RUN_TEMPLATE, "mine");

    JLinkTestSupport.invoke(mojo, "installTemplate", new Class<?>[] { String.class, String.class, boolean.class },
                            JLinkMojo.RUN_TEMPLATE, JLinkMojo.RUN_TEMPLATE, false);

    assertEquals(JLinkTestSupport.readString(new File(templateDir, JLinkMojo.RUN_TEMPLATE)), "mine");
  }

  /**
   * With the overwrite flag the plugin's default is restored.
   */
  @Test
  public void templatesAreOverwrittenOnDemand() throws Exception {
    JLinkTestSupport.writeFile(templateDir, JLinkMojo.RUN_TEMPLATE, "mine");

    JLinkTestSupport.invoke(mojo, "installTemplate", new Class<?>[] { String.class, String.class, boolean.class },
                            JLinkMojo.RUN_TEMPLATE, JLinkMojo.RUN_TEMPLATE, true);

    String contents = JLinkTestSupport.readString(new File(templateDir, JLinkMojo.RUN_TEMPLATE));
    assertTrue(contents.contains("template to create the runner script"), contents);
  }

  /**
   * A template may be installed under a project specific name.
   */
  @Test
  public void templatesCanBeRenamed() throws Exception {
    JLinkTestSupport.invoke(mojo, "installTemplate", new Class<?>[] { String.class, String.class, boolean.class },
                            JLinkMojo.RUN_TEMPLATE, "my-run.ftl", false);

    assertEquals(JLinkTestSupport.fileNames(templateDir), List.of("my-run.ftl"));
  }

  /**
   * There is no default for a template the plugin does not ship.
   */
  @Test
  public void unknownTemplateFailsTheBuild() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () ->
        JLinkTestSupport.invoke(mojo, "installTemplate", new Class<?>[] { String.class, String.class, boolean.class },
                                "nosuch.ftl", "nosuch.ftl", false));

    assertEquals(ex.getMessage(), "no such resource: /templates/nosuch.ftl");
  }

  /**
   * The main class is the only parameter without a default.
   */
  @Test
  public void missingMainClassFailsTheBuild() {
    JLinkTestSupport.setField(mojo, "mainClass", null);
    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(mojo, "jlinkTool", new File("jlink"));

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo));

    assertEquals(ex.getMessage(), "mainClass missing");
  }

  /**
   * Without the tools nothing can be built.
   */
  @Test
  public void missingToolsFailTheBuild() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo));
    assertEquals(ex.getMessage(), "jdeps tool not found");

    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    ex = expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo));
    assertEquals(ex.getMessage(), "jlink tool not found");
  }

  /**
   * A template directory that is a file is a configuration error.
   */
  @Test
  public void templateDirectoryMustBeADirectory() {
    JLinkTestSupport.writeFile(tempDir, "templates", "not a directory");
    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(mojo, "jlinkTool", new File("jlink"));

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo));

    assertTrue(ex.getMessage().endsWith("is not a directory"), ex.getMessage());
  }

  /**
   * A layered application without a main module has nothing for a {@code ModuleLayer} to hold.
   */
  @Test
  public void layeredApplicationRequiresAMainModule() {
    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(mojo, "jlinkTool", new File("jlink"));
    JLinkTestSupport.setField(mojo, "layers", List.of(layer("lib")));
    JLinkTestSupport.setField(mojo, "leafLayer", "app");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo));

    assertEquals(ex.getMessage(), "layered applications must be modular: mainModule missing");
  }

  /**
   * The leaf layer name is what {@code layers.properties} keys the application's own module under.
   */
  @Test
  public void layeredApplicationRequiresALeafLayerName() {
    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(mojo, "jlinkTool", new File("jlink"));
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");
    JLinkTestSupport.setField(mojo, "layers", List.of(layer("lib")));

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo));

    assertEquals(ex.getMessage(), "leafLayer missing: required if layers are configured");
  }

  /**
   * The leaf layer's name is generated as {@code layer.<leafLayer>.*} in layers.properties, so it
   * must not collide with one of the declared layers.
   */
  @Test
  public void leafLayerNameMustNotCollideWithADeclaredLayer() {
    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(mojo, "jlinkTool", new File("jlink"));
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");
    JLinkTestSupport.setField(mojo, "layers", List.of(layer("app")));
    JLinkTestSupport.setField(mojo, "leafLayer", "app");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo));

    assertEquals(ex.getMessage(), "layer name 'app' collides with leafLayer");
  }

  /**
   * Every declared layer needs its own, independently resolvable dependencies - it has no other
   * source of content.
   */
  @Test
  public void layerWithoutDependenciesFailsTheBuild() {
    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(mojo, "jlinkTool", new File("jlink"));
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");
    JLinkTestSupport.setField(mojo, "leafLayer", "app");
    Layer empty = new Layer();
    empty.setName("lib");
    JLinkTestSupport.setField(mojo, "layers", List.of(empty));

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo));

    assertEquals(ex.getMessage(), "layer 'lib' has no dependencies");
  }

  /**
   * A valid layered configuration passes validation and enables {@link AbstractJLinkMojo#isLayered()}.
   */
  @Test
  public void validLayeredConfigurationPassesValidation() throws Exception {
    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(mojo, "jlinkTool", new File("jlink"));
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");
    JLinkTestSupport.setField(mojo, "leafLayer", "app");
    JLinkTestSupport.setField(mojo, "layers", List.of(layer("lib")));

    assertTrue(JLinkTestSupport.callValidate(mojo));
    assertTrue(mojo.isLayered());
  }

  /**
   * The artifacts jlink did not link are copied to the image's {@code mp} and {@code cp} folders.
   */
  @Test
  public void artifactsAreCopiedToTheImage() throws Exception {
    JLinkResolver.Result result = mixedResult();

    JLinkTestSupport.invoke(mojo, "copyArtifacts", new Class<?>[] { JLinkResolver.Result.class }, result);

    assertEquals(JLinkTestSupport.fileNames(new File(imageDir, AbstractJLinkMojo.DEST_MODULEPATH)),
                 List.of("legacy-1.0.jar"));
    assertEquals(JLinkTestSupport.fileNames(new File(imageDir, AbstractJLinkMojo.DEST_CLASSPATH)),
                 List.of("plain-1.0.jar"));
    assertEquals(JLinkTestSupport.readString(new File(new File(imageDir, AbstractJLinkMojo.DEST_MODULEPATH),
                                                      "legacy-1.0.jar")), "legacy");
  }

  /**
   * The resources are copied to the {@code conf} folder, which is then prepended to the classpath.
   */
  @Test
  public void resourcesAreCopiedAndAddedToTheClassPath() throws Exception {
    File resourcesDir = JLinkTestSupport.createDir(tempDir, "classes");
    JLinkTestSupport.writeFile(resourcesDir, "backend.properties", "url=jdbc:h2:mem:test");
    JLinkTestSupport.setField(mojo, "resourcesDirectory", resourcesDir);
    JLinkResolver.Result result = emptyResult();

    JLinkTestSupport.invoke(mojo, "copyResources", new Class<?>[] { JLinkResolver.Result.class }, result);

    assertEquals(JLinkTestSupport.fileNames(new File(imageDir, AbstractJLinkMojo.DEST_RESOURCES)),
                 List.of("backend.properties"));
    assertEquals(result.getExtraClassPathElements(), List.of(AbstractJLinkMojo.DEST_RESOURCES));
  }

  /**
   * An empty or non-existent resource directory leaves the classpath alone: an empty {@code conf}
   * entry would only pollute it.
   */
  @Test
  public void emptyResourceDirectoryIsNotAddedToTheClassPath() throws Exception {
    JLinkTestSupport.setField(mojo, "resourcesDirectory", JLinkTestSupport.createDir(tempDir, "empty"));
    JLinkResolver.Result result = emptyResult();

    JLinkTestSupport.invoke(mojo, "copyResources", new Class<?>[] { JLinkResolver.Result.class }, result);

    assertTrue(result.getExtraClassPathElements().isEmpty());

    JLinkTestSupport.setField(mojo, "resourcesDirectory", new File(tempDir, "none"));
    JLinkTestSupport.invoke(mojo, "copyResources", new Class<?>[] { JLinkResolver.Result.class }, result);

    assertTrue(result.getExtraClassPathElements().isEmpty());
  }

  /**
   * Extra directories keep their name below the image root.
   */
  @Test
  public void extraDirectoriesAreCopiedToTheImageRoot() throws Exception {
    File extraDir = JLinkTestSupport.createDir(tempDir, "sql");
    JLinkTestSupport.writeFile(extraDir, "README.txt", "the schema");
    JLinkTestSupport.writeFile(extraDir, "migrate/001.sql", "create table foo (id bigint)");
    JLinkTestSupport.setField(mojo, "extraDirectories", List.of(extraDir));

    JLinkTestSupport.invoke(mojo, "copyExtraDirectories", new Class<?>[0]);

    assertEquals(JLinkTestSupport.fileNames(new File(imageDir, "sql")), List.of("README.txt", "migrate"));
    assertEquals(JLinkTestSupport.fileNames(new File(new File(imageDir, "sql"), "migrate")), List.of("001.sql"));
  }


  // ---------------- fixture ----------------

  private Artifact artifact(String groupId, String artifactId, String classifier) {
    return JLinkTestSupport.createArtifact(groupId, artifactId, "1.0", "jar", classifier,
                                           new File(tempDir, artifactId + "-1.0.jar"));
  }

  private ClasspathDependency classpathDependency(String groupId, String artifactId, String classifier) {
    ClasspathDependency dependency = new ClasspathDependency();
    dependency.setGroupId(groupId);
    dependency.setArtifactId(artifactId);
    dependency.setClassifier(classifier);
    return dependency;
  }

  private Layer layer(String name) {
    Layer layer = new Layer();
    layer.setName(name);
    org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
    dependency.setGroupId("org.example");
    dependency.setArtifactId("some-lib");
    dependency.setVersion("1.0");
    layer.setDependencies(List.of(dependency));
    return layer;
  }

  /**
   * Creates an empty resolver result.<br>
   * The result is an inner class of the resolver, hence it needs an enclosing instance.
   *
   * @return the result
   */
  private JLinkResolver.Result emptyResult() {
    return new JLinkResolver(mojo, java.util.Set.of()).new Result();
  }

  /**
   * Creates a result with one artifact on the module- and one on the class path.
   *
   * @return the result
   * @throws Exception if the result could not be filled in
   */
  private JLinkResolver.Result mixedResult() throws Exception {
    JLinkResolver.Result result = emptyResult();
    JLinkTestSupport.invoke(result, "setModular", new Class<?>[] { boolean.class }, true);

    Artifact legacy = artifact("org.example", "legacy", null);
    JLinkTestSupport.writeFile(tempDir, "legacy-1.0.jar", "legacy");
    JLinkTestSupport.invoke(result, "addToModulePath", new Class<?>[] { ModularArtifact.class },
                            new ModularArtifact(legacy, JavaModuleDescriptor.newAutomaticModule("legacy").build()));

    Artifact plain = artifact("org.example", "plain", null);
    JLinkTestSupport.writeFile(tempDir, "plain-1.0.jar", "plain");
    JLinkTestSupport.invoke(result, "addToClassPath", new Class<?>[] { Artifact.class }, plain);

    return result;
  }

}

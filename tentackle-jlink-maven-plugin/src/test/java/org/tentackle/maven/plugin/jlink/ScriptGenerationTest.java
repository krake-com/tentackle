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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.StringHelper;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJLinkMojo;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJPackageMojo;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the files generated from the freemarker templates.
 * <p>
 * The launch script is the only thing standing between the image and a running application, and
 * the jpackage option files are what the jpackage tool is actually invoked with. Both are
 * generated from the templates the plugin ships, so the tests run against those, not against
 * fixtures of their own.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class ScriptGenerationTest {

  private File tempDir;
  private File templateDir;
  private File imageDir;
  private File targetDir;
  private TestJLinkMojo mojo;


  @BeforeMethod
  public void setUp() throws Exception {
    tempDir = JLinkTestSupport.createTempDir("jlinkscript");
    templateDir = new File(tempDir, "templates");
    imageDir = JLinkTestSupport.createDir(tempDir, "jlink");
    JLinkTestSupport.createDir(imageDir, "bin");    // jlink would have created it
    targetDir = JLinkTestSupport.createDir(tempDir, "target");

    mojo = new TestJLinkMojo();
    mojo.setLogger(new RecordingLog());
    JLinkTestSupport.setField(mojo, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(mojo, "mojoExecution", JLinkTestSupport.createMojoExecution("jlink", "default-jlink", "package"));
    JLinkTestSupport.setField(mojo, "mainClass", "com.example.Main");
    JLinkTestSupport.setField(mojo, "templateDir", templateDir);
    JLinkTestSupport.setField(mojo, "imageDirectory", imageDir);
    JLinkTestSupport.setField(mojo, "variablesPrecedence", "system, maven, variables");
    JLinkTestSupport.setField(mojo, "nameTemplate", JLinkMojo.NAME_TEMPLATE);
    JLinkTestSupport.setField(mojo, "runTemplate", JLinkMojo.RUN_TEMPLATE);
    JLinkTestSupport.setField(mojo, "updateTemplate", JLinkMojo.UPDATE_TEMPLATE);
    installTemplates(mojo);
  }

  @AfterMethod
  public void tearDown() {
    JLinkTestSupport.deleteRecursively(tempDir);
  }


  /**
   * A modular application is started with the main module and the extra paths, if any.
   */
  @Test
  public void modularRunScriptStartsTheMainModule() throws Exception {
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");
    JLinkResolver.Result result = result();
    result.addToClasspath(AbstractJLinkMojo.DEST_RESOURCES);

    new JLinkGenerator(mojo, result).generateScripts();

    String script = JLinkTestSupport.readString(runScript());
    assertTrue(script.contains("-m com.example.app/com.example.Main"), script);
    assertTrue(script.contains("-cp conf"), script);
    assertTrue(runScript().canExecute(), "the run script must be executable");
  }

  /**
   * A plain modular application needs neither a module- nor a classpath.
   */
  @Test
  public void plainModularRunScriptNeedsNoPaths() throws Exception {
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");

    new JLinkGenerator(mojo, result()).generateScripts();

    String script = JLinkTestSupport.readString(runScript());
    assertTrue(script.contains("-m com.example.app/com.example.Main"), script);
    assertTrue(!script.contains(" -p "), script);
    assertTrue(!script.contains(" -cp "), script);
  }

  /**
   * A classpath application is started with the main class.
   */
  @Test
  public void classpathRunScriptStartsTheMainClass() throws Exception {
    JLinkResolver.Result result = result();
    result.addToClasspath(AbstractJLinkMojo.DEST_RESOURCES);

    new JLinkGenerator(mojo, result).generateScripts();

    String script = JLinkTestSupport.readString(runScript());
    assertTrue(script.contains("-cp conf com.example.Main"), script);
    assertTrue(!script.contains("-m "), script);
  }

  /**
   * A layered application is started via the bootstrap. The layers configuration must be a JVM
   * option, i.e. precede {@code -m}: anything after the main class is an application argument.
   */
  @Test
  public void layeredRunScriptStartsTheBootstrap() throws Exception {
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");
    Layer layer = new Layer();
    layer.setName("lib");
    JLinkTestSupport.setField(mojo, "layers", List.of(layer));
    JLinkTestSupport.setField(mojo, "leafLayer", "app");

    new JLinkGenerator(mojo, result()).generateScripts();

    String script = JLinkTestSupport.readString(runScript());
    assertTrue(script.contains("java -Dtentackle.layers=conf/layers.properties -m " +
                               "org.tentackle.common/org.tentackle.common.LayerBootstrap " + argsVariable()), script);
  }

  /**
   * The run script passes its arguments to the application.
   */
  @Test
  public void theRunScriptPassesItsArguments() throws Exception {
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");

    new JLinkGenerator(mojo, result()).generateScripts();

    String script = JLinkTestSupport.readString(runScript());
    assertTrue(script.contains("-m com.example.app/com.example.Main " + argsVariable()), script);
  }

  /**
   * The update script is generated next to the run script, but only with the updater enabled.
   */
  @Test
  public void theUpdateScriptIsGeneratedOnlyWithTheUpdater() throws Exception {
    new JLinkGenerator(mojo, result()).generateScripts();
    assertEquals(JLinkTestSupport.fileNames(new File(imageDir, "bin")), List.of(runScriptName()));

    JLinkTestSupport.setField(mojo, "withUpdater", true);
    new JLinkGenerator(mojo, result()).generateScripts();

    List<String> names = JLinkTestSupport.fileNames(new File(imageDir, "bin"));
    assertTrue(names.contains(updateScriptName()), "got " + names);
    assertTrue(new File(new File(imageDir, "bin"), updateScriptName()).canExecute());
  }

  /**
   * A name template producing nothing leaves the image without a launcher, which must not pass
   * silently.
   */
  @Test
  public void anEmptyScriptNameFailsTheBuild() throws Exception {
    JLinkTestSupport.writeFile(templateDir, "empty-name.ftl", "\n");
    JLinkTestSupport.setField(mojo, "nameTemplate", "empty-name.ftl");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                            () -> new JLinkGenerator(mojo, result()).generateScripts());

    assertEquals(ex.getMessage(), "no valid name for runner script");
  }

  /**
   * The jpackage goal writes the two option files the jpackage tool is invoked with.
   */
  @Test
  public void jpackageOptionFilesAreGenerated() throws Exception {
    TestJPackageMojo jpackageMojo = jpackageMojo();

    new JPackageGenerator(jpackageMojo, result(jpackageMojo)).generateOptions();

    assertEquals(JLinkTestSupport.fileNames(targetDir),
                 List.of(JPackageMojo.OPTIONS_IMAGE, JPackageMojo.OPTIONS_INSTALLER));
  }

  /**
   * The package updater script goes into the application image, not into the runtime image.
   */
  @Test
  public void thePackageUpdateScriptIsGeneratedIntoTheApplicationImage() throws Exception {
    TestJPackageMojo jpackageMojo = jpackageMojo();
    JLinkTestSupport.setField(jpackageMojo, "withUpdater", true);
    File appImageDir = JLinkTestSupport.createDir(tempDir, "app-image");

    new JPackageGenerator(jpackageMojo, result(jpackageMojo)).generateUpdateScript(appImageDir);

    File script = new File(new File(appImageDir, "bin"), updateScriptName());
    assertTrue(script.isFile(), "no " + script);
    assertTrue(script.canExecute());
  }

  /**
   * Without the updater there is nothing to generate.
   */
  @Test
  public void noPackageUpdateScriptWithoutTheUpdater() throws Exception {
    TestJPackageMojo jpackageMojo = jpackageMojo();
    File appImageDir = JLinkTestSupport.createDir(tempDir, "app-image");

    new JPackageGenerator(jpackageMojo, result(jpackageMojo)).generateUpdateScript(appImageDir);

    assertEquals(JLinkTestSupport.fileNames(appImageDir), List.of());
  }


  // ---------------- fixture ----------------

  private TestJPackageMojo jpackageMojo() throws Exception {
    TestJPackageMojo jpackageMojo = new TestJPackageMojo();
    jpackageMojo.setLogger(new RecordingLog());
    JLinkTestSupport.setField(jpackageMojo, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(jpackageMojo, "mojoExecution",
                              JLinkTestSupport.createMojoExecution("jpackage", "default-jpackage", "package"));
    JLinkTestSupport.setField(jpackageMojo, "mainClass", "com.example.Main");
    JLinkTestSupport.setField(jpackageMojo, "templateDir", templateDir);
    JLinkTestSupport.setField(jpackageMojo, "imageDirectory", imageDir);
    JLinkTestSupport.setField(jpackageMojo, "variablesPrecedence", "system, maven, variables");
    JLinkTestSupport.setField(jpackageMojo, "packageImageTemplate", JPackageMojo.PACKAGE_IMAGE_TEMPLATE);
    JLinkTestSupport.setField(jpackageMojo, "packageInstallerTemplate", JPackageMojo.PACKAGE_INSTALLER_TEMPLATE);
    JLinkTestSupport.setField(jpackageMojo, "packageUpdateTemplate", JPackageMojo.PACKAGE_UPDATE_TEMPLATE);
    installTemplates(jpackageMojo);
    return jpackageMojo;
  }

  private void installTemplates(AbstractJLinkMojo target) throws Exception {
    JLinkTestSupport.invoke(target, "installTemplates", new Class<?>[] { boolean.class }, true);
    // the updater templates are only installed with the updater enabled, but the tests switch it
    // on after the fact
    JLinkTestSupport.invoke(target, "installTemplate", new Class<?>[] { String.class, String.class, boolean.class },
                            JLinkMojo.UPDATE_TEMPLATE, JLinkMojo.UPDATE_TEMPLATE, true);
    JLinkTestSupport.invoke(target, "installTemplate", new Class<?>[] { String.class, String.class, boolean.class },
                            JPackageMojo.PACKAGE_UPDATE_TEMPLATE, JPackageMojo.PACKAGE_UPDATE_TEMPLATE, true);
  }

  private JLinkResolver.Result result() {
    return result(mojo);
  }

  private JLinkResolver.Result result(AbstractJLinkMojo target) {
    return new JLinkResolver(target, Set.of()).new Result();
  }

  private File runScript() {
    return new File(new File(imageDir, "bin"), runScriptName());
  }

  private String runScriptName() {
    return StringHelper.getPlatform().contains("win") ? "run.cmd" : "run.sh";
  }

  private String argsVariable() {
    return StringHelper.getPlatform().contains("win") ? "%*" : "\"$@\"";
  }

  private String updateScriptName() {
    return StringHelper.getPlatform().contains("win") ? "update.cmd" : "update.sh";
  }

}

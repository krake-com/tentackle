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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestInitMojo;

import java.io.File;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@code init} goal, which puts the plugin's freemarker templates into the project so
 * that they can be adapted.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class InitMojoTest {

  private static final List<String> ALL_TEMPLATES =
      List.of(JLinkMojo.NAME_TEMPLATE,
              JPackageMojo.PACKAGE_IMAGE_TEMPLATE,
              JPackageMojo.PACKAGE_INSTALLER_TEMPLATE,
              JPackageMojo.PACKAGE_UPDATE_TEMPLATE,
              JLinkMojo.RUN_TEMPLATE,
              JLinkMojo.UPDATE_TEMPLATE);

  private File tempDir;
  private File templateDir;
  private RecordingLog log;
  private TestInitMojo mojo;


  @BeforeMethod
  public void setUp() {
    tempDir = JLinkTestSupport.createTempDir("jlinkinit");
    templateDir = new File(tempDir, "templates");
    log = new RecordingLog();

    mojo = new TestInitMojo();
    mojo.setLogger(log);
    JLinkTestSupport.setField(mojo, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(mojo, "templateDir", templateDir);
    JLinkTestSupport.setField(mojo, "copyAll", true);
  }

  @AfterMethod
  public void tearDown() {
    JLinkTestSupport.deleteRecursively(tempDir);
  }


  /**
   * By default every template of both goals is installed.
   */
  @Test
  public void allTemplatesAreCopiedByDefault() throws Exception {
    mojo.executeImpl();

    assertEquals(JLinkTestSupport.fileNames(templateDir), ALL_TEMPLATES);
    for (String name : ALL_TEMPLATES) {
      assertTrue(JLinkTestSupport.readString(new File(templateDir, name)).startsWith("<#--"),
                 name + " is not a freemarker template");
    }
  }

  /**
   * The goal restores the defaults, i.e. it overwrites what the project has changed.
   */
  @Test
  public void existingTemplatesAreOverwritten() throws Exception {
    JLinkTestSupport.writeFile(templateDir, JLinkMojo.RUN_TEMPLATE, "mine");

    mojo.executeImpl();

    assertTrue(JLinkTestSupport.readString(new File(templateDir, JLinkMojo.RUN_TEMPLATE))
                               .contains("template to create the runner script"));
  }

  /**
   * Without {@code copyAll} only the templates the project already uses are restored.
   */
  @Test
  public void onlyExistingTemplatesAreRestoredOnDemand() throws Exception {
    JLinkTestSupport.setField(mojo, "copyAll", false);
    JLinkTestSupport.writeFile(templateDir, JLinkMojo.RUN_TEMPLATE, "mine");

    mojo.executeImpl();

    assertEquals(JLinkTestSupport.fileNames(templateDir), List.of(JLinkMojo.RUN_TEMPLATE));
    assertTrue(JLinkTestSupport.readString(new File(templateDir, JLinkMojo.RUN_TEMPLATE))
                               .contains("template to create the runner script"));
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "'" + JLinkMojo.RUN_TEMPLATE + "' restored to its default"));
  }

  /**
   * A project specific template has no default and is left alone.
   */
  @Test
  public void projectSpecificTemplatesAreSkipped() throws Exception {
    JLinkTestSupport.setField(mojo, "copyAll", false);
    JLinkTestSupport.writeFile(templateDir, "my-own.ftl", "mine");

    mojo.executeImpl();

    assertEquals(JLinkTestSupport.readString(new File(templateDir, "my-own.ftl")), "mine");
    assertTrue(RecordingLog.contains(log.getWarnMessages(), "no default template for 'my-own.ftl'"));
  }

  /**
   * The goal needs neither the java tools nor any installed template to validate, and the
   * templates are installed only once, by the execution itself.
   */
  @Test
  public void validationNeedsNoToolsAndInstallsNothing() throws Exception {
    assertTrue(JLinkTestSupport.callValidate(mojo));
    assertEquals(templateDir.exists(), false, "templates installed during validation");

    mojo.executeImpl();

    assertEquals(log.getInfoMessages().stream().filter(m -> m.startsWith("installed template")).count(),
                 (long) ALL_TEMPLATES.size());
  }

  /**
   * The goal does not build an image, hence it has no image directory.
   */
  @Test
  public void theInitGoalHasNoImage() {
    assertEquals(mojo.getImageDirectory(), null);
  }

}

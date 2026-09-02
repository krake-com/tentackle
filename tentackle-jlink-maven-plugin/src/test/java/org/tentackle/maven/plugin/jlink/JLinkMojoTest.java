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

import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJLinkMojo;

import java.io.File;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the configuration of the {@code jlink} goal.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class JLinkMojoTest {

  private File tempDir;
  private File templateDir;
  private TestJLinkMojo mojo;


  @BeforeMethod
  public void setUp() {
    tempDir = JLinkTestSupport.createTempDir("jlinkgoal");
    templateDir = new File(tempDir, "templates");

    mojo = new TestJLinkMojo();
    mojo.setLog(new RecordingLog());
    JLinkTestSupport.setField(mojo, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(mojo, "mainClass", "com.example.Main");
    JLinkTestSupport.setField(mojo, "templateDir", templateDir);
    JLinkTestSupport.setField(mojo, "jdepsTool", new File("jdeps"));
    JLinkTestSupport.setField(mojo, "jlinkTool", new File("jlink"));
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
   * Validating the goal installs the templates it is going to need - and only those.
   */
  @Test
  public void validationInstallsTheMissingTemplates() throws Exception {
    assertTrue(JLinkTestSupport.callValidate(mojo));

    assertEquals(JLinkTestSupport.fileNames(templateDir), List.of(JLinkMojo.NAME_TEMPLATE, JLinkMojo.RUN_TEMPLATE));
  }

  /**
   * The update script is generated only for applications using the auto-update feature.
   */
  @Test
  public void theUpdateTemplateIsInstalledOnlyWithTheUpdater() throws Exception {
    assertNull(mojo.getUpdateTemplate());

    JLinkTestSupport.setField(mojo, "withUpdater", true);

    assertEquals(mojo.getUpdateTemplate(), JLinkMojo.UPDATE_TEMPLATE);
    assertTrue(JLinkTestSupport.callValidate(mojo));
    assertEquals(JLinkTestSupport.fileNames(templateDir),
                 List.of(JLinkMojo.NAME_TEMPLATE, JLinkMojo.RUN_TEMPLATE, JLinkMojo.UPDATE_TEMPLATE));
  }

  /**
   * Every template the goal needs must be named.<br>
   * The names are checked before the templates are installed, or a missing name would surface as
   * a {@code NullPointerException} from the file handling instead.
   */
  @Test
  public void missingTemplateNamesFailTheBuild() {
    JLinkTestSupport.setField(mojo, "nameTemplate", "  ");
    assertEquals(expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo)).getMessage(),
                 "nameTemplate missing");

    JLinkTestSupport.setField(mojo, "nameTemplate", null);
    assertEquals(expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo)).getMessage(),
                 "nameTemplate missing");

    JLinkTestSupport.setField(mojo, "nameTemplate", JLinkMojo.NAME_TEMPLATE);
    JLinkTestSupport.setField(mojo, "runTemplate", null);
    assertEquals(expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo)).getMessage(),
                 "runTemplate missing");

    assertEquals(JLinkTestSupport.fileNames(templateDir), List.of(),
                 "nothing may be installed before the names are known to be valid");
  }

  /**
   * The update template is only required if the updater is enabled.
   */
  @Test
  public void blankUpdateTemplateFailsOnlyWithTheUpdater() throws Exception {
    JLinkTestSupport.setField(mojo, "updateTemplate", "");

    assertTrue(JLinkTestSupport.callValidate(mojo));

    JLinkTestSupport.setField(mojo, "withUpdater", true);
    assertEquals(expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo)).getMessage(),
                 "updateTemplate missing");
  }

  /**
   * A pom module has nothing to link.
   */
  @Test
  public void skippingIsHonored() throws Exception {
    JLinkTestSupport.setField(mojo, "skip", Boolean.TRUE);

    assertFalse(JLinkTestSupport.callValidate(mojo));
    assertEquals(JLinkTestSupport.fileNames(templateDir), List.of(), "must not touch the project");
  }

}

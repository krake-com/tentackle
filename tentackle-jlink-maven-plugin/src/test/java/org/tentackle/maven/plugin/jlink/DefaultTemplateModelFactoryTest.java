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
import org.apache.maven.project.MavenProject;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.buildsupport.codegen.TemplateModel;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJLinkMojo;

import java.io.File;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

/**
 * Tests the model handed to the freemarker templates.
 * <p>
 * The templates are part of the application's project and refer to these variables by name, so
 * both the names and the order in which the sources override each other are API.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class DefaultTemplateModelFactoryTest {

  /** a system property that is unlikely to collide with a real one. */
  private static final String PROPERTY = "tentackle.jlink.test.value";

  private File tempDir;
  private TestJLinkMojo mojo;
  private MavenProject project;


  @BeforeMethod
  public void setUp() {
    tempDir = JLinkTestSupport.createTempDir("jlinkmodel");
    project = JLinkTestSupport.createProject(tempDir);

    mojo = new TestJLinkMojo();
    mojo.setLogger(new RecordingLog());
    JLinkTestSupport.setField(mojo, "mavenProject", project);
    JLinkTestSupport.setField(mojo, "mojoExecution", JLinkTestSupport.createMojoExecution("jlink", "default-jlink", "package"));
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");
    JLinkTestSupport.setField(mojo, "mainClass", "com.example.Main");
    JLinkTestSupport.setField(mojo, "variablesPrecedence", "system, maven, variables");
  }

  @AfterMethod
  public void tearDown() {
    System.clearProperty(PROPERTY);
    JLinkTestSupport.deleteRecursively(tempDir);
  }


  /**
   * The fixed variables describe the execution and the application's entry point.
   */
  @Test
  public void theFixedVariablesDescribeTheExecution() throws Exception {
    TemplateModel model = createModel();

    assertEquals(model.get("goal"), "jlink");
    assertEquals(model.get("id"), "default-jlink");
    assertEquals(model.get("phase"), "package");
    assertEquals(model.get("mainModule"), "com.example.app");
    assertEquals(model.get("mainClass"), "com.example.Main");
    assertEquals(model.get("modulePath"), "");
    assertEquals(model.get("classPath"), "");
  }

  /**
   * A classpath application has no main module, and the templates test it for the empty string.
   */
  @Test
  public void nullValuesBecomeTheEmptyString() throws Exception {
    JLinkTestSupport.setField(mojo, "mainModule", null);

    assertEquals(createModel().get("mainModule"), "");
  }

  /**
   * The system properties are available under camelCase names, which is what the shipped
   * templates use to tell the platforms apart.
   */
  @Test
  public void systemPropertiesAreAvailableInCamelCase() throws Exception {
    TemplateModel model = createModel();

    assertEquals(model.get("osName"), System.getProperty("os.name"));
  }

  /**
   * By default the extra variables win over the maven properties, which win over the system ones.
   */
  @Test
  public void theDefaultPrecedenceLetsTheVariablesWin() throws Exception {
    System.setProperty(PROPERTY, "from system");

    assertEquals(createModel().get(camelCase()), "from system");

    project.getProperties().setProperty(PROPERTY, "from maven");
    assertEquals(createModel().get(camelCase()), "from maven");

    JLinkTestSupport.setField(mojo, "variables", Map.of(PROPERTY, "from variables"));
    assertEquals(createModel().get(camelCase()), "from variables");
  }

  /**
   * The precedence can be reversed.
   */
  @Test
  public void thePrecedenceIsConfigurable() throws Exception {
    System.setProperty(PROPERTY, "from system");
    project.getProperties().setProperty(PROPERTY, "from maven");
    JLinkTestSupport.setField(mojo, "variables", Map.of(PROPERTY, "from variables"));

    JLinkTestSupport.setField(mojo, "variablesPrecedence", "variables, maven, system");
    assertEquals(createModel().get(camelCase()), "from system");

    JLinkTestSupport.setField(mojo, "variablesPrecedence", "vars\tprops\tsys");
    assertEquals(createModel().get(camelCase()), "from system");
  }

  /**
   * A source can be left out entirely.
   */
  @Test
  public void aSourceCanBeOmitted() throws Exception {
    System.setProperty(PROPERTY, "from system");
    JLinkTestSupport.setField(mojo, "variablesPrecedence", "variables");

    TemplateModel model = createModel();

    assertEquals(model.get(camelCase()), null);
    assertEquals(model.get("goal"), "jlink", "the fixed variables are always there");
  }

  /**
   * The fixed variables cannot be overridden by a property of the same name.
   */
  @Test
  public void theFixedVariablesCannotBeOverridden() throws Exception {
    JLinkTestSupport.setField(mojo, "variables", Map.of("mainClass", "com.example.Hacked",
                                                        "goal", "hacked"));

    TemplateModel model = createModel();

    assertEquals(model.get("mainClass"), "com.example.Main");
    assertEquals(model.get("goal"), "jlink");
  }

  /**
   * A typo in the precedence must not silently drop a source.
   */
  @Test
  public void anUnknownPrecedenceKeywordFailsTheBuild() {
    JLinkTestSupport.setField(mojo, "variablesPrecedence", "system, mavne, variables");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, this::createModel);

    assertEquals(ex.getMessage(), "unknown variables precedence keyword: mavne");
  }

  /**
   * The factory is a service that an application specific plugin dependency may replace.
   */
  @Test
  public void theDefaultFactoryIsTheSingleton() {
    assertSame(TemplateModelFactory.getInstance().getClass(), DefaultTemplateModelFactory.class);
    assertSame(TemplateModelFactory.getInstance(), TemplateModelFactory.getInstance());
  }


  // ---------------- fixture ----------------

  private TemplateModel createModel() throws MojoExecutionException {
    JLinkResolver.Result result = new JLinkResolver(mojo, java.util.Set.of()).new Result();
    return new DefaultTemplateModelFactory().create(mojo, result);
  }

  /**
   * Gets the camelCase name the test property appears under in the model.
   *
   * @return the model key
   */
  private String camelCase() {
    return org.tentackle.common.StringHelper.toCamelCase(PROPERTY);
  }

}

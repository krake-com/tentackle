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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;

import java.io.File;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code layers} goal, which materializes the isolated module layers for the IDE.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class LayersMojoTest {

  private File tempDir;
  private LayersMojo mojo;


  @BeforeMethod
  public void setUp() {
    tempDir = JLinkTestSupport.createTempDir("jlinklayers");
    mojo = new LayersMojo();
    mojo.setLogger(new RecordingLog());
    JLinkTestSupport.setField(mojo, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(mojo, "mainModule", "com.example.app");
    JLinkTestSupport.setField(mojo, "mainClass", "com.example.app.Main");
    JLinkTestSupport.setField(mojo, "leafLayer", "app");
    JLinkTestSupport.setField(mojo, "layers", List.of(layer("lib")));
  }

  @AfterMethod
  public void tearDown() {
    JLinkTestSupport.deleteRecursively(tempDir);
  }


  /**
   * The goal needs no java tools and no templates.
   */
  @Test
  public void validationNeedsNoTools() throws Exception {
    assertTrue(JLinkTestSupport.callValidate(mojo));
  }

  /**
   * Without layers there is nothing to do.
   */
  @Test
  public void nothingToDoWithoutLayers() throws Exception {
    JLinkTestSupport.setField(mojo, "layers", null);

    assertFalse(JLinkTestSupport.callValidate(mojo));
  }

  /**
   * The common {@code skip} parameter applies to this goal as well.
   */
  @Test
  public void theGoalCanBeSkipped() throws Exception {
    JLinkTestSupport.setField(mojo, "skip", true);

    assertFalse(JLinkTestSupport.callValidate(mojo));
  }

  /**
   * The layer configuration is validated just like for the image building goals.
   */
  @Test
  public void anInvalidLayerConfigurationFailsTheGoal() {
    JLinkTestSupport.setField(mojo, "leafLayer", null);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> JLinkTestSupport.callValidate(mojo));

    assertEquals(ex.getMessage(), "leafLayer missing: required if layers are configured");
  }

  /**
   * The goal writes absolute paths, which contain backslashes on Windows. Since a backslash is an
   * escape character in {@code layers.properties}, they must be written as forward slashes.
   */
  @Test
  public void windowsPathsAreWrittenWithForwardSlashes() throws Exception {
    new LayerResolver(mojo).writeLayersProperties(tempDir, "C:\\project\\target\\tentackle-layers\\mp",
                                                  "C:\\project\\target\\classes");

    String props = JLinkTestSupport.readString(new File(tempDir, LayerResolver.LAYERS_PROPERTIES));
    assertTrue(props.contains("layer.lib.dir=C:/project/target/tentackle-layers/mp/lib\n"), props);
    assertTrue(props.contains("layer.app.dir=C:/project/target/classes\n"), props);
  }


  private Layer layer(String name) {
    Layer layer = new Layer();
    layer.setName(name);
    Dependency dependency = new Dependency();
    dependency.setGroupId("org.example");
    dependency.setArtifactId("some-lib");
    dependency.setVersion("1.0");
    layer.setDependencies(List.of(dependency));
    return layer;
  }

}

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

package org.tentackle.maven.plugin.mcp;

import org.tentackle.maven.plugin.mcp.tool.ModelTool;
import org.tentackle.maven.plugin.mcp.tool.ToolArguments;
import org.tentackle.maven.plugin.mcp.tool.ToolRegistry;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests a model that is read from the classpath as well, as {@code loadModelFromDependencies} configures it.
 * <p>
 * {@code META-INF/MODEL-INDEX.LIST} in the test resources stands in for a dependency shipping its model: the
 * entity it lists is part of the model the build sees, so everything the server answers - a candidate validation
 * above all - has to see it too.
 */
public class DependencyModelTest {

  private ModelSession session;
  private ToolRegistry registry;

  @BeforeClass
  public void loadModel() throws Exception {
    ModelConfiguration config = new ModelConfiguration(
        "mcp-dependency-test",
        List.of(ModelToolTest.modelDir()),
        true,     // load the model from the classpath resources as well
        new ModelDefaults(ModelToolTest.MODEL_DEFAULTS),
        null,
        false,
        null);
    session = new ModelSession(config, ModelSession.DEFAULT_STALENESS_CHECK_MILLIS);
    session.load();
    registry = new ToolRegistry(1000);
  }

  @AfterClass(alwaysRun = true)
  public void unloadModel() {
    session.getModel().clearModel();
    ModelManager.getInstance().removeModel(session.getModel());
  }

  private String call(String tool, Map<String, Object> args) throws ModelException {
    ModelTool modelTool = registry.getTool(tool);
    return session.call(() -> modelTool.execute(session, ToolArguments.of(args)));
  }


  @Test
  public void loadsTheEntitiesFromTheClasspathAsWell() throws Exception {
    assertEquals(session.getModel().getAllEntities().size(), 5);
    assertTrue(call("model_list_entities", Map.of()).contains("Warehouse"));
  }

  @Test
  public void reportsTheClasspathAsASourceInTheOverview() throws Exception {
    assertTrue(call("model_overview", Map.of()).contains("plugin classpath resources"));
  }

  @Test
  public void validatesACandidateAgainstTheEntitiesFromTheClasspath() throws Exception {
    // 8501 is the class id of Warehouse: without the classpath model the candidate would look consistent
    String text = call("model_validate", Map.of("source", """
        name := Note
        table := note
        id := 8501
        integrity := none
        alias := note

        ## attributes
        String(200)  text  ntext  the note text
        """));
    assertTrue(text.contains("NOT consistent"), text);
    assertTrue(text.contains("duplicate entity id 8501"), text);
  }

  @Test
  public void countsOnlyTheFilesOfTheModelDirectories() {
    assertEquals(session.getModelFileCount(), 4, "the classpath model is not a directory of files");
  }

}

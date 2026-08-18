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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the tools against the fixture model in {@code src/test/resources/model}.
 * <p>
 * The fixture is a three-level aggregate (Customer &gt; Order &gt; OrderItem) plus a standalone Product, which
 * is the smallest model that still exercises composites, referencing relations and composite paths.
 */
public class ModelToolTest {

  /** The model defaults the fixture is written against. */
  public static final String MODEL_DEFAULTS = "remote, bind, size, autoselect, tracked, root, rootid, rootclassid";

  private ModelSession session;
  private ToolRegistry registry;

  @BeforeClass
  public void loadModel() throws Exception {
    session = createSession("mcp-tool-test");
    registry = new ToolRegistry(1000);
  }

  /**
   * Creates a session on the fixture model.
   *
   * @param modelName the model name, unique per test class to keep the models isolated
   * @return the loaded session
   * @throws ModelException if the fixture is inconsistent
   */
  static ModelSession createSession(String modelName) throws ModelException {
    ModelConfiguration config = new ModelConfiguration(
        modelName,
        List.of(modelDir()),
        false,
        new ModelDefaults(MODEL_DEFAULTS),
        null,
        false,
        null);
    ModelSession session = new ModelSession(config, ModelSession.DEFAULT_STALENESS_CHECK_MILLIS);
    session.load();
    return session;
  }

  /**
   * Gets the directory holding the fixture model.
   *
   * @return the absolute path
   */
  static String modelDir() {
    return new java.io.File(ModelToolTest.class.getResource("/model").getPath()).getAbsolutePath();
  }

  private String call(String tool, Map<String, Object> args) throws ModelException {
    ModelTool modelTool = registry.getTool(tool);
    return session.call(() -> modelTool.execute(session, ToolArguments.of(args)));
  }


  @Test
  public void loadsTheFixtureModel() throws Exception {
    assertEquals(session.getModel().getAllEntities().size(), 4);
    assertEquals(session.getModelFileCount(), 4);
  }

  @Test
  public void reportsNoWarningsWhenFullyConfiguredExceptForTheBackends() {
    // the fixture is loaded with model defaults but without a backend, so exactly one warning is expected
    assertEquals(session.getWarnings().size(), 1, "warnings: " + session.getWarnings());
    assertTrue(session.getWarnings().getFirst().contains("no backends configured"));
  }

  @Test
  public void overviewReportsRootsAndIntegrity() throws Exception {
    String text = call("model_overview", Map.of());
    assertTrue(text.contains("entities:       4"), text);
    assertTrue(text.contains("Customer"), text);
    assertTrue(text.contains("Product"), text);
    assertTrue(text.contains("## aggregate roots"), text);
    assertTrue(text.contains("COMPOSITE"), text);
  }

  @Test
  public void listsEntitiesAsAnAlignedTable() throws Exception {
    String text = call("model_list_entities", Map.of());
    assertTrue(text.startsWith("4 of 4 entities"), text);
    assertTrue(text.contains("ENTITY"), text);
    assertTrue(text.contains("OrderItem"), text);
  }

  @Test
  public void narrowsTheListingByPattern() throws Exception {
    String text = call("model_list_entities", Map.of("pattern", "Order*"));
    assertTrue(text.startsWith("2 of 4 entities"), text);
    assertTrue(text.contains("OrderItem"), text);
    assertFalse(text.contains("Product"), text);
  }

  @Test
  public void narrowsTheListingByFilter() throws Exception {
    String text = call("model_list_entities", Map.of("filter", List.of("composite")));
    assertTrue(text.contains("Customer"), text);
    assertFalse(text.contains("Product"), text);
  }

  @Test
  public void rejectsAnUnknownFilter() {
    assertThrows(IllegalArgumentException.class, () -> call("model_list_entities", Map.of("filter", "bogus")));
  }

  @Test
  public void findsAttributesAndColumns() throws Exception {
    String text = call("model_find", Map.of("pattern", "customer_id"));
    assertTrue(text.contains("column"), text);
    assertTrue(text.contains("Order"), text);
  }

  @Test
  public void findReportsNothingForAnUnknownName() throws Exception {
    String text = call("model_find", Map.of("pattern", "doesnotexistanywhere"));
    assertTrue(text.startsWith("nothing matches"), text);
  }

  @Test
  public void describesAnEntityWithItsDefinition() throws Exception {
    String text = call("model_describe_entity", Map.of("entity", "Customer"));
    assertTrue(text.startsWith("Customer  (id 9001, table customer"), text);
    assertTrue(text.contains("## definition"), text);
    assertTrue(text.contains("name := Customer"), text);
    assertTrue(text.contains("## inheritance"), text);
    assertTrue(text.contains("## referenced by"), text);
  }

  @Test
  public void describesTheAggregateOfADeepComponent() throws Exception {
    String text = call("model_describe_entity", Map.of("entity", "OrderItem", "sections", List.of("aggregate")));
    assertTrue(text.contains("root:         Customer"), text);
    assertTrue(text.contains("Customer --Order--> Order --OrderItem--> OrderItem"), text);
  }

  @Test
  public void resolvesAnEntityByTableAndByClassId() throws Exception {
    assertTrue(call("model_describe_entity", Map.of("entity", "table:ordr")).startsWith("Order "));
    assertTrue(call("model_describe_entity", Map.of("entity", "id:9004")).startsWith("Product "));
  }

  @Test
  public void reportsAnUnknownEntityWithAHint() {
    try {
      call("model_describe_entity", Map.of("entity", "Nope"));
      fail("an unknown entity must be rejected");
    }
    catch (IllegalArgumentException | ModelException ex) {
      assertTrue(ex.getMessage().contains("model_list_entities"), ex.getMessage());
    }
  }

  @Test
  public void rendersTheSourceInEveryFormat() throws Exception {
    assertTrue(call("model_source", Map.of("entity", "Product", "format", "raw")).contains("name := Product"));
    assertTrue(call("model_source", Map.of("entity", "Product", "format", "canonical")).contains("id := 9004"));
    String block = call("model_source", Map.of("entity", "Product", "format", "compact", "asComment", true));
    assertTrue(block.contains("@>"), block);
    assertTrue(block.contains("@<"), block);
  }

  @Test
  public void findsThePathBetweenTwoEntities() throws Exception {
    String text = call("model_paths", Map.of("from", "Customer", "to", "OrderItem", "direction", "forward"));
    assertTrue(text.contains("Customer --Order--> Order --OrderItem--> OrderItem"), text);
  }

  @Test
  public void listsTheNeighborsWhenNoTargetIsGiven() throws Exception {
    String text = call("model_paths", Map.of("from", "Order"));
    assertTrue(text.contains("## outgoing"), text);
    assertTrue(text.contains("## incoming"), text);
    assertTrue(text.contains("OrderItem"), text);
  }

  @Test
  public void findsThePathWithAnUnusableLimit() throws Exception {
    // a limit below one must not end the search before it starts and report the path as missing
    String text = call("model_paths", Map.of("from", "Customer", "to", "OrderItem", "limit", 0));
    assertTrue(text.contains("Customer --Order--> Order --OrderItem--> OrderItem"), text);
  }

  @Test
  public void listsAtLeastOneRowWithAnUnusableLimit() throws Exception {
    String text = call("model_list_entities", Map.of("limit", 0));
    assertTrue(text.startsWith("4 of 4 entities, showing the first 1"), text);
  }

  @Test
  public void reportsThatThereIsNoPath() throws Exception {
    String text = call("model_paths", Map.of("from", "Product", "to", "Customer"));
    assertTrue(text.startsWith("no path from Product to Customer"), text);
  }

  @Test
  public void explainsHowToConfigureABackendForDdl() throws Exception {
    String text = call("model_ddl", Map.of("entity", List.of("Product")));
    assertTrue(text.contains("no backends configured"), text);
  }

  @Test
  public void validatesTheLoadedModel() throws Exception {
    String text = call("model_validate", Map.of());
    assertTrue(text.startsWith("model is consistent: 4 entities"), text);
  }

  @Test
  public void acceptsAConsistentCandidate() throws Exception {
    String text = call("model_validate", Map.of("source", candidate(9005, "note")));
    assertTrue(text.contains("is consistent"), text);
    assertTrue(text.contains("Nothing was written"), text);
    assertEquals(session.getModel().getAllEntities().size(), 4, "the live model must be untouched");
  }

  @Test
  public void rejectsACandidateWithADuplicateClassId() throws Exception {
    String text = call("model_validate", Map.of("source", candidate(9001, "note")));
    assertTrue(text.contains("NOT consistent"), text);
    assertTrue(text.contains("duplicate entity id 9001"), text);
  }

  @Test
  public void rejectsACandidateWithADuplicateTable() throws Exception {
    String text = call("model_validate", Map.of("source", candidate(9005, "customer")));
    assertTrue(text.contains("NOT consistent"), text);
  }

  @Test
  public void rejectsTheEntityArgumentWithoutASource() {
    assertThrows(IllegalArgumentException.class, () -> call("model_validate", Map.of("entity", "Customer")));
  }

  @Test
  public void reloadsWithoutChanges() throws Exception {
    String text = call("model_reload", Map.of("full", true));
    assertTrue(text.startsWith("full reload: 4 entities"), text);
    assertTrue(text.contains("nothing changed"), text);
  }

  @Test
  public void offersEveryToolWithASchemaAndADescription() {
    assertEquals(registry.getTools().size(), 9);
    for (ModelTool tool : registry.getTools()) {
      assertTrue(tool.getName().startsWith("model_"), tool.getName());
      assertTrue(tool.getDescription().length() > 40, tool.getName() + " needs a description an agent can use");
      assertTrue(tool.getInputSchema().startsWith("{\"type\":\"object\""), tool.getName());
    }
  }

  private String candidate(int classId, String tableName) {
    return """
        # a note
        name := Note
        table := %s
        id := %d
        integrity := none
        alias := note

        ## attributes
        String(200)  text  ntext  the note text
        """.formatted(tableName, classId);
  }

  @AfterClass(alwaysRun = true)
  public void unloadModel() {
    session.getModel().clearModel();
    ModelManager.getInstance().removeModel(session.getModel());
  }

}

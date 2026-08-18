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

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests which entities get a table, against the fixture model in {@code src/test/resources/inheritance}.
 * <p>
 * Whether an entity provides a table is not "is it abstract": the abstract root of a single-table hierarchy
 * provides the table all of its concrete sub entities are stored in, while those sub entities provide none, and
 * a {@code provided} entity is mapped to a table that is maintained elsewhere. The fixture is the smallest model
 * holding one of each.
 */
public class DdlToolTest {

  /** The model defaults the fixture is written against. */
  public static final String MODEL_DEFAULTS = ModelToolTest.MODEL_DEFAULTS;

  private ModelSession session;
  private ToolRegistry registry;

  @BeforeClass
  public void loadModel() throws Exception {
    ModelConfiguration config = new ModelConfiguration(
        "mcp-ddl-test",
        List.of(new File(DdlToolTest.class.getResource("/inheritance").getPath()).getAbsolutePath()),
        false,
        new ModelDefaults(MODEL_DEFAULTS),
        null,
        false,
        "H2");
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

  private int countCreateTables(String ddl) {
    return (int) ddl.lines().filter(line -> line.startsWith("CREATE TABLE ")).count();
  }


  @Test
  public void loadsTheFixtureModel() throws Exception {
    assertEquals(session.getModel().getAllEntities().size(), 3);
  }

  @Test
  public void rendersTheTableOfAnAbstractHierarchyRoot() throws Exception {
    // Vehicle is abstract and still owns the table every Car is stored in
    String ddl = call("model_ddl", Map.of());
    assertTrue(ddl.contains("CREATE TABLE vehicle"), ddl);
  }

  @Test
  public void rendersNoTableForAnEntityStoredInItsSuperEntitysTable() throws Exception {
    String ddl = call("model_ddl", Map.of());
    assertEquals(countCreateTables(ddl), 1, "only Vehicle provides a table:\n" + ddl);
  }

  @Test
  public void rendersNoTableForAProvidedEntity() throws Exception {
    // the tentackle-sql-maven-plugin does not create it either: the ddl must not claim otherwise
    String ddl = call("model_ddl", Map.of("entity", List.of("Garage")));
    assertFalse(ddl.contains("CREATE TABLE garage"), ddl);
    assertTrue(ddl.contains("Garage: no table of its own"), ddl);
  }

  @Test
  public void saysWhereAnExplicitlyRequestedEntityIsStored() throws Exception {
    String ddl = call("model_ddl", Map.of("entity", List.of("Car")));
    assertEquals(countCreateTables(ddl), 0, ddl);
    assertTrue(ddl.contains("Car: no table of its own, stored in the table of Vehicle"), ddl);
  }

  @Test
  public void namesTheTableProvidingEntityInTheHeader() throws Exception {
    String text = call("model_describe_entity", Map.of("entity", "Car"));
    assertTrue(text.startsWith("Car  (id 8002, table vehicle, alias vhc (provided by Vehicle)"), text);
  }

  @Test
  public void describesWhereAnEntityWithoutItsOwnTableIsStored() throws Exception {
    String text = call("model_describe_entity", Map.of("entity", "Car", "sections", List.of("ddl")));
    assertTrue(text.contains("stored in the table of Vehicle"), text);
    assertFalse(text.contains("CREATE TABLE"), "there is no table to create for Car:\n" + text);
  }

  @Test
  public void describesTheDdlOfATableProvidingEntity() throws Exception {
    String text = call("model_describe_entity", Map.of("entity", "Vehicle", "sections", List.of("ddl")));
    assertTrue(text.contains("CREATE TABLE vehicle"), text);
  }

  @Test
  public void describesThatAProvidedTableIsMaintainedExternally() throws Exception {
    String text = call("model_describe_entity", Map.of("entity", "Garage", "sections", List.of("ddl")));
    assertTrue(text.contains("provided"), text);
    assertFalse(text.contains("CREATE TABLE"), text);
  }

}

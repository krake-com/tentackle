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

package org.tentackle.maven.plugin.sql.reveng;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that {@link RevengModelWriter} turns real, JDBC-introspected table metadata (H2)
 * into model source text that the real model parser accepts, and that the resulting {@link Entity}
 * shapes match what was reverse-engineered.
 */
public class RevengModelWriterTest {

  private static final String URL = "jdbc:h2:mem:reveng;DB_CLOSE_DELAY=-1";

  private static Connection connection;

  private Backend backend;
  private ModelMetaData modelMetaData;
  private Set<String> batchEntityNames;

  @BeforeClass(alwaysRun = true)
  public void openDatabase() throws SQLException {
    connection = DriverManager.getConnection(URL, "sa", "");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
      stmt.execute("""
          CREATE TABLE customer (
            id BIGINT NOT NULL PRIMARY KEY,
            name VARCHAR(60) NOT NULL,
            email VARCHAR(80),
            CONSTRAINT ux_customer_name UNIQUE (name)
          )
          """);
      stmt.execute("""
          CREATE TABLE order_item (
            id BIGINT NOT NULL PRIMARY KEY,
            customer_id BIGINT NOT NULL REFERENCES customer(id),
            quantity INT NOT NULL,
            price DECIMAL(12,2)
          )
          """);
      stmt.execute("CREATE INDEX ix_order_item_customer ON order_item(customer_id)");
      stmt.execute("""
          CREATE TABLE legacy_lookup (
            code VARCHAR(10) NOT NULL PRIMARY KEY,
            description VARCHAR(100) NOT NULL
          )
          """);
      stmt.execute("""
          CREATE TABLE app_user (
            id BIGINT NOT NULL PRIMARY KEY,
            login VARCHAR(30) NOT NULL
          )
          """);
      stmt.execute("""
          CREATE TABLE audit_log (
            id BIGINT NOT NULL PRIMARY KEY,
            actor_id BIGINT NOT NULL REFERENCES app_user(id),
            action VARCHAR(50) NOT NULL
          )
          """);
    }

    BackendInfo backendInfo = new BackendInfo(URL, "sa", null, null);
    backend = backendInfo.getBackend();
    assertNotNull(backend, "H2 backend not resolved from url " + URL);

    DatabaseMetaData[] jdbcMetaData = backend.getMetaData(backendInfo);
    // note: requesting only the tables that are actually reverse-engineered together;
    // app_user deliberately stays out to exercise the "foreign key outside the batch" path.
    modelMetaData = backend.getModelMetaData(jdbcMetaData, null,
                                              "customer", "order_item", "legacy_lookup", "audit_log");

    batchEntityNames = new LinkedHashSet<>();
    batchEntityNames.add("Customer");
    batchEntityNames.add("OrderItem");
    batchEntityNames.add("LegacyLookup");
    batchEntityNames.add("AuditLog");
  }

  @AfterClass(alwaysRun = true)
  public void closeDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }


  private TableMetaData getTable(String name) {
    for (TableMetaData table : modelMetaData.getTables()) {
      if (table.getModelTableName().equalsIgnoreCase(name)) {
        return table;
      }
    }
    throw new AssertionError("table not found in modelMetaData: " + name);
  }

  /**
   * Loads the given model source text (as produced for one entity) into an isolated,
   * named model and returns the parsed entity — exercising the real DSL parser, not just
   * the writer's own string output.
   */
  private Entity parse(String entityName, String modelSource) throws ModelException, IOException {
    return parseAll(Map.of(entityName, modelSource)).get(entityName);
  }

  /**
   * Loads several entities' model source text together into one isolated, named model —
   * needed whenever a relation must resolve against another entity in the same batch,
   * exactly as {@code tentackle-wizard:pdo}'s batch mode loads a whole directory at once.
   */
  private Map<String, Entity> parseAll(Map<String, String> modelSourceByEntityName) throws ModelException, IOException {
    String modelName = "reveng-test-" + String.join("-", modelSourceByEntityName.keySet());
    Model model = ModelManager.getInstance().getModel(modelName);
    model.clearModel();
    Path dir = Files.createTempDirectory("reveng-test");
    for (Map.Entry<String, String> entry : modelSourceByEntityName.entrySet()) {
      Files.writeString(dir.resolve(entry.getKey() + ".map"), entry.getValue(), StandardCharsets.UTF_8);
    }
    model.loadFromDirectory(dir.toString(), true);
    Map<String, Entity> entities = new LinkedHashMap<>();
    for (String entityName : modelSourceByEntityName.keySet()) {
      Entity entity = model.getByEntityName(entityName);
      assertNotNull(entity, "entity " + entityName + " not found after parsing generated model source");
      entities.put(entityName, entity);
    }
    return entities;
  }


  @Test
  public void testSimpleTableWithUniqueIndex() throws ModelException, IOException {
    RevengModelWriter.Result result = RevengModelWriter.write(
        backend, getTable("customer"), "Customer", 100, "related", null, batchEntityNames);

    assertTrue(result.warnings().isEmpty(), "unexpected warnings: " + result.warnings());
    assertTrue(result.modelSource().contains("name := Customer"));
    assertTrue(result.modelSource().contains("id := 100"));
    // "id" column is implicit and must not be re-declared as an attribute
    assertFalse(result.modelSource().contains(" id "));

    Entity entity = parse("Customer", result.modelSource());
    assertEquals(entity.getClassId(), 100);
    assertNotNull(entity.getAttributeByJavaName("name", false));
    assertNotNull(entity.getAttributeByJavaName("email", false));
    // NOT NULL string column -> not representable by the type system -> surfaced as a validation
    assertTrue(result.modelSource().contains("name: @NotNull"));
    assertTrue(result.modelSource().contains("unique index"));
    assertTrue(result.modelSource().contains(":= name"));
    assertTrue(entity.getRelations().isEmpty());
  }

  @Test
  public void testForeignKeyWithinBatchBecomesRelation() throws ModelException, IOException {
    RevengModelWriter.Result customerResult = RevengModelWriter.write(
        backend, getTable("customer"), "Customer", 100, "related", null, batchEntityNames);
    RevengModelWriter.Result result = RevengModelWriter.write(
        backend, getTable("order_item"), "OrderItem", 101, "related", null, batchEntityNames);

    // H2 auto-creates a backing index for the FK constraint, duplicating the explicitly
    // created ix_order_item_customer on the same column -> the writer must drop the duplicate
    // rather than emit two logically identical index declarations (which the model rejects)
    assertEquals(result.warnings().size(), 1);
    assertTrue(result.warnings().getFirst().contains("duplicates another index"));

    // the relation must resolve against Customer, so both are loaded into the same batch model,
    // exactly as tentackle-wizard:pdo loads a whole -DbatchDir directory at once
    Map<String, Entity> entities = parseAll(Map.of("Customer", customerResult.modelSource(),
                                                    "OrderItem", result.modelSource()));
    Entity entity = entities.get("OrderItem");
    // NOT NULL bigint FK column -> primitive "long", named by convention <relation>Id
    assertNotNull(entity.getAttributeByJavaName("customerId", false));
    assertNotNull(entity.getAttributeByJavaName("quantity", false));
    assertNotNull(entity.getAttributeByJavaName("price", false));

    assertEquals(entity.getRelations().size(), 1);
    assertEquals(entity.getRelations().getFirst().getClassName(), "Customer");

    assertTrue(result.modelSource().contains("index"));
    assertTrue(result.modelSource().contains(":= customer_id"));
  }

  @Test
  public void testTableWithoutIdColumnGetsNopkey() throws ModelException, IOException {
    RevengModelWriter.Result result = RevengModelWriter.write(
        backend, getTable("legacy_lookup"), "LegacyLookup", 102, "related", null, batchEntityNames);

    assertEquals(result.warnings().size(), 1);
    assertTrue(result.warnings().getFirst().contains("nopkey"));
    assertTrue(result.modelSource().contains("[nopkey]"));

    Entity entity = parse("LegacyLookup", result.modelSource());
    assertNotNull(entity.getAttributeByJavaName("code", false));
    assertNotNull(entity.getAttributeByJavaName("description", false));
    assertTrue(result.modelSource().contains("code: @NotNull"));
    assertTrue(result.modelSource().contains("description: @NotNull"));
  }

  @Test
  public void testForeignKeyOutsideBatchStaysPlainAttribute() throws ModelException, IOException {
    RevengModelWriter.Result result = RevengModelWriter.write(
        backend, getTable("audit_log"), "AuditLog", 103, "related", null, batchEntityNames);

    assertEquals(result.warnings().size(), 1);
    assertTrue(result.warnings().getFirst().contains("app_user"));
    assertTrue(result.warnings().getFirst().contains("not part of this reveng run"));

    Entity entity = parse("AuditLog", result.modelSource());
    assertTrue(entity.getRelations().isEmpty());
    assertNotNull(entity.getAttributeByJavaName("actorId", false));
    assertTrue(result.modelSource().contains("add relation by hand"));
  }

  @Test
  public void testEntityOptionsAndStereotypeAreEmitted() throws ModelException, IOException {
    RevengModelWriter.Result result = RevengModelWriter.write(
        backend, getTable("customer"), "Customer", 104, "related", "remote, tracked, #MasterData", batchEntityNames);

    assertTrue(result.modelSource().contains("[remote, tracked, #MasterData]"));
    Entity entity = parse("Customer", result.modelSource());
    assertTrue(entity.getOptions().getStereotypes().stream().anyMatch(s -> s.equalsIgnoreCase("MasterData")));
  }

}

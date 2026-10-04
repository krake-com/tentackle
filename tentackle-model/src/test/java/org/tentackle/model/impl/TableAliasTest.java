/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.model.impl;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import org.tentackle.model.Model;
import org.tentackle.model.ModelManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the automatic table aliases.
 * <p>
 * The entities at the core of the model, i.e. the ones with the most relations, get the shortest aliases.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TableAliasTest {

  /** the temporary model directories created by the tests. */
  private final List<Path> modelDirs = new ArrayList<>();

  /** the next class id. */
  private int classId = 1;


  @AfterClass
  public void removeModelDirs() throws IOException {
    for (Path modelDir : modelDirs) {
      try (var files = Files.list(modelDir)) {
        for (Path file : files.toList()) {
          Files.deleteIfExists(file);
        }
      }
      Files.deleteIfExists(modelDir);
    }
  }


  /**
   * Alphabetically, {@code Country} comes first, but {@code Customer} is the core entity.
   */
  @Test
  public void coreEntityGetsTheShortestAlias() throws Exception {
    Model model = createModel("core", Map.of(
        "Country", "",
        "Customer", reference("Country"),
        "Invoice", reference("Customer"),
        "Delivery", reference("Customer"),
        "Payment", reference("Customer")));

    assertEquals(alias(model, "Customer"), "c");
    assertEquals(alias(model, "Country"), "ct");
    assertEquals(alias(model, "Invoice"), "i");
    assertEquals(alias(model, "Delivery"), "d");
    assertEquals(alias(model, "Payment"), "p");
  }

  /**
   * A central entity gets a single letter alias even if its name consists of several words.
   */
  @Test
  public void centralMultiWordEntityGetsASingleLetter() throws Exception {
    Model model = createModel("multiword", Map.of(
        "Currency", "",
        "CustomerOrder", "",
        "Invoice", reference("CustomerOrder"),
        "Delivery", reference("CustomerOrder")));

    assertEquals(alias(model, "CustomerOrder"), "c");
    assertTrue(alias(model, "Currency").length() > 1, alias(model, "Currency"));
  }

  /**
   * The full camel-case initials are preferred over the syllables, even if longer.
   */
  @Test
  public void initialsArePreferredOverSyllables() throws Exception {
    Model model = createModel("initials", Map.of(
        "Order", reference("OrderPosNode"),
        "OrderPos", reference("OrderPosNode"),
        "OrderPosNode", ""));

    assertEquals(alias(model, "OrderPosNode"), "o");
    assertEquals(alias(model, "Order"), "od");    // first letters of the syllables "or" and "der"
    assertEquals(alias(model, "OrderPos"), "op");
  }

  /**
   * Explicit aliases are never overridden and not handed out twice.
   */
  @Test
  public void explicitAliasIsRespected() throws Exception {
    Map<String,String> models = new LinkedHashMap<>();
    models.put("country.map", entityModel("Country", "c", ""));
    models.put("customer.map", entityModel("Customer", null, reference("Country")));
    models.put("invoice.map", entityModel("Invoice", null, reference("Customer")));
    Model model = load("explicit", models);

    assertEquals(alias(model, "Country"), "c");
    assertEquals(alias(model, "Customer"), "co");
  }

  /**
   * Entities of equal centrality are resolved by name, and the result does not depend on the load.
   */
  @Test
  public void tiesAreResolvedByName() throws Exception {
    for (String name : List.of("tie1", "tie2")) {
      Model model = createModel(name, Map.of(
          "Apple", "",
          "Alpha", ""));
      assertEquals(alias(model, "Alpha"), "a");
      assertEquals(alias(model, "Apple"), "al");    // first letters of the syllables "ap" and "le"
    }
  }

  /**
   * The relations of the sub entities count for the whole inheritance hierarchy.<br>
   * Since {@code Van} has no meaningful candidate left, it falls back to the counter.
   */
  @Test
  public void hierarchyCountsAsOneEntity() throws Exception {
    Map<String,String> models = new LinkedHashMap<>();
    models.put("vehicle.map", "#@Vehicle.java:0\n" +
                              "name := Vehicle\n" +
                              "table := vehicle\n" +
                              "integrity := none\n" +
                              "inheritance := multi\n" +
                              "\n[]\n" +
                              "String(30) code code the code\n");
    models.put("car.map", "#@Car.java:0\n" +
                          "name := Car\n" +
                          "id := " + classId++ + "\n" +
                          "table := car\n" +
                          "integrity := none\n" +
                          "extends := Vehicle\n" +
                          "\n[]\n" +
                          reference("Owner"));
    models.put("owner.map", entityModel("Owner", null, ""));
    models.put("van.map", entityModel("Van", null, ""));
    Model model = load("hierarchy", models);

    ModelImpl modelImpl = (ModelImpl) model;
    assertEquals(modelImpl.getRelatedEntities(model.getByEntityName("Vehicle")), Set.of(model.getByEntityName("Owner")));
    assertEquals(modelImpl.getRelatedEntities(model.getByEntityName("Owner")), Set.of(model.getByEntityName("Vehicle")));
    assertEquals(alias(model, "Vehicle"), "v");
    assertEquals(alias(model, "Van"), "v1");
  }


  /**
   * Creates a model from entities without explicit aliases.
   *
   * @param name the model name
   * @param entities the extra attribute and relation lines mapped by entity name
   * @return the model
   * @throws Exception if the model is broken
   */
  private Model createModel(String name, Map<String,String> entities) throws Exception {
    Map<String,String> models = new LinkedHashMap<>();
    for (Map.Entry<String,String> entry : entities.entrySet()) {
      models.put(entry.getKey().toLowerCase() + ".map", entityModel(entry.getKey(), null, entry.getValue()));
    }
    return load(name, models);
  }

  /**
   * Loads the model.
   *
   * @param name the model name
   * @param models the model sources by filename
   * @return the model
   * @throws Exception if the model is broken
   */
  private Model load(String name, Map<String,String> models) throws Exception {
    Path modelDir = Files.createTempDirectory("tentackle-alias-" + name);
    modelDirs.add(modelDir);
    for (Map.Entry<String,String> model : models.entrySet()) {
      Files.writeString(modelDir.resolve(model.getKey()), model.getValue(), StandardCharsets.UTF_8);
    }
    Model model = ModelManager.getInstance().getModel(TableAliasTest.class.getSimpleName() + "-" + name);
    model.loadFromDirectory(modelDir.toString(), true);
    return model;
  }

  private String alias(Model model, String entityName) throws Exception {
    return model.getByEntityName(entityName).getTableAlias();
  }

  private String reference(String entityName) {
    String attributeName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1) + "Id";
    return "long " + attributeName + " " + attributeName.toLowerCase() + " the " + entityName + "\n" +
           "\n" + entityName + ":\n";
  }

  private String entityModel(String name, String alias, String extraLines) {
    return "#@" + name + ".java:0\n" +
           "name := " + name + "\n" +
           "id := " + classId++ + "\n" +
           "table := " + name.toLowerCase() + "\n" +
           (alias == null ? "" : "alias := " + alias + "\n") +
           "integrity := none\n" +
           "\n[]\n" +
           "String(30) code code the code\n" +
           extraLines;
  }

}

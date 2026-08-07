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

import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;
import org.tentackle.model.parse.Document;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Regression tests for defects that made the model layer loop endlessly,
 * swallow errors or invert options.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class ModelRegressionTest {

  /** timeout for the tests loading a model: they used to loop endlessly. */
  private static final long TIMEOUT_MS = 30000;

  /** the temporary model directories created by the tests. */
  private final List<Path> modelDirs = new ArrayList<>();


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
   * The model must not be re-read forever if a model file changed in the meantime.
   */
  @Test(timeOut = TIMEOUT_MS)
  public void getAllEntitiesTerminatesAfterModelChange() throws Exception {
    Path modelDir = createModelDir("terminates",
                                   Map.of("simple.map", entityModel("Simple", 1, "simple", "")));
    Model model = createModel("terminates");
    model.loadFromDirectory(modelDir.toString(), true);
    assertEquals(model.getAllEntities().size(), 1);

    // touch the model file so that EntityInfo#hasChanged() reports a change
    File modelFile = modelDir.resolve("simple.map").toFile();
    if (modelFile.setLastModified(modelFile.lastModified() + 10000)) {
      Collection<Entity> entities = model.getAllEntities();   // used to loop forever
      assertEquals(entities.size(), 1, "entities must not be collected more than once");
    }
  }


  /**
   * The associated entities must contain the entities reachable via relations, in both directions.
   */
  @Test(timeOut = TIMEOUT_MS)
  public void associatedEntitiesAreTransitive() throws Exception {
    Path modelDir = createModelDir("associated",
                                   Map.of("invoice.map", entityModel("Invoice", 1, "invoice",
                                                                     "long customerId customer_id the customer\n" +
                                                                     "\nCustomer: select = lazy\n"),
                                          "customer.map", entityModel("Customer", 2, "customer",
                                                                      "long countryId country_id the country\n" +
                                                                      "\nCountry: select = lazy\n"),
                                          "country.map", entityModel("Country", 3, "country", "")));
    Model model = createModel("associated");
    model.loadFromDirectory(modelDir.toString(), true);

    Entity invoice = model.getByEntityName("Invoice");
    Entity customer = model.getByEntityName("Customer");
    Entity country = model.getByEntityName("Country");
    assertNotNull(invoice);

    // forward, transitively
    assertTrue(invoice.getAssociatedEntities().contains(invoice));
    assertTrue(invoice.getAssociatedEntities().contains(customer));
    assertTrue(invoice.getAssociatedEntities().contains(country));

    // and backwards, from the referenced to the referencing entity
    assertTrue(country.getAssociatedEntities().contains(customer));
    assertTrue(country.getAssociatedEntities().contains(invoice));
  }


  /**
   * A cyclic inheritance must be rejected instead of hanging when the hierarchy is walked.
   */
  @Test(timeOut = TIMEOUT_MS)
  public void cyclicInheritanceIsRejected() throws Exception {
    Path modelDir = createModelDir("cyclic",
                                   Map.of("alpha.map", extendingEntityModel("Alpha", "alpha", "Beta"),
                                          "beta.map", extendingEntityModel("Beta", "beta", "Alpha")));
    Model model = createModel("cyclic");
    try {
      model.loadFromDirectory(modelDir.toString(), true);    // used to loop forever
      fail("cyclic inheritance not detected");
    }
    catch (ModelException mex) {
      assertTrue(mex.getMessage().contains("cyclic inheritance"), mex.getMessage());
    }
  }


  /**
   * Model defaults may switch an option off, not only on.
   */
  @Test
  public void negatedModelDefaultsTurnOptionsOff() throws Exception {
    Document document = new Document(entityModel("Simple", 1, "simple", ""));
    Entity entity = createModel("defaults").getEntityFactory()
                    .createEntity(document, new ModelDefaults("!bind, !size, !autoselect"));
    assertFalse(entity.getOptions().isBindable());
    assertFalse(entity.getOptions().isMaxCol());
    assertFalse(entity.getOptions().isAutoSelect());
    assertEquals(entity.getOptions().getBindOptions(), "");

    document = new Document(entityModel("Simple", 1, "simple", ""));
    entity = createModel("defaultsOn").getEntityFactory()
             .createEntity(document, new ModelDefaults("bind, size, autoselect"));
    assertTrue(entity.getOptions().isBindable());
    assertTrue(entity.getOptions().isMaxCol());
    assertTrue(entity.getOptions().isAutoSelect());
  }


  /**
   * An object relation without its linking {@code <name>Id} attribute must be rejected.
   */
  @Test(timeOut = TIMEOUT_MS)
  public void missingRelationAttributeIsRejected() throws Exception {
    Path modelDir = createModelDir("missing",
                                   Map.of("invoice.map", entityModel("Invoice", 1, "invoice",
                                                                     "\nCustomer: select = lazy\n"),
                                          "customer.map", entityModel("Customer", 2, "customer", "")));
    Model model = createModel("missing");
    try {
      model.loadFromDirectory(modelDir.toString(), true);
      fail("missing relation attribute not detected");
    }
    catch (ModelException mex) {
      assertTrue(mex.getMessage().contains("missing attribute 'customerId'"), mex.getMessage());
    }
  }


  /**
   * Creates a model source for an entity.
   *
   * @param name the entity name
   * @param classId the class id
   * @param tableName the table name
   * @param extraLines optional attribute- and relation lines
   * @return the model source
   */
  private String entityModel(String name, int classId, String tableName, String extraLines) {
    return "#@" + name + ".java:0\n" +
           "name := " + name + "\n" +
           "id := " + classId + "\n" +
           "table := " + tableName + "\n" +
           "integrity := none\n" +
           "\n[]\n" +
           "String(30) code code the code\n" +
           extraLines;
  }

  /**
   * Creates a model source for an abstract entity extending another one.
   *
   * @param name the entity name
   * @param tableName the table name
   * @param superEntityName the name of the extended entity
   * @return the model source
   */
  private String extendingEntityModel(String name, String tableName, String superEntityName) {
    return "#@" + name + ".java:0\n" +
           "name := " + name + "\n" +
           "table := " + tableName + "\n" +
           "integrity := none\n" +
           "inheritance := plain\n" +
           "extends := " + superEntityName + "\n" +
           "\n[]\n" +
           "String(30) code" + name + " code_" + tableName + " the code\n";
  }

  /**
   * Creates a temporary model directory.
   *
   * @param name the directory name prefix
   * @param models the model sources by filename
   * @return the directory
   * @throws IOException if creation failed
   */
  private Path createModelDir(String name, Map<String,String> models) throws IOException {
    Path modelDir = Files.createTempDirectory("tentackle-model-" + name);
    modelDirs.add(modelDir);
    for (Map.Entry<String,String> model : models.entrySet()) {
      Files.writeString(modelDir.resolve(model.getKey()), model.getValue(), StandardCharsets.UTF_8);
    }
    return modelDir;
  }

  /**
   * Creates a model of its own, so that the tests don't interfere with each other.
   *
   * @param name the model name
   * @return the model
   */
  private Model createModel(String name) {
    return ModelManager.getInstance().getModel(ModelRegressionTest.class.getSimpleName() + "-" + name);
  }

}

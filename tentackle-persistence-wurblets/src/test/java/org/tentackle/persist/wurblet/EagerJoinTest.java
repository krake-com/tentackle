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

package org.tentackle.persist.wurblet;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import org.tentackle.model.Entity;
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the eager join tree of {@link DbModelWurblet}.
 * <p>
 * The tree is what the {@code ClassVariables} wurblet bakes into an entity's class variables and
 * what {@code PdoRelations} turns into load join terms of the generated relation finders. Both must
 * describe the same object graph, and the cascade must stop exactly where the model says it does.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class EagerJoinTest {

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
   * Without a depth the cascade stops after the first join, as it always did.
   */
  @Test
  public void staysAtOneLevelByDefault() throws Exception {
    DbModelWurblet wurblet = createChain("default", "eager", "eager");
    List<EagerJoin> tree = wurblet.getEagerJoinTree();
    assertEquals(tree.size(), 1);
    assertEquals(tree.get(0).getAlias(), "e_1");
    assertTrue(tree.get(0).getJoins().isEmpty(), "the default depth must not cascade");
    assertEquals(wurblet.getEagerJoinPaths(), List.of("*Customer"));
  }

  /**
   * A depth of 2 pulls the eager relations of the joined entity into the same statement.
   */
  @Test
  public void cascadesToTheDeclaredDepth() throws Exception {
    DbModelWurblet wurblet = createChain("depth2", "eager:2", "eager");
    List<EagerJoin> tree = wurblet.getEagerJoinTree();
    assertEquals(tree.size(), 1);
    EagerJoin customer = tree.get(0);
    assertEquals(customer.getAlias(), "e_1");
    assertEquals(customer.getJoins().size(), 1);
    EagerJoin country = customer.getJoins().get(0);
    assertEquals(country.getAlias(), "e_1_1", "aliases must stay unique down the tree");
    assertEquals(country.getRelation().getName(), "Country");
    assertTrue(country.getJoins().isEmpty(), "the budget is exhausted");
    assertEquals(wurblet.getEagerJoinPaths(), List.of("*Customer*Country"),
                 "the relation finders must load the same graph");
  }

  /**
   * A lazy relation is never joined, however deep the budget is.
   */
  @Test
  public void doesNotFollowLazyRelations() throws Exception {
    DbModelWurblet wurblet = createChain("lazy", "eager:3", "lazy");
    List<EagerJoin> tree = wurblet.getEagerJoinTree();
    assertEquals(tree.size(), 1);
    assertTrue(tree.get(0).getJoins().isEmpty(), "only eager relations cascade");
  }

  /**
   * The generated class variables must nest the joins via {@code addJoin}.
   */
  @Test
  public void generatesNestedJoins() throws Exception {
    String code = createChain("generate", "eager:2", "eager").createEagerJoins();
    // the closing delimiter sits at column 0, so the text block keeps the generated indentation
    assertEquals(code, """
                    List.of(
                      new Join<Invoice, Customer>(JoinType.LEFT, "inv.customer_id", "e_1.id", Customer.class, "e_1",
                        (inv, cus) -> {
                          inv.setCustomer(cus);
                        }
                      )
                        .addJoin(
                          new Join<>(JoinType.LEFT, "e_1.country_id", "e_1_1.id", Country.class, "e_1_1",
                            (Customer e_1, Country e_1_1) -> {
                              e_1.setCountry(e_1_1);
                            }
                          )
                        )
                    )
                );
""");
  }

  /**
   * A cycle in the eager relations is bounded by the depth, not by a visited set.
   */
  @Test
  public void stopsACycleAtTheDepth() throws Exception {
    DbModelWurblet wurblet = createModel("cycle", Map.of(
            "invoice.map", entityModel("Invoice", 1, "invoice", "inv",
                                       "long customerId customer_id the customer\n" +
                                       "\nCustomer: select = eager:3\n"),
            "customer.map", entityModel("Customer", 2, "customer", "cus",
                                        "long invoiceId invoice_id the invoice\n" +
                                        "\nInvoice: select = eager\n")));
    EagerJoin customer = wurblet.getEagerJoinTree().get(0);
    EagerJoin invoice = customer.getJoins().get(0);
    EagerJoin customerAgain = invoice.getJoins().get(0);
    assertEquals(customerAgain.getAlias(), "e_1_1_1");
    assertTrue(customerAgain.getJoins().isEmpty(), "the depth must terminate the cycle");
    assertEquals(wurblet.getEagerJoinPaths(), List.of("*Customer*Invoice*Customer"));

    // every join that carries sub-joins is the receiver of addJoin() and needs explicit type
    // arguments; only the leaf can keep the diamond
    assertEquals(wurblet.createEagerJoins(), """
                    List.of(
                      new Join<Invoice, Customer>(JoinType.LEFT, "inv.customer_id", "e_1.id", Customer.class, "e_1",
                        (inv, cus) -> {
                          inv.setCustomer(cus);
                        }
                      )
                        .addJoin(
                          new Join<Customer, Invoice>(JoinType.LEFT, "e_1.invoice_id", "e_1_1.id", Invoice.class, "e_1_1",
                            (Customer e_1, Invoice e_1_1) -> {
                              e_1.setInvoice(e_1_1);
                            }
                          )
                            .addJoin(
                              new Join<>(JoinType.LEFT, "e_1_1.customer_id", "e_1_1_1.id", Customer.class, "e_1_1_1",
                                (Invoice e_1_1, Customer e_1_1_1) -> {
                                  e_1_1.setCustomer(e_1_1_1);
                                }
                              )
                            )
                        )
                    )
                );
""");
  }

  /**
   * A generated finder gets no merging of the superclass joins at runtime, so the inherited eager
   * relations have to be part of its load join terms -- whereas the class variables must not repeat
   * them, because the superclass holds them already.
   */
  @Test
  public void addsInheritedRelationsToTheFindersOnly() throws Exception {
    DbModelWurblet wurblet = createModel("inherited", Map.of(
            "base.map", "#@Base.java:0\n" +
                        "name := Base\n" +
                        "table := base\n" +
                        "alias := bas\n" +
                        "integrity := none\n" +
                        "inheritance := multi\n" +
                        "\n[]\n" +
                        "long countryId country_id the country\n" +
                        "\nCountry: select = eager\n",
            "invoice.map", "#@Invoice.java:0\n" +
                           "name := Invoice\n" +
                           "id := 2\n" +
                           "table := invoice\n" +
                           "alias := inv\n" +
                           "integrity := none\n" +
                           "extends := Base\n" +
                           "\n[]\n" +
                           "String(30) code code the code\n",
            "country.map", entityModel("Country", 3, "country", "cty", "")));

    assertTrue(wurblet.getEagerJoinTree().isEmpty(), "the superclass contributes its joins at runtime");
    assertEquals(wurblet.getEagerJoinPaths(), List.of("*Country"), "but a finder has to list them");
  }

  /**
   * An entity without eager relations gets no join list at all.
   */
  @Test
  public void generatesNullWithoutEagerRelations() throws Exception {
    assertEquals(createChain("none", "lazy", "lazy").createEagerJoins(), "                    null);\n");
  }


  /**
   * Creates a wurblet for {@code Invoice -> Customer -> Country}.
   *
   * @param name the model name
   * @param invoiceSelect the select property of the Invoice-to-Customer relation
   * @param customerSelect the select property of the Customer-to-Country relation
   * @return the wurblet, bound to the Invoice entity
   * @throws Exception if the model is broken
   */
  private DbModelWurblet createChain(String name, String invoiceSelect, String customerSelect) throws Exception {
    Map<String,String> models = new LinkedHashMap<>();
    models.put("invoice.map", entityModel("Invoice", 1, "invoice", "inv",
                                          "long customerId customer_id the customer\n" +
                                          "\nCustomer: select = " + invoiceSelect + "\n"));
    models.put("customer.map", entityModel("Customer", 2, "customer", "cus",
                                           "long countryId country_id the country\n" +
                                           "\nCountry: select = " + customerSelect + "\n"));
    models.put("country.map", entityModel("Country", 3, "country", "cty", ""));

    return createModel(name, models);
  }

  /**
   * Creates a wurblet bound to the {@code Invoice} entity of the given model.
   *
   * @param name the model name
   * @param models the model sources by filename
   * @return the wurblet
   * @throws Exception if the model is broken
   */
  private DbModelWurblet createModel(String name, Map<String,String> models) throws Exception {
    Path modelDir = Files.createTempDirectory("tentackle-eager-" + name);
    modelDirs.add(modelDir);
    for (Map.Entry<String,String> model : models.entrySet()) {
      Files.writeString(modelDir.resolve(model.getKey()), model.getValue(), StandardCharsets.UTF_8);
    }

    Model model = ModelManager.getInstance().getModel(EagerJoinTest.class.getSimpleName() + "-" + name);
    model.loadFromDirectory(modelDir.toString(), true);
    Entity invoice = model.getByEntityName("Invoice");

    return new DbModelWurblet() {
      @Override
      public Entity getEntity() {
        return invoice;
      }
      @Override
      public String getClassName() {
        return "InvoicePersistenceImpl";
      }
      @Override
      public String getPdoClassName() {
        return "Invoice";
      }
    };
  }

  private String entityModel(String name, int classId, String tableName, String alias, String extraLines) {
    return "#@" + name + ".java:0\n" +
           "name := " + name + "\n" +
           "id := " + classId + "\n" +
           "table := " + tableName + "\n" +
           "alias := " + alias + "\n" +
           "integrity := none\n" +
           "\n[]\n" +
           "String(30) code code the code\n" +
           extraLines;
  }

}

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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.Article;
import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.Code;
import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.Contact;
import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.Customer;
import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.InvoiceLine;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;
import org.tentackle.model.Relation;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests the generation of Java code from data object trees.
 */
public class JavaCodeFactoryTest {

  /** resource directory holding the test model. */
  private static final String MODEL_DIR = "refmodel";

  private Model model;


  @BeforeClass
  public void loadModel() throws ModelException, URISyntaxException {
    URL url = getClass().getResource(MODEL_DIR);
    assertNotNull(url, "model directory '" + MODEL_DIR + "' not found");
    model = ModelManager.getInstance().getModel("java-code-factory-test");
    model.clearModel();
    model.loadFromDirectory(new File(url.toURI()).getPath(), true);
  }

  @AfterMethod
  public void resetSettings() {
    JavaCodeFactory.distinctVariables = false;
    JavaCodeFactory.variableOffset = null;
  }


  /**
   * The variable offset applies to distinct variables only.<br>
   * Without distinct variables, the variable is declared once and reused.
   */
  @Test
  public void testOffsetWithoutDistinctVariables() throws ModelException {
    JavaCodeFactory.variableOffset = 1;
    assertEquals(JavaCodeFactory.generateCode(null, article(1, "A"), article(2, "B")),
                 """
                 Article article = on(Article.class);    // [1/1]
                 article.setName("A");
                 article.save();

                 article = on(Article.class);    // [2/1]
                 article.setName("B");
                 article.save();
                 """);
  }

  /**
   * Distinct variables are numbered starting at the offset.
   */
  @Test
  public void testOffsetWithDistinctVariables() throws ModelException {
    JavaCodeFactory.distinctVariables = true;
    JavaCodeFactory.variableOffset = 1;
    assertEquals(JavaCodeFactory.generateCode(null, article(1, "A"), article(2, "B")),
                 """
                 Article article1 = on(Article.class);    // [1/1]
                 article1.setName("A");
                 article1 = article1.persist();

                 Article article2 = on(Article.class);    // [2/1]
                 article2.setName("B");
                 article2 = article2.persist();
                 """);
  }

  /**
   * All code of a hidden component is commented out, not just its declaration.
   */
  @Test
  public void testHiddenComponent() throws ModelException {
    Entity customerEntity = getEntity("Customer");
    Entity contactEntity = getEntity("Contact");
    Relation contactRelation = customerEntity.getRelation("contact", true);
    assertNotNull(contactRelation, "relation Customer.contact missing");
    DataObject contact = new DataObject(PdoStub.create(Contact.class, 2, 1, false, null, null), "contact", null,
                                        List.of(item(contactEntity, "email", "a@b.c")),
                                        contactEntity, contactRelation, null, true, null);
    DataObject customer = new DataObject(PdoStub.create(Customer.class, 1, null), null, null,
                                         List.of(item(customerEntity, "name", "Smith"), contact),
                                         customerEntity, null, null, false, null);
    assertEquals(JavaCodeFactory.generateCode(null, customer),
                 """
                 Customer customer = on(Customer.class);    // [1/1]
                 customer.setName("Smith");
                 // hidden: Contact contact = on(Contact.class);    // [2/1]
                 // hidden: contact.setEmail("a@b.c");
                 // hidden: customer.setContact(contact);
                 customer.save();
                 """);
  }

  /**
   * A composite unique domain key is rendered as an escaped string literal.
   */
  @Test
  public void testCompositeUdkEscaped() throws ModelException {
    Entity lineEntity = getEntity("InvoiceLine");
    Relation articleRelation = lineEntity.getRelation("article", true);
    assertNotNull(articleRelation, "relation InvoiceLine.article missing");
    Article article = PdoStub.create(Article.class, 7, new Code("a\"b\\c"));
    DataItem articleId = new DataItem("long", "articleId", 7L, null, lineEntity,
                                      lineEntity.getAttributeByJavaName("articleId", false), false,
                                      new NonCompositeRelation(articleRelation, article), null);
    DataObject line = new DataObject(PdoStub.create(InvoiceLine.class, 5, null), null, null,
                                     List.of(articleId), lineEntity, null, null, false, null);
    assertEquals(JavaCodeFactory.generateCode(null, line),
                 """
                 InvoiceLine invoiceLine = on(InvoiceLine.class);    // [5/1]
                 invoiceLine.setArticle(Objects.requireNonNull(on(Article.class).selectByUniqueDomainKey(Code.valueOf("a\\"b\\\\c"))));    // [7/1]
                 invoiceLine.save();
                 """);
  }


  private DataObject article(long id, String name) throws ModelException {
    Entity entity = getEntity("Article");
    return new DataObject(PdoStub.create(Article.class, id, null), null, null,
                          List.of(item(entity, "name", name)), entity, null, null, false, null);
  }

  private DataItem item(Entity entity, String name, Object value) {
    Attribute attribute = entity.getAttributeByJavaName(name, false);
    assertNotNull(attribute, "attribute " + entity + "." + name + " missing");
    return new DataItem("String", name, value, null, entity, attribute, false, null, null);
  }

  private Entity getEntity(String name) throws ModelException {
    Entity entity = model.getByEntityName(name);
    assertNotNull(entity, "entity " + name + " missing in model");
    return entity;
  }

}

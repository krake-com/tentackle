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

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class SqlCodeFactoryTest {

  /** resource directory holding the test model. */
  private static final String MODEL_DIR = "refmodel";

  private Model model;


  @BeforeClass
  public void loadModel() throws ModelException, URISyntaxException {
    URL url = getClass().getResource(MODEL_DIR);
    assertNotNull(url, "model directory '" + MODEL_DIR + "' not found");
    model = Model.getInstance();
    model.clearModel();
    model.loadFromDirectory(new File(url.toURI()).getPath(), true);
  }


  /**
   * A non-composite object relation from another entity.<br>
   * The reference from the abstract {@code AbstractStockItem} is spread over the tables of its
   * concrete sub entities.
   */
  @Test
  public void testReference() throws ModelException {
    assertEquals(SqlCodeFactory.createReferencingSelects(getEntity("Article"), 4711, 7),
                 """
                 -- PDOs referencing Article id=4711

                 -- references
                 -- InvoiceLine.articleId
                 SELECT * FROM invoiceline WHERE article_id=4711;
                 -- AbstractStockItem.articleId
                 SELECT * FROM reserveditem WHERE article_id=4711;
                 -- AbstractStockItem.articleId
                 SELECT * FROM stockitem WHERE article_id=4711;
                 """);
  }

  /**
   * A reference declared within an embedded entity: the column lives in the embedding table
   * and carries the prefix of the embedding relation.
   */
  @Test
  public void testEmbeddedReference() throws ModelException {
    assertEquals(SqlCodeFactory.createReferencingSelects(getEntity("Agent"), 4711, 3),
                 """
                 -- PDOs referencing Agent id=4711

                 -- references
                 -- Customer.Contact.agentId
                 SELECT * FROM customer WHERE contact_agent_id=4711;
                 """);
  }

  /**
   * The root entity of an aggregate: the components are reached via their foreign key,
   * the nested components via their root columns.
   */
  @Test
  public void testAggregateRoot() throws ModelException {
    assertEquals(SqlCodeFactory.createReferencingSelects(getEntity("Invoice"), 4711, 4),
                 """
                 -- PDOs referencing Invoice id=4711

                 -- components
                 -- InvoiceLine.invoiceId
                 SELECT * FROM invoiceline WHERE invoice_id=4711;

                 -- aggregate members
                 -- LineNote.rootId
                 SELECT * FROM linenote WHERE rootid=4711 AND rootclassid=4;
                 """);
  }

  /**
   * A component: it is referenced by its own components only, its root columns point to the
   * root entity, not to itself.
   */
  @Test
  public void testComponent() throws ModelException {
    assertEquals(SqlCodeFactory.createReferencingSelects(getEntity("InvoiceLine"), 4711, 5),
                 """
                 -- PDOs referencing InvoiceLine id=4711

                 -- components
                 -- LineNote.invoiceLineId
                 SELECT * FROM linenote WHERE invoiceline_id=4711;
                 """);
  }

  /**
   * An entity nothing points to.
   */
  @Test
  public void testUnreferenced() throws ModelException {
    assertEquals(SqlCodeFactory.createReferencingSelects(getEntity("LineNote"), 4711, 6),
                 """
                 -- PDOs referencing LineNote id=4711
                 -- nothing references LineNote
                 """);
  }


  private Entity getEntity(String name) throws ModelException {
    Entity entity = model.getByEntityName(name);
    assertNotNull(entity, "entity " + name + " missing in model");
    return entity;
  }

}

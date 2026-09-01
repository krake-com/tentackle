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

package org.tentackle.model.impl;

import org.testng.annotations.Test;

import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.SelectionType;
import org.tentackle.model.parse.Document;
import org.tentackle.model.print.EntityPrinter;
import org.tentackle.model.print.PrintConfiguration;

import java.util.ArrayList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code eager:<depth>} form of the relation's {@code select} property.
 * <p>
 * The depth tells the persistence layer how many relation hops the generated eager join tree may
 * span. It defaults to 1, which is the single-level join Tentackle always generated.
 */
@SuppressWarnings("missing-explicit-ctor")
public class EagerDepthTest {

  @Test
  public void defaultsToASingleLevel() throws Exception {
    Relation relation = relationOf("eager");
    assertEquals(relation.getSelectionType(), SelectionType.EAGER);
    assertEquals(relation.getEagerDepth(), 1);
  }

  @Test
  public void defaultsToASingleLevelForLazyRelations() throws Exception {
    assertEquals(relationOf("lazy").getEagerDepth(), 1, "the depth is meaningless but well defined");
  }

  @Test
  public void parsesTheDepth() throws Exception {
    Relation relation = relationOf("eager:3");
    assertEquals(relation.getSelectionType(), SelectionType.EAGER);
    assertEquals(relation.getEagerDepth(), 3);
  }

  @Test
  public void rejectsADepthBelowOne() {
    ModelException ex = expectThrows(ModelException.class, () -> relationOf("eager:0"));
    assertTrue(ex.getMessage().contains("at least 1"), ex.getMessage());
  }

  @Test
  public void rejectsANonNumericDepth() {
    ModelException ex = expectThrows(ModelException.class, () -> relationOf("eager:x"));
    assertTrue(ex.getMessage().contains("illegal eager depth"), ex.getMessage());
  }

  @Test
  public void rejectsADepthOnAnotherStrategy() {
    ModelException ex = expectThrows(ModelException.class, () -> relationOf("lazy:2"));
    assertTrue(ex.getMessage().contains("only allowed for the eager strategy"), ex.getMessage());
  }

  /**
   * The printed model is model input again, so the depth must survive being printed and re-read.
   */
  @Test
  public void roundTripsThroughThePrinter() throws Exception {
    Entity entity = entityOf("eager:4");
    PrintConfiguration configuration = new PrintConfiguration(false, false, false, null, new ArrayList<>(), 2);
    String text = new EntityPrinter(entity, configuration).print();
    assertTrue(text.contains("select = eager:4"), text);

    Entity reparsed = Model.getInstance().getEntityFactory().createEntity(new Document(text), null);
    assertEquals(reparsed.getRelations().get(0).getEagerDepth(), 4);
  }

  /**
   * The default depth must not show up in the printed model.
   */
  @Test
  public void printsNoDepthForTheDefault() throws Exception {
    PrintConfiguration configuration = new PrintConfiguration(false, false, false, null, new ArrayList<>(), 2);
    String text = new EntityPrinter(entityOf("eager"), configuration).print();
    assertTrue(text.contains("select = eager\n"), text);
  }


  private Relation relationOf(String select) throws ModelException {
    return entityOf(select).getRelations().get(0);
  }

  private Entity entityOf(String select) throws ModelException {
    String modelSource = """
                         name := Invoice
                         id := 1
                         table := invoice
                         integrity := none

                         []
                         long  customerId  customer_id  the customer

                         ## relations
                         Customer: select = %s
                         """.formatted(select);
    return Model.getInstance().getEntityFactory().createEntity(new Document(modelSource), null);
  }

}

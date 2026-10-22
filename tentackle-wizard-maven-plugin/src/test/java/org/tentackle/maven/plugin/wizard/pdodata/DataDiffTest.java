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

import org.testng.annotations.Test;

import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.Article;
import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.Code;
import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.Customer;
import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.InvoiceLine;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the comparison of data object trees.
 */
public class DataDiffTest {

  /**
   * The elements of a 1:N list are matched by ID, regardless of the select order.
   */
  @Test
  public void testUnsortedList() {
    DataObject left = root(element(2), element(1));
    DataObject right = root(element(1), element(2));
    DataDiff list = DataDiff.compare(left, right).getNodes().getFirst();
    assertEquals(list.getNodes().size(), 2);
    for (DataDiff element : list.getNodes()) {
      assertTrue(element.isLeftPresent() && element.isRightPresent(), element.toString());
    }
    assertFalse(list.isDifferent());
  }

  /**
   * The elements of an N:M list are matched by the related PDO, not by the ID of the link.
   */
  @Test
  public void testNmList() {
    Article a = PdoStub.create(Article.class, 100, new Code("A"));
    Article b = PdoStub.create(Article.class, 101, new Code("B"));
    Article c = PdoStub.create(Article.class, 102, new Code("C"));
    DataObject left = root(nmElement(1, a), nmElement(2, c));
    DataObject right = root(nmElement(3, b), nmElement(2, c));
    DataDiff list = DataDiff.compare(left, right).getNodes().getFirst();

    List<String> rows = new ArrayList<>();
    for (DataDiff element : list.getNodes()) {
      rows.add((element.isLeftPresent() ? element.getLeft().getValue() : "-") + " | " +
               (element.isRightPresent() ? element.getRight().getValue() : "-"));
    }
    assertEquals(rows, List.of("[1/1] -> Article A | -",
                               "- | [3/1] -> Article B",
                               "[2/1] -> Article C | [2/1] -> Article C"));
  }

  /**
   * Without a right side, the data is just shown in its original order.
   */
  @Test
  public void testShowOnly() {
    DataObject left = root(element(2), element(1));
    DataDiff list = DataDiff.compare(left, null).getNodes().getFirst();
    assertEquals(list.getNodes().get(0).getLeft(), left.getNodes().getFirst().getNodes().get(0));
    assertEquals(list.getNodes().get(1).getLeft(), left.getNodes().getFirst().getNodes().get(1));
    assertNull(list.getRight());
  }


  private DataObject root(DataObject... elements) {
    DataList list = new DataList("InvoiceLine", "lines", null, List.of(elements), null, null);
    return new DataObject(PdoStub.create(Customer.class, 1, null), null, null, List.of(list),
                          null, null, null, false, null);
  }

  private DataObject element(long id) {
    return new DataObject(PdoStub.create(InvoiceLine.class, id, 1, false, null, null), "lines", null, List.of(),
                          null, null, null, false, null);
  }

  private DataObject nmElement(long id, Article related) {
    return new DataObject(PdoStub.create(InvoiceLine.class, id, 1, false, null, null), "lines", null, List.of(),
                          null, null, new NonCompositeRelation(null, related), false, null);
  }

}

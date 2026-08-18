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

package org.tentackle.fx.table.filter;

import org.testng.annotations.Test;

import org.tentackle.fx.FxFxBundle;

import java.time.LocalDate;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class TableFilterOperatorTest {

  @Test
  public void testOperandCount() {
    assertEquals(TableFilterOperator.NONE.getOperandCount(), 0);
    assertEquals(TableFilterOperator.EMPTY.getOperandCount(), 0);
    assertEquals(TableFilterOperator.NOT_EMPTY.getOperandCount(), 0);
    assertEquals(TableFilterOperator.BETWEEN.getOperandCount(), 2);
    assertEquals(TableFilterOperator.NOT_BETWEEN.getOperandCount(), 2);
    for (TableFilterOperator operator : TableFilterOperator.values()) {
      if (operator.isTextual()) {
        assertEquals(operator.getOperandCount(), 1, operator.name());
      }
    }
  }

  @Test
  public void testCategories() {
    assertTrue(TableFilterOperator.CONTAINS.isTextual());
    assertTrue(TableFilterOperator.ENDS_WITH.isTextual());
    assertFalse(TableFilterOperator.EQUAL.isTextual());
    assertTrue(TableFilterOperator.BETWEEN.isOrdering());
    assertTrue(TableFilterOperator.LESS.isOrdering());
    assertFalse(TableFilterOperator.CONTAINS.isOrdering());
  }

  @Test
  public void testDisplayedText() {
    // every operator must resolve a bundle key, else the key is missing in the properties files
    for (TableFilterOperator operator : TableFilterOperator.values()) {
      String text = operator.getDisplayedText();
      assertNotNull(text, operator.name());
      assertFalse(text.isBlank(), operator.name());
      assertEquals(operator.toString(), text);
    }
  }

  @Test
  public void testFilterUiBundleKeys() {
    // the keys used by the filter popup and the header buttons must be present in the bundle
    for (String key : new String[] { "FILTER", "SEARCH",
                                     "(SELECT ALL)", "(BLANK)", "CLEAR", "CANCEL", "OK",
                                     "CONDITION", "AND", "SHOWING FIRST {0} OF {1} VALUES",
                                     "YES", "NO" }) {
      String text = FxFxBundle.getString(key);
      assertNotNull(text, key);
      assertFalse(text.isBlank(), key);
    }
  }

  @Test
  public void testApplicableToString() {
    List<TableFilterOperator> operators = TableFilterOperator.applicableTo(String.class);
    assertEquals(operators.getFirst(), TableFilterOperator.NONE);
    assertTrue(operators.contains(TableFilterOperator.CONTAINS));
    assertTrue(operators.contains(TableFilterOperator.LESS));   // strings are comparable
    assertTrue(operators.contains(TableFilterOperator.EMPTY));
  }

  @Test
  public void testApplicableToNumber() {
    List<TableFilterOperator> operators = TableFilterOperator.applicableTo(Integer.class);
    assertFalse(operators.contains(TableFilterOperator.CONTAINS));
    assertFalse(operators.contains(TableFilterOperator.STARTS_WITH));
    assertTrue(operators.contains(TableFilterOperator.BETWEEN));
    assertTrue(operators.contains(TableFilterOperator.GREATER_OR_EQUAL));
  }

  @Test
  public void testApplicableToBoolean() {
    List<TableFilterOperator> operators = TableFilterOperator.applicableTo(Boolean.class);
    assertFalse(operators.contains(TableFilterOperator.CONTAINS));
    assertFalse(operators.contains(TableFilterOperator.LESS));
    assertTrue(operators.contains(TableFilterOperator.EQUAL));
    assertTrue(operators.contains(TableFilterOperator.NOT_EMPTY));
  }

  @Test
  public void testApplicableToDate() {
    List<TableFilterOperator> operators = TableFilterOperator.applicableTo(LocalDate.class);
    assertFalse(operators.contains(TableFilterOperator.CONTAINS));
    assertTrue(operators.contains(TableFilterOperator.BETWEEN));
  }

  @Test
  public void testApplicableToUnknown() {
    List<TableFilterOperator> operators = TableFilterOperator.applicableTo(null);
    assertTrue(operators.contains(TableFilterOperator.CONTAINS));
    assertFalse(operators.contains(TableFilterOperator.BETWEEN));   // not comparable
  }

}

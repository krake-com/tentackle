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

import org.tentackle.fx.table.filter.FilterTestSupport.Row;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class TableColumnFilterTest {

  private TableColumnFilter<Row,String> stringFilter() {
    return FilterTestSupport.createColumnFilter("city", String.class);
  }

  private TableColumnFilter<Row,Integer> intFilter() {
    return FilterTestSupport.createColumnFilter("amount", Integer.class);
  }

  private TableColumnFilter<Row,LocalDate> dateFilter() {
    return FilterTestSupport.createColumnFilter("when", LocalDate.class);
  }


  @Test
  public void testInactiveByDefault() {
    TableColumnFilter<Row,String> filter = stringFilter();
    assertFalse(filter.isActive());
    assertFalse(filter.isValueSetActive());
    assertFalse(filter.isConditionActive());
    assertTrue(filter.testValue("anything"));
    assertTrue(filter.testValue(null));
  }

  @Test
  public void testValueSet() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.setSelectedTexts(List.of("Hamburg", "München"));
    assertTrue(filter.isActive());
    assertTrue(filter.testValue("Hamburg"));
    assertTrue(filter.testValue("München"));
    assertFalse(filter.testValue("Berlin"));
    assertTrue(filter.testValue(null));       // blanks are selected by default
    filter.setBlankSelected(false);
    assertFalse(filter.testValue(null));
    assertFalse(filter.testValue("   "));
  }

  @Test
  public void testValueSetIsCaseSensitive() {
    // the value set matches exactly what is displayed, as the user picked it from the list
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.setSelectedTexts(List.of("Hamburg"));
    assertFalse(filter.testValue("hamburg"));
  }

  @Test
  public void testTextualOperators() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.setOperator(TableFilterOperator.CONTAINS);
    filter.setOperands("bur", null);
    assertTrue(filter.isConditionActive());
    assertTrue(filter.testValue("Hamburg"));
    assertFalse(filter.testValue("Berlin"));

    filter.setOperator(TableFilterOperator.NOT_CONTAINS);
    assertFalse(filter.testValue("Hamburg"));
    assertTrue(filter.testValue("Berlin"));

    filter.setOperator(TableFilterOperator.STARTS_WITH);
    filter.setOperands("ham", null);
    assertTrue(filter.testValue("Hamburg"));    // case insensitive
    assertFalse(filter.testValue("Bad Hamburg"));

    filter.setOperator(TableFilterOperator.ENDS_WITH);
    filter.setOperands("burg", null);
    assertTrue(filter.testValue("Hamburg"));
    assertFalse(filter.testValue("Berlin"));
  }

  @Test
  public void testTextualOperatorsIgnoreDiacritics() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.setOperator(TableFilterOperator.CONTAINS);
    filter.setOperands("munchen", null);
    assertTrue(filter.testValue("München"));
  }

  @Test
  public void testEqualOperators() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.setOperator(TableFilterOperator.EQUAL);
    filter.setOperands("Hamburg", null);
    assertTrue(filter.testValue("Hamburg"));
    assertTrue(filter.testValue("hamburg"));    // equality is case insensitive
    assertFalse(filter.testValue("Berlin"));

    filter.setOperator(TableFilterOperator.NOT_EQUAL);
    assertFalse(filter.testValue("Hamburg"));
    assertTrue(filter.testValue("Berlin"));
  }

  @Test
  public void testEmptyOperators() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.setOperator(TableFilterOperator.EMPTY);
    assertTrue(filter.isConditionActive());     // no operand required
    assertTrue(filter.testValue(null));
    assertTrue(filter.testValue("  "));
    assertFalse(filter.testValue("Hamburg"));

    filter.setOperator(TableFilterOperator.NOT_EMPTY);
    assertFalse(filter.testValue(null));
    assertTrue(filter.testValue("Hamburg"));
  }

  @Test
  public void testNumericOrdering() {
    TableColumnFilter<Row,Integer> filter = intFilter();
    filter.setOperator(TableFilterOperator.GREATER);
    filter.setOperands("9", null);
    assertTrue(filter.testValue(10));           // numbers are not compared as text
    assertFalse(filter.testValue(9));
    assertFalse(filter.testValue(8));

    filter.setOperator(TableFilterOperator.GREATER_OR_EQUAL);
    assertTrue(filter.testValue(9));

    filter.setOperator(TableFilterOperator.LESS);
    assertTrue(filter.testValue(8));
    assertFalse(filter.testValue(9));

    filter.setOperator(TableFilterOperator.LESS_OR_EQUAL);
    assertTrue(filter.testValue(9));
  }

  @Test
  public void testBetween() {
    TableColumnFilter<Row,Integer> filter = intFilter();
    filter.setOperator(TableFilterOperator.BETWEEN);
    filter.setOperands("10", "20");
    assertTrue(filter.testValue(10));
    assertTrue(filter.testValue(15));
    assertTrue(filter.testValue(20));
    assertFalse(filter.testValue(9));
    assertFalse(filter.testValue(21));

    filter.setOperator(TableFilterOperator.NOT_BETWEEN);
    assertFalse(filter.testValue(15));
    assertTrue(filter.testValue(21));

    // the boundaries may be given in any order
    filter.setOperator(TableFilterOperator.BETWEEN);
    filter.setOperands("20", "10");
    assertTrue(filter.testValue(15));
  }

  @Test
  public void testConditionIsInactiveWithoutOperands() {
    TableColumnFilter<Row,Integer> filter = intFilter();
    filter.setOperator(TableFilterOperator.BETWEEN);
    filter.setOperands("10", null);
    assertFalse(filter.isConditionActive());
    assertFalse(filter.isActive());
    assertTrue(filter.testValue(99));
    filter.setOperands("10", "20");
    assertTrue(filter.isConditionActive());
  }

  @Test
  public void testDateOrdering() {
    TableColumnFilter<Row,LocalDate> filter = dateFilter();
    filter.setOperator(TableFilterOperator.GREATER);
    filter.setOperands("2026-01-31", null);
    assertTrue(filter.testValue(LocalDate.of(2026, 2, 1)));
    assertFalse(filter.testValue(LocalDate.of(2026, 1, 30)));
  }

  @Test
  public void testBlankNeverMatchesACondition() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.setOperator(TableFilterOperator.CONTAINS);
    filter.setOperands("a", null);
    assertFalse(filter.testValue(null));
    assertFalse(filter.testValue(""));
  }

  @Test
  public void testValueSetAndConditionAreAnded() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.setSelectedTexts(List.of("Hamburg", "Bremen"));
    filter.setOperator(TableFilterOperator.STARTS_WITH);
    filter.setOperands("B", null);
    assertFalse(filter.testValue("Hamburg"));   // in the value set, but fails the condition
    assertTrue(filter.testValue("Bremen"));
    assertFalse(filter.testValue("Berlin"));    // matches the condition, but not in the value set
  }

  @Test
  public void testClear() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.setSelectedTexts(List.of("Hamburg"));
    filter.setOperator(TableFilterOperator.CONTAINS);
    filter.setOperands("x", null);
    filter.clear();
    assertFalse(filter.isActive());
    assertNull(filter.getSelectedTexts());
    assertEquals(filter.getOperator(), TableFilterOperator.NONE);
    assertNull(filter.getFirstOperand());
    assertTrue(filter.isBlankSelected());
  }

  @Test
  public void testPreferencesRoundTrip() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.apply(List.of("Hamburg", "München"), false, TableFilterOperator.CONTAINS, "bur", null);
    String prefs = filter.toPreferencesString();

    TableColumnFilter<Row,String> restored = stringFilter();
    restored.fromPreferencesString(prefs);
    assertEquals(restored.getSelectedTexts(), Set.of("Hamburg", "München"));
    assertFalse(restored.isBlankSelected());
    assertEquals(restored.getOperator(), TableFilterOperator.CONTAINS);
    assertEquals(restored.getFirstOperand(), "bur");
    assertNull(restored.getSecondOperand());
    assertTrue(restored.isActive());
  }

  @Test
  public void testPreferencesRoundTripWithSpecialCharacters() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.apply(List.of("a,b", "with \"quotes\"", "two\nlines", ""), true,
                 TableFilterOperator.BETWEEN, "a,1", "b\"2");
    TableColumnFilter<Row,String> restored = stringFilter();
    restored.fromPreferencesString(filter.toPreferencesString());
    assertEquals(restored.getSelectedTexts(), Set.of("a,b", "with \"quotes\"", "two\nlines", ""));
    assertEquals(restored.getFirstOperand(), "a,1");
    assertEquals(restored.getSecondOperand(), "b\"2");
  }

  @Test
  public void testPreferencesRoundTripConditionOnly() {
    TableColumnFilter<Row,Integer> filter = intFilter();
    filter.apply(null, true, TableFilterOperator.GREATER, "42", null);
    TableColumnFilter<Row,Integer> restored = intFilter();
    restored.fromPreferencesString(filter.toPreferencesString());
    assertNull(restored.getSelectedTexts());
    assertFalse(restored.isValueSetActive());
    assertTrue(restored.isConditionActive());
    assertEquals(restored.getFirstOperand(), "42");
  }

  @Test
  public void testPreferencesRoundTripEmptyValueSet() {
    TableColumnFilter<Row,String> filter = stringFilter();
    filter.apply(List.of(), false, TableFilterOperator.NONE, null, null);
    TableColumnFilter<Row,String> restored = stringFilter();
    restored.fromPreferencesString(filter.toPreferencesString());
    assertTrue(restored.isValueSetActive());
    assertEquals(restored.getSelectedTexts(), Set.of());
    assertFalse(restored.isBlankSelected());
    assertFalse(restored.testValue("anything"));
  }

  @Test
  public void testPreferencesFailSoft() {
    for (String prefs : new String[] { null, "", "garbage", "1,NO_SUCH_OPERATOR,,,1,0", "2,EQUAL,x,,1,0" }) {
      TableColumnFilter<Row,String> filter = stringFilter();
      filter.setSelectedTexts(List.of("Hamburg"));
      filter.fromPreferencesString(prefs);
      assertFalse(filter.isActive(), "expected to be cleared: " + prefs);
      assertNull(filter.getSelectedTexts(), "expected to be cleared: " + prefs);
    }
  }

}

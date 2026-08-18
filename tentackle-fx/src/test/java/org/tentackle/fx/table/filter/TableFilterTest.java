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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.fx.table.filter.FilterTestSupport.Row;
import org.tentackle.fx.table.filter.FilterTestSupport.TestTableFilter;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class TableFilterTest {

  private TableFilter<Row> filter;
  private TableColumnFilter<Row,String> cityFilter;
  private TableColumnFilter<Row,Integer> amountFilter;

  private static final Row HAMBURG_10 = new Row("Hamburg", 10, true);
  private static final Row HAMBURG_20 = new Row("Hamburg", 20, false);
  private static final Row BERLIN_10 = new Row("Berlin", 10, true);


  @BeforeMethod
  public void setUp() {
    DefaultTableConfiguration<Row> configuration = FilterTestSupport.createTableConfiguration();
    TestTableFilter testFilter = new TestTableFilter(configuration);
    testFilter.register("city", Row::city);
    testFilter.register("amount", Row::amount);
    filter = testFilter;
    cityFilter = filter.getColumnFilter(
            FilterTestSupport.createColumnConfiguration(configuration, "city", String.class));
    amountFilter = filter.getColumnFilter(
            FilterTestSupport.createColumnConfiguration(configuration, "amount", Integer.class));
  }


  @Test
  public void testInactiveLetsEverythingPass() {
    assertFalse(filter.isActive());
    assertTrue(filter.test(HAMBURG_10));
    assertTrue(filter.test(BERLIN_10));
    assertTrue(filter.test(null));
  }

  @Test
  public void testColumnFiltersAreAnded() {
    cityFilter.setSelectedTexts(List.of("Hamburg"));
    amountFilter.setOperator(TableFilterOperator.GREATER);
    amountFilter.setOperands("15", null);
    assertTrue(filter.isActive());
    assertFalse(filter.test(HAMBURG_10));    // right city, wrong amount
    assertTrue(filter.test(HAMBURG_20));
    assertFalse(filter.test(BERLIN_10));
  }

  @Test
  public void testExcludedFilterIsIgnored() {
    cityFilter.setSelectedTexts(List.of("Hamburg"));
    amountFilter.setSelectedTexts(List.of("20"));
    assertFalse(filter.test(HAMBURG_10));
    // this is what the popup of the amount column does: the value list shows the amounts of all
    // rows passing the other columns' filters
    assertTrue(filter.test(HAMBURG_10, amountFilter));
    assertFalse(filter.test(BERLIN_10, amountFilter));
  }

  @Test
  public void testRevision() {
    int revision = filter.getRevision();
    cityFilter.setSelectedTexts(List.of("Hamburg"));
    assertNotEquals(filter.getRevision(), revision);
    revision = filter.getRevision();
    cityFilter.setOperator(TableFilterOperator.CONTAINS);
    assertNotEquals(filter.getRevision(), revision);
  }

  @Test
  public void testPredicateIdentityChangesWithRevision() {
    // a FilteredList re-evaluates only if the predicate is not equal to the previous one
    TableFilterPredicate<Row> first = TableFilterPredicate.of(filter);
    assertEquals(TableFilterPredicate.of(filter), first);
    cityFilter.setSelectedTexts(List.of("Hamburg"));
    assertNotEquals(TableFilterPredicate.of(filter), first);
    assertTrue(TableFilterPredicate.of(filter).test(HAMBURG_10));
    assertFalse(TableFilterPredicate.of(filter).test(BERLIN_10));
  }

  @Test
  public void testClear() {
    cityFilter.setSelectedTexts(List.of("Hamburg"));
    amountFilter.setOperator(TableFilterOperator.NOT_EMPTY);
    assertTrue(filter.isActive());
    filter.clear();
    assertFalse(filter.isActive());
    assertTrue(filter.test(BERLIN_10));
  }

}

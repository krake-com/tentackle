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

package org.tentackle.fx.table;

import org.testng.annotations.Test;

import java.time.LocalDate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests the formats derived from a column's pattern.<p>
 * The column configuration is created directly instead of being added to the table
 * configuration, which would eagerly create controls (see {@code FilterTestSupport}).
 */
@SuppressWarnings("missing-explicit-ctor")
public class ColumnFormatTest {

  /** the row object of the tests. */
  private record Row(String text) { }


  /**
   * Creates a column configuration.
   *
   * @return the configuration
   */
  private DefaultTableColumnConfiguration<Row,?> createColumn() {
    return new DefaultTableColumnConfiguration<>(new DefaultTableConfiguration<>(Row.class, "rows"),
                                                 "column", "Column");
  }


  /**
   * Changing the pattern must invalidate the date/time formatter, not only the number format.
   */
  @Test
  public void testPatternChangeInvalidatesDateTimeFormatter() {
    DefaultTableColumnConfiguration<Row,?> column = createColumn();
    column.setPattern("dd.MM.yyyy");
    assertEquals(column.getDateTimeFormatter().format(LocalDate.of(2026, 8, 7)), "07.08.2026");

    column.setPattern("yyyy-MM-dd");
    assertEquals(column.getDateTimeFormatter().format(LocalDate.of(2026, 8, 7)), "2026-08-07");
  }


  /**
   * Changing the pattern invalidates the number format as well.
   */
  @Test
  public void testPatternChangeInvalidatesNumberFormat() {
    DefaultTableColumnConfiguration<Row,?> column = createColumn();
    column.setPattern("#0.0");
    assertEquals(column.getNumberFormat().format(1.5), "1.5");

    column.setPattern("#0.000");
    assertEquals(column.getNumberFormat().format(1.5), "1.500");
  }


  /**
   * A scale without a pattern falls back to the integer pattern, but must not
   * store it in the pattern, which the date/time formatter is derived from as well.
   */
  @Test
  public void testScaleWithoutPatternKeepsPatternUnset() {
    DefaultTableColumnConfiguration<Row,?> column = createColumn();
    column.setScale(2);
    assertEquals(column.getNumberFormat().format(1.5), "1.50");

    assertNull(column.getPattern());
    assertNull(column.getDateTimeFormatter());
  }


  /**
   * The scale is derived from the pattern if not set explicitly.
   */
  @Test
  public void testScaleDerivedFromPattern() {
    DefaultTableColumnConfiguration<Row,?> column = createColumn();
    column.setPattern("#0.000");
    column.getNumberFormat();
    assertEquals(column.getScale(), Integer.valueOf(3));

    // a derived scale must not survive the next pattern, else it would override it
    column.setPattern("#0.0");
    assertEquals(column.getNumberFormat().format(1.5), "1.5");
    assertEquals(column.getScale(), Integer.valueOf(1));
  }


  /**
   * A scale configured by the application survives a pattern change.
   */
  @Test
  public void testConfiguredScaleSurvivesPatternChange() {
    DefaultTableColumnConfiguration<Row,?> column = createColumn();
    column.setScale(3);
    column.setPattern("#0.0");
    assertEquals(column.getScale(), Integer.valueOf(3));
    assertEquals(column.getNumberFormat().format(1.5), "1.500");
  }

}

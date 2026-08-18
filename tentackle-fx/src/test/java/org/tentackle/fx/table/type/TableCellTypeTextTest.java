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

package org.tentackle.fx.table.type;

import org.testng.annotations.Test;

import org.tentackle.fx.table.DefaultTableColumnConfiguration;
import org.tentackle.fx.table.DefaultTableConfiguration;

import java.time.LocalDate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests the display text delivered by the cell types outside a cell.<br>
 * This is what the column filters and the exports show.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TableCellTypeTextTest {

  private record Row(String name) { }

  private <T> DefaultTableColumnConfiguration<Row, T> createConfiguration(Class<T> type, String pattern) {
    DefaultTableConfiguration<Row> configuration = new DefaultTableConfiguration<>(Row.class, "rows");
    DefaultTableColumnConfiguration<Row, T> columnConfiguration =
      new DefaultTableColumnConfiguration<>(configuration, "column", "column");
    columnConfiguration.setType(type);
    columnConfiguration.setPattern(pattern);
    return columnConfiguration;
  }


  @Test
  public void testString() {
    DefaultTableColumnConfiguration<Row, String> config = createConfiguration(String.class, null);
    StringTableCellType cellType = new StringTableCellType();
    assertEquals(cellType.getText(config, "Hamburg"), "Hamburg");
    config.setCaseConversion(Boolean.TRUE);
    assertEquals(cellType.getText(config, "Hamburg"), "HAMBURG");
    config.setCaseConversion(Boolean.FALSE);
    assertEquals(cellType.getText(config, "Hamburg"), "hamburg");
  }

  @Test
  public void testNumberWithPattern() {
    DefaultTableColumnConfiguration<Row, Integer> config = createConfiguration(Integer.class, "#,##0.00");
    NumberTableCellType<Integer> cellType = new NumberTableCellType<>();
    assertEquals(cellType.getText(config, 1234), "1,234.00");   // tests run with locale en_US
  }

  @Test
  public void testNumberWithoutPattern() {
    // without a pattern the column has no number format, so the number renders unformatted
    DefaultTableColumnConfiguration<Row, Integer> config = createConfiguration(Integer.class, null);
    NumberTableCellType<Integer> cellType = new NumberTableCellType<>();
    assertEquals(cellType.getText(config, 1234), "1234");
  }

  @Test
  public void testLocalDate() {
    DefaultTableColumnConfiguration<Row, LocalDate> config = createConfiguration(LocalDate.class, "dd.MM.yyyy");
    LocalDateTableCellType cellType = new LocalDateTableCellType();
    assertEquals(cellType.getText(config, LocalDate.of(2026, 7, 24)), "24.07.2026");
  }

  @Test
  public void testBoolean() {
    DefaultTableColumnConfiguration<Row, Boolean> config = createConfiguration(Boolean.class, null);
    BooleanTableCellType cellType = new BooleanTableCellType();
    // a boolean is rendered as a graphic, but must still provide a text for filters and exports
    assertEquals(cellType.getText(config, Boolean.TRUE), "yes");
    assertEquals(cellType.getText(config, Boolean.FALSE), "no");
  }

  @Test
  public void testObjectFallback() {
    DefaultTableColumnConfiguration<Row, Object> config = createConfiguration(Object.class, null);
    ObjectTableCellType cellType = new ObjectTableCellType();
    assertEquals(cellType.getText(config, "any"), "any");
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void testEnum() {
    DefaultTableColumnConfiguration<Row, Enum> config = createConfiguration(Enum.class, null);
    EnumTableCellType cellType = new EnumTableCellType();
    assertEquals(cellType.getText(config, Thread.State.NEW), "NEW");
  }

  @Test
  public void testNullValueViaColumnConfiguration() {
    DefaultTableColumnConfiguration<Row, String> config = createConfiguration(String.class, null);
    config.setCellType(new StringTableCellType());
    assertNull(config.getText(null));
    assertEquals(config.getText("Hamburg"), "Hamburg");
  }

}

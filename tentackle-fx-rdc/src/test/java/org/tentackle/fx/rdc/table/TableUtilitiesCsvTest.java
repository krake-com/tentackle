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

package org.tentackle.fx.rdc.table;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.misc.CsvConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Unit tests for the default (CSV) spreadsheet export of {@link TableUtilities}.
 * <p>
 * No JavaFX toolkit is needed: table and tree table columns are created lazily by the column
 * configurations, but no {@code Control} is ever instantiated.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class TableUtilitiesCsvTest {

  private TableUtilities utilities;
  private Path file;

  @BeforeMethod
  public void setUp() throws IOException {
    utilities = new TableUtilities();
    file = Files.createTempFile("tentackle-csv", ".csv");
  }

  @AfterMethod
  public void tearDown() throws IOException {
    Files.deleteIfExists(file);
  }


  /**
   * Creates a bound table configuration.
   *
   * @param columnNames the column names; must be bindable attributes of {@link ExportRow}
   * @return the configuration
   */
  private DefaultTableConfiguration<ExportRow> createConfiguration(String... columnNames) {
    DefaultTableConfiguration<ExportRow> config = new DefaultTableConfiguration<>(ExportRow.class, "rows");
    for (String columnName : columnNames) {
      config.addColumn(columnName, columnName);
    }
    config.getBinder().bind();
    return config;
  }

  /**
   * Exports the rows and reads the CSV file back.
   * <p>
   * The raw lines are asserted rather than the result of {@link CsvConverter#fromCsv},
   * because the CSV representation drops <em>trailing</em> empty fields: a row written
   * correctly as {@code "a,c,"} parses back as two fields.
   *
   * @param config the table configuration
   * @param rows the rows to export
   * @return the lines of the file, the header being the first
   */
  private List<String> export(DefaultTableConfiguration<ExportRow> config, ExportRow... rows) throws IOException {
    utilities.toSpreadsheet(config.getColumnConfigurations(), file.toFile(), List.of(rows));
    return Files.readAllLines(file, StandardCharsets.UTF_8);
  }


  /**
   * Every row must provide one field per column, else the values would be read
   * under the wrong header.
   */
  @Test
  public void testNullValueKeepsTheFollowingColumnsAligned() throws IOException {
    DefaultTableConfiguration<ExportRow> config = createConfiguration("first", "second", "third");

    List<String> lines = export(config, new ExportRow("a", null, "c"));

    assertEquals(lines.get(0), "first,second,third");
    assertEquals(lines.get(1), "a,,c", "the null value must leave an empty field, not drop it");

    // an interior empty field survives the round trip, so the columns really line up
    List<List<String>> parsed = CsvConverter.getInstance().fromCsv(',', lines);
    assertEquals(parsed.get(1).size(), 3);
    assertEquals(parsed.get(1).get(2), "c", "the value after a null stays in its own column");
  }

  /**
   * Leading null values keep their fields as well.
   */
  @Test
  public void testLeadingNullValuesKeepTheirFields() throws IOException {
    DefaultTableConfiguration<ExportRow> config = createConfiguration("first", "second", "third");

    List<String> lines = export(config, new ExportRow(null, null, "c"));

    assertEquals(lines.get(1), ",,c");
  }

  /**
   * An unbound column has no value at all, but still occupies its position.
   */
  @Test
  public void testUnboundColumnKeepsTheFollowingColumnsAligned() throws IOException {
    // "unbound" is no attribute of the row object, hence it has no binding
    DefaultTableConfiguration<ExportRow> config = createConfiguration("first", "unbound", "third");
    assertNull(config.getColumnConfiguration("unbound").getBinding(), "the test column must be unbound");

    List<String> lines = export(config, new ExportRow("a", "b", "c"));

    assertEquals(lines.get(0), "first,unbound,third");
    assertEquals(lines.get(1), "a,,c", "the unbound column must leave an empty field, not drop it");
  }

  /**
   * An invisible column is left out of the header and of every row alike.
   */
  @Test
  public void testInvisibleColumnIsSkippedEverywhere() throws IOException {
    DefaultTableConfiguration<ExportRow> config = createConfiguration("first", "second", "third");
    config.getColumnConfiguration("second").getTableColumn().setVisible(false);

    List<String> lines = export(config, new ExportRow("a", "b", "c"));

    assertEquals(lines.get(0), "first,third");
    assertEquals(lines.get(1), "a,c");
  }

}

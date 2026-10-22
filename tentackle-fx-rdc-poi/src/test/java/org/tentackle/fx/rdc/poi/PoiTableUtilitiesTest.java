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

package org.tentackle.fx.rdc.poi;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.BMoney;
import org.tentackle.common.Time;
import org.tentackle.common.Timestamp;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.rdc.table.TableUtilities;
import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.fx.table.TableColumnConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link PoiTableUtilities}.
 * <p>
 * The tests build a real {@link DefaultTableConfiguration} with real bindings and export it to a
 * temporary workbook, which is then read back with POI and verified cell by cell.
 * No JavaFX toolkit is needed: table- and treetable columns are created lazily by the column
 * configurations, but no {@code Control} is ever instantiated.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PoiTableUtilitiesTest {

  /** the date format of exported dates: the builtin "m/d/yy". */
  private static final short DATE_FORMAT = HSSFDataFormat.getBuiltinFormat("m/d/yy");

  /** the format of exported date-and-time values: the builtin "m/d/yy h:mm". */
  private static final short DATE_TIME_FORMAT = HSSFDataFormat.getBuiltinFormat("m/d/yy h:mm");

  /** the format of exported time values: the builtin "h:mm:ss". */
  private static final short TIME_FORMAT = HSSFDataFormat.getBuiltinFormat("h:mm:ss");

  private PoiTableUtilities utilities;
  private Path file;
  private HSSFWorkbook workbook;    // the workbook read back from the exported file

  @BeforeMethod
  public void setUp() throws IOException {
    utilities = new PoiTableUtilities();
    file = Files.createTempFile("tentackle-poi", ".xls");
  }

  @AfterMethod
  public void tearDown() throws IOException {
    if (workbook != null) {
      workbook.close();
      workbook = null;
    }
    Files.deleteIfExists(file);
  }


  /**
   * Creates a bound table configuration.
   *
   * @param columnNames the column names; must be bindable attributes of {@link SpreadsheetRow}
   * @return the configuration
   */
  private DefaultTableConfiguration<SpreadsheetRow> createConfiguration(String... columnNames) {
    DefaultTableConfiguration<SpreadsheetRow> config = new DefaultTableConfiguration<>(SpreadsheetRow.class, "rows");
    for (String columnName : columnNames) {
      config.addColumn(columnName, columnName.toUpperCase(Locale.ROOT));
    }
    config.getBinder().bind();
    return config;
  }

  /**
   * Exports the rows and reads the workbook back.
   *
   * @param config the table configuration
   * @param rows the rows to export
   * @return the sheet of the exported workbook
   */
  private HSSFSheet export(DefaultTableConfiguration<SpreadsheetRow> config, SpreadsheetRow... rows) throws IOException {
    utilities.toSpreadsheet(config.getColumnConfigurations(), file.toFile(), List.of(rows));
    try (InputStream in = Files.newInputStream(file)) {
      workbook = new HSSFWorkbook(in);    // closed by tearDown
      return workbook.getSheetAt(0);
    }
  }

  /**
   * Creates a date without a millisecond fraction (Excel stores dates as days since 1900).
   *
   * @param year the year
   * @param month the month, 1-12
   * @param day the day of the month
   * @return the date
   */
  private Date createDate(int year, int month, int day) {
    return createCalendar(year, month, day).getTime();
  }

  /**
   * Creates a calendar without a millisecond fraction.
   *
   * @param year the year
   * @param month the month, 1-12
   * @param day the day of the month
   * @return the calendar
   */
  private Calendar createCalendar(int year, int month, int day) {
    Calendar cal = Calendar.getInstance(TimeZone.getDefault());
    cal.clear();
    cal.set(year, month - 1, day, 12, 0, 0);
    return cal;
  }


  @Test
  public void testHeaderRowIsStyled() throws IOException {
    HSSFSheet sheet = export(createConfiguration("name", "count"), new SpreadsheetRow());

    HSSFRow header = sheet.getRow(0);
    assertEquals(header.getCell(0).getStringCellValue(), "NAME");
    assertEquals(header.getCell(1).getStringCellValue(), "COUNT");

    HSSFCellStyle style = header.getCell(0).getCellStyle();
    assertEquals(style.getAlignment(), HorizontalAlignment.CENTER, "headers are centered");
    HSSFFont font = sheet.getWorkbook().getFontAt(style.getFontIndex());
    assertTrue(font.getBold(), "headers are bold");
    assertTrue(font.getItalic(), "headers are italic");

    assertEquals(header.getCell(1).getCellStyle().getIndex(), style.getIndex(), "all headers share one style");
  }

  @Test
  public void testEmptyItemListWritesHeaderOnly() throws IOException {
    HSSFSheet sheet = export(createConfiguration("name"));
    assertEquals(sheet.getLastRowNum(), 0, "only the header row");
    assertEquals(sheet.getRow(0).getCell(0).getStringCellValue(), "NAME");
  }

  @Test
  public void testValuesKeepTheirNativeCellType() throws IOException {
    SpreadsheetRow row = new SpreadsheetRow();
    row.setName("some text");
    row.setPaid(Boolean.TRUE);
    row.setCount(42);
    row.setRate(3.5);
    row.setStatus(SpreadsheetRow.Status.CLOSED);

    HSSFRow cells = export(createConfiguration("name", "paid", "count", "rate", "status"), row).getRow(1);

    assertEquals(cells.getCell(0).getCellType(), CellType.STRING);
    assertEquals(cells.getCell(0).getStringCellValue(), "some text");

    assertEquals(cells.getCell(1).getCellType(), CellType.BOOLEAN);
    assertTrue(cells.getCell(1).getBooleanCellValue());

    assertEquals(cells.getCell(2).getCellType(), CellType.NUMERIC);
    assertEquals(cells.getCell(2).getNumericCellValue(), 42.0);

    assertEquals(cells.getCell(3).getCellType(), CellType.NUMERIC);
    assertEquals(cells.getCell(3).getNumericCellValue(), 3.5);

    // no native cell type: exported as the text the table displays
    assertEquals(cells.getCell(4).getCellType(), CellType.STRING);
    assertEquals(cells.getCell(4).getStringCellValue(), "CLOSED");
  }

  @Test
  public void testMoneyCellsAreNumericWithScaledFormat() throws IOException {
    SpreadsheetRow row = new SpreadsheetRow();
    row.setPrice(new BMoney(12.34, 2));
    row.setTax(new BMoney(7.0, 0));

    HSSFRow cells = export(createConfiguration("price", "tax"), row).getRow(1);

    assertEquals(cells.getCell(0).getCellType(), CellType.NUMERIC);
    assertEquals(cells.getCell(0).getNumericCellValue(), 12.34);
    assertEquals(cells.getCell(0).getCellStyle().getDataFormatString(), "#,##0.00", "two decimal digits");

    assertEquals(cells.getCell(1).getNumericCellValue(), 7.0);
    assertEquals(cells.getCell(1).getCellStyle().getDataFormatString(), "#,##0", "no decimal digits at scale 0");
  }

  @Test
  public void testMoneyCellStylesAreReusedPerScale() throws IOException {
    SpreadsheetRow first = new SpreadsheetRow();
    first.setPrice(new BMoney(1.0, 2));
    first.setDiscount(new BMoney(2.0, 2));
    first.setTax(new BMoney(3.0, 3));
    SpreadsheetRow second = new SpreadsheetRow();
    second.setPrice(new BMoney(4.0, 2));
    second.setDiscount(new BMoney(5.0, 2));
    second.setTax(new BMoney(6.0, 3));

    HSSFSheet sheet = export(createConfiguration("price", "discount", "tax"), first, second);

    HSSFCellStyle priceStyle = sheet.getRow(1).getCell(0).getCellStyle();
    assertEquals(sheet.getRow(1).getCell(1).getCellStyle().getIndex(), priceStyle.getIndex(),
                 "same scale within a row shares the style");
    assertEquals(sheet.getRow(2).getCell(0).getCellStyle().getIndex(), priceStyle.getIndex(),
                 "same scale across rows shares the style");
    assertEquals(sheet.getRow(2).getCell(1).getCellStyle().getIndex(), priceStyle.getIndex());

    HSSFCellStyle taxStyle = sheet.getRow(1).getCell(2).getCellStyle();
    assertNotEquals(taxStyle.getIndex(), priceStyle.getIndex(), "another scale needs another style");
    assertEquals(taxStyle.getDataFormatString(), "#,##0.000");
    assertEquals(sheet.getRow(2).getCell(2).getCellStyle().getIndex(), taxStyle.getIndex());
  }

  @Test
  public void testDatesAndCalendarsUseTheBuiltinDateFormat() throws IOException {
    Date date = createDate(2026, 7, 27);
    Calendar calendar = createCalendar(2025, 12, 24);
    SpreadsheetRow row = new SpreadsheetRow();
    row.setDate(date);
    row.setCalendar(calendar);

    HSSFRow cells = export(createConfiguration("date", "calendar"), row).getRow(1);

    assertEquals(cells.getCell(0).getCellType(), CellType.NUMERIC, "dates are numeric cells");
    assertEquals(cells.getCell(0).getDateCellValue(), date);
    assertEquals(cells.getCell(0).getCellStyle().getDataFormat(), DATE_FORMAT);

    assertEquals(cells.getCell(1).getDateCellValue(), calendar.getTime());
    assertEquals(cells.getCell(1).getCellStyle().getDataFormat(), DATE_FORMAT);

    assertEquals(cells.getCell(1).getCellStyle().getIndex(), cells.getCell(0).getCellStyle().getIndex(),
                 "dates and calendars share one style");
  }

  @Test
  public void testJavaTimeDatesUseTheBuiltinDateFormats() throws IOException {
    SpreadsheetRow row = new SpreadsheetRow();
    row.setLocalDate(LocalDate.of(2026, 8, 7));
    row.setLocalDateTime(LocalDateTime.of(2026, 8, 7, 14, 30));

    HSSFRow cells = export(createConfiguration("localDate", "localDateTime"), row).getRow(1);

    // a date must become a real date cell, not the text of its toString()
    assertEquals(cells.getCell(0).getCellType(), CellType.NUMERIC);
    assertEquals(cells.getCell(0).getLocalDateTimeCellValue(), LocalDate.of(2026, 8, 7).atStartOfDay());
    assertEquals(cells.getCell(0).getCellStyle().getDataFormat(), DATE_FORMAT);

    assertEquals(cells.getCell(1).getCellType(), CellType.NUMERIC);
    assertEquals(cells.getCell(1).getLocalDateTimeCellValue(), LocalDateTime.of(2026, 8, 7, 14, 30));
    assertEquals(cells.getCell(1).getCellStyle().getDataFormat(), DATE_TIME_FORMAT);
  }

  @Test
  public void testZonedAndOffsetDatesAreExportedAsLocalDateTime() throws IOException {
    LocalDateTime local = LocalDateTime.of(2026, 8, 7, 9, 15);
    ZoneOffset offset = ZoneOffset.ofHours(3);
    SpreadsheetRow row = new SpreadsheetRow();
    row.setOffsetDateTime(OffsetDateTime.of(local, offset));
    row.setZonedDateTime(ZonedDateTime.of(local, offset));
    // a spreadsheet has no notion of a time zone: the instant is resolved in the default zone
    Instant instant = local.atZone(ZoneId.systemDefault()).toInstant();
    row.setInstant(instant);

    HSSFRow cells = export(createConfiguration("offsetDateTime", "zonedDateTime", "instant"), row).getRow(1);

    for (int index = 0; index < 3; index++) {
      assertEquals(cells.getCell(index).getCellType(), CellType.NUMERIC, "cell " + index);
      assertEquals(cells.getCell(index).getLocalDateTimeCellValue(), local, "cell " + index);
      assertEquals(cells.getCell(index).getCellStyle().getDataFormat(), DATE_TIME_FORMAT, "cell " + index);
    }
  }

  @Test
  public void testTimesAreExportedAsFractionOfADay() throws IOException {
    SpreadsheetRow row = new SpreadsheetRow();
    row.setLocalTime(LocalTime.of(6, 0));                                  // a quarter of a day
    row.setOffsetTime(OffsetTime.of(LocalTime.of(18, 0), ZoneOffset.UTC)); // three quarters

    HSSFRow cells = export(createConfiguration("localTime", "offsetTime"), row).getRow(1);

    assertEquals(cells.getCell(0).getCellType(), CellType.NUMERIC);
    assertEquals(cells.getCell(0).getNumericCellValue(), 0.25);
    assertEquals(cells.getCell(0).getCellStyle().getDataFormat(), TIME_FORMAT);

    assertEquals(cells.getCell(1).getCellType(), CellType.NUMERIC);
    assertEquals(cells.getCell(1).getNumericCellValue(), 0.75);
    assertEquals(cells.getCell(1).getCellStyle().getDataFormat(), TIME_FORMAT);
  }

  @Test
  public void testSqlTimesAndTimestampsAreNotExportedAsDates() throws IOException {
    // both extend java.util.Date, but a date-only format would hide the time
    // or, for a time, show the epoch date
    Time time = Time.valueOf("18:00:00");
    Timestamp timestamp = Timestamp.valueOf("2026-08-07 14:30:00");
    SpreadsheetRow row = new SpreadsheetRow();
    row.setTime(time);
    row.setTimestamp(timestamp);

    HSSFRow cells = export(createConfiguration("time", "timestamp"), row).getRow(1);

    assertEquals(cells.getCell(0).getCellType(), CellType.NUMERIC);
    assertEquals(cells.getCell(0).getNumericCellValue(), 0.75, "a time is the fraction of a day");
    assertEquals(cells.getCell(0).getCellStyle().getDataFormat(), TIME_FORMAT);

    assertEquals(cells.getCell(1).getCellType(), CellType.NUMERIC);
    assertEquals(cells.getCell(1).getLocalDateTimeCellValue(), LocalDateTime.of(2026, 8, 7, 14, 30));
    assertEquals(cells.getCell(1).getCellStyle().getDataFormat(), DATE_TIME_FORMAT);
  }

  @Test
  public void testTextCellsShowTheTextDisplayedInTheTable() throws IOException {
    DefaultTableConfiguration<SpreadsheetRow> config = createConfiguration("name");
    config.getColumnConfiguration("name").setCaseConversion(Boolean.TRUE);   // displayed in uppercase
    SpreadsheetRow row = new SpreadsheetRow();
    row.setName("Mixed Case");

    HSSFRow cells = export(config, row).getRow(1);

    assertEquals(cells.getCell(0).getCellType(), CellType.STRING);
    assertEquals(cells.getCell(0).getStringCellValue(), "MIXED CASE", "same text as in the table, not toString()");
  }

  @Test
  public void testTooManyRowsAreRejectedWithAClearMessage() {
    DefaultTableConfiguration<SpreadsheetRow> config = createConfiguration("name");
    // one row too many: the header takes the last free line of the sheet
    List<SpreadsheetRow> items = Collections.nCopies(SpreadsheetVersion.EXCEL97.getMaxRows(), new SpreadsheetRow());

    RdcRuntimeException ex = expectThrows(RdcRuntimeException.class,
        () -> utilities.toSpreadsheet(config.getColumnConfigurations(), file.toFile(), items));
    assertTrue(ex.getMessage().contains("too many rows"), ex.getMessage());
  }

  @Test
  public void testMaximumNumberOfRowsIsAccepted() throws IOException {
    DefaultTableConfiguration<SpreadsheetRow> config = createConfiguration("name");
    List<SpreadsheetRow> items = Collections.nCopies(SpreadsheetVersion.EXCEL97.getMaxRows() - 1, new SpreadsheetRow());

    utilities.toSpreadsheet(config.getColumnConfigurations(), file.toFile(), items);

    try (InputStream in = Files.newInputStream(file)) {
      workbook = new HSSFWorkbook(in);
      assertEquals(workbook.getSheetAt(0).getLastRowNum(), SpreadsheetVersion.EXCEL97.getLastRowIndex());
    }
  }

  @Test
  public void testTooManyColumnsAreRejectedWithAClearMessage() {
    DefaultTableConfiguration<SpreadsheetRow> config = createConfiguration("name");
    // unbound columns are enough here, the limit is checked before anything is written
    for (int index = 0; index < SpreadsheetVersion.EXCEL97.getMaxColumns(); index++) {
      config.addColumn("filler" + index, "FILLER" + index);
    }

    RdcRuntimeException ex = expectThrows(RdcRuntimeException.class,
        () -> utilities.toSpreadsheet(config.getColumnConfigurations(), file.toFile(),
                                      List.of(new SpreadsheetRow())));
    assertTrue(ex.getMessage().contains("too many columns"), ex.getMessage());
  }

  @Test
  public void testInvisibleColumnsDoNotCountTowardsTheColumnLimit() throws IOException {
    DefaultTableConfiguration<SpreadsheetRow> config = createConfiguration("name");
    for (int index = 0; index < SpreadsheetVersion.EXCEL97.getMaxColumns(); index++) {
      config.addColumn("filler" + index, "FILLER" + index).getTableColumn().setVisible(false);
    }

    HSSFSheet sheet = export(config, new SpreadsheetRow());

    assertEquals(sheet.getRow(0).getLastCellNum(), 1, "only the visible column is exported");
  }

  @Test
  public void testNullValuesLeaveBlankCells() throws IOException {
    SpreadsheetRow row = new SpreadsheetRow();
    row.setCount(1);
    // name and price are null

    HSSFRow cells = export(createConfiguration("name", "count", "price"), row).getRow(1);

    assertEquals(cells.getCell(0).getCellType(), CellType.BLANK);
    assertEquals(cells.getCell(1).getNumericCellValue(), 1.0, "the column after a null value keeps its index");
    assertEquals(cells.getCell(2).getCellType(), CellType.BLANK);
  }

  @Test
  public void testInvisibleTableColumnIsSkipped() throws IOException {
    DefaultTableConfiguration<SpreadsheetRow> config = createConfiguration("name", "count", "rate");
    config.getColumnConfiguration("count").getTableColumn().setVisible(false);

    SpreadsheetRow row = new SpreadsheetRow();
    row.setName("visible");
    row.setCount(99);
    row.setRate(1.5);

    HSSFSheet sheet = export(config, row);

    assertEquals(sheet.getRow(0).getLastCellNum(), 2, "only two columns are exported");
    assertEquals(sheet.getRow(0).getCell(0).getStringCellValue(), "NAME");
    assertEquals(sheet.getRow(0).getCell(1).getStringCellValue(), "RATE");
    assertEquals(sheet.getRow(1).getCell(0).getStringCellValue(), "visible");
    assertEquals(sheet.getRow(1).getCell(1).getNumericCellValue(), 1.5, "the hidden column leaves no gap");
  }

  @Test
  public void testInvisibleTreeTableColumnIsSkipped() throws IOException {
    // a column is visible only if it is visible in both the table and the treetable
    DefaultTableConfiguration<SpreadsheetRow> config = createConfiguration("name", "count");
    config.getColumnConfiguration("name").getTreeTableColumn().setVisible(false);

    SpreadsheetRow row = new SpreadsheetRow();
    row.setName("hidden");
    row.setCount(7);

    HSSFSheet sheet = export(config, row);

    assertEquals(sheet.getRow(0).getLastCellNum(), 1);
    assertEquals(sheet.getRow(0).getCell(0).getStringCellValue(), "COUNT");
    assertEquals(sheet.getRow(1).getCell(0).getNumericCellValue(), 7.0);
  }

  @Test
  public void testUnboundColumnKeepsItsHeaderButExportsNoValue() throws IOException {
    DefaultTableConfiguration<SpreadsheetRow> config = createConfiguration("name", "count");
    // "unbound" is no attribute of the row object, hence it has no binding
    TableColumnConfiguration<SpreadsheetRow, ?> unbound = config.addColumn("unbound", "UNBOUND");
    assertNull(unbound.getBinding(), "the test column must be unbound");

    SpreadsheetRow row = new SpreadsheetRow();
    row.setName("some text");
    row.setCount(5);

    HSSFSheet sheet = export(config, row);

    assertEquals(sheet.getRow(0).getCell(2).getStringCellValue(), "UNBOUND", "the header is written anyway");
    assertNull(sheet.getRow(1).getCell(2), "no cell is created for an unbound column");
    assertEquals(sheet.getRow(1).getCell(0).getStringCellValue(), "some text");
    assertEquals(sheet.getRow(1).getCell(1).getNumericCellValue(), 5.0);
  }

  @Test
  public void testRowsAreExportedInOrder() throws IOException {
    SpreadsheetRow first = new SpreadsheetRow();
    first.setName("first");
    SpreadsheetRow second = new SpreadsheetRow();
    second.setName("second");
    SpreadsheetRow third = new SpreadsheetRow();
    third.setName("third");

    HSSFSheet sheet = export(createConfiguration("name"), first, second, third);

    assertEquals(sheet.getLastRowNum(), 3, "header plus three rows");
    assertEquals(sheet.getRow(1).getCell(0).getStringCellValue(), "first");
    assertEquals(sheet.getRow(2).getCell(0).getStringCellValue(), "second");
    assertEquals(sheet.getRow(3).getCell(0).getStringCellValue(), "third");
  }

  @Test
  public void testUnwritableFileThrowsRdcRuntimeException() throws IOException {
    DefaultTableConfiguration<SpreadsheetRow> config = createConfiguration("name");
    Path directory = Files.createTempDirectory("tentackle-poi");
    try {
      // a directory can't be opened for writing
      assertThrows(RdcRuntimeException.class,
                   () -> utilities.toSpreadsheet(config.getColumnConfigurations(), directory.toFile(),
                                                 List.of(new SpreadsheetRow())));
    }
    finally {
      Files.deleteIfExists(directory);
    }
  }

  @Test
  public void testSelectSpreadsheetFileAsksForXlsFiles() {
    TestRdcUtilities rdcUtilities = TestRdcUtilities.instance();
    File selectedFile = file.toFile();
    rdcUtilities.setSelectedFile(selectedFile);

    assertSame(utilities.selectSpreadsheetFile("someTable", null), selectedFile);

    assertEquals(rdcUtilities.getPrefName(), "lastXlsNames/someTable", "the last name is remembered per table");
    assertEquals(rdcUtilities.getPrefKey(), "path");
    assertEquals(rdcUtilities.getFileExtension(), ".xls", "the POI extension exports xls, not csv");
    assertEquals(rdcUtilities.getFileType(), RdcFxRdcBundle.getString("SPREADSHEET FILE"));
    assertNull(rdcUtilities.getOwner());
  }

  @Test
  public void testSelectSpreadsheetFileWithoutTableNameTrimsTheSeparator() {
    TestRdcUtilities rdcUtilities = TestRdcUtilities.instance();
    rdcUtilities.setSelectedFile(null);

    assertNull(utilities.selectSpreadsheetFile("", null), "aborted selection returns null");
    assertEquals(rdcUtilities.getPrefName(), "lastXlsNames", "the trailing separator is trimmed");
  }

  @Test
  public void testServiceReplacesTheDefaultTableUtilities() {
    TableUtilities instance = TableUtilities.getInstance();
    assertNotNull(instance);
    assertEquals(instance.getClass(), PoiTableUtilities.class,
                 "@Service(TableUtilities.class) must make this module's implementation the singleton");
  }

  @Test
  public void testWorkbookContainsExactlyOneSheet() throws IOException {
    utilities.toSpreadsheet(createConfiguration("name").getColumnConfigurations(),
                            file.toFile(), List.of(new SpreadsheetRow()));
    try (InputStream in = Files.newInputStream(file); HSSFWorkbook wb = new HSSFWorkbook(in)) {
      assertEquals(wb.getNumberOfSheets(), 1);
      HSSFCell cell = wb.getSheetAt(0).getRow(0).getCell(0);
      assertEquals(cell.getStringCellValue(), "NAME");
    }
  }

}

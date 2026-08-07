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
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

import org.tentackle.common.BMoney;
import org.tentackle.common.ClasspathFirst;
import org.tentackle.common.Service;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.rdc.RdcUtilities;
import org.tentackle.fx.rdc.table.TableUtilities;
import org.tentackle.fx.table.TableColumnConfiguration;

import javafx.stage.Stage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Table-related utility methods with POI extension to create XLS files.
 *
 * @author harald
 */
@Service(TableUtilities.class)
@ClasspathFirst(TableUtilities.class)   // POI isn't modularized yet, but fx-rdc is
public class PoiTableUtilities extends TableUtilities {

  private static final String SPREADSHEET_EXTENSION = ".xls";
  private static final String LAST_SPREADSHEET_PREFIX = "lastXlsNames/";
  private static final String SPREADSHEET_KEY = "path";

  /** the spreadsheet version written by {@link HSSFWorkbook}, i.e. BIFF8. */
  private static final SpreadsheetVersion SPREADSHEET_VERSION = SpreadsheetVersion.EXCEL97;

  /** number of seconds per day, to convert a time to the fraction of a day. */
  private static final int SECONDS_PER_DAY = 24 * 60 * 60;


  @Override
  public File selectSpreadsheetFile(String tableName, Stage owner) {
    return RdcUtilities.getInstance().selectFile(
        StringHelper.trimRight(LAST_SPREADSHEET_PREFIX + tableName, '/'),
        SPREADSHEET_KEY, SPREADSHEET_EXTENSION,
        RdcFxRdcBundle.getString("SPREADSHEET FILE"), owner);
  }

  @Override
  protected <S> void toSpreadsheet(Collection<TableColumnConfiguration<S,?>> columnConfigurations, File file, List<S> items) {

    // the visibility cannot change while exporting, so determine it once instead of per cell
    List<TableColumnConfiguration<S, ?>> visibleColumns = new ArrayList<>();
    for (TableColumnConfiguration<S, ?> tc : columnConfigurations) {
      if (isColumnVisible(tc)) {
        visibleColumns.add(tc);
      }
    }
    assertFitsIntoSheet(visibleColumns.size(), items.size() + 1);   // + the header row

    try (HSSFWorkbook wb = new HSSFWorkbook()) {
      HSSFSheet sheet = wb.createSheet();

      int srow = 0;   // current spreadsheet row

      // column headers
      HSSFRow row = sheet.createRow(srow++);
      HSSFFont font = wb.createFont();
      font.setItalic(true);
      font.setBold(true);
      HSSFCellStyle headerStyle = wb.createCellStyle();
      headerStyle.setAlignment(HorizontalAlignment.CENTER);
      headerStyle.setFont(font);
      int columnIndex = 0;
      for (TableColumnConfiguration<S, ?> tc : visibleColumns) {
        HSSFCell cell = row.createCell(columnIndex);
        cell.setCellValue(new HSSFRichTextString(tc.getDisplayedName()));
        cell.setCellStyle(headerStyle);
        columnIndex++;
      }

      // default cell-styles for the date- and time types
      HSSFCellStyle dateStyle = createFormatStyle(wb, "m/d/yy");
      HSSFCellStyle dateTimeStyle = createFormatStyle(wb, "m/d/yy h:mm");
      HSSFCellStyle timeStyle = createFormatStyle(wb, "h:mm:ss");

      // cell styles for numbers, mapped by their data format
      Map<Short, HSSFCellStyle> numberStyles = new HashMap<>();
      HSSFDataFormat format = wb.createDataFormat();

      for (S item : items) {

        row = sheet.createRow(srow++);
        columnIndex = 0;

        for (TableColumnConfiguration<S, ?> tc : visibleColumns) {
          FxTableBinding<S, ?> binding = tc.getBinding();
          if (binding != null) {
            binding.setBoundRootObject(item);
            Object value = binding.getModelValue();

            HSSFCell cell = row.createCell(columnIndex);

            if (value instanceof Boolean boolValue) {
              cell.setCellValue(boolValue);
            }
            else if (value instanceof BMoney money) {
              cell.setCellValue(money.doubleValue());

              String fmt = "#,##0";
              if (money.scale() > 0) {
                fmt += '.' + "0".repeat(money.scale());
              }
              // create the format and reuse the style of an already exported scale, if any
              short fmtIndex = format.getFormat(fmt);
              cell.setCellStyle(numberStyles.computeIfAbsent(fmtIndex, index -> {
                HSSFCellStyle numberStyle = wb.createCellStyle();
                numberStyle.setDataFormat(index);
                return numberStyle;
              }));
            }
            else if (value instanceof Number) {
              cell.setCellValue(((Number) value).doubleValue());
            }
            else if (value instanceof Date) {
              cell.setCellValue((Date) value);
              cell.setCellStyle(dateStyle);
            }
            else if (value instanceof Calendar) {
              cell.setCellValue((Calendar) value);
              cell.setCellStyle(dateStyle);
            }
            else if (value instanceof LocalDate localDate) {
              cell.setCellValue(localDate);
              cell.setCellStyle(dateStyle);
            }
            else if (value instanceof LocalDateTime localDateTime) {
              cell.setCellValue(localDateTime);
              cell.setCellStyle(dateTimeStyle);
            }
            else if (value instanceof OffsetDateTime offsetDateTime) {
              cell.setCellValue(offsetDateTime.toLocalDateTime());
              cell.setCellStyle(dateTimeStyle);
            }
            else if (value instanceof ZonedDateTime zonedDateTime) {
              cell.setCellValue(zonedDateTime.toLocalDateTime());
              cell.setCellStyle(dateTimeStyle);
            }
            else if (value instanceof Instant instant) {
              cell.setCellValue(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
              cell.setCellStyle(dateTimeStyle);
            }
            else if (value instanceof LocalTime localTime) {
              cell.setCellValue(toDayFraction(localTime));
              cell.setCellStyle(timeStyle);
            }
            else if (value instanceof OffsetTime offsetTime) {
              cell.setCellValue(toDayFraction(offsetTime.toLocalTime()));
              cell.setCellStyle(timeStyle);
            }
            else if (value != null) {
              cell.setCellValue(new HSSFRichTextString(value.toString()));
            }
          }
          columnIndex++;
        }
      }

      try (FileOutputStream fileOut = new FileOutputStream(file)) {
        wb.write(fileOut);
      }
    }
    catch (IOException ex) {
      throw new RdcRuntimeException("creating spreadsheet file failed", ex);
    }
  }


  /**
   * Asserts that the data fits into a sheet of the {@value #SPREADSHEET_EXTENSION} format.<br>
   * Without this check, POI would fail with an {@link IllegalArgumentException} naming nothing
   * but the offending row- or column index.
   *
   * @param columnCount the number of columns to export
   * @param rowCount the number of rows to export, including the header row
   * @throws RdcRuntimeException if the data exceeds the format's limits
   */
  protected void assertFitsIntoSheet(int columnCount, int rowCount) {
    if (rowCount > SPREADSHEET_VERSION.getMaxRows()) {
      throw new RdcRuntimeException("too many rows for the " + SPREADSHEET_EXTENSION + " format: " +
                                    rowCount + " > " + SPREADSHEET_VERSION.getMaxRows());
    }
    if (columnCount > SPREADSHEET_VERSION.getMaxColumns()) {
      throw new RdcRuntimeException("too many columns for the " + SPREADSHEET_EXTENSION + " format: " +
                                    columnCount + " > " + SPREADSHEET_VERSION.getMaxColumns());
    }
  }


  /**
   * Creates a cell style for a builtin data format.
   *
   * @param wb the workbook
   * @param format the builtin format, see {@link HSSFDataFormat#getBuiltinFormat(String)}
   * @return the cell style
   */
  private HSSFCellStyle createFormatStyle(HSSFWorkbook wb, String format) {
    HSSFCellStyle style = wb.createCellStyle();
    style.setDataFormat(HSSFDataFormat.getBuiltinFormat(format));
    return style;
  }

  /**
   * Converts a time to the fraction of a day, which is how a spreadsheet stores a time.<br>
   * Notice that a spreadsheet time has no sub-second precision.
   *
   * @param time the time
   * @return the fraction of the day, 0.0 &le; fraction &lt; 1.0
   */
  private double toDayFraction(LocalTime time) {
    return time.toSecondOfDay() / (double) SECONDS_PER_DAY;
  }

}

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

import javafx.stage.Stage;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

import org.tentackle.common.BMoney;
import org.tentackle.common.ClasspathFirst;
import org.tentackle.common.Service;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.fx.rdc.FileUtilities;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.rdc.table.TableUtilities;
import org.tentackle.fx.table.TableColumnConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


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


  @Override
  public File selectSpreadsheetFile(String tableName, Stage owner) {
    return FileUtilities.getInstance().selectFile(
        StringHelper.trimRight(LAST_SPREADSHEET_PREFIX + tableName, '/'),
        SPREADSHEET_KEY, SPREADSHEET_EXTENSION,
        RdcFxRdcBundle.getString("SPREADSHEET FILE"), owner);
  }

  @Override
  protected <S> void toSpreadsheet(Collection<TableColumnConfiguration<S,?>> columnConfigurations, File file, List<S> items) {

    try (HSSFWorkbook wb = new HSSFWorkbook()) {
      HSSFSheet sheet = wb.createSheet();

      int srow = 0;   // current spreadsheet row

      // column headers
      HSSFRow row = sheet.createRow(srow++);
      HSSFFont font = wb.createFont();
      font.setItalic(true);
      font.setBold(true);
      HSSFCellStyle cs = wb.createCellStyle();
      cs.setAlignment(HorizontalAlignment.CENTER);
      cs.setFont(font);
      int columnIndex = 0;
      for (TableColumnConfiguration<S, ?> tc : columnConfigurations) {
        if (isColumnVisible(tc)) {
          HSSFCell cell = row.createCell(columnIndex);
          cell.setCellValue(new HSSFRichTextString(tc.getDisplayedName()));
          cell.setCellStyle(cs);
          columnIndex++;
        }
      }

      // default cell-style for date
      HSSFCellStyle dateStyle = wb.createCellStyle();
      dateStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("m/d/yy"));

      // cellstyles for numbers
      List<HSSFCellStyle> numberStyles = new ArrayList<>();
      HSSFDataFormat format = wb.createDataFormat();

      for (S item : items) {

        row = sheet.createRow(srow++);
        columnIndex = 0;

        for (TableColumnConfiguration<S, ?> tc : columnConfigurations) {
          if (isColumnVisible(tc)) {
            FxTableBinding<S, ?> binding = tc.getBinding();
            if (binding != null) {
              binding.setBoundRootObject(item);
              Object value = binding.getModelValue();

              HSSFCell cell = row.createCell(columnIndex);

              if (value instanceof Boolean) {
                cell.setCellValue(((Boolean) value));
              }
              else if (value instanceof BMoney) {
                BMoney money = (BMoney) value;
                cell.setCellValue(money.doubleValue());

                String fmt = "#,##0";
                if (money.scale() > 0) {
                  fmt += '.' + "0".repeat(money.scale());
                }
                // create format
                short fmtIndex = format.getFormat(fmt);

                // check if there is already a cellstyle with this scale
                Iterator<HSSFCellStyle> iter = numberStyles.iterator();
                boolean found = false;
                while (iter.hasNext()) {
                  cs = iter.next();
                  if (cs.getDataFormat() == fmtIndex) {
                    // reuse that
                    found = true;
                    break;
                  }
                }
                if (!found) {
                  // create a new style
                  cs = wb.createCellStyle();
                  cs.setDataFormat(fmtIndex);
                  numberStyles.add(cs);
                }
                cell.setCellStyle(cs);
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
              else if (value != null) {
                cell.setCellValue(new HSSFRichTextString(value.toString()));
              }
            }
            columnIndex++;
          }
        }
      }

      try (FileOutputStream fileOut = new FileOutputStream(file)) {
        wb.write(fileOut);
      }
    }
    catch (IOException ex) {
      throw new FxRuntimeException("creating spreadsheet file failed", ex);
    }
  }

}

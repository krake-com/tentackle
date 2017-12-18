/*
 * Tentackle - http://www.tentackle.org
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import javafx.print.PrinterJob;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
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
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

interface TableUtilities$Singleton {
  TableUtilities INSTANCE = ServiceFactory.createService(TableUtilities.class);
}

/**
 * Table-related utility methods.
 *
 * @author harald
 */
@Service(TableUtilities.class)    // defaults to self
public class TableUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static TableUtilities getInstance() {
    return TableUtilities$Singleton.INSTANCE;
  }


  /**
   * Converts the table to a spreadsheet file.
   *
   * @param <S> the row type
   * @param table the table view
   * @param file the output file
   * @param onlySelected true if export only selected rows
   */
  public <S> void toSpreadsheet(FxTableView<S> table, File file, boolean onlySelected) {

    HSSFWorkbook wb = new HSSFWorkbook();
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
    for (TableColumnConfiguration<S, ?> tc : table.getConfiguration().getColumnConfigurations()) {
      HSSFCell cell = row.createCell(columnIndex);
      cell.setCellValue(new HSSFRichTextString(tc.getDisplayedName()));
      cell.setCellStyle(cs);
      columnIndex++;
    }

    // default cell-style for date
    HSSFCellStyle dateStyle = wb.createCellStyle();
    dateStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("m/d/yy"));

    // cellstyles for numbers
    List<HSSFCellStyle> numberStyles = new ArrayList<>();
    HSSFDataFormat format = wb.createDataFormat();

    List<S> items = onlySelected ? table.getSelectionModel().getSelectedItems() : table.getItems();

    for (S item : items) {

      row = sheet.createRow(srow++);
      columnIndex = 0;

      for (TableColumnConfiguration<S, ?> tc : table.getConfiguration().getColumnConfigurations()) {

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
              StringBuilder buf = new StringBuilder(fmt).append('.');
              for (int j = 0; j < money.scale(); j++) {
                buf.append('0');
              }
              fmt = buf.toString();
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
          else if (value instanceof GregorianCalendar) {
            cell.setCellValue((GregorianCalendar) value);
            cell.setCellStyle(dateStyle);
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
    catch (IOException ex) {
      throw new FxRuntimeException("creating spreadsheet file failed", ex);
    }
  }

  /**
   * Exports a table to an XML file.
   *
   * @param <S> the row type
   * @param table the table view
   * @param file the output file
   * @param onlySelected true if export only selected rows
   */
  public <S> void toXml(FxTableView<S> table, File file, boolean onlySelected) {

    try (PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))) {

      StreamResult streamResult = new StreamResult(out);
      SAXTransformerFactory tf = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
      // SAX2.0 ContentHandler.
      TransformerHandler hd = tf.newTransformerHandler();
      Transformer serializer = hd.getTransformer();
      serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      serializer.setOutputProperty(OutputKeys.INDENT, "yes");
      hd.setResult(streamResult);
      hd.startDocument();
      AttributesImpl atts = new AttributesImpl();
      hd.startElement("", "", "Table", atts);
      List<S> items = onlySelected ? table.getSelectionModel().getSelectedItems() : table.getItems();

      for (S item : items) {

        hd.startElement("", "", table.getConfiguration().getName(), atts);

        for (TableColumnConfiguration<S, ?> tc : table.getConfiguration().getColumnConfigurations()) {
          String tag = tc.getName();
          FxTableBinding<S, ?> binding = tc.getBinding();
          if (binding != null) {
            binding.setBoundRootObject(item);
            Object value = binding.getModelValue();
            String text = value == null ? "" : value.toString();
            hd.startElement("", "", tag, atts);
            hd.characters(text.toCharArray(), 0, text.length());
            hd.endElement("", "", tag);
          }
        }

        hd.endElement("", "", table.getConfiguration().getName());
      }

      hd.endElement("", "", "Table");
      hd.endDocument();
    }
    catch (IOException | TransformerConfigurationException | SAXException ex) {
      throw new FxRuntimeException("creating XML file failed", ex);
    }
  }


  /**
   * Prints a table view.<br>
   * Tables need a special handling for multiple pages.
   *
   * @param table the table to print
   * @param title the optional title, null if take from table's configuration
   */
  public void print(FxTableView<?> table, String title) {
    final PrinterJob job = PrinterJob.createPrinterJob();
    if (job != null && job.showPrintDialog(Fx.getStage(table))) {
      TablePrinter.print(table, title, job);
    }
  }

}

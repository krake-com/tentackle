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

import javafx.print.PrinterJob;
import javafx.stage.Stage;
import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.rdc.FileUtilities;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.pdo.PdoMember;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentObject;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.ResourceBundle;

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


  private static final String SPREADSHEET_EXTENSION = ".csv";
  private static final String LAST_SPREADSHEET_PREFIX = "/_lastSpreadsheetNames_";
  private static final String SPREADSHEET_KEY = "path";

  private static final String XML_EXTENSION = ".xml";
  private static final String LAST_XML_PREFIX = "/_lastXmlNames_";
  private static final String XML_KEY = "path";


  /**
   * Creates a default table configuration for a given PDO class.<br>
   * Each attribute getter (annotated with {@link org.tentackle.session.Persistent}) gets its own column,
   * except the default attributes from {@link PersistentObject} and object ids for object relations.
   *
   * @param pdoClass the PDO class
   * @param bundle optional bundle to determine the displayed column names, null if name is attribute name
   * @param <T> the PDO type
   * @return the table configuration
   */
  public <T extends PersistentDomainObject<T>> TableConfiguration<T> createTableConfiguration(Class<T> pdoClass, ResourceBundle bundle) {

    TableConfiguration<T> config = FxFactory.getInstance().createTableConfiguration(pdoClass, null);

    for (PdoMember member: PdoUtilities.getInstance().getAttributes(config.getObjectClass(), true)) {
      String columnName = StringHelper.firstToLower(member.getName());    // corresponds to the binding path
      String displayName = bundle != null ? bundle.getString(columnName) : member.getName();
      config.addColumn(columnName, displayName);
    }

    if (config.getColumnConfigurations().isEmpty()) {
      // no columns at all: fallback to the ID and SERIAL columns
      config.addColumn(Constants.CN_ID, Constants.AN_ID);
      config.addColumn(Constants.CN_SERIAL, Constants.AN_SERIAL);
    }

    return config;
  }


  /**
   * Selects the spreadsheet file.
   *
   * @param tableName the configured table name
   * @param owner the window owner for the selection dialog, null if none
   * @return the selected file, null if aborted
   */
  public File selectSpreadsheetFile(String tableName, Stage owner) {
    return FileUtilities.getInstance().selectFile(
                  StringHelper.trimRight(LAST_SPREADSHEET_PREFIX + tableName, '/'),
                  SPREADSHEET_KEY, SPREADSHEET_EXTENSION,
                  RdcFxRdcBundle.getString("SPREADSHEET FILE"), owner);
  }


  /**
   * Selects the XML file.
   *
   * @param tableName the configured table name
   * @param owner the window owner for the selection dialog, null if none
   * @return the selected file, null if aborted
   */
  public File selectXmlFile(String tableName, Stage owner) {
    return FileUtilities.getInstance().selectFile(
                  StringHelper.trimRight(LAST_XML_PREFIX + tableName, '/'),
                  XML_KEY, XML_EXTENSION,
                  RdcFxRdcBundle.getString("XML FILE"), owner);
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
    try (PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))) {
      // write header
      int ndx = 0;
      for (TableColumnConfiguration<S, ?> tc : table.getConfiguration().getColumnConfigurations()) {
        if (ndx > 0) {
          out.append(',');
        }
        out.append(toCSV(tc.getDisplayedName()));
        ndx++;
      }
      out.append('\n');

      // write rows
      List<S> items = onlySelected ? table.getSelectionModel().getSelectedItems() : table.getItems();
      for (S item : items) {
        ndx = 0;
        for (TableColumnConfiguration<S, ?> tc : table.getConfiguration().getColumnConfigurations()) {
          if (ndx > 0) {
            out.append(',');
          }
          FxTableBinding<S, ?> binding = tc.getBinding();
          if (binding != null) {
            binding.setBoundRootObject(item);
            Object value = binding.getModelValue();
            if (value != null) {
              out.append(toCSV(value.toString()));
            }
          }
          ndx++;
        }
        out.append('\n');
      }
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


  /**
   * Converts a string to a quoted CSV-Field.
   *
   * @return the quoted string, null if value was null
   */
  private String toCSV(String value)  {
    if (value != null)  {
      StringBuilder field = new StringBuilder();
      field.append('"');
      int length = value.length();
      for (int i=0; i < length; i++)  {
        char c = value.charAt(i);
        if (c == '"') {
          field.append('"');
        }
        field.append(c);
      }
      field.append('"');
      return field.toString();
    }
    return null;
  }

}

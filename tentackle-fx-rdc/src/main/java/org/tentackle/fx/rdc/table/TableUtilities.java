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

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.tentackle.bind.Bindable;
import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.rdc.RdcUtilities;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.misc.CsvConverter;
import org.tentackle.pdo.PdoMember;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentObject;

import javafx.print.PrinterJob;
import javafx.stage.Stage;
import javax.xml.XMLConstants;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;


interface TableUtilitiesHolder {
  TableUtilities INSTANCE = ServiceFactory.createService(TableUtilities.class, TableUtilities.class);
}


/**
 * Table-related utility methods.<br>
 * Creates the default table configuration of a PDO class, exports tables and tree tables
 * to CSV or XML files, and prints them.
 * <p>
 * Replaceable via {@code @Service(TableUtilities.class)}. The module {@code tentackle-fx-rdc-poi},
 * for example, replaces the CSV export with an Excel export.
 *
 * @author harald
 */
@Service(TableUtilities.class)    // defaults to self
public class TableUtilities {

  /*
   * TableView and TreeTableView don't inherit from each other and thus
   * each spawn their own related types such as TableColumn, TreeTableColumn, etc...
   * As a result, since TableUtilities apply to both tables and tree tables,
   * a lot of semantically identical code is necessary.
   * However, it is not really duplicate code. There's not much we can do to reduce it.
   */

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static TableUtilities getInstance() {
    return TableUtilitiesHolder.INSTANCE;
  }


  private static final String SPREADSHEET_EXTENSION = ".csv";
  private static final String LAST_SPREADSHEET_PREFIX = "lastCsvNames/";
  private static final String SPREADSHEET_KEY = "path";

  private static final String XML_EXTENSION = ".xml";
  private static final String LAST_XML_PREFIX = "lastXmlNames/";
  private static final String XML_KEY = "path";


  /**
   * Creates the table utilities.
   */
  public TableUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Creates a default table configuration for a given PDO class.<br>
   * Each attribute with a getter annotated with {@link Bindable} gets its own column,
   * except the default attributes from {@link PersistentObject} and the object IDs of object relations.
   * The column name is the attribute name, which is also the binding path.
   * If there are no such attributes at all, the ID and serial columns are added instead.
   *
   * @param pdoClass the PDO class
   * @param bundle optional bundle to determine the displayed column names, null to use the capitalized attribute names
   * @param <T> the PDO type
   * @return the table configuration
   */
  public <T extends PersistentDomainObject<T>> TableConfiguration<T> createTableConfiguration(Class<T> pdoClass, ResourceBundle bundle) {

    TableConfiguration<T> config = FxFactory.getInstance().createTableConfiguration(pdoClass, null);

    for (PdoMember member: PdoUtilities.getInstance().getAttributes(config.getObjectClass(), true)) {
      Bindable bindable = member.getGetter().getAnnotation(Bindable.class);
      if (bindable != null) {   // skip non-bindables
        // column name corresponds to the binding path
        String displayName = bundle != null ? bundle.getString(member.getName()) : StringHelper.firstToUpper(member.getName());
        config.addColumn(member.getName(), displayName);
      }
    }

    if (config.getColumnConfigurations().isEmpty()) {
      // no columns at all: fallback to the ID and SERIAL columns
      config.addColumn(Constants.CN_ID, Constants.AN_ID);
      config.addColumn(Constants.CN_SERIAL, Constants.AN_SERIAL);
    }

    return config;
  }


  /**
   * Selects the spreadsheet file.<br>
   * The last selected file is remembered per table in the user preferences.
   *
   * @param tableName the configured table name
   * @param owner the window owner for the selection dialog, null if none
   * @return the selected file, null if aborted
   */
  public File selectSpreadsheetFile(String tableName, Stage owner) {
    return RdcUtilities.getInstance().selectFile(
                  StringHelper.trimRight(LAST_SPREADSHEET_PREFIX + tableName, '/'),
                  SPREADSHEET_KEY, SPREADSHEET_EXTENSION,
                  RdcFxRdcBundle.getString("SPREADSHEET FILE"), owner);
  }


  /**
   * Selects the XML file.<br>
   * The last selected file is remembered per table in the user preferences.
   *
   * @param tableName the configured table name
   * @param owner the window owner for the selection dialog, null if none
   * @return the selected file, null if aborted
   */
  public File selectXmlFile(String tableName, Stage owner) {
    return RdcUtilities.getInstance().selectFile(
                  StringHelper.trimRight(LAST_XML_PREFIX + tableName, '/'),
                  XML_KEY, XML_EXTENSION,
                  RdcFxRdcBundle.getString("XML FILE"), owner);
  }


  /**
   * Exports the table to a spreadsheet file.<br>
   * Only the visible columns are exported.
   *
   * @param <S> the row type
   * @param table the table view
   * @param file the output file
   * @param onlySelected true if export only selected rows
   */
  public <S> void toSpreadsheet(FxTableView<S> table, File file, boolean onlySelected) {
    List<S> items = onlySelected ? table.getSelectionModel().getSelectedItems() : table.getItems();
    toSpreadsheet(table.getConfiguration().getColumnConfigurations(), file, items);
  }

  /**
   * Exports the tree table to a spreadsheet file.<br>
   * Only the visible columns are exported.
   *
   * @param <S> the row type
   * @param treeTable the tree table view
   * @param file the output file
   * @param onlySelected true if export only selected rows
   */
  public <S> void toSpreadsheet(FxTreeTableView<S> treeTable, File file, boolean onlySelected) {
    List<S> items = onlySelected ? treeTable.getSelectedItems() : treeTable.getItems();
    toSpreadsheet(treeTable.getConfiguration().getColumnConfigurations(), file, items);
  }


  /**
   * Exports the items to a spreadsheet file.<br>
   * The default implementation creates a CSV file according to RFC 4180, with a header line
   * of the displayed column names and the values formatted as in the table.
   *
   * @param columnConfigurations the column configurations
   * @param file the output file
   * @param items the items to export
   * @param <S> the row type
   */
  protected <S> void toSpreadsheet(Collection<TableColumnConfiguration<S,?>> columnConfigurations, File file, List<S> items) {
    Collection<Collection<String>> values = new ArrayList<>();

    List<String> headerValues = new ArrayList<>();
    for (TableColumnConfiguration<S, ?> tc : columnConfigurations) {
      if (isColumnVisible(tc)) {
        headerValues.add(tc.getDisplayedName());
      }
    }
    values.add(headerValues);

    for (S item : items) {
      List<String> rowValues = new ArrayList<>();
      for (TableColumnConfiguration<S, ?> tc : columnConfigurations) {
        if (isColumnVisible(tc)) {
          String text = "";
          FxTableBinding<S, ?> binding = tc.getBinding();
          if (binding != null) {
            binding.setBoundRootObject(item);
            Object value = binding.getModelValue();
            if (value != null) {
              text = getText(tc, value);
            }
          }
          // one value per visible column, even if empty: leaving it out would move
          // all following values of this row under the wrong column header
          rowValues.add(text);
        }
      }
      values.add(rowValues);
    }

    // default exports to CSV (RFC 4180)
    // overridden by export to XLS in module tentackle-fx-rdc-poi
    try (PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))) {
      boolean first = true;   // the last line should not be terminated with a line-ending
      String lineEnding = CsvConverter.getInstance().getLineEnding(); // line ending is always CR+LF, regardless of the platform
      for (String csv : CsvConverter.getInstance().toCsv(values)) {
        if (first) {
          first = false;
        }
        else {
          out.print(lineEnding);
        }
        out.print(csv);
      }
    }
    catch (IOException ex) {
      throw new RdcRuntimeException("creating spreadsheet file failed", ex);
    }
  }

  /**
   * Gets the text displayed for a column's value.<br>
   * Exports show the same text as the table.
   *
   * @param columnConfiguration the column configuration
   * @param value the value, never null
   * @return the displayed text, never null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected String getText(TableColumnConfiguration<?, ?> columnConfiguration, Object value) {
    String text = ((TableColumnConfiguration) columnConfiguration).getText(value);
    return text == null ? "" : text;
  }

  /**
   * Returns whether a column is visible.<br>
   * Applies to both the table and the tree table column.
   *
   * @param columnConfiguration the column configuration
   * @return true if visible
   */
  public boolean isColumnVisible(TableColumnConfiguration<?, ?> columnConfiguration) {
    return columnConfiguration.getTableColumn().isVisible() && columnConfiguration.getTreeTableColumn().isVisible();
  }

  /**
   * Exports a table to an XML file.<br>
   * Only the visible columns are exported. Each row becomes an element named after the table,
   * and each column an element named after the column.
   *
   * @param <S> the row type
   * @param table the table view
   * @param file the output file
   * @param onlySelected true if export only selected rows
   */
  public <S> void toXml(FxTableView<S> table, File file, boolean onlySelected) {
    List<S> items = onlySelected ? table.getSelectionModel().getSelectedItems() : table.getItems();
    toXml(table.getConfiguration().getColumnConfigurations(), table.getConfiguration().getName(), file, items);
  }

  /**
   * Exports a tree table to an XML file.
   *
   * @param <S> the row type
   * @param treeTable the tree table view
   * @param file the output file
   * @param onlySelected true if export only selected rows
   */
  public <S> void toXml(FxTreeTableView<S> treeTable, File file, boolean onlySelected) {
    treeTable.getExpandedItemCount();
    List<S> items = onlySelected ? treeTable.getSelectedItems() : treeTable.getItems();
    toXml(treeTable.getConfiguration().getColumnConfigurations(), treeTable.getConfiguration().getName(), file, items);
  }

  private <S> void toXml(Collection<TableColumnConfiguration<S,?>> columnConfigurations, String name, File file, List<S> items) {

    try (PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))) {

      StreamResult streamResult = new StreamResult(out);
      SAXTransformerFactory tf = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
      tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      // SAX2.0 ContentHandler.
      TransformerHandler hd = tf.newTransformerHandler();
      Transformer serializer = hd.getTransformer();
      serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      serializer.setOutputProperty(OutputKeys.INDENT, "yes");
      hd.setResult(streamResult);
      hd.startDocument();
      AttributesImpl atts = new AttributesImpl();
      hd.startElement("", "", "Table", atts);

      for (S item : items) {

        hd.startElement("", "", name, atts);

        for (TableColumnConfiguration<S, ?> tc : columnConfigurations) {
          if (isColumnVisible(tc)) {
            String tag = tc.getName();
            FxTableBinding<S, ?> binding = tc.getBinding();
            if (binding != null) {
              binding.setBoundRootObject(item);
              Object value = binding.getModelValue();
              String text = value == null ? "" : getText(tc, value);
              hd.startElement("", "", tag, atts);
              hd.characters(text.toCharArray(), 0, text.length());
              hd.endElement("", "", tag);
            }
          }
        }

        hd.endElement("", "", name);
      }

      hd.endElement("", "", "Table");
      hd.endDocument();
    }
    catch (IOException | TransformerConfigurationException | SAXException ex) {
      throw new RdcRuntimeException("creating XML file failed", ex);
    }
  }


  /**
   * Prints a table view.<br>
   * Tables need a special handling for multiple pages, see {@link TablePrinter}.
   *
   * @param table the table to print
   * @param title the optional title, null to take from the table's configuration
   */
  public void print(FxTableView<?> table, String title) {
    PrinterJob job = PrinterJob.createPrinterJob();
    if (job != null) {
      if (job.showPrintDialog(Fx.getStage(table))) {
        TablePrinter.print(table, title, job);
      }
    }
    else {
      Fx.error(table, RdcFxRdcBundle.getString("NO PRINTERS FOUND"));
    }
  }


  /**
   * Prints a tree table view.<br>
   * Tables need a special handling for multiple pages, see {@link TablePrinter}.
   *
   * @param treeTable the tree table to print
   * @param title the optional title, null to take from the tree table's configuration
   */
  public void print(FxTreeTableView<?> treeTable, String title) {
    PrinterJob job = PrinterJob.createPrinterJob();
    if (job != null) {
      if (job.showPrintDialog(Fx.getStage(treeTable))) {
        TablePrinter.print(treeTable, title, job);
      }
    }
    else {
      Fx.error(treeTable, RdcFxRdcBundle.getString("NO PRINTERS FOUND"));
    }
  }

}

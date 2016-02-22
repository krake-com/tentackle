/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.fx.table;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
import org.tentackle.common.BMoney;
import org.tentackle.common.LocaleProvider;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxBindingFactory;
import org.tentackle.fx.bind.FxTableBinder;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.ReflectionHelper;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Default implementation of a table configuration.<br>
 *
 * @param <S> type of the objects contained within the table's items list
 * @author harald
 */
public class DefaultTableConfiguration<S> implements TableConfiguration<S> {

  /** preferences key for table width. */
  private static final String PREF_TABLE_WIDTH = "width";

  /** preferences key for table height. */
  private static final String PREF_TABLE_HEIGHT = "height";

  /** preferences key for column width. */
  private static final String PREF_COLUMN_WIDTH = "w_";

  /** preferences key for column view index. */
  private static final String PREF_COLUMN_INDEX = "i_";

  /** preferences key for column visibility. */
  private static final String PREF_COLUMN_VISIBILITY = "v_";



  /**
   * Template object.
   */
  private final S template;

  /**
   * The table's name.
   */
  private final String name;

  /**
   * the row object's class.
   */
  private final Class<S> objectClass;

  /**
   * the column configs.
   */
  private final List<TableColumnConfiguration<S, ?>> columnConfigurations;

  /**
   * The binder.
   */
  private FxTableBinder<S> binder;


  /**
   * Creates a configuration.
   *
   * @param template a template object
   * @param name the table's name, null if basename from effective class of template
   */
  @SuppressWarnings("unchecked")
  public DefaultTableConfiguration(S template, String name) {
    this.template = template;
    this.objectClass = (Class<S>) EffectiveClassProvider.getEffectiveClass(template);
    this.name = name == null ? ReflectionHelper.getClassBaseName(objectClass) : name;
    this.columnConfigurations = new ArrayList<>();
  }

  /**
   * Creates a configuration.
   *
   * @param objectClass the object class
   * @param name the table's name, null if basename from effective class of template
   */
  @SuppressWarnings("unchecked")
  public DefaultTableConfiguration(Class<S> objectClass, String name) {
    this.template = null;
    this.objectClass = objectClass;
    this.name = name == null ? ReflectionHelper.getClassBaseName(objectClass) : name;
    this.columnConfigurations = new ArrayList<>();
  }

  /**
   * Gets the template.
   *
   * @return the template, null if only class given
   */
  public S getTemplate() {
    return template;
  }


  @Override
  public String toString() {
    return name;
  }


  /**
   * Adds a column.
   *
   * @param name the column name
   * @param displayedName the displayed column name
   * @return the created configuration
   */
  @Override
  public TableColumnConfiguration<S, ?> addColumn(String name, String displayedName) {
    TableColumnConfiguration<S, ?> columnConfiguration = new DefaultTableColumnConfiguration<>(this, name, displayedName);
    addColumnConfiguration(columnConfiguration);
    return columnConfiguration;
  }

  /**
   * Adds a column configuration.
   *
   * @param columnConfiguration the config
   */
  @Override
  public void addColumnConfiguration(TableColumnConfiguration<S, ?> columnConfiguration) {
    columnConfigurations.add(columnConfiguration);
  }


  @Override
  public List<TableColumnConfiguration<S, ?>> getColumnConfigurations() {
    return columnConfigurations;
  }

  @Override
  public TableColumnConfiguration<S, ?> getColumnConfiguration(String name) {
    if (name != null) {
      for (TableColumnConfiguration<S, ?> config: columnConfigurations) {
        if (name.equals(config.getName())) {
          return config;
        }
      }
    }
    return null;
  }

  @Override
  public TableColumnConfiguration<S, ?> getColumnConfiguration(TableColumn<S, ?> column) {
    if (name != null) {
      for (TableColumnConfiguration<S, ?> config: columnConfigurations) {
        if (column == config.getTableColumn()) {
          return config;
        }
      }
    }
    return null;
  }



  @Override
  public String getName() {
    return name;
  }

  @Override
  public void savePreferences(TableView<S> table, String suffix, boolean system) {

    StringBuilder prefName = new StringBuilder();
    prefName.append(name);
    if (suffix != null) {
      prefName.append('_').append(suffix);
    }
    // always add the locale since header sizes etc... may vary
    prefName.append('_').append(LocaleProvider.getInstance().getEffectiveLocale());

    try {
      PersistedPreferences prefs = system ?
                                      PersistedPreferences.systemRoot().node(prefName.toString()) :
                                      PersistedPreferences.userRoot().node(prefName.toString());

      int viewIndex = 0;
      for (TableColumn<S, ?> column : table.getColumns()) {
        TableColumnConfiguration<S, ?> columnConfig = getColumnConfiguration(column);
        if (columnConfig != null) {
          PersistedPreferences columnPrefs = prefs.node(columnConfig.getName());
          columnPrefs.putInt(PREF_COLUMN_VISIBILITY, column.isVisible() ? 1 : 0);
          columnPrefs.putDouble(PREF_COLUMN_WIDTH, column.getWidth());
          columnPrefs.putInt(PREF_COLUMN_INDEX, viewIndex);
        }
        viewIndex++;
      }

      // store also the dimensions of the table
      prefs.putDouble(PREF_TABLE_HEIGHT, table.getHeight());
      prefs.putDouble(PREF_TABLE_WIDTH, table.getWidth());

      prefs.flush();
    }
    catch (BackingStoreException | RuntimeException ex) {
      throw new FxRuntimeException("saving table preferences failed for '" + prefName + "'", ex);
    }
  }

  @Override
  public void loadPreferences(TableView<S> table, String suffix, boolean system, boolean noViewSize) {

    StringBuilder prefName = new StringBuilder();
    prefName.append(name);
    if (suffix != null) {
      prefName.append('_').append(suffix);
    }
    // always add the locale since header sizes etc... may vary
    prefName.append('_').append(LocaleProvider.getInstance().getEffectiveLocale());

    try {
      // get the preferences
      PersistedPreferences systemPref = PersistedPreferences.systemRoot().node(prefName.toString());
      PersistedPreferences userPref = system ? null : PersistedPreferences.userRoot().node(prefName.toString());

      // map of column name to settings
      Map<String, PersistedPreferences> columnMapByName = new HashMap<>();
      for (String columnName : systemPref.childrenNames()) {
        columnMapByName.put(columnName, systemPref.node(columnName));
      }
      if (userPref != null) {
        // replace by user settings, if present
        for (String columnName : userPref.childrenNames()) {
          columnMapByName.put(columnName, userPref.node(columnName));
        }
      }

      Map<Integer, TableColumn<S, ?>> columnMapByIndex = new TreeMap<>();
      for (Map.Entry<String, PersistedPreferences> entry : columnMapByName.entrySet()) {
        TableColumnConfiguration<S, ?> columnConfig = getColumnConfiguration(entry.getKey());
        if (columnConfig != null) {
          TableColumn<S, ?> column = columnConfig.getTableColumn();
          if (column != null) {
            PersistedPreferences columnPref = entry.getValue();
            int index = columnPref.getInt(PREF_COLUMN_INDEX, -1);
            if (index >= 0) {
              columnMapByIndex.put(index, column);
            }
            int visibility = columnPref.getInt(PREF_COLUMN_VISIBILITY, -1);
            column.setVisible(visibility != 0);
            double width = columnPref.getDouble(PREF_COLUMN_WIDTH, -1.0);
            if (width >= 0.0) {
              table.resizeColumn(column, width - column.getWidth());
            }
          }
        }
      }

      table.getColumns().removeAll(columnMapByIndex.values());
      table.getColumns().addAll(columnMapByIndex.values());

      // read table dimensions
      if (!noViewSize) {
        double width = -1.0;
        String key = PREF_TABLE_WIDTH;
        if (!system) {
          width = userPref.getDouble(key, width);
        }
        if (width < 0.0) {
          width = systemPref.getDouble(key, width);
        }
        if (width > 0.0) {
          table.setPrefWidth(width);
        }

        double height = -1.0;
        key = PREF_TABLE_HEIGHT;
        if (!system) {
          height = userPref.getDouble(key, height);
        }
        if (height < 0.0) {
          height = systemPref.getDouble(key, height);
        }
        if (height > 0.0) {
          table.setPrefHeight(height);
        }
      }

    }
    catch (BackingStoreException | RuntimeException ex) {
      throw new FxRuntimeException("loading table preferences failed for '" + prefName + "'", ex);
    }
  }

  @Override
  public void toSpreadsheet(TableView<S> table, File file, boolean onlySelected) {
    HSSFWorkbook wb = new HSSFWorkbook();
    HSSFSheet sheet = wb.createSheet();

    int srow = 0;   // current spreadsheet row

    // column headers
    HSSFRow row = sheet.createRow(srow++);
    HSSFFont font = wb.createFont();
    font.setItalic(true);
    font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
    HSSFCellStyle cs = wb.createCellStyle();
    cs.setAlignment(HSSFCellStyle.ALIGN_CENTER);
    cs.setFont(font);
    int columnIndex = 0;
    for (TableColumnConfiguration<S, ?> tc : getColumnConfigurations()) {
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

      for (TableColumnConfiguration<S, ?> tc : getColumnConfigurations()) {

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

  @Override
  public void toXml(TableView<S> table, File file, boolean onlySelected) {

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

        hd.startElement("", "", getName(), atts);

        for (TableColumnConfiguration<S, ?> tc : getColumnConfigurations()) {
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

        hd.endElement("", "", getName());
      }

      hd.endElement("", "", "Table");
      hd.endDocument();
    }
    catch (IOException | TransformerConfigurationException | SAXException ex) {
      throw new FxRuntimeException("creating XML file failed", ex);
    }
  }


  @Override
  public void configure(TableView<S> table) {
    table.getColumns().clear();
    for (TableColumnConfiguration<S,?> config: columnConfigurations) {
      table.getColumns().add(config.getTableColumn());
    }
    if (table instanceof FxTableView) {
      ((FxTableView<S>) table).setConfiguration(this);
    }
  }

  @Override
  public Class<S> getObjectClass() {
    return objectClass;
  }

  @Override
  public BINDING getBindingType() {
    return BINDING.YES;
  }

  @Override
  public FxTableBinder<S> getBinder() {
    if (binder == null) {
      binder = FxBindingFactory.getInstance().createTableBinder(this);
    }
    return binder;
  }

  @Override
  public <T> TableCellType<T> getCellType(Class<T> type) {
    return TableCellTypeFactory.getInstance().getTableCellType(type);
  }

}

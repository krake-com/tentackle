/**
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


// Created on January 31, 2003, 11:30 AM

package org.tentackle.swing;

import java.awt.Desktop;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.print.PrinterException;
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
import java.util.prefs.BackingStoreException;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
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
import org.apache.poi.ss.util.CellRangeAddress;
import org.tentackle.common.BMoney;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.prefs.PreferencesInvalidException;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;



/**
 * FormTable utility popup.<br>
 * A popup menu to allow a FormTable to be printed, converted to Excel or Xml,
 * find an item in the table, set/get the preferences, size, columns, etc...
 * The popup-Menu will be triggered by pressing the right-mouse button (using isPopupTrigger())
 * somewhere in the header of the table (by default).
 *
 * @author harald
 */
public class FormTableUtilityPopup implements MouseListener, KeyListener {


  /** component name in popup for the menu */
  public static final String POPUP_MENU = "popupMenu";
  /** component name in popup for the column submenu */
  public static final String POPUP_COLUMN_MENU = "columnMenu";
  /** component name in popup for the column item */
  public static final String POPUP_COLUMN = "column";
  /** component name in popup for select all columns item */
  public static final String POPUP_COLUMN_SELECTALL = "selectAllColumns";
  /** component name in popup for  */
  public static final String POPUP_COLUMN_DESELECTALL = "deselectAllColumns";
  /** component name in popup for  */
  public static final String POPUP_SEARCH = "search";
  /** component name in popup for  */
  public static final String POPUP_PRINT = "print";
  /** component name in popup for  */
  public static final String POPUP_EXPORT_EXCEL = "exportExcel";
  /** component name in popup for  */
  public static final String POPUP_EXPORT_EXCEL_SELECTED = "exportExcelSelected";
  /** component name in popup for  */
  public static final String POPUP_EXPORT_XML = "exportXML";
  /** component name in popup for  */
  public static final String POPUP_EXPORT_XML_SELECTED = "exportXmlSelected";
  /** component name in popup for  */
  public static final String POPUP_SAVE_SYSTEM_PREFS = "saveSystemPrefs";
  /** component name in popup for  */
  public static final String POPUP_LOAD_SYSTEM_PREFS = "loadSystemPrefs";
  /** component name in popup for  */
  public static final String POPUP_SAVE_USER_PREFS = "saveUserPrefs";
  /** component name in popup for  */
  public static final String POPUP_LOAD_USER_PREFS = "loadUserPrefs";
  /** component name in popup for  */
  public static final String POPUP_LOAD_DEFAULTS = "loadDefaults";
  /** autowidth  */
  public static final String POPUP_AUTOWIDTH = "autoWidth";


  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(FormTableUtilityPopup.class);


  private FormTable<?> table;                   // table
  private String title;                         // title of table (null = title from window)
  private String intro;                         // intro on first printed page (null = none)
  private boolean columnMenuEnabled = true;     // true if user is allowed to hide/show columns (default)

  // last search criteria
  private String searchText;
  private boolean caseSensitive;
  private int searchKey;            // search-Key (default is F3)

  private static final String EXCEL_EXTENSION = ".xls";
  private static final String LAST_EXCEL_PREFIX = "/_lastExcelNames_";
  private static final String EXCEL_KEY = "path";

  private static final String XML_EXTENSION = ".xml";
  private static final String LAST_XML_PREFIX = "/_lastXmlNames_";
  private static final String XML_KEY = "path";


  /**
   * Sets the table.
   *
   * @param table the table, null to clear
   */
  public void setTable(FormTable<?> table) {
    this.table = table;
  }

  /**
   * Gets the table.
   * @return the table
   */
  public FormTable<?> getTable() {
    return table;
  }

  /**
   * Sets the title.<br>
 The title will be printed pn each page and shown in toExcel sheets.
   *
   * @param title the title, null if default from window
   */
  public void setTitle(String title)  {
    this.title = title;
  }

  /**
   * Gets the title.
   * @return the title, null if default from window
   */
  public String getTitle()  {
    return title;
  }

  /**
   * Sets the intro-text.<br>
 The intro will be printed on the first page and shown in toExcel sheets.
   *
   * @param intro the intro, null = none
   */
  public void setIntro(String intro)  {
    this.intro = intro;
  }

  /**
   * Gets the intro text.
   *
   * @return the intro, null = none
   */
  public String getIntro()  {
    return intro;
  }


  /**
   * Creates the popup-menu.
   * @return the popup menu
   */
  public JPopupMenu createPopupMenu() {

    JPopupMenu menu = new JPopupMenu();
    menu.setName(getClass().getName() + "/" + POPUP_MENU);

    TableColumnModel cm = table.getColumnModel();   // visible columns
    int numcol = cm.getColumnCount();

    boolean someRowSelected    = table.getSelectedRow() >= 0;
    boolean someColumnSelected = cm.getSelectionModel().getAnchorSelectionIndex() >= 0;

    if (columnMenuEnabled && cm instanceof FormTableColumnModel)  {
      // build columns-menu
      JMenu columnMenu = new JMenu();
      columnMenu.setName(POPUP_COLUMN_MENU);
      columnMenu.setText(SwingSwingBundle.getString("COLUMNS..."));
      TableModel dm = table.getModel();         // original columns
      boolean isAbstractFormTableModel = dm instanceof AbstractFormTableModel;
      int maxcol = dm.getColumnCount();
      JCheckBoxMenuItem[] items = new JCheckBoxMenuItem[maxcol];
      for (int i=0; i < maxcol; i++)  {
        items[i] = new JCheckBoxMenuItem(
                new ColumnAction(isAbstractFormTableModel ?
                                 ((AbstractFormTableModel)dm).getDisplayedColumnName(i) :
                                 dm.getColumnName(i), i));
        items[i].setName(POPUP_COLUMN + i);
        if (table.isColumnVisible(i)) {
          items[i].setSelected(true);
          items[i].setEnabled(numcol > 1);    // don't allow user to deselect last column
        }
        else  {
          items[i].setSelected(false);
        }
        columnMenu.add(items[i]);
      }

      // add select all button
      JRadioButtonMenuItem allItem = new JRadioButtonMenuItem(new AllColumnsAction(0, maxcol-1, true));
      allItem.setEnabled(numcol < maxcol);
      allItem.setName(POPUP_COLUMN_SELECTALL);
      columnMenu.add(allItem);

      // add deselect all button
      allItem = new JRadioButtonMenuItem(new AllColumnsAction(0, maxcol-1, false));
      allItem.setEnabled(numcol > 1); // don't allow user to deselect last column
      allItem.setName(POPUP_COLUMN_DESELECTALL);
      columnMenu.add(allItem);

      menu.add(columnMenu);
    }

    JMenuItem autoWidthItem = new JMenuItem();
    autoWidthItem.setName(POPUP_AUTOWIDTH);
    autoWidthItem.setText(SwingSwingBundle.getString("AUTO WIDTH"));
    autoWidthItem.addActionListener((ActionEvent e) -> table.autoResizeColumnWidths());
    menu.add(autoWidthItem);

    JMenuItem searchItem = new JMenuItem();
    searchItem.setName(POPUP_SEARCH);
    searchItem.setText(SwingSwingBundle.getString("SEARCH"));
    searchItem.addActionListener((ActionEvent e) -> showSearchDialog());
    searchItem.setEnabled(someRowSelected && someColumnSelected);
    menu.add(searchItem);

    JMenuItem printItem = new JMenuItem();
    printItem.setName(POPUP_PRINT);
    printItem.setText(SwingSwingBundle.getString("PRINT"));
    printItem.addActionListener((ActionEvent e) -> showPrintDialog());
    menu.add(printItem);

    JMenuItem excelItem = new JMenuItem();
    excelItem.setName(POPUP_EXPORT_EXCEL);
    excelItem.setText(SwingSwingBundle.getString("EXPORT TO EXCEL"));
    excelItem.addActionListener((ActionEvent e) -> showExcelDialog(false));
    menu.add(excelItem);

    JMenuItem selectedExcelItem = new JMenuItem();
    selectedExcelItem.setName(POPUP_EXPORT_EXCEL_SELECTED);
    selectedExcelItem.setText(SwingSwingBundle.getString("EXPORT TO EXCEL (ONLY SELECTED)"));
    selectedExcelItem.addActionListener((ActionEvent e) -> showExcelDialog(true));
    selectedExcelItem.setEnabled(someRowSelected);
    menu.add(selectedExcelItem);

    JMenuItem xmlItem = new JMenuItem();
    xmlItem.setName(POPUP_EXPORT_XML);
    xmlItem.setText(SwingSwingBundle.getString("EXPORT TO XML"));
    xmlItem.addActionListener((ActionEvent e) -> showXmlDialog(false));
    menu.add(xmlItem);

    JMenuItem selectedXmlItem = new JMenuItem();
    selectedXmlItem.setName(POPUP_EXPORT_XML_SELECTED);
    selectedXmlItem.setText(SwingSwingBundle.getString("EXPORT TO XML (ONLY SELECTED)"));
    selectedXmlItem.addActionListener((ActionEvent e) -> showXmlDialog(true));
    selectedXmlItem.setEnabled(someRowSelected);
    menu.add(selectedXmlItem);

    if (table.isCreateDefaultColumnsFromPreferences()) {

      if (PersistedPreferencesFactory.getInstance().isSystemOnly()) {
        if (!PersistedPreferencesFactory.getInstance().isReadOnly())  {
          JMenuItem saveItem = new JMenuItem();
          saveItem.setName(POPUP_SAVE_SYSTEM_PREFS);
          saveItem.setText(SwingSwingBundle.getString("SAVE SYSTEM PREFERENCES"));
          saveItem.addActionListener((ActionEvent e) -> {
            if (FormQuestion.yesNo(SwingSwingBundle.getString("SAVE SYSTEM PREFERENCES FOR THIS TABLE?"))) {
              saveSettings(true);
            }
          });
          menu.add(saveItem);
        }

        JMenuItem restoreItem = new JMenuItem();
        restoreItem.setName(POPUP_LOAD_SYSTEM_PREFS);
        restoreItem.setText(SwingSwingBundle.getString("LOAD SYSTEM PREFERENCES"));
        restoreItem.addActionListener((ActionEvent e) -> restoreSettings(true));
        menu.add(restoreItem);
      }
      else  {
        if (!PersistedPreferencesFactory.getInstance().isReadOnly())  {
          JMenuItem saveItem = new JMenuItem();
          saveItem.setName(POPUP_SAVE_USER_PREFS);
          saveItem.setText(SwingSwingBundle.getString("SAVE USER PREFERENCES"));
          saveItem.addActionListener((ActionEvent e) -> {
            if (FormQuestion.yesNo(SwingSwingBundle.getString("SAVE USER PREFERENCES FOR THIS TABLE?"))) {
              saveSettings(false);
            }
          });
          menu.add(saveItem);
        }

        JMenuItem restoreItem = new JMenuItem();
        restoreItem.setName(POPUP_LOAD_USER_PREFS);
        restoreItem.setText(SwingSwingBundle.getString("LOAD USER PREFERENCES"));
        restoreItem.addActionListener((ActionEvent e) -> restoreSettings(false));
        menu.add(restoreItem);

        JMenuItem restoreSysItem = new JMenuItem();
        restoreSysItem.setName(POPUP_LOAD_SYSTEM_PREFS);
        restoreSysItem.setText(SwingSwingBundle.getString("LOAD SYSTEM PREFERENCES"));
        restoreSysItem.addActionListener((ActionEvent e) -> restoreSettings(true));
        menu.add(restoreSysItem);
      }

      JMenuItem defaultItem = new JMenuItem();
      defaultItem.setName(POPUP_LOAD_DEFAULTS);
      defaultItem.setText(SwingSwingBundle.getString("LOAD DEFAULT PREFERENCES"));
      defaultItem.addActionListener((ActionEvent e) -> FormTableUtilityPopup.this.table.createDefaultColumnsFromDefaultModel());
      menu.add(defaultItem);
    }

    return menu;
  }



  /**
   * Returns whether the column menu is enabled.
   *
   * @return true if column menu is enabled
   */
  public boolean isColumnMenuEnabled() {
    return columnMenuEnabled;
  }

  /**
   * Enables or disables the column menu.<br>
   * The column menu allows to set the visibility of columns.
   *
   * @param columnMenuEnabled  true to enable column menu (default)
   */
  public void setColumnMenuEnabled(boolean columnMenuEnabled) {
    this.columnMenuEnabled = columnMenuEnabled;
  }


  /**
   * Saves the table settings to the Preferences.
   *
   * @param system is true if save to systemRoot, else userRoot
   */
  public void saveSettings(boolean system)  {
    try {
      table.savePreferences(table.getPreferencesName(), system);
    }
    catch (Exception ex)  {
      FormError.showException(SwingSwingBundle.getString("COULD NOT SAVE PREFERENCES"), ex);
    }
  }


  /**
   * Restores the tables default settings from the Preferences.
   *
   * @param system is true if load ONLY from systemRoot, else try userRoot first (should be default)
   */
  public void restoreSettings(boolean system) {
    try {
      if (!table.createDefaultColumnsFromPreferences(table.getPreferencesName(), system)) {
        if (FormQuestion.yesNo(SwingSwingBundle.getString("NO PREFERENCES FOUND. LOAD DEFAULTS?"))) {
          table.createDefaultColumnsFromDefaultModel();
        }
      }
    }
    catch (Exception ex)  {
      FormError.showException(SwingSwingBundle.getString("COULD NOT LOAD PREFERENCES"), ex);
    }
  }


  /**
   * Searches for a text starting at the current cell.
   *
   * @param searchText the search text
   * @param caseSensitive true if case sensitive
   * @return true if found
   */
  public boolean search(String searchText, boolean caseSensitive) {
    if (searchText != null && searchText.length() > 0)  {
      String text = caseSensitive ? searchText : searchText.toUpperCase();
      int currentRow = table.getSelectionModel().getAnchorSelectionIndex();
      // start after current field (+1)
      int currentCol = table.getColumnModel().getSelectionModel().getAnchorSelectionIndex() + 1;
      if (currentCol >= table.getColumnCount())  {
        // wrap to next line
        currentRow++;
        currentCol = 0;
      }
      TableModel model = table.getModel();
      int rows = model.getRowCount();
      int cols = model.getColumnCount();

      int startRow = currentRow;
      int endRow   = rows;
      int startCol = currentCol;

      for (int part=0; part < 2; part++)  {
        if (part == 1)  {
          startRow = 0;
          endRow   = currentRow;
        }
        for (int row=startRow; row < endRow; row++) {
          for (int col=startCol; col < cols; col++) {
            Object value = model.getValueAt(row, col);
            if (value != null)  {
              String cellText = value.toString();
              if (!caseSensitive)  {
                cellText = cellText.toUpperCase();
              }
              if (cellText.contains(text)) {
                // found: set selection
                table.setSelectedRow(row);
                table.getColumnModel().getSelectionModel().setAnchorSelectionIndex(col);
                table.scrollToCell(row, table.convertColumnIndexToView(col));
                return true;
              }
            }
          }
          startCol = 0;
        }
      }
    }
    return false;
  }


  /**
   * Opens a dialog to search in a table
   * starting at the current cell.
   */
  public void showSearchDialog() {
    SearchTextDialog sd = new SearchTextDialog();
    if (sd.showDialog(searchText, caseSensitive))  {
      // remember last parameters
      searchText = sd.getSearchText();
      caseSensitive = sd.isCaseSensitive();
      // run the search
      search(searchText, caseSensitive);
    }
  }



  /**
   * Prints the table.
   */
  public void showPrintDialog() {
    table.clearSelection();
    try {
      table.print();
    }
    catch (PrinterException pe) {
      FormError.showException(SwingSwingBundle.getString("PRINTING TABLE FAILED"), pe);
    }
  }


  /**
   * Converts the table to an Excel spreadsheet.
   *
   * @param file the output file
   * @param onlySelected true if export only selected rows
   * @throws IOException if export failed
   */
  public void toExcel(File file, boolean onlySelected) throws IOException {

    HSSFWorkbook wb = new HSSFWorkbook();
    HSSFSheet sheet = wb.createSheet();

    TableModel       model       = table.getModel();
    TableColumnModel columnModel = table.getColumnModel();

    int[] selectedRows = onlySelected ? table.getSelectedRows() : new int[] { };

    int rows = onlySelected ? selectedRows.length : table.getRowCount();  // number of (visible) data rows
    int cols = columnModel.getColumnCount();    // number of data columns

    short srow = 0;                             // current spreadsheet row

    // local copies cause might be changed
    String xTitle = this.title;
    String xIntro = this.intro;

    if (xTitle == null)  {
      // get default from window title
      Window parent = FormUtilities.getInstance().getParentWindow(table);
      try {
        // paint page-title
        xTitle = ((FormWindow)parent).getTitle();
      }
      catch (Exception e) {
        xTitle = null;
      }
    }
    if (xTitle != null)  {
      HSSFRow row = sheet.createRow(srow);
      HSSFFont font = wb.createFont();
      font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
      HSSFCellStyle cs = wb.createCellStyle();
      cs.setAlignment(HSSFCellStyle.ALIGN_CENTER);
      cs.setFont(font);
      HSSFCell cell = row.createCell(0);
      cell.setCellStyle(cs);
      cell.setCellValue(new HSSFRichTextString(xTitle));
      // region rowFrom, colFrom, rowTo, colTo
      sheet.addMergedRegion(new CellRangeAddress(0, srow, 0, cols - 1));
      srow++;
    }

    if (xIntro != null || onlySelected)  {
      HSSFRow row = sheet.createRow(srow);
      HSSFCell cell = row.createCell(0);
      HSSFCellStyle cs = wb.createCellStyle();
      cs.setAlignment(HSSFCellStyle.ALIGN_LEFT);
      cs.setVerticalAlignment(HSSFCellStyle.VERTICAL_CENTER);
      cs.setWrapText(true);
      cell.setCellStyle(cs);
      if (onlySelected) {
        if (xIntro == null) {
          xIntro = "";
        }
        else {
          xIntro += ", ";
        }
        xIntro += SwingSwingBundle.getString("<ONLY SELECTED ROWS>");
      }
      cell.setCellValue(new HSSFRichTextString(xIntro));
      sheet.addMergedRegion(new CellRangeAddress(srow, srow + 2, 0, cols - 1));
      srow += 3;
    }

    // column headers
    boolean isAbstractFormTableModel = model instanceof AbstractFormTableModel;
    srow++;   // always skip one line
    HSSFRow row = sheet.createRow(srow);
    HSSFFont font = wb.createFont();
    font.setItalic(true);
    font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
    HSSFCellStyle cs = wb.createCellStyle();
    cs.setAlignment(HSSFCellStyle.ALIGN_CENTER);
    cs.setFont(font);
    for (int c=0; c < cols; c++)  {
      HSSFCell cell = row.createCell(c);
      cell.setCellValue(new HSSFRichTextString(isAbstractFormTableModel ?
                         ((AbstractFormTableModel)model).getDisplayedColumnName(columnModel.getColumn(c).getModelIndex()) :
                         model.getColumnName(columnModel.getColumn(c).getModelIndex())));
      cell.setCellStyle(cs);
    }
    srow++;

    // default cell-style for date
    HSSFCellStyle dateStyle = wb.createCellStyle();
    dateStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("m/d/yy"));

    // cellstyles for numbers
    List<HSSFCellStyle> numberStyles = new ArrayList<>();
    HSSFDataFormat format = wb.createDataFormat();


    for (int r=0; r < rows; r++)  {

      int modelRow = table.convertRowIndexToModel(onlySelected ? selectedRows[r] : r);

      row = sheet.createRow(srow + (short)r);

      for (int i=0; i < cols; i++)  {

        int c = columnModel.getColumn(i).getModelIndex();

        Object value = model.getValueAt(modelRow, c);

        HSSFCell cell = row.createCell(i);

        if      (value instanceof Boolean)  {
          cell.setCellValue(((Boolean) value));
        }
        else if (value instanceof BMoney)   {
          BMoney money = (BMoney)value;
          cell.setCellValue(money.doubleValue());

          String fmt = "#,##0";
          if (money.scale() > 0) {
            StringBuilder buf = new StringBuilder(fmt).append('.');
            for (int j=0; j < money.scale(); j++)  {
              buf.append('0');
            }
            fmt = buf.toString();
          }
          // create format
          short fmtIndex = format.getFormat(fmt);

          // check if there is already a cellstyle with this scale
          Iterator<HSSFCellStyle> iter = numberStyles.iterator();
          boolean found = false;
          while (iter.hasNext())  {
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
        else if (value instanceof Number)   {
          cell.setCellValue(((Number)value).doubleValue());
        }
        else if (value instanceof Date) {
          cell.setCellValue((Date)value);
          cell.setCellStyle(dateStyle);
        }
        else if (value instanceof GregorianCalendar) {
          cell.setCellValue((GregorianCalendar)value);
          cell.setCellStyle(dateStyle);
        }
        else if (value != null) {
          cell.setCellValue(new HSSFRichTextString(value.toString()));
        }
      }
    }

    // set the width for each column
    for (int c=0; c < cols; c++)  {
      short width = (short)(columnModel.getColumn(c).getWidth() * 45); // is a reasonable value
      sheet.setColumnWidth(c, width);
    }

    try (FileOutputStream fileOut = new FileOutputStream(file)) {
      wb.write(fileOut);
    }

    // open Excel
    try {
      LOGGER.info("launching EDIT action for {0}", file);
      Desktop.getDesktop().edit(file);
    }
    catch (UnsupportedOperationException ex) {
      LOGGER.info("{0} -> trying OPEN action for {1}", ex.getMessage(), file);
      Desktop.getDesktop().open(file);
      LOGGER.info("success!");
    }
  }




  /**
   * Opens a dialog to export a table to an Excel sheet.
   *
   * @param onlySelected true if export only selected rows
   */
  public void showExcelDialog(boolean onlySelected) {
    try {
      // Pfadname fuer diese Tabelle merken (immer in den userPrefs und immer abseits der normalen prefs)
      String prefName = LAST_EXCEL_PREFIX + table.getPreferencesName();
      // java.util.prefs.AbstractPreferences cannot cope with trailing slashes for nodenames
      prefName = StringHelper.trimRight(prefName, '/');
      PersistedPreferences prefs = PersistedPreferences.userRoot().node(prefName);
      String lastName = prefs.get(EXCEL_KEY, null);

      // Filename erfragen (default vom letzten Mal)
      JFileChooser jfc = new JFileChooser(lastName);
      if (lastName != null) {
        jfc.setSelectedFile(new File(lastName));
      }
      jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      jfc.setFileFilter(new FileFilter()  {
        @Override
        public boolean accept(File f) {
          return f.getName().toLowerCase().endsWith(EXCEL_EXTENSION) || f.isDirectory();
        }
        @Override
        public String getDescription()  {
          return SwingSwingBundle.getString("EXCEL FILE");
        }
      });
      if (jfc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        File outFile = jfc.getSelectedFile();
        if (!outFile.getName().toLowerCase().endsWith(EXCEL_EXTENSION))  {
          outFile = new File(outFile.getPath() + EXCEL_EXTENSION);
        }
        toExcel(outFile, onlySelected);
        if (!PersistedPreferencesFactory.getInstance().isReadOnly()) {
          prefs.put(EXCEL_KEY, outFile.getAbsolutePath());
          try {
            prefs.flush();
          }
          catch (PreferencesInvalidException pix) {
            // ignore if changed meanwhile
          }
        }
      }
    }
    catch (BackingStoreException ex)  {
      // no harm
    }
    catch (Exception ex)  {
      FormError.showException(SwingSwingBundle.getString("COULD NOT CREATE EXCEL FILE"), ex);
    }
  }



  /**
   * Exports a table to an XML file.
   *
   * @param file the output file
   * @param onlySelected true if export only selected rows
   * @throws IOException if file could not be opened
   * @throws TransformerConfigurationException if no transformer could be created
   * @throws SAXException if xml document creation failed
   */
  public void toXml(File file, boolean onlySelected) throws IOException, TransformerConfigurationException, SAXException {

    TableModel       model       = table.getModel();
    TableColumnModel columnModel = table.getColumnModel();

    try (PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))) {

      int[] selectedRows = onlySelected ? table.getSelectedRows() : new int[] { };

      int rows = onlySelected ? selectedRows.length : table.getRowCount();  // number of (visible) data rows
      int cols = columnModel.getColumnCount();                              // number of data columns

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

      hd.startElement("", "", "FormTable", atts);

      // local copies cause might be changed
      String xTitle = this.title;
      String xIntro = this.intro;

      if (xTitle == null)  {
        // get default from window title
        Window parent = FormUtilities.getInstance().getParentWindow(table);
        try {
          // paint page-title
          xTitle = ((FormWindow )parent).getTitle();
        }
        catch (Exception e) {
          xTitle = null;
        }
      }

      if (xTitle != null)  {
        hd.startElement("", "", "title", atts);
        hd.characters(xTitle.toCharArray(), 0, xTitle.length());
        hd.endElement("", "", "title");
      }

      if (xIntro != null || onlySelected)  {
        if (onlySelected) {
          if (xIntro == null) {
            xIntro = "";
          }
          else {
            xIntro += ", ";
          }
          xIntro += "<only selected rows>";
        }
        hd.startElement("", "", "intro", atts);
        hd.characters(xIntro.toCharArray(), 0, xIntro.length());
        hd.endElement("", "", "intro");
      }

      boolean isAbstractFormTableModel = model instanceof AbstractFormTableModel;

      // create xml-tags
      String[] tags = new String[cols];
      if (rows > 0) {
        for (int i = 0; i < cols; i++) {
          int c = columnModel.getColumn(i).getModelIndex();
          tags[i] = StringHelper.toVarName(StringHelper.unDiacrit(
                  isAbstractFormTableModel ?
                          ((AbstractFormTableModel) model).getDisplayedColumnName(c) :
                          model.getColumnName(c), false));
        }
      }

      for (int r=0; r < rows; r++)  {

        int modelRow = table.convertRowIndexToModel(onlySelected ? selectedRows[r] : r);

        hd.startElement("", "", table.getName(), atts);

        for (int i=0; i < cols; i++)  {
          int c = columnModel.getColumn(i).getModelIndex();
          String tag = tags[i];
          Object object = model.getValueAt(modelRow, c);
          String value = object == null ? "" : object.toString();
          hd.startElement("", "", tag, atts);
          hd.characters(value.toCharArray(), 0, value.length());
          hd.endElement("", "", tag);
        }

        hd.endElement("", "", table.getName());
      }

      hd.endElement("", "", "FormTable");
      hd.endDocument();
    }

    // open XML-File
    try {
      LOGGER.info("launching EDIT action for {0}", file);
      Desktop.getDesktop().edit(file);
    }
    catch (UnsupportedOperationException ex) {
      LOGGER.info("{0} -> trying OPEN action for {1}", ex.getMessage(), file);
      Desktop.getDesktop().open(file);
      LOGGER.info("success!");
    }
  }


  /**
   * Opens a dialog to export a table to an XML file.
   *
   * @param onlySelected true if export only selected rows
   */
  public void showXmlDialog(boolean onlySelected) {
    try {
      // Pfadname fuer diese Tabelle merken (immer in den userPrefs und immer abseits der normalen prefs)
      String prefName = LAST_XML_PREFIX + table.getPreferencesName();
      // java.util.prefs.AbstractPreferences cannot cope with trailing slashes for nodenames
      prefName = StringHelper.trimRight(prefName, '/');
      PersistedPreferences prefs = PersistedPreferences.userRoot().node(prefName);
      String lastName = prefs.get(XML_KEY, null);

      // Filename erfragen (default vom letzten Mal)
      JFileChooser jfc = new JFileChooser(lastName);
      if (lastName != null) {
        jfc.setSelectedFile(new File(lastName));
      }
      jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      jfc.setFileFilter(new FileFilter()  {
        @Override
        public boolean accept(File f) {
          return f.getName().toLowerCase().endsWith(XML_EXTENSION) || f.isDirectory();
        }
        @Override
        public String getDescription()  {
          return SwingSwingBundle.getString("XML FILE");
        }
      });
      if (jfc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        File outFile = jfc.getSelectedFile();
        if (!outFile.getName().toLowerCase().endsWith(XML_EXTENSION))  {
          outFile = new File(outFile.getPath() + XML_EXTENSION);
        }
        toXml(outFile, onlySelected);
        if (!PersistedPreferencesFactory.getInstance().isReadOnly()) {
          prefs.put(XML_KEY, outFile.getAbsolutePath());
          try {
            prefs.flush();
          }
          catch (PreferencesInvalidException pix) {
            // ignore if changed meanwhile
          }
        }
      }
    }
    catch (BackingStoreException ex)  {
      // no harm
    }
    catch (Exception ex)  {
      FormError.showException(SwingSwingBundle.getString("COULD NOT CREATE XML FILE"), ex);
    }
  }



  @Override
  public void mouseClicked(MouseEvent e) {
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
  }

  @Override
  public void mousePressed(MouseEvent e) {
    processTableMouseEvent(e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    processTableMouseEvent(e);
  }

  // process the mouse event and show popup
  private void processTableMouseEvent(MouseEvent e)  {
    if (table != null && e.isPopupTrigger())  {
      // if we do not reset the draggedColumn here we might
      // get ArrayIndexOutOfBoundsException during repaint events of the table later
      table.getTableHeader().setDraggedColumn(null);
      createPopupMenu().show(e.getComponent(), e.getX(), e.getY());
    }
  }


  /**
   * Adds a key listener for a given keycode.
   *
   * @param keyCode the keycode
   */
  public void addKeyListenerForKey(int keyCode) {
    searchKey = keyCode;
    table.addKeyListener(this);
  }

  /**
   * Adds the default key listener for {@link KeyEvent#VK_F3}.
   * Pressing this key will start the search dialog or continue the search.
   */
  public void addKeyListener()  {
    addKeyListenerForKey(KeyEvent.VK_F3);
  }

  /**
   * Removes the key listener.
   */
  public void removeKeyListener()  {
    table.removeKeyListener(this);
  }


  @Override
  public void keyPressed(KeyEvent e) {
    if (e.getKeyCode() == searchKey)  {
      if (searchText == null) {
        showSearchDialog();
      }
      else  {
        search(searchText, caseSensitive);
      }
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {
  }

  @Override
  public void keyTyped(KeyEvent e) {
  }



  /**
   * to turn on/off columns in columnMenu
   */
  @SuppressWarnings("serial")
  private class ColumnAction extends AbstractAction {

    private final int ndx;

    /**
     * @param text is the column name
     * @param ndx is the column index (according to datamodel)
     */
    public ColumnAction(String text, int ndx)  {
      super(text);
      this.ndx = ndx;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      table.setColumnVisible(ndx, !table.isColumnVisible(ndx)); // toggle
    }
  }


  /**
   * select all columns action
   */
  @SuppressWarnings("serial")
  private class AllColumnsAction extends AbstractAction {

    private final int first;    // first column index
    private final int last;     // last column index
    private final boolean show; // true = show, false = hide

    public AllColumnsAction(int first, int last, boolean show) {
      super(show ? SwingSwingBundle.getString("SHOW ALL") : SwingSwingBundle.getString("HIDE ALL"));
      this.first = first;
      this.last  = last;
      this.show  = show;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (show) {
        for (int ndx = first; ndx <= last; ndx++) {
          table.setColumnVisible(ndx, true);
        }
      }
      else  {
        for (int ndx = last; ndx >= first; ndx--) {
          // make it invisible, but only if it's not the last visible column at all.
          // otherwise we wont be able to invoke the menu anymore :-(
          if (table.getColumnModel().getColumnCount() > 1)  {
            table.setColumnVisible(ndx, false);
          }
        }
      }
    }
  }

}

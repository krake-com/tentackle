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


package org.tentackle.swing;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.EventObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import org.tentackle.common.BMoney;
import org.tentackle.common.DMoney;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.FormatHelper;
import org.tentackle.misc.StringHelper;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.swing.bind.FormTableBinding;




/**
 * Extended JTable.
 * <p>
 * The FormTable provides the following extensions to the standard JTable:
 * <ul>
 * <li>Provides the concept of a so-called "cell traversal". This allows
 * tables with spreadsheet-like usability.</li>
 * <li>works with extended datamodels (FormTableModel) with automatic configuration
 * based on the data wrapped by FormTableEntries.</li>
 * <li>many enhancements regarding the cell editors and renderers, automatic
 * row height, keyboard handling, etc...</li>
 * </ul>
 *
 * @param <T> the type handled by this table
 * @author harald
 */
@SuppressWarnings("serial")
public class FormTable<T> extends JTable implements FormChangeableComponent {

  private static final Logger LOGGER = LoggerFactory.getLogger(FormTable.class);


  /** preferences key for table width */
  public static final String PREF_TABLE_WIDTH = "width";

  /** preferences key for table height */
  public static final String PREF_TABLE_HEIGHT = "height";

  /** preferences key for column width */
  public static final String PREF_COLUMN_WIDTH = "w_";

  /** preferences key for column view index */
  public static final String PREF_COLUMN_INDEX = "i_";

  /** preferences key for column visibility */
  public static final String PREF_COLUMN_VISIBILITY = "v_";



  /** action string for mouse click */
  public static final String CLICK_ACTION = "click";

  /** action string for enter keypress */
  public static final String ENTER_ACTION = "enter";

  // caution: the following two strings must match the ones in the (Metal)LookAndFeel!

  /** next cell action */
  public static final String NEXTCELL_ACTION = "selectNextRowCell";

  /** previous cell action */
  public static final String PREVIOUSCELL_ACTION = "selectPreviousRowCell";

  /** help action */
  public static final String HELP_ACTION = "helpURL";




  /** cell traversal default mode, no traversal at all. */
  public static final int CELLTRAVERSAL_NONE = 0x00;

  /** cell traversal next/previous column. */
  public static final int CELLTRAVERSAL_COLUMN = 0x01;

  /** cell traversal next/previous row. */
  public static final int CELLTRAVERSAL_ROW = 0x02;

  /** no wrap at start/end of table. */
  public static final int CELLTRAVERSAL_NOTABLEWRAP = 0x10;

  /** move to next/previous editable cell only. */
  public static final int CELLTRAVERSAL_SKIPNOEDIT = 0x20;

  /** if cell is editable, start editing. */
  public static final int CELLTRAVERSAL_AUTOEDIT = 0x40;

  /** transfer focus to the previous/next component when stepping out at begin/end of table  (implies NOTABLEWRAP). */
  public static final int CELLTRAVERSAL_TRANSFERFOCUS = 0x80 + CELLTRAVERSAL_NOTABLEWRAP;

  /** don't wrap at start or end of row/column (implies NOTABLEWRAP). */
  public static final int CELLTRAVERSAL_NOLINEWRAP  = 0x04 + CELLTRAVERSAL_NOTABLEWRAP;

  /** stay in current row/column (implies NOTABLEWRAP). */
  public static final int CELLTRAVERSAL_WRAPINLINE = 0x08 + CELLTRAVERSAL_NOTABLEWRAP;



  /**
   * Default mapping of interfaces to renderers.<br>
   * Notice: unlike with {@link #defaultRenderersByColumnClass} the order of interfaces is important.
   * That's why we're using a {@link LinkedHashMap} here.
   */
  protected transient LinkedHashMap<Class<?>, TableCellRenderer> defaultRenderersByColumnInterface;

  /**
   * Default mapping of interfaces to editors.<br>
   * Notice: unlike with {@link #defaultEditorsByColumnClass} the order of interfaces is important.
   * That's why we're using a {@link LinkedHashMap} here.
   */
  protected transient LinkedHashMap<Class<?>, TableCellEditor> defaultEditorsByColumnInterface;

  /**
   * size from preferences (even if sizeInPreferencesIgnored = true!)
   */
  protected Dimension preferencesSize;



  private boolean sizeInPreferencesIgnored;                    // true if the size info is ignored in the preferences for preferredSize
  private boolean createDefaultColumnsFromPreferences;        // create the columns and size from the preferences?
  private int cellTraversal = CELLTRAVERSAL_NONE;             // current cell traversal mode
  private boolean inhibitCellTraversal;                       // inhibit cell traversal after cell has been edited
  private boolean usingAbstractFormTableModel;                // true if table model is AbstractFormTableModel
  private BindableTableModel<T> bindableModel;                // != null if a BindableTableModel found in model-chain
  private boolean cellEditorFixed = true;                     // true if cell editors are fixed per column
  private boolean cellRendererFixed = true;                   // true if cell renderers are fixed per column
  private boolean cellRectFixed = true;                       // true if cell dimensions are fixed per column
  private boolean honourChangeable = true;                    // honour the changeable-request
  private boolean changeable = true;                          // true if component is changeable
  private boolean allChangeable = true;                       // true if container is changeable
  private boolean enableEnterAction;                          // true if enter fires actionPerformed on selected row
  private int clicks;                                         // last number of clicks
  private int clickCountToAction = 2;                         // number of clicks to trigger action performed
  private int clickCountToStart = 2;                          // number of clicks to trigger editing mode of field
  private String  helpURL;                                    // != null for online help
  private boolean cellDragEnabled = true;                     // true = drag cells instead of rows (if columnSelectionAllowed = false)
                                                              // false = JDK default.
  private boolean formTraversable;                            // true = Table is FormFocusPolicy-traversable, i.e. can get Keyboard focus
  private FormTableUtilityPopup utilityPopup;                 // the utility popup, null if none (default)


  private List<String>        format;         // overwriting default formats
  private List<DateFormat>    dateFormat;     // special date-formats
  private List<NumberFormat>  numberFormat;   // special number-formats
  private List<String>        formatFlags;    // special formatting flags
  private List<Integer>       maxColumns;     // max. columns for cell editors
  private List<Integer>       scales;         // scale for cell editors
  private List<Integer>       hAlignment;     // horizontal alignment (array of Integers)
  private List<Integer>       vAlignment;     // vertical alignment (array of Integers)

  private static final char FORMAT_AUTOSELECT  = 'S';       // autoselect field
  private static final char FORMAT_BLANKZERO   = 'Z';       // blankzero field


  // renderer colors (will be picked up from FormTableCellRenderer)
  private Color selectedForeground;
  private Color selectedBackground;
  private Color unselectedForeground;
  private Color unselectedBackground;
  private Color focusedForeground;
  private Color focusedBackground;
  private Color selectedMandatoryBackground;
  private Color unselectedMandatoryBackground;

  // min- and max-rowheights
  private int minRowHeight;
  private int maxRowHeight;

  // additional actions for editing traversal
  private Action nextCellAction;          // move to next cell and optionally edit
  private Action previousCellAction;      // move to previous cell and optionally and edit
  private Action helpAction;              // invoke Help


  /**
   * the next serial for CellEvents below
   */
  private long cellEventSerial;           // starts at 0 for each table instance, 64-Bit should be enough ;-)
  private long minCellEventSerial;        // skip all events up to minCellEventSerial

  private long paintCount;                // to detect excessive repaints





  /**
   * Creates a formtable for a given data model.
   *
   * @param model the data model
   */
  public FormTable(TableModel model) {

    super();    // not super(model)! due to setModel() is overridden

    // setup renderer colors for FormTableCellRenderer
    setSelectedForeground(getSelectionForeground());
    setSelectedBackground(getSelectionBackground());
    setUnselectedForeground(getForeground());
    setUnselectedBackground(getBackground());
    setFocusedForeground(UIManager.getColor("Table.focusCellForeground"));
    setFocusedBackground(UIManager.getColor("Table.focusCellBackground"));
    Color color = UIManager.getColor("Table.unselectedMandatoryBackground");
    setUnselectedMandatoryBackground(color == null ? getBackground() : color);
    color = UIManager.getColor("Table.selectedMandatoryBackground");
    setSelectedMandatoryBackground(color == null ? getSelectionBackground() : color);

    // generate addon-actions
    nextCellAction      = new NextCellAction();
    previousCellAction  = new PreviousCellAction();
    helpAction          = new AbstractAction() {
      private static final long serialVersionUID = -5644390861803492172L;
      @Override
      public void actionPerformed(ActionEvent e)  {
        FormUtilities.getInstance().openHelpURL(FormTable.this);
      }
    };

    // register new Actions:
    getActionMap().put(NEXTCELL_ACTION, nextCellAction);
    getActionMap().put(PREVIOUSCELL_ACTION, previousCellAction);
    getActionMap().put(HELP_ACTION, helpAction);

    // register key-binding for these actions.
    // notice: we don't do that in the plaf cause its an application feature, not a UI-style
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                      NEXTCELL_ACTION);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Event.SHIFT_MASK),
                      PREVIOUSCELL_ACTION);

    // add the help key
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),
                      HELP_ACTION);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
                      HELP_ACTION);

    cellTraversal = CELLTRAVERSAL_NONE;

    // setup the mouse-listener to process double-click.
    // double-click: select row and actionPerformed (for example to edit a row)
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1)  {
          clicks = e.getClickCount();
          doFireActionPerformed(CLICK_ACTION);
        }
      }
    });

    // register renderers
    defaultRenderersByColumnInterface = new LinkedHashMap<>();
    setDefaultRenderer(Object.class, new FormTableCellRenderer());
    setDefaultRenderer(String.class, new FormTableCellRenderer());
    setDefaultRenderer(Date.class, new FormTableCellRenderer());
    setDefaultRenderer(Boolean.class, new BooleanTableCellRenderer());
    // notice: Number.class doesnt work because subclasses override!
    setDefaultRenderer(Byte.class, new FormTableCellRenderer());
    setDefaultRenderer(Short.class, new FormTableCellRenderer());
    setDefaultRenderer(Integer.class, new FormTableCellRenderer());
    setDefaultRenderer(Long.class, new FormTableCellRenderer());
    setDefaultRenderer(Float.class, new FormTableCellRenderer());
    setDefaultRenderer(Double.class, new FormTableCellRenderer());
    setDefaultRenderer(BMoney.class, new FormTableCellRenderer());

    // register editors
    defaultEditorsByColumnInterface = new LinkedHashMap<>();
    setDefaultEditor(String.class, new FormFieldComponentCellEditor(new StringFormField()));
    setDefaultEditor(Timestamp.class, new FormFieldComponentCellEditor(new DateFormField(FormatHelper.getShortTimestampPattern())));
    setDefaultEditor(Date.class, new FormFieldComponentCellEditor(new DateFormField()));
    setDefaultEditor(Byte.class, new FormFieldComponentCellEditor(new ByteFormField()));
    setDefaultEditor(Short.class, new FormFieldComponentCellEditor(new ShortFormField()));
    setDefaultEditor(Integer.class, new FormFieldComponentCellEditor(new IntegerFormField()));
    setDefaultEditor(Long.class, new FormFieldComponentCellEditor(new LongFormField()));
    setDefaultEditor(Float.class, new FormFieldComponentCellEditor(new FloatFormField()));
    setDefaultEditor(Double.class, new FormFieldComponentCellEditor(new DoubleFormField()));
    setDefaultEditor(DMoney.class, new FormFieldComponentCellEditor(new BMoneyFormField(true)));
    setDefaultEditor(BMoney.class, new FormFieldComponentCellEditor(new BMoneyFormField()));
    FormCheckBox box = new FormCheckBox();
    box.setHorizontalAlignment(JLabel.CENTER);
    box.setBackground(getFocusedBackground());
    setDefaultEditor(Boolean.class, new FormComponentCellEditor(box));

    // now the table is ready to set the data model
    if (model != null) {
      setModel(model);
    }
  }


  /**
   * Creates an empty form table.
   * The model has to set by {@link #setModel}.
   */
  public FormTable()  {
    this(null);
  }



  /**
   * Gets the help url.
   *
   * @return the help url, null if none
   */
  public String getHelpURL() {
    return helpURL;
  }

  /**
   * Sets the help url.
   *
   * @param helpURL  the help url, null if none
   */
  public void setHelpURL(String helpURL) {
    this.helpURL = helpURL;
  }



  /**
   * Returns whether the table may receive keyboard focus
   * due to a form traversal.
   * @return true if can receive focus, default is false (focus only by mouse-click)
   */
  public boolean isFormTraversable() {
    return formTraversable;
  }

  /**
   * Sets whether the table may receive keyboard focus
   * due to a form traversal.
   * @param formTraversable  true if can receive focus, default is false (focus only by mouse-click)
   */
  public void setFormTraversable(boolean formTraversable) {
    this.formTraversable = formTraversable;
  }


  /**
   * Sets the utility popup.
   *
   * @param utilityPopup the popup, null to clear
   */
  public void setUtilityPopup(FormTableUtilityPopup utilityPopup) {
    uninstallUtilityPopup();
    this.utilityPopup = utilityPopup;
    installUtilityPopup();
  }


  /**
   * Performs initialization of the utility popup.<br>
   * The method is invoked from within {@link #setUtilityPopup}.
   */
  protected void installUtilityPopup() {
    if (this.utilityPopup != null) {
      getTableHeader().addMouseListener(this.utilityPopup);
      addMouseListener(this.utilityPopup);
      JTableHeader header = getTableHeader();
      if (header != null) {
        header.addMouseListener(this.utilityPopup);
      }
      this.utilityPopup.setTable(this);
    }
  }


  /**
   * Performs de-initialization of the utility popup.<br>
   * The method is invoked from within {@link #setUtilityPopup}.
   */
  protected void uninstallUtilityPopup() {
    if (this.utilityPopup != null) {
      getTableHeader().removeMouseListener(this.utilityPopup);
      removeMouseListener(this.utilityPopup);
      JTableHeader header = getTableHeader();
      if (header != null) {
        header.removeMouseListener(this.utilityPopup);
      }
      this.utilityPopup.setTable(null);
    }
  }


  /**
   * Gets the utility popup.
   *
   * @return the popup, null if none
   */
  public FormTableUtilityPopup getUtilityPopup() {
    return utilityPopup;
  }


  /**
   * Resize the column widths according to the displayed data.
   */
  public void autoResizeColumnWidths() {
    for (int colNdx = 0; colNdx < getColumnCount(); colNdx++) {
      DefaultTableColumnModel colModel = (DefaultTableColumnModel) getColumnModel();
      TableColumn col = colModel.getColumn(colNdx);
      TableCellRenderer renderer = col.getHeaderRenderer();
      if (renderer == null) {
        renderer = getTableHeader().getDefaultRenderer();
      }

      Component comp = renderer.getTableCellRendererComponent(this, col.getHeaderValue(), false, false, 0, 0);
      int width = comp.getPreferredSize().width;

      for (int rowNdx = 0; rowNdx < this.getRowCount(); rowNdx++) {
        renderer = this.getCellRenderer(rowNdx, colNdx);
        comp = renderer.getTableCellRendererComponent(this, getValueAt(rowNdx, colNdx), false, false, rowNdx, colNdx);
        width = Math.max(width, comp.getPreferredSize().width);
      }

      col.setPreferredWidth(width + 10);    // with some margin
    }
  }



  /**
   * Determines whether some data cell has been changed.
   *
   * @return true if table data has been changed by user
   */
  public boolean isDataChanged()  {
    TableModel m = getModel();
    if (m instanceof AbstractFormTableModel)  {
      return ((AbstractFormTableModel)m).isDataChanged();
    }
    return false; // no idea
  }



  /**
   * Gets the Preferences-name for this table.<br>
   * The name is built from the classname of the FormTableEntry associated with the table-model
   * plus the name of the table.
   * E.g.: <tt>"/de/krake/bixworx/common/OpAusziffTableEntry/opAusziffOpTable"</tt>
   *
   * @param entry the formtable entry
   * @return the Preferences name, null if entry is null
   */
  public String getPreferencesName(FormTableEntry<?> entry) {
    if (entry != null) {
      return FormUtilities.getInstance().getPreferencesName(entry.getClass(), getName());
    }
    return null;
  }


  /**
   * Gets the Preferences-name for this table.<br>
   * By default, the name is evaluated from the template of the model which is assumed
   * to be an {@link AbstractFormTableModel}.
   *
   * @return the Preferences name, null if table model does not support that feature
   */
  public String getPreferencesName() {
    TableModel model = getModel();
    if (model instanceof AbstractFormTableModel) {
      return getPreferencesName(((AbstractFormTableModel) model).getTemplate());
    }
    return null;
  }


  /**
   * Creates the columns from the Preferences and sets the preferred table
   * size according to the column sizes.
   *
   * @param prefName is the name of the Preferences-node
   * @param systemOnly is true if take from SystemPreferences only. Otherwise
   *        system is only consulted if there is no user setting.
   *
   * @return true if columns or size changed, false if no preferences applied
   */
  public boolean createDefaultColumnsFromPreferences(String prefName, boolean systemOnly)  {

    TableModel m = getModel();

    boolean somePrefsLoaded = false;    // true if _some_ preferences found
    boolean somePrefsMissing = false;   // true if _some_ preferences missing

    if (m != null)  {

      int colCount = m.getColumnCount();

      // get the preferences
      PersistedPreferences systemPref = PersistedPreferences.systemRoot().node(prefName);
      PersistedPreferences userPref   = systemOnly ? null : PersistedPreferences.userRoot().node(prefName);

      // read table dimensions
      int width = -1;
      String key = PREF_TABLE_WIDTH;
      if (!systemOnly)  {
        width = userPref.getInt(key, width);
      }
      if (width == -1)  {
        width = systemPref.getInt(key, width);
      }

      int height = -1;
      key = PREF_TABLE_HEIGHT;
      if (!systemOnly)  {
        height = userPref.getInt(key, height);
      }
      if (height == -1)  {
        height = systemPref.getInt(key, height);
      }

      if (width > 0 && height > 0)  {
        preferencesSize = new Dimension(width, height);
        if (!sizeInPreferencesIgnored) {
          // set the preferred size of the container if it is a viewport
          Container p = getParent();
          if (p instanceof JViewport)  {
            p = p.getParent();
            if (p instanceof JScrollPane)  {
              // usually in a scrolling region
              ((JScrollPane)p).setPreferredSize(preferencesSize);
            }
          }
        }
      }

      // set 1:  columns by modelindex unordered, i.e. without any index given in preferences
      TableColumn[] uCols = new TableColumn[colCount];
      boolean[] uVisible = new boolean[colCount];
      int uColCount = 0;

      // set 2:  columns by modelindex ordered, i.e. with index given in preferences
      TableColumn[] oCols = new TableColumn[colCount];
      boolean[] oVisible  = new boolean[colCount];

      // initialize
      for (int i = 0; i < colCount; i++) {
        oCols[i]    = null;     // set to null = <not used>
        oVisible[i] = false;    // set to not visible as default
        uVisible[i] = false;    // set to not visible as default
      }

      boolean isAbstractFormTableModel = m instanceof AbstractFormTableModel;

      // build and set preferredwidth and location if Preferences available
      for (int i = 0; i < colCount; i++) {

        TableColumn tc = new TableColumn(i);

        // set displayed column name
        tc.setHeaderValue(isAbstractFormTableModel ? ((AbstractFormTableModel)m).getDisplayedColumnName(i) : m.getColumnName(i));

        // get the the width, if given by preferences, else leave default
        key = PREF_COLUMN_WIDTH + m.getColumnName(i);     // keys in the node are "w_<columnname>"

        width = -1;
        if (!systemOnly)  {
          width = userPref.getInt(key, width);
        }
        if (width == -1)  {
          width = systemPref.getInt(key, width);
        }
        // set the width
        if (width >= 0)  {
          tc.setPreferredWidth(width);
        }

        // get the location (view index)
        key = PREF_COLUMN_INDEX + m.getColumnName(i);     // keys in the node are "i_<columnname>"
        int index = -1;
        if (!systemOnly)  {
          index = userPref.getInt(key, index);
        }
        if (index == -1)  {
          index = systemPref.getInt(key, index);
        }

        // get the visibility
        // keys in the node are "v_<columnname>"
        key = PREF_COLUMN_VISIBILITY + m.getColumnName(i);
        int visible = -1;   // unknown
        if (!systemOnly)  {
          visible = userPref.getInt(key, visible);
        }
        if (visible == -1)  {
          visible = systemPref.getInt(key, visible);
        }

        if (visible == 1) {
          somePrefsLoaded = true;     // at least one column visible
        }
        else if (visible == -1) {
          somePrefsMissing = true;    // at least one column setting missing (new or changed table?)
        }

        if (index >= 0 && index < colCount && oCols[index] == null)  {
          // if index given and in range and ordered slot is free
          oCols[index]    = tc;
          oVisible[index] = true;     // default is now visible
          if (visible != -1) {
            oVisible[index] = visible == 1;
          }
        }
        else  {
          uCols[uColCount] = tc;    // add to unordered
          if (visible != -1) {
            uVisible[uColCount] = visible == 1;
          }
          uColCount++;
        }
      }

      if (somePrefsLoaded)  {   // if some prefs found and table will have at least have one visible column

        if (somePrefsMissing) {
          /**
           * The preferences don't match the table model. May be the model has changed.
           * Ask the user whether to apply those incomplete preferences or leave the default.
           */
          if (!FormQuestion.yesNo(MessageFormat.format(
                  "Table {0} has changed.\nSaved preferences are incomplete.\nApply preferences anyway?",
                  StringHelper.lastAfter(prefName, '/')))) {
            return false;
          }
        }

        // Remove any current columns
        TableColumnModel cm = getColumnModel();

        if (cm instanceof FormTableColumnModel) {
          FormTableColumnModel fcm = (FormTableColumnModel)cm;
          fcm.removeAllColumns();
          // append the columns to the column-model
          uColCount = 0;
          for (int i = 0; i < colCount; i++) {
            if (oCols[i] != null)  {
              fcm.addColumn(oCols[i]);             // place at preferred location
              if (oVisible[i] == false) {
                fcm.setOriginalColumnVisible(i, false);
              }
            }
            else  {
              fcm.addColumn(uCols[uColCount]);     // take from pool of unordered
              if (uVisible[uColCount] == false) {
                fcm.setOriginalColumnVisible(i, false);
              }
              uColCount++;
            }
          }
        }
        else  {
          while (cm.getColumnCount() > 0) {
            cm.removeColumn(cm.getColumn(0));
          }
          // append the columns to the column-model
          uColCount = 0;
          for (int i = 0; i < colCount; i++) {
            if (oCols[i] != null)  {
              cm.addColumn(oCols[i]);             // place at preferred location
            }
            else  {
              cm.addColumn(uCols[uColCount++]);   // take from pool of unordered
            }
          }
        }

        configureRenderers();
        configureEditors();
      }
    }

    return somePrefsLoaded;
  }




  /**
   * Gets the preferred size as defined by the preferences.
   *
   * @return the preferred size from the preferences
   */
  public Dimension getPreferencesSize() {
    return preferencesSize;
  }


  /**
   * Returns whether the columns are created according to the preferences.
   *
   * @return true if columns are created according to Preferences
   */
  public boolean isCreateDefaultColumnsFromPreferences() {
    return createDefaultColumnsFromPreferences;
  }

  /**
   * Sets whether the columns are created according to the preferences.
   * @param createDefaultColumnsFromPreferences  true if createDefaultColumnsFromModel() should invoke createDefaultColumnsFromPreferences()
   */
  public void setCreateDefaultColumnsFromPreferences(boolean createDefaultColumnsFromPreferences) {
    this.createDefaultColumnsFromPreferences = createDefaultColumnsFromPreferences;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to create the columns according to the Preferences
   * if the tableModel is an AbstractFormTableModel (and Preferences exist for that table).
   */
  @Override
  public void createDefaultColumnsFromModel() {
    createDefaultColumnsFromDefaultModel();
    if (createDefaultColumnsFromPreferences && getModel() instanceof AbstractFormTableModel &&
        createDefaultColumnsFromPreferences(
          getPreferencesName(), PersistedPreferencesFactory.getInstance().isSystemOnly())) {
    }
    else  {
      createDefaultColumnsFromDefaultModel();
    }
  }


  /**
   * Creates the columns from the default column model
   */
  public void createDefaultColumnsFromDefaultModel()  {

    TableColumnModel cm = getColumnModel();
    if (cm instanceof FormTableColumnModel) {
      ((FormTableColumnModel)cm).removeAllColumns();
    }

    super.createDefaultColumnsFromModel();

    configureRenderers();
    configureEditors();

    // set the header names if AbstractFormTableModel
    TableModel dm = getModel();
    if (dm instanceof AbstractFormTableModel) {
      // set the header names
      for (int i = 0; i < dm.getColumnCount(); i++) {
        TableColumn tc = cm.getColumn(i);
        tc.setHeaderValue(((AbstractFormTableModel)dm).getDisplayedColumnName(i));
      }
    }
  }




  /**
   * Saves the preferences of this table.
   *
   * @param prefName the name of the Preferences-node
   * @param system true if store to system-preferences, else store in userprefs
   * @throws BackingStoreException if save failed
   */
  public void savePreferences(String prefName, boolean system) throws BackingStoreException {

    TableModel m = getModel();
    PersistedPreferences prefs = system ?
            PersistedPreferences.systemRoot().node(prefName) :
            PersistedPreferences.userRoot().node(prefName);
    TableColumnModel cm = getColumnModel();

    if (cm instanceof FormTableColumnModel) {
      FormTableColumnModel fcm = (FormTableColumnModel)cm;
      int viewIndex = 0;
      Enumeration<TableColumn> ocols = fcm.getOriginalColumns();  // all columns! even those not visible!
      while (ocols.hasMoreElements())  {
        TableColumn tc = ocols.nextElement();
        String columnIdentifier = createColumnIdentifier(tc);
        prefs.putInt(PREF_COLUMN_WIDTH + columnIdentifier, tc.getPreferredWidth());
        prefs.putInt(PREF_COLUMN_INDEX + columnIdentifier, viewIndex);
        prefs.putInt(PREF_COLUMN_VISIBILITY + columnIdentifier, fcm.isOriginalColumnVisible(viewIndex) ? 1 : 0); // not bool, cause of default is unknown
        viewIndex++;
      }
    }
    else  {
      int viewIndex = 0;
      Enumeration<TableColumn> cols = cm.getColumns();
      while (cols.hasMoreElements())  {
        TableColumn tc = cols.nextElement();
        String columnIdentifier = createColumnIdentifier(tc);
        prefs.putInt(PREF_COLUMN_WIDTH + columnIdentifier, tc.getPreferredWidth());
        prefs.putInt(PREF_COLUMN_INDEX + columnIdentifier, viewIndex);
        viewIndex++;
      }
    }

    // store also the dimensions of the table
    Container p = getParent();
    if (p instanceof JViewport)  {
      p = p.getParent();
      if (p instanceof JScrollPane)  {
        // usually in a scrolling region
        prefs.putInt(PREF_TABLE_HEIGHT, p.getHeight());
        prefs.putInt(PREF_TABLE_WIDTH, p.getWidth());
      }
    }
    else  {
      prefs.putInt(PREF_TABLE_HEIGHT, -1);         // makes no sense if not in a viewport
      prefs.putInt(PREF_TABLE_WIDTH, getWidth());  // store just for documentation
    }
    prefs.flush();
  }



  /**
   * Creates an identification String for the specified TableColumn.<br>
   * Used for storing/retrieving table preferences.
   *
   * @param column   the tableColumn
   * @return the created identifier String
   */
  protected String createColumnIdentifier(final TableColumn column) {
    // the default implementation just returns the column name
    TableModel m = getModel();
    return m.getColumnName(column.getModelIndex());
  }



  /**
   * Sets the row-selection (single row)
   * (getSelectedRow is implemented in JTable but not setSelectedRow, for whatever reason)
   *
   * @param row the row number
   */
  public void setSelectedRow(int row) {
    selectionModel.setSelectionInterval(row, row);
  }


  /**
   * Sets the row-selection for an array of rows.
   * @param rows the rows
   */
  public void setSelectedRows(int[] rows) {
    selectionModel.clearSelection();
    if (rows != null) {
      for (int i=0; i < rows.length; i++) {
        selectionModel.addSelectionInterval(rows[i], rows[i]);
      }
    }
  }




  /**
   * Sets the selected column.
   * (getSelectedColumn is implemented in JTable but not setSelectedColumn, for whatever reason)
   * @param col the column number
   */
  public void setSelectedColumn(int col) {
    columnModel.getSelectionModel().setSelectionInterval(col, col);
  }


  /**
   * Sets the col-selection for an array of columns.
   * @param cols the columns
   */
  public void setSelectedColumns(int[] cols) {
    columnModel.getSelectionModel().clearSelection();
    if (cols != null) {
      for (int i=0; i < cols.length; i++) {
        columnModel.getSelectionModel().addSelectionInterval(cols[i], cols[i]);
      }
    }
  }




  /**
   * Gets the FormTableEntry at a given (visible) row.
   * Works only if the model is a FormTableModel.
   * @param row the row number
   * @return the formtable entry, null if no such entry
   */
  @SuppressWarnings("unchecked")
  public FormTableEntry<T> getEntryAt(int row) {
    if (row >= 0) {
      row = convertRowIndexToModel(row);    // in case of RowSorter
    }
    if (usingAbstractFormTableModel) {
      return ((AbstractFormTableModel<T>) getModel()).getEntryAt(row);
    }
    return null;
  }


  /**
   * Gets the data object that is associated to a given (visible) row.
   * Works only if the model is a FormTableModel.
   *
   * @param row the row number
   * @return the data object, null if no such row
   */
  public T getObjectAt(int row) {
    FormTableEntry<T> entry = getEntryAt(row);
    return entry == null ? null : entry.getObject();
  }


  /**
   * Gets the data object at the current row.
   * Useful for action listeners when row is double-clicked.
   * Works only if the model is a FormTableModel.
   *
   * @return the dataobject, null if no current selection
   */
  public T getSelectedObject() {
    int row = getSelectedRow();
    return row >= 0 ? getObjectAt(row) : null;
  }



  /**
   * Queues an Event at the end of the event-queue.
   * @param e the event
   */
  protected void queueEvent(final AWTEvent e) {
    EventQueue.invokeLater(() -> {
      processEvent(e);
    });
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to handle cell-traversal events
   */
  @Override
  protected void processEvent(AWTEvent e)  {

    if (e instanceof FormTable.CellTraversalEvent) {
      if (cellEventSerial > minCellEventSerial) {
        if (((FormTable.CellTraversalEvent) e).next) {
          nextCellAction.actionPerformed(new CellActionEvent(cellTraversal));
        }
        else  {
          previousCellAction.actionPerformed(new CellActionEvent(cellTraversal));
        }
      }
    }

    else if (e instanceof FormTable.CellEditingEvent) {
      if (cellEventSerial > minCellEventSerial) {
        @SuppressWarnings("unchecked")
        FormTable<T>.CellEditingEvent cee = (FormTable<T>.CellEditingEvent) e;
        int row = cee.row;
        int col = cee.col;
        changeSelection(cee.row, cee.col, false, false);
        if (cee.mode != CellEditingEvent.EXACT && !isCellEditable(row, col))  {
          // create a temporary traversal from table's traversal
          int ct = this.cellTraversal;
          if (ct == CELLTRAVERSAL_NONE) {
            ct = CELLTRAVERSAL_COLUMN;
          }
          ct |= CELLTRAVERSAL_SKIPNOEDIT | CELLTRAVERSAL_AUTOEDIT; // turn on if not on
          if (cee.mode == CellEditingEvent.NEXT)  {
            nextCellAction.actionPerformed(new CellActionEvent(ct));
          }
          else if (cee.mode == CellEditingEvent.PREVIOUS) {
            previousCellAction.actionPerformed(new CellActionEvent(ct));
          }
        }
        else  {
          editCellAt(row, col);
        }
      }
    }

    else  {
      super.processEvent(e);
    }
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to use FormTableColumnModel instead of DefaultTableColumnModel.
   * This is necessary to allow setVisible() of a column.
   */
  @Override
  protected TableColumnModel createDefaultColumnModel() {
    return new FormTableColumnModel();
  }



  /**
   * Hides or displays a column.
   * @param columnIndex the column index according to the data-model.
   * @param visible true if column is made visible, false if invisible
   */
  public void setColumnVisible(int columnIndex, boolean visible) {
    if (columnModel instanceof FormTableColumnModel)  {
      ((FormTableColumnModel)columnModel).setModelColumnVisible(columnIndex, visible);
    }
    else  {
      throw new IllegalArgumentException("column model is not FormTableColumnModel");
    }
  }

  /**
   * Returns whether column is visible or not.
   *
   * @param columnIndex the column index according to the data-model.
   * @return true if column is made visible, false if invisible
   */
  public boolean isColumnVisible(int columnIndex) {
    if (columnModel instanceof FormTableColumnModel)  {
      return ((FormTableColumnModel)columnModel).isModelColumnVisible(columnIndex);
    }
    else  {
      throw new IllegalArgumentException("column model is not FormTableColumnModel");
    }
  }




  /**
   * {@inheritDoc}
   * <p>
   * Overridden to implement enhanced keyboard handling.
   */
  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e,
                                      int condition, boolean pressed) {

    if (e != null && e.getID() == KeyEvent.KEY_PRESSED) {

      if (isEditing() && !isFocusOwner()) {
        if (e.getKeyCode() == KeyEvent.VK_F2) {
          // don't stop editing with F2. This allows FormComponentCellEditors to use
          // the F2-key for other purposes
          return false;
        }
      }
      else {
        if (condition == WHEN_ANCESTOR_OF_FOCUSED_COMPONENT &&
            !Boolean.FALSE.equals((Boolean)getClientProperty("JTable.autoStartsEdit")) &&
            e.isAltDown()) {
          /**
           * this will not start editing if an ALT-<key> is pressed
           * as this is often an accelerator to trigger a button.
           */
          return false;
        }
        else if (condition == WHEN_FOCUSED && e.getKeyCode() == KeyEvent.VK_ENTER &&
                 enableEnterAction && getSelectedRow() >= 0) {
          clicks = clickCountToAction;
          doFireActionPerformed(ENTER_ACTION);
          return false;
        }
      }
    }

    // do the default processing
    return super.processKeyBinding(ks, e, condition, pressed);
  }



  /**
   * Sets the color for the selected foreground.
   *
   * @param c the foreground color
   */
  public void setSelectedForeground(Color c) {
    selectedForeground = c;
  }

  /**
   * Gets the color for the selected foreground.
   *
   * @return the foreground color
   */
  public Color getSelectedForeground()  {
    return selectedForeground;
  }

  /**
   * Sets the color for the selected background.
   *
   * @param c the background color
   */
  public void setSelectedBackground(Color c) {
    selectedBackground = c;
  }

  /**
   * Gets the color for the selected background.
   *
   * @return the background color
   */
  public Color getSelectedBackground()  {
    return selectedBackground;
  }


  /**
   * Sets the color for the unselected foreground.
   *
   * @param c the unselected foreground color
   */
  public void setUnselectedForeground(Color c) {
    unselectedForeground = c;
  }

  /**
   * Gets the color for the unselected foreground.
   *
   * @return the unselected foreground color
   */
  public Color getUnselectedForeground()  {
    return unselectedForeground;
  }

  /**
   * Sets the color for the unselected background.
   *
   * @param c the background color
   */
  public void setUnselectedBackground(Color c) {
    unselectedBackground = c;
  }

  /**
   * Gets the color for the unselected background.
   *
   * @return the unselected background color
   */
  public Color getUnselectedBackground()  {
    return unselectedBackground;
  }


  /**
   * Sets the color for the focus foreground.
   *
   * @param c the focus foreground color
   */
  public void setFocusedForeground(Color c) {
    focusedForeground = c;
  }

  /**
   * Gets the color for the focus foreground.
   *
   * @return the focus foreground color
   */
  public Color getFocusedForeground()  {
    return focusedForeground;
  }

  /**
   * Sets the color for the focus background.
   *
   * @param c the focus background color
   */
  public void setFocusedBackground(Color c) {
    focusedBackground = c;
  }

  /**
   * Gets the color for the focus background.
   *
   * @return the focus background color
   */
  public Color getFocusedBackground()  {
    return focusedBackground;
  }


  /**
   * Gets the mandatory background for selected cells.
   *
   * @return mandatory background if not default from LAF
   */
  public Color getSelectedMandatoryBackground() {
    return selectedMandatoryBackground;
  }

  /**
   * Sets the mandatory background for selected cells.
   *
   * @param selectedMandatoryBackground mandatory background if not default from LAF
   */
  public void setSelectedMandatoryBackground(Color selectedMandatoryBackground) {
    this.selectedMandatoryBackground = selectedMandatoryBackground;
  }

  /**
   * Gets the mandatory background for unselected cells.
   *
   * @return mandatory background if not default from LAF
   */
  public Color getUnselectedMandatoryBackground() {
    return unselectedMandatoryBackground;
  }

  /**
   * Sets the mandatory background for unselected cells.
   *
   * @param unselectedMandatoryBackground mandatory background if not default from LAF
   */
  public void setUnselectedMandatoryBackground(Color unselectedMandatoryBackground) {
    this.unselectedMandatoryBackground = unselectedMandatoryBackground;
  }


  /**
   * Returns whether the given cell is currently being edited.
   *
   * @param row the row index
   * @param column the column index
   * @return true is cell is being edited
   */
  public boolean isEditing(int row, int column) {
    return isEditing() &&
           getEditingRow() == row &&
           getEditingColumn() == column;
  }



  /**
   * if the cell is already being edited, disable autoSelect() once
   * thus avoiding typed ahead keys to be deleted
   */
  private boolean shouldStartEditing(int row, int column)  {
    if (isEditing(row, column)) {
      Component comp = getEditorComponent();
      if (comp instanceof FormFieldComponent && ((FormFieldComponent)comp).isAutoSelect())  {
        ((FormFieldComponent)comp).setInhibitAutoSelect(true);
      }
      return false;
    }
    return true;    // yes, start editing
  }



  /**
   * Starts editing at a given row/column.<br>
   * If the cell is already being edited (i.e. cause of keyboard type-ahead)
   * the editing will *NOT* start over, i.e. does nothing.
   * This is in order to avoid typed ahead keys with EnterKeyTraversal enabled.
   * <p>
   * Notice that the edit-request is queued!
   *
   * @param row the row index
   * @param column the column index
   */
  public void editCellLater(int row, int column) {
    if (shouldStartEditing(row, column))  {
      queueEvent(new CellEditingEvent(CellEditingEvent.EXACT, row, column));
    }
  }

  /**
   * Same as editCellLater but advance to next editable cell if
   * given cell is not editable.
   * <p>
   * Notice that the edit-request is queued!
   *
   * @param row the row index
   * @param column the column index
   * @see #editCellLater(int, int)
   */
  public void editNextCellLater(int row, int column) {
    if (shouldStartEditing(row, column))  {
      queueEvent(new CellEditingEvent(CellEditingEvent.NEXT, row, column));
    }
  }

  /**
   * Same as editCellLater but advance to previous editable cell if
   * given cell is not editable
   *
   * <p>
   * Notice that the edit-request is queued!
   *
   * @param row the row index
   * @param column the column index
   * @see #editCellLater(int, int)
   */
  public void editPreviousCellLater(int row, int column) {
    if (shouldStartEditing(row, column))  {
      queueEvent(new CellEditingEvent(CellEditingEvent.PREVIOUS, row, column));
    }
  }


  /**
   * Edits the next cell according to cellTraversal.
   *
   * <p>
   * Notice that the edit-request is queued!
   */
  public void editNextCellLater() {
    queueEvent(new CellTraversalEvent(true));
  }


  /**
   * Edits the previous cell according to cellTraversal.
   *
   * <p>
   * Notice that the edit-request is queued!
   */
  public void editPreviousCellLater() {
    queueEvent(new CellTraversalEvent(false));
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to fix certain flaws in JTable.
   */
  @Override
  public boolean editCellAt (int row, int column, EventObject e) {

    if (isCellRectFixed() == false)  {
      FormTableEntry<?> entry = ((AbstractFormTableModel)getModel()).getEntryAt(row);
      int refRow              = entry.getReferencedRow(row, column);
      int refColumn           = entry.getReferencedColumn(row, column);
      row                     = refRow;
      column                  = refColumn;
    }

    if (super.editCellAt(row, column, e)) {

      if (getSurrendersFocusOnKeystroke())  {

        // this fixes a bug in JTable that the caret isn't visible the first time
        final Component editor = getEditorComponent();

        editor.requestFocusInWindow();    // does not work when called by invokeLater()!?

        AWTEvent evt = EventQueue.getCurrentEvent();

        if (evt instanceof KeyEvent)  {

          final KeyEvent kevt = (KeyEvent)evt;
          final char key = kevt.getKeyChar();

          if (!kevt.isConsumed() &&
              !kevt.isActionKey() &&
              kevt.getKeyCode() != KeyEvent.VK_ENTER &&
              kevt.getKeyCode() != KeyEvent.VK_ESCAPE)  {
            /**
             * fix the bug that the first keystroke will not be passed to components like combobox,
             * checkbox, radiobutton,...
             * The components only receive the KEY_RELEASED event, but neither KEY_PRESSED nor KEY_TYPED.
             * Does matter in editable FormComboBoxes and FormCheckBox (0/1-keys).
             */
            if (editor instanceof FormComboBox ||
                editor instanceof FormCheckBox ||
                editor instanceof FormRadioButton) {

              if (key != KeyEvent.CHAR_UNDEFINED) {
                // if valid character
                if (editor instanceof FormComboBox) {
                  final FormComboBox<?> box = (FormComboBox<?>) editor;
                  if (box.isEditable()) {
                    Component field = box.getEditor().getEditorComponent();
                    if (field instanceof AbstractFormField && ((AbstractFormField)field).isAutoSelect()) {
                      // clear contents of field first as otherwise the key would be
                      // appended to the selected area
                      ((AbstractFormField)field).clearText();
                      ((AbstractFormField)field).setInhibitAutoSelect(true);
                    }
                    field.dispatchEvent(
                        new KeyEvent(field, KeyEvent.KEY_TYPED,
                                     kevt.getWhen()+1,  // +1 to get processed!
                                     kevt.getModifiers(), KeyEvent.VK_UNDEFINED, key));
                  }
                  else  {
                    EventQueue.invokeLater(() -> {
                      box.selectWithKeyChar(key);
                    });
                  }
                }
                else {
                  EventQueue.invokeLater(() -> {
                    editor.dispatchEvent(
                            new KeyEvent(editor, KeyEvent.KEY_PRESSED,
                                    kevt.getWhen()+1,  // +1 to get processed!
                                    kevt.getModifiers(), KeyEvent.VK_UNDEFINED, key));
                  });
                }
              }
            }
            else if (editor instanceof FormComponentPanel) {
              /**
               * JTable will pass the key to the panel, which is wrong.
               * We will re-post it to the effective field here.
               */
              FormComponent field = ((FormComponentPanel) editor).getFormComponent();

              if (field instanceof FormFieldComponent && ((FormFieldComponent)field).isAutoSelect()) {
                // clear the textfield if autoselect is turned on
                ((FormFieldComponent)field).setFormValue(null);
              }

              if (key != KeyEvent.CHAR_UNDEFINED) {
                ((Component) field).dispatchEvent(
                        new KeyEvent((Component) field, KeyEvent.KEY_TYPED,
                                     kevt.getWhen()+1,  // +1 to get processed!
                                     kevt.getModifiers(), KeyEvent.VK_UNDEFINED, key));
              }
            }
            else if (editor instanceof FormFieldComponent && ((FormFieldComponent)editor).isAutoSelect()) {
              // autoselect would be applied after the character shows up in the editor.
              // so we clear first
              ((FormFieldComponent)editor).setFormValue(null);
            }
          }
        }
      }
      return true;
    }
    return false;
  }





  /**
   * {@inheritDoc}
   * <p>
   * Overridden to catch the last key for generating CellTraversalEvents
   */
  @Override
  public void editingStopped(ChangeEvent e) {

    Component comp = getEditorComponent();

    super.editingStopped(e);

    if (comp instanceof FormComponent &&
        cellTraversal != CELLTRAVERSAL_NONE && !inhibitCellTraversal)  {
      if (EventQueue.getCurrentEvent() instanceof FocusEvent ||
          EventQueue.getCurrentEvent() instanceof KeyEvent) {
        // editing stopped due to ENTER/TAB
        if (((FormComponent)comp).wasTransferFocusByEnter()) {
          // last keystroke in editor was ENTER/TAB or Shift-ENTER/TAB
          queueEvent(new CellTraversalEvent(!((FormComponent)comp).wasTransferFocusBackward()));
        }
      }
    }

    inhibitCellTraversal = false;
  }


  @Override
  public void editingCanceled(ChangeEvent e)  {
    super.editingCanceled(e);
  }



  /**
   * Inhibits cell traversal once.<br>
   * Useful if we should stay in selected field even after Enter-key has been pressed.
   * For ex. to disable wrapping to the next line if we are at end of line.
   */
  public void inhibitCellTraversal() {
    inhibitCellTraversal = true;
  }


  /**
   * Clears the inhibit of the cell traversal if for some reason set erroneously.
   */
  public void clearInhibitCellTraversal() {
    inhibitCellTraversal = false;
  }


  /**
   * Discards all cell events in the event queue.
   */
  public void discardCellEvents() {
    minCellEventSerial = cellEventSerial;
  }




  /**
   * {@inheritDoc}
   * <p>
   * Overridden to setup the formatting, renderers and editors.
   */
  @Override
  @SuppressWarnings("unchecked")
  public void setModel(TableModel dataModel)  {

    if (dataModel instanceof AbstractFormTableModel) {
      usingAbstractFormTableModel = true;
      ((AbstractFormTableModel<T>) dataModel).setTable(this);
    }
    else  {
      usingAbstractFormTableModel = false;
    }

    // get the last BindableTableModel in chain, if any
    bindableModel = (BindableTableModel<T>) findBindableTableModel(dataModel);

    super.setModel(dataModel);

    if (usingAbstractFormTableModel) {
      applyTemplate(((AbstractFormTableModel<T>) dataModel).getTemplate());
      configureRenderers();
      configureEditors();
    }
    else  {
      applyTemplate(null);
    }

    setClickCountToStart(clickCountToStart);
  }



  /**
   * Finds the last {@link BindableTableModel} in the model chain.
   *
   * @param model the table model
   * @return the bindable table model, null if none
   */
  public static BindableTableModel<?> findBindableTableModel(TableModel model) {

    BindableTableModel<?> bindableModel = null;

    while (model != null) {

      if (model instanceof BindableTableModel) {
        bindableModel = (BindableTableModel<?>) model;
      }

      if (model instanceof FormTableMap) {
        // walk along the model chain
        model = ((FormTableMap) model).getModel();
      }
      else {
        // end of chain
        model = null;
      }
    }

    return bindableModel;
  }



  /**
   * Sets the formatting flags from a {@link FormTableEntry}.
   *
   * @param template the table entry, null to set defaults
   */
  public void applyTemplate(FormTableEntry<T> template) {

    int columns = dataModel.getColumnCount();
    format        = new ArrayList<>(columns);
    formatFlags   = new ArrayList<>(columns);
    dateFormat    = new ArrayList<>(columns);
    numberFormat  = new ArrayList<>(columns);
    maxColumns    = new ArrayList<>(columns);
    scales        = new ArrayList<>(columns);
    hAlignment    = new ArrayList<>(columns);
    vAlignment    = new ArrayList<>(columns);

    if (template != null) {

      setCellEditorFixed(template.isCellEditorFixed());
      setCellRendererFixed(template.isCellRendererFixed());
      setCellRectFixed(template.isCellRectFixed());

      for (int i=0; i < columns; i++) {
        // setup format
        String fmt = template.getFormat(i);
        format.add(fmt);
        // initialize format-flags
        formatFlags.add("");
        Boolean blankZero = template.isBlankZero(i);
        if (blankZero != null) {
          setBlankZero(i, blankZero);  // set blankzero as default
        }
        Boolean autoSelect = template.isAutoSelect(i);
        if (autoSelect != null) {
          setAutoSelect(i, autoSelect);
        }
        Character conv = template.getConvert(i);
        if (conv != null) {
          setConvert(i, conv);
        }
        dateFormat.add(null); // delayed, see getDateFormat() below
        numberFormat.add(null);
        hAlignment.add(template.getHorizontalAlignment(i));
        vAlignment.add(template.getVerticalAlignment(i));
        maxColumns.add(template.getMaxColumns(i));
        scales.add(template.getScale(i));
      }
    }
    else  {
      setCellEditorFixed(true);
      setCellRendererFixed(true);
      setCellRectFixed(true);

      for (int i=0; i < columns; i++) {
        format.add(null);
        formatFlags.add("");
        dateFormat.add(null);
        numberFormat.add(null);
        hAlignment.add(null);
        vAlignment.add(null);
        maxColumns.add(null);
        scales.add(null);
      }
    }
  }


  /**
   * Updates the renderers.<br>
   * Useful, for example, if the some configuration changed and the
   * renderers are fixed (see {@link FormTableEntry#isCellRendererFixed}).
   * Does nothing if the current model is not an AbstractFormTableModel.
   */
  public void configureRenderers() {
    if (usingAbstractFormTableModel) {
      int columns = dataModel.getColumnCount();
      FormTableEntry<?> template = ((AbstractFormTableModel)dataModel).getTemplate();
      for (int i=0; i < columns; i++) {
        TableCellRenderer renderer = template.getCellRenderer(i);
        if (renderer != null) {
          TableColumn col = getColumnByModelIndex(i);
          if (col != null) {
            col.setCellRenderer(renderer);
          }
        }
      }
    }
  }


  /**
   * Updates the editors.<br>
   * Useful, for example, if the some configuration changed and the
   * editors are fixed (see {@link FormTableEntry#isCellEditorFixed}).
   * Does nothing if the current model is not an AbstractFormTableModel.
   */
  public void configureEditors() {
    if (usingAbstractFormTableModel) {
      int columns = dataModel.getColumnCount();
      FormTableEntry<?> template = ((AbstractFormTableModel) dataModel).getTemplate();
      for (int i=0; i < columns; i++) {
        TableCellEditor editor = template.getCellEditor(i);
        if (editor != null) {
          TableColumn col = getColumnByModelIndex(i);
          if (col != null) {
            col.setCellEditor(editor);
          }
        }
      }
    }
  }



  /**
   * Gets the table-column according to the data-model index.
   *
   * @param modelIndex the column index according to the data model
   * @return the table-column (even if it is not visible, see FormTableColumnModel)
   */
  public TableColumn getColumnByModelIndex(int modelIndex)  {
    TableColumnModel colModel = getColumnModel();
    if (colModel instanceof FormTableColumnModel) {
      return ((FormTableColumnModel)colModel).getColumnByModelIndex(modelIndex);
    }
    // else standard column model, implement feature of FormTableColumnModel here:
    Enumeration<TableColumn> columns = colModel.getColumns();
    while (columns.hasMoreElements())  {
      TableColumn column = columns.nextElement();
      if (column.getModelIndex() == modelIndex) {
        return column;
      }
    }
    return null;
  }



  /**
   * Refers to the default implementation of getCellRect()
   * because overridden.
   * @param row the row index
   * @param column the column index
   * @param includeSpacing false to return the true cell bounds
   * @return the rectangle
   * @see JTable#getCellRect(int, int, boolean)
   */
  public Rectangle getDefaultCellRect(int row, int column, boolean includeSpacing) {
    return super.getCellRect(row, column, includeSpacing);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to implement cell merging.
   */
  @Override
  public Rectangle getCellRect(int row, int column, boolean includeSpacing) {
    if (isCellRectFixed() == false)  {
      @SuppressWarnings("unchecked")
      AbstractFormTableModel<T> amodel = (AbstractFormTableModel<T>) getModel();
      FormTableEntry<T> entry = amodel.getEntryAt(row);
      Rectangle rect = entry.getCellRect(row, column, includeSpacing);
      if (rect != null) {
        return rect;
      }
    }
    return getDefaultCellRect(row, column, includeSpacing);
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to implement cell merging.
   */
  @Override
  public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {

    if (isCellRectFixed() == false)  {

      FormTableEntry<?> entry = ((AbstractFormTableModel) getModel()).getEntryAt(rowIndex);
      int refRowIndex         = entry.getReferencedRow(rowIndex, columnIndex);
      int refColumnIndex      = entry.getReferencedColumn(rowIndex, columnIndex);

      /**
       * special handling for TAB/BackTAB and Arrow-Keys if pressed in a selected
       * and referenced cell.
       * The straight forward way would need to reimplement the whole BasicTableUI
       * but this is not easy because of sun.swing.UIAction is unpublished code.
       *
       * We simple check whether the old and new selection sticks when
       * a key was pressed that invoked changeSelection.
       * This is a bad workaround, but I don't see any alternative.
       */

      if (toggle == false && extend == false &&   // if simple selection
          refRowIndex == rowIndex &&          // if same line
          refColumnIndex != columnIndex &&    // and mapped to another column
          getColumnModel().getSelectionModel().getLeadSelectionIndex() == refColumnIndex) { // which is already selected

        AWTEvent event = EventQueue.getCurrentEvent();
        if (event instanceof KeyEvent)  {

          int keyCode = ((KeyEvent)event).getKeyCode();
          if (keyCode == KeyEvent.VK_TAB || keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_LEFT)  {
            boolean visibleFound = false;
            boolean wrapped = false;
            int maxcol = getColumnModel().getColumnCount();
            int maxrow = getRowCount();

            if (columnIndex > refColumnIndex) {   // Tab, right-arrow, etc...
              // skip to next unmapped column in current row.
              // if there is none and key was tab, continue on next row, else stop.
              while (!wrapped || rowIndex != refRowIndex) {
                entry = ((AbstractFormTableModel)getModel()).getEntryAt(rowIndex);
                while (columnIndex < maxcol) {
                  if (entry.isCellVisible(rowIndex, columnIndex)) {
                    visibleFound = true;
                    break;
                  }
                  columnIndex++;
                }
                if (!visibleFound && keyCode == KeyEvent.VK_TAB) {
                  // try next line
                  rowIndex++;
                  columnIndex = 0;
                  if (rowIndex >= maxrow) {
                    rowIndex = 0;
                    wrapped  = true;
                  }
                }
                else {
                  break;
                }
              }
            }
            else  {
              // skip to previous unmapped column in current row.
              // if there is none and key was tab, continue on previous row, else stop.
              while (!wrapped || rowIndex != refRowIndex) {
                entry = ((AbstractFormTableModel)getModel()).getEntryAt(rowIndex);
                while (columnIndex >= 0) {
                  if (entry.isCellVisible(rowIndex, columnIndex)) {
                    visibleFound = true;
                    break;
                  }
                  columnIndex--;
                }
                if (!visibleFound && keyCode == KeyEvent.VK_TAB) {
                  // try previous line
                  rowIndex--;
                  columnIndex = maxcol - 1;
                  if (rowIndex < 0) {
                    rowIndex = maxrow - 1;
                    wrapped  = true;
                  }
                }
                else {
                  break;
                }
              }
            }

            if (visibleFound) {
              refRowIndex = rowIndex;
              refColumnIndex = columnIndex;
            }
          }
        }
      }

      rowIndex = refRowIndex;
      columnIndex = refColumnIndex;
      // go ahead...
    }

    super.changeSelection(rowIndex, columnIndex, toggle, extend);
  }



  /**
   * Sets the cell traversal mode.
   *
   * @param mode the cell traversal mode, one or more of <tt>CELLTRAVERSAL_...</tt>
   */

  public void setCellTraversal (int mode)  {
    cellTraversal = mode;
  }


  /**
   * Sets the cell traversal mode.
   *
   * @return the cell traversal mode
   */
  public int getCellTraversal()  {
    return cellTraversal;
  }


  /**
   * Adds a selection changed listener.
   * @param listener the listener to add
   */
  public synchronized void addListSelectionListener (ListSelectionListener listener)  {
    listenerList.add (ListSelectionListener.class, listener);
  }

  /**
   * Removes a selection changed Listener.
   *
   * @param listener the listener to remove
   */
  public synchronized void removeListSelectionListener (ListSelectionListener listener) {
     listenerList.remove (ListSelectionListener.class, listener);
  }

  /**
   * Notifies all Listeners that the selection has changed.
   *
   * @param evt the selection event
   */
  public void fireValueChanged (ListSelectionEvent evt) {
    Object[] listeners = this.listenerList.getListenerList();
    if (listeners != null)  {
      for (int i = listeners.length-2; i >= 0; i -= 2)  {
        if (listeners[i] == ListSelectionListener.class)  {
          ((ListSelectionListener)listeners[i+1]).valueChanged(evt);
        }
      }
    }
  }

  /**
   * Invoked when the row selection changes.
   * <p>
   * Overridden to fire the formtable listeners.
   * @param evt the selection event
   */
  @Override
  public void valueChanged (ListSelectionEvent evt) {
    super.valueChanged(evt);    // this sets the selection
    fireValueChanged(evt);
  }




  /**
   * Adds a traversal listener.
   * @param listener the listener to add
   */
  public synchronized void addFormTableTraversalListener (FormTableTraversalListener listener)  {
    listenerList.add (FormTableTraversalListener.class, listener);
  }

  /**
   * Removes a traversal listener
   * @param listener the listener to remove
   */
  public synchronized void removeFormTableTraversalListener (FormTableTraversalListener listener) {
     listenerList.remove (FormTableTraversalListener.class, listener);
  }

  /**
   * Notifies all traversal listeners that a cell traversal is triggered.
   *
   * @param evt the traversal event
   * @throws FormTableTraversalVetoException if traversal vetoed
   */
  public void fireTraversalRequested (FormTableTraversalEvent evt) throws FormTableTraversalVetoException {
    Object[] listeners = this.listenerList.getListenerList();
    if (listeners != null)  {
      for (int i = listeners.length-2; i >= 0; i -= 2)  {
        if (listeners[i] == FormTableTraversalListener.class)  {
          ((FormTableTraversalListener)listeners[i+1]).traversalRequested(evt);
        }
      }
    }
  }




  /**
   * Adds an action listener (usually a double click on a selection).
   *
   *  @param listener the listener to add
   */
  public synchronized void addActionListener (ActionListener listener)  {
    listenerList.add (ActionListener.class, listener);
  }

  /**
   * Removes an action Listener
   *
   * @param listener the listener to remove
   */
  public synchronized void removeActionListener (ActionListener listener) {
     listenerList.remove (ActionListener.class, listener);
  }

  /**
   * Notifies all action listeners.
   *
   * @param evt the action event
   */
  public void fireActionPerformed (ActionEvent evt) {
    Object[] listeners = this.listenerList.getListenerList();
    if (listeners != null)  {
      for (int i = listeners.length-2; i >= 0; i -= 2)  {
        if (listeners[i] == ActionListener.class)  {
          ((ActionListener)listeners[i+1]).actionPerformed(evt);
        }
      }
    }
  }


  /**
   * fires action performed on a valid selection
   */
  private void doFireActionPerformed(String actionCommand)  {
    if (clicks >= clickCountToAction && getSelectedRowCount() > 0) {
      fireActionPerformed (new ActionEvent(this, ActionEvent.ACTION_PERFORMED, actionCommand));
    }
  }



  /**
   * Gets the number of mouse clicks that caused the current selection.
   * @return the number of mouse clicks
   */
  public int getClickCount()  {
    return clicks;
  }

  /**
   * Sets the number of mouse clicks to trigger an action event.
   *
   * @param clicks the number of clicks, default is 2.
   */
  public void setClickCountToAction (int clicks) {
    clickCountToAction = clicks;
  }

  /**
   * Gets the number of mouse clicks to trigger an action event.
   *
   * @return the number of clicks, default is 2.
   */
  public int getClickCountToAction() {
    return clickCountToAction;
  }


  /**
   * Enables/disables ENTER to trigger actionPerformed if row is selected.
   * <p>
   * Notice that enabling ENTER action is only meaningful if the
   * table is not editable and celltraversal is turned off.
   * @param enableEnterAction true to enable, default is false
   */
  public void setEnterActionEnabled(boolean enableEnterAction) {
    this.enableEnterAction = enableEnterAction;
  }

  /**
   * Returns whether ENTER triggers actionPerformed.
   *
   * @return true if enabled, default is false
   */
  public boolean isEnterActionEnabled() {
    return enableEnterAction;
  }



  /**
   * Sets the number of clicks to start the full editing mode of a cell.
   *
   * @param clicks the number of clicks, default is 2
   */
  public void setClickCountToStart (int clicks)  {
    clickCountToStart = clicks;
    if (defaultEditorsByColumnClass != null) {
      // set the default editors
      Enumeration<?> editors = defaultEditorsByColumnClass.elements();
      while (editors.hasMoreElements()) {
        Object editor = editors.nextElement();
        if (editor instanceof DefaultCellEditor) {
          ((DefaultCellEditor)editor).setClickCountToStart(clicks);
        }
        else if (editor instanceof FormComponentCellEditor) {
          ((FormComponentCellEditor)editor).setClickCountToStart(clicks);
        }
      }
    }
    if (getColumnModel() != null) {
      // nun die Editors im TableModel setzen
      Enumeration<?> columns = getColumnModel().getColumns();
      while (columns.hasMoreElements()) {
        TableColumn col = (TableColumn)columns.nextElement();
        TableCellEditor editor = col.getCellEditor();
        if (editor instanceof DefaultCellEditor) {
          ((DefaultCellEditor)editor).setClickCountToStart(clicks);
        }
        else if (editor instanceof FormComponentCellEditor) {
          ((FormComponentCellEditor)editor).setClickCountToStart(clicks);
        }
      }
    }
  }

  /**
   * Gets the number of clicks to start the full editing mode of a cell.
   *
   * @return the number of clicks, default is 2
   */
  public int getClickCountToStart()  {
    return clickCountToStart;
  }




  @Override
  public void setChangeable (boolean changeable) {
    if (isHonourChangeable()) {
      this.changeable = changeable;
    }
  }

  @Override
  public boolean isChangeable() {
    return changeable;
  }

  @Override
  public void setHonourChangeable(boolean flag) {
    this.honourChangeable = flag;
  }

  @Override
  public boolean isHonourChangeable() {
    return honourChangeable;
  }

  @Override
  public void updateAllChangeable(boolean allChangeable) {
    if (this.allChangeable != allChangeable) {
      this.allChangeable = allChangeable;
    }
  }




  /**
   * {@inheritDoc}
   * <p>
   * Overridden to implement the changeable attribute.
   */
  @Override
  public boolean isCellEditable(int row, int column) {
    return row >= 0 && column >= 0 &&   // avoid -1 == no current cell
           changeable && allChangeable ? super.isCellEditable(row, column) : false;
  }


  /**
   * Determines from model binding whether cell is mandatory.
   *
   * @param row the view row
   * @param column the view column
   * @return true if cell is mandatory
   */
  public boolean isCellMandatory(int row, int column) {
    boolean mandatory = false;
    if (bindableModel != null) {
      FormTableBinding binding = bindableModel.getBinding(convertColumnIndexToModel(column));
      if (binding != null) {
        mandatory = binding.isMandatory();
      }
    }
    return mandatory;
  }



  /**
   * Sets the format for a given column.<br>
   *
   * @param column the column index
   * @param fmt the format string
   */
  public void setFormat (int column, String fmt) {
    format.set(column, fmt);
    dateFormat.set(column, null);      // force creation of cached format next time
    numberFormat.set(column, null);
  }

  /**
   * Sets the format for all columns.
   *
   * @param fmt the format array (size must match the number of columns)
   */
  public void setFormat (String[] fmt) {
    for (int i=0; i < fmt.length; i++)  {
      setFormat(i, fmt[i]);
    }
  }



  /**
   * Gets the format of a column.
   *
   * @param column the column index
   * @return the format string
   */
  public String getFormat (int column) {
    return format == null ? null : format.get(column);
  }

  /**
   * Gets the format strings of all columns.
   *
   * @return the format strings
   */
  public String[] getFormat() {
    return format == null ? null : (String[])(format.toArray());
  }


  /**
   * Sets the autoselect flag.
   * (only effective in editing-mode of the cell, usually a double-click)
   *
   * @param column the column index
   * @param autoSelect true to enable autoselect, default is false
   */
  public void setAutoSelect (int column, boolean autoSelect) {
    if (autoSelect) {
      addFormatFlag(column, FORMAT_AUTOSELECT);
    }
    else  {
      removeFormatFlag(column, FORMAT_AUTOSELECT);
    }
  }

  /**
   * Sets autoSelect for all columns.
   * @param autoSelect true to enable autoselect, default is false
   */
  public void setAutoSelect (boolean autoSelect) {
    if (autoSelect) {
      addFormatFlag(FORMAT_AUTOSELECT);
    }
    else  {
      removeFormatFlag(FORMAT_AUTOSELECT);
    }
  }

  /**
   * Gets the autoselect flag.
   *
   * @param column the column index
   * @return true if autoselect is enabled
   */
  public boolean isAutoSelect(int column) {
    return formatFlags.get(column).indexOf(FORMAT_AUTOSELECT) >= 0;
  }



  /**
   * Sets the blankzero flag.
   * (only applicable for Number-Fields)
   *
   * @param column the column index
   * @param blankZero true to enable zero suppression, default is false
   */
  public void setBlankZero (int column, boolean blankZero) {
    if (blankZero) {
      addFormatFlag(column, FORMAT_BLANKZERO);
    }
    else  {
      removeFormatFlag(column, FORMAT_BLANKZERO);
    }
  }

  /**
   * Sets the blankzero flag for all columns.
   * (only applicable for Number-Fields)
   *
   * @param blankZero true to enable zero suppression, default is false
   */
  public void setBlankZero (boolean blankZero) {
    if (blankZero) {
      addFormatFlag(FORMAT_BLANKZERO);
    }
    else  {
      removeFormatFlag(FORMAT_BLANKZERO);
    }
  }

  /**
   * Gets the blankzero flag.
   *
   * @param column the column index
   * @return true if zero suppression is enabled
   */
  public boolean isBlankZero(int column) {
    return formatFlags.get(column).indexOf(FORMAT_BLANKZERO) >= 0;
  }



  /**
   * Sets the convert flag.
   *
   * @param column the column index
   * @param convert the character conversion mode, default is {@link AbstractFormField#CONVERT_NONE}
   */
  public void setConvert (int column, char convert) {
    removeFormatFlag(column, AbstractFormField.CONVERT_LC);
    removeFormatFlag(column, AbstractFormField.CONVERT_UC);
    if (convert == AbstractFormField.CONVERT_LC ||
        convert == AbstractFormField.CONVERT_UC)  {
      addFormatFlag(column, convert);
    }
  }

  /**
   * Sets the convert flag for all columns.
   *
   * @param convert the character conversion mode, default is {@link AbstractFormField#CONVERT_NONE}
   */
  public void setConvert (char convert) {
    removeFormatFlag(AbstractFormField.CONVERT_LC);
    removeFormatFlag(AbstractFormField.CONVERT_UC);
    if (convert == AbstractFormField.CONVERT_LC ||
        convert == AbstractFormField.CONVERT_UC)  {
      addFormatFlag(convert);
    }
  }

  /**
   * Gets the convert flag.
   *
   * @param column the column index
   * @return the character conversion mode
   */
  public char getConvert (int column)  {
    String formatFlag = formatFlags.get(column);
    if (formatFlag.indexOf(AbstractFormField.CONVERT_LC) >= 0)  {
      return AbstractFormField.CONVERT_LC;
    }
    if (formatFlag.indexOf(AbstractFormField.CONVERT_UC) >= 0)  {
      return AbstractFormField.CONVERT_UC;
    }
    return AbstractFormField.CONVERT_NONE;
  }



  /**
   * Sets the adjust flag.
   *
   * @param column the column index
   * @param adjust the adjustment mode, default is {@link AbstractFormField#ADJUST_TRIM}
   */
  public void setAdjust (int column, char adjust) {
    removeFormatFlag(column, AbstractFormField.ADJUST_LEFT);
    removeFormatFlag(column, AbstractFormField.ADJUST_RIGHT);
    removeFormatFlag(column, AbstractFormField.ADJUST_TRIM);
    if (adjust == AbstractFormField.ADJUST_RIGHT ||
        adjust == AbstractFormField.ADJUST_LEFT  ||
        adjust == AbstractFormField.ADJUST_TRIM)  {
      addFormatFlag(column, adjust);
    }
  }

  /**
   * Sets the adjust flag for all columns.
   * @param adjust the adjustment mode, default is {@link AbstractFormField#ADJUST_TRIM}
   */
  public void setAdjust (char adjust) {
    removeFormatFlag(AbstractFormField.ADJUST_LEFT);
    removeFormatFlag(AbstractFormField.ADJUST_RIGHT);
    removeFormatFlag(AbstractFormField.ADJUST_TRIM);
    if (adjust == AbstractFormField.ADJUST_RIGHT ||
        adjust == AbstractFormField.ADJUST_LEFT  ||
        adjust == AbstractFormField.ADJUST_TRIM)  {
      addFormatFlag(adjust);
    }
  }

  /**
   * Gets the adjust flag.
   * @param column the column index
   * @return the adjustment mode
   */
  public char getAdjust (int column)  {
    String formatFlag = formatFlags.get(column);
    if (formatFlag.indexOf(AbstractFormField.ADJUST_LEFT) >= 0)  {
      return AbstractFormField.ADJUST_LEFT;
    }
    if (formatFlag.indexOf(AbstractFormField.ADJUST_RIGHT) >= 0)  {
      return AbstractFormField.ADJUST_RIGHT;
    }
    if (formatFlag.indexOf(AbstractFormField.ADJUST_TRIM) >= 0)  {
      return AbstractFormField.ADJUST_TRIM;
    }
    return AbstractFormField.ADJUST_NONE;
  }


  /**
   * Sets the maxcolumns.
   *
   * @param column the column index
   * @param maxColumn the maximum number of columns, null to clear
   * @see AbstractFormField#setMaxColumns(int)
   */
  public void setMaxColumn(int column, Integer maxColumn) {
    maxColumns.set(column, maxColumn);
  }

  /**
   * Gets the maxcolumns.
   *
   * @param column the column index
   * @return the maximum number of columns, null if not set
   * @see AbstractFormField#getMaxColumns
   */
  public Integer getMaxColumn(int column) {
    return maxColumns.get(column);
  }



  /**
   * Sets the scale.
   *
   * @param column the column index
   * @param maxColumn the maximum number of columns, null to clear
   * @see AbstractFractionNumberFormField#setScale(int)
   */
  public void setScale(int column, Integer maxColumn) {
    scales.set(column, maxColumn);
  }

  /**
   * Gets the scale.
   *
   * @param column the column index
   * @return the maximum number of columns, null if not set
   * @see AbstractFractionNumberFormField#getScale()
   */
  public Integer getScale(int column) {
    return scales.get(column);
  }


  /**
   * Sets the horizontal alignment (not to be mixed up with ADJUST_...)
   *
   * @param column the column index
   * @param align the alignment
   * @see JLabel#setHorizontalAlignment(int)
   */
  public void setHorizontalAlignment (int column, int align) {
    hAlignment.set(column, align);
  }

  /**
   * Sets the horizontal alignment for all columns.
   * @param align the alignment
   * @see JLabel#setHorizontalAlignment(int)
   */
  public void setHorizontalAlignment (int align) {
    for (int i=0; i < hAlignment.size(); i++)  {
      hAlignment.set(i, align);
    }
  }

  /**
   * Gets the horizontal alignment.
   *
   * @param column the column index
   * @return the alignment, -1 if not set
   * @see JLabel#setHorizontalAlignment(int)
   */
  public int getHorizontalAlignment (int column)  {
    Integer align = hAlignment.get(column);
    return align == null ? -1 : align;
  }


  /**
   * Sets the vertical alignment.<br>
   * Works only if a tentackle-plaf is used.
   *
   * @param column the column index
   * @param align the alignment
   * @see JLabel#setVerticalAlignment(int)
   */
  public void setVerticalAlignment (int column, int align) {
    vAlignment.set(column, align);
  }

  /**
   * Sets the vertical alignment for all columns.<br>
   * Works only if a tentackle-plaf is used.
   *
   * @param align the alignment
   * @see JLabel#setVerticalAlignment(int)
   */
  public void setVerticalAlignment (int align) {
    for (int i=0; i < vAlignment.size(); i++)  {
      vAlignment.set(i, align);
    }
  }

  /**
   * Gets the vertical alignment.
   *
   * @param column the column index
   * @return the alignment, -1 if not set
   * @see JLabel#setHorizontalAlignment(int)
   */
  public int getVerticalAlignment (int column)  {
    Integer align = vAlignment.get(column);
    return align == null ? -1 : align;
  }



  // add a format-Flag
  private void addFormatFlag (int index, char flag) {
    String formatFlag = formatFlags.get(index);
    if (formatFlag.indexOf(flag) == -1) {
      formatFlags.set(index, formatFlag + flag);
    }
  }

  // same for all columns
  private void addFormatFlag (char flag) {
    for (int i=0; i < formatFlags.size(); i++)  {
      addFormatFlag(i, flag);
    }
  }

  // remove a format-flag
  private void removeFormatFlag (int index, char flag) {
    String formatFlag = formatFlags.get(index);
    String newFlag = "";
    for (int i=0; i < formatFlag.length(); i++) {
      char c = formatFlag.charAt(i);
      if (c != flag) {
        newFlag += c;
      }
    }
    formatFlags.set(index, newFlag);
  }

  // same for all columns
  private void removeFormatFlag (char flag) {
    for (int i=0; i < formatFlags.size(); i++)  {
      removeFormatFlag(i, flag);
    }
  }



  /**
   * Gets the date format.<br>
   * If not set the default format is used from StringHelper.
   *
   * @param column the column index
   * @param asTimestamp true if default format to use is for timestamps
   * @return the date format
   */
  public DateFormat getDateFormat(int column, boolean asTimestamp) {
    DateFormat df = dateFormat.get(column);
    if (df == null) {
      String fmt = getFormat(column);
      if (fmt != null)  {
        df = new SimpleDateFormat(fmt);
      }
      else  {
        df = new SimpleDateFormat(asTimestamp ?
                FormatHelper.getShortTimestampPattern() : FormatHelper.getShortDatePattern());
      }
      dateFormat.set(column, df);
    }
    return df;
  }

  /**
   * Gets the date format.<br>
   * If not set the default format is used from StringHelper.
   *
   * @param column the column index
   * @return the date format
   */
  public DateFormat getDateFormat(int column) {
    return getDateFormat(column, false);
  }


  /**
   * Gets the number format.<br>
   *
   * @param column the column index
   * @return the number format
   */
  public NumberFormat getNumberFormat(int column) {
    NumberFormat nf = numberFormat.get(column);
    if (nf == null) {
      String fmt = getFormat(column);
      if (fmt != null)  {
        nf = new DecimalFormat(fmt);
      }
      else  {
        nf = new DecimalFormat(FormatHelper.getIntegerPattern());     // default format
      }
      numberFormat.set(column, nf);
    }
    return nf;
  }



  /**
   * Sets the minimum row-height.
   *
   * @param minRowHeight the minimum row height in pixels, 0 = no limit
   */
  public void setMinRowHeight(int minRowHeight)  {
    this.minRowHeight = minRowHeight;
  }


  /**
   * Gets the minimum row-height.
   *
   * @return the minimum row height in pixels, default is 0
   */
  public int getMinRowHeight() {
    return minRowHeight;
  }

  /**
   * Sets the maximum row-height.
   *
   * @param maxRowHeight the minimum row height in pixels, 0 = no limit
   */
  public void setMaxRowHeight(int maxRowHeight)  {
    this.maxRowHeight = maxRowHeight;
  }

  /**
   * Gets the maximum row-height.
   *
   * @return the maximum row height in pixels, default is 0 = no limit
   */
  public int getMaxRowHeight() {
    return maxRowHeight;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to check against min/max-rowheights
   */
  @Override
  public void setRowHeight(int row, int rowHeight)  {
    if (minRowHeight != 0 && rowHeight < minRowHeight) {
      rowHeight = minRowHeight;
    }
    if (maxRowHeight != 0 && rowHeight > maxRowHeight) {
      rowHeight = maxRowHeight;
    }
    if (rowHeight != getRowHeight(row)) {
      super.setRowHeight(row, rowHeight);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to check against min/max-rowheights
   */
  @Override
  public void setRowHeight(int rowHeight)  {
    if (minRowHeight != 0 && rowHeight < minRowHeight) {
      rowHeight = minRowHeight;
    }
    if (maxRowHeight != 0 && rowHeight > maxRowHeight) {
      rowHeight = maxRowHeight;
    }
    if (rowHeight != getRowHeight())  {
      super.setRowHeight(rowHeight);
    }
  }







  // special TableCellRenderers


  // Boolean cell renderer
  @SuppressWarnings("serial")
  private class BooleanTableCellRenderer extends JCheckBox
          implements TableCellRenderer {

    public BooleanTableCellRenderer() {
      super();
      this.setHorizontalAlignment(JLabel.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent (JTable table,
                Object value, boolean isSelected, boolean hasFocus,
                int row, int column)  {

      boolean editable = table.isCellEditable(row, column);

      setEnabled(true);

      if (isSelected) {
        if (hasFocus) {
          if (editable) {
            setForeground(table.getForeground());
            setBackground(table.getBackground());
          }
          else  {
            setEnabled(false);
            setForeground(table.getSelectionForeground());
            setBackground(table.getSelectionBackground());
          }
        } else {
          setForeground(table.getSelectionForeground());
          setBackground(table.getSelectionBackground());
        }
      }
      else  {
        setEnabled(true);
        setForeground(table.getForeground());
        setBackground(table.getBackground());
      }

      setSelected((value != null && ((Boolean) value)));
      return this;
    }

  }




  /**
   * The cell action event (to implement cell traversal)
   */
  @SuppressWarnings("serial")
  private class CellActionEvent extends ActionEvent {

    private final int traversal;

    CellActionEvent(int cellTraversal)  {
      super(FormTable.this, 0, null);
      this.traversal = cellTraversal;
    }
  }



  /**
   * next cell action
   */
  @SuppressWarnings("serial")
  private class NextCellAction extends AbstractAction {

    @Override
    public void actionPerformed(ActionEvent e)  {

      try {

        ListSelectionModel rowSelection = getSelectionModel();
        ListSelectionModel colSelection = getColumnModel().getSelectionModel();
        int leadRow = rowSelection.getLeadSelectionIndex();   // current cell
        int leadCol = colSelection.getLeadSelectionIndex();

        // cellTraversal will be overridden if ActionEvent is a CellActionEvent
        int cellTraversal = FormTable.this.cellTraversal;
        if (e instanceof FormTable.CellActionEvent) {
          cellTraversal = ((FormTable.CellActionEvent) e).traversal;
        }
        else if ((cellTraversal & CELLTRAVERSAL_AUTOEDIT) == CELLTRAVERSAL_AUTOEDIT &&
                 isEditing() == false && isCellEditable(leadRow, leadCol)) {
          /**
           * nextCellAction is trigged because user pressed VK_ENTER while not in cell-editing.
           * We check for the special case that CELLTRAVERSAL_AUTOEDIT is enabled and the current cell
           * is editable. This prevents start editing at the next cell which usually is not what
           * the user intended when pressing ENTER.
           */
          fireTraversalRequested(new FormTableTraversalEvent(FormTable.this, false, leadRow, leadCol, leadRow, leadCol, true, true));
          // start editing cell if possible
          editCellAt(leadRow, leadCol);
          return;
        }

        if (cellTraversal != CELLTRAVERSAL_NONE)  {

          int row = leadRow;        // new cell
          int col = leadCol;
          int cols = -1;            // column count
          int rows = -1;            // row count
          boolean found = false;    // true if cell found

          if ((cellTraversal & CELLTRAVERSAL_COLUMN) == CELLTRAVERSAL_COLUMN)  {
            // by column
            do {
              // compute new col
              col++;
              // get column count if not yet done
              if (cols < 0) {
                cols = getColumnCount();
              }
              if (col >= cols)  {
                // we will wrap
                if ((cellTraversal & CELLTRAVERSAL_NOLINEWRAP) == CELLTRAVERSAL_NOLINEWRAP)  {
                  col--;
                  break;    // no wrap at end of line, i.e. no wrap at all
                }
                col = 0;    // wrap to start of line
                if ((cellTraversal & CELLTRAVERSAL_WRAPINLINE) != CELLTRAVERSAL_WRAPINLINE) {
                  row++;
                  // get row count if not yet done
                  if (rows < 0) {
                    rows = getRowCount();
                  }
                  if (row >= rows)  {
                    // at end of last row
                    if ((cellTraversal & CELLTRAVERSAL_NOTABLEWRAP) == CELLTRAVERSAL_NOTABLEWRAP) {
                      col = cols - 1;
                      row--;
                      if ((cellTraversal & CELLTRAVERSAL_TRANSFERFOCUS) == CELLTRAVERSAL_TRANSFERFOCUS) {
                        transferFocus();
                      }
                      break;  // no wrap at end of table
                    }
                    // wrap to start of table
                    row = 0;
                  }
                }
              }
              if ((cellTraversal & CELLTRAVERSAL_SKIPNOEDIT) != CELLTRAVERSAL_SKIPNOEDIT ||
                  isCellEditable(row, col)) {
                if (isCellRectFixed() == false)  {
                  FormTableEntry<?> entry = ((AbstractFormTableModel) getModel()).getEntryAt(row);
                  if (entry.isCellVisible(row, col) == false) {
                    continue;
                  }
                }
                // found cell: select it
                found = true;
                break;  // done
              }
              // advance further
            } while (row != leadRow || col != leadCol); // loop only once
          }

          else if ((cellTraversal & CELLTRAVERSAL_ROW) == CELLTRAVERSAL_ROW)  {
            // by row
            do {
              // compute new row
              row++;
              // get row count if not yet done
              if (rows < 0) {
                rows = getRowCount();
              }
              if (row >= rows)  {
                // we will wrap
                if ((cellTraversal & CELLTRAVERSAL_NOLINEWRAP) == CELLTRAVERSAL_NOLINEWRAP)  {
                  row--;
                  break;    // no wrap at end of column, i.e. no wrap at all
                }
                row = 0;    // wrap to start of column
                if ((cellTraversal & CELLTRAVERSAL_WRAPINLINE) != CELLTRAVERSAL_WRAPINLINE) {
                  col++;
                  // get column count if not yet done
                  if (cols < 0) {
                    cols = getColumnCount();
                  }
                  if (col >= cols)  {
                    // at end of last row
                    if ((cellTraversal & CELLTRAVERSAL_NOTABLEWRAP) == CELLTRAVERSAL_NOTABLEWRAP) {
                      row = rows - 1;
                      col--;
                      if ((cellTraversal & CELLTRAVERSAL_TRANSFERFOCUS) == CELLTRAVERSAL_TRANSFERFOCUS) {
                        transferFocus();
                      }
                      break;  // no wrap at end of table
                    }
                    // wrap to start of table
                    col = 0;
                  }
                }
              }
              if ((cellTraversal & CELLTRAVERSAL_SKIPNOEDIT) != CELLTRAVERSAL_SKIPNOEDIT ||
                  isCellEditable(row, col)) {
                if (isCellRectFixed() == false)  {
                  FormTableEntry<?> entry = ((AbstractFormTableModel)getModel()).getEntryAt(row);
                  if (entry.isCellVisible(row, col) == false) {
                    continue;
                  }
                }
                // found cell: select it
                found = true;
                break;  // done
              }
              // advance further
            } while (row != leadRow || col != leadCol); // loop only once
          }

          boolean edit = (cellTraversal & CELLTRAVERSAL_AUTOEDIT) == CELLTRAVERSAL_AUTOEDIT;
          fireTraversalRequested(new FormTableTraversalEvent(FormTable.this, !found, leadRow, leadCol, row, col, edit, true));
          if (found)  {
            changeSelection(row, col, false, false);
            if (edit) {
              // start editing cell if possible
              editCellAt(row, col);
            }
          }
        }
      }
      catch (FormTableTraversalVetoException ve) {
        LOGGER.fine("{0}", ve);
      }
    }
  }


  /**
   * previous cell action
   */
  @SuppressWarnings("serial")
  private class PreviousCellAction extends AbstractAction {

    @Override
    public void actionPerformed(ActionEvent e)  {

      try {
        // cellTraversal will be overridden if ActionEvent is a CellActionEvent
        int cellTraversal = e instanceof FormTable.CellActionEvent ? ((FormTable.CellActionEvent) e).traversal : FormTable.this.cellTraversal;

        if (cellTraversal != CELLTRAVERSAL_NONE)  {

          ListSelectionModel rowSelection = getSelectionModel();
          ListSelectionModel colSelection = getColumnModel().getSelectionModel();
          int leadRow = rowSelection.getLeadSelectionIndex();   // current cell
          int leadCol = colSelection.getLeadSelectionIndex();
          int row = leadRow;        // new cell
          int col = leadCol;
          int cols = -1;            // column count
          int rows = -1;            // row count
          boolean found = false;    // true if cell found

          if ((cellTraversal & CELLTRAVERSAL_COLUMN) == CELLTRAVERSAL_COLUMN)  {
            // by column
            do {
              // compute new col
              col--;
              if (col < 0)  {
                // we will wrap
                if ((cellTraversal & CELLTRAVERSAL_NOLINEWRAP) == CELLTRAVERSAL_NOLINEWRAP)  {
                  col++;
                  break;    // no wrap at start of line, i.e. no wrap at all
                }
                // get column count if not yet done
                if (cols < 0) {
                  cols = getColumnCount();
                }
                col = cols - 1;    // wrap to end of line
                if ((cellTraversal & CELLTRAVERSAL_WRAPINLINE) != CELLTRAVERSAL_WRAPINLINE) {
                  row--;
                  if (row < 0)  {
                    // at start of first row
                    if ((cellTraversal & CELLTRAVERSAL_NOTABLEWRAP) == CELLTRAVERSAL_NOTABLEWRAP) {
                      row++;
                      col = 0;
                      if ((cellTraversal & CELLTRAVERSAL_TRANSFERFOCUS) == CELLTRAVERSAL_TRANSFERFOCUS) {
                        transferFocusBackward();
                      }
                      break;  // no wrap at start of table
                    }
                    // get row count if not yet done
                    if (rows < 0) {
                      rows = getRowCount();
                    }
                    // wrap to end of table
                    row = rows - 1;
                  }
                }
              }
              if ((cellTraversal & CELLTRAVERSAL_SKIPNOEDIT) != CELLTRAVERSAL_SKIPNOEDIT ||
                  isCellEditable(row, col)) {
                if (isCellRectFixed() == false)  {
                  FormTableEntry<?> entry = ((AbstractFormTableModel)getModel()).getEntryAt(row);
                  if (entry.isCellVisible(row, col) == false) {
                    continue;
                  }
                }
                // found cell: select it
                found = true;
                break;  // done
              }
              // advance further
            } while (row != leadRow || col != leadCol); // loop only once
          }

          else if ((cellTraversal & CELLTRAVERSAL_ROW) == CELLTRAVERSAL_ROW)  {
            // by row
            do {
              // compute new row
              row--;
              if (row < 0)  {
                // we will wrap
                if ((cellTraversal & CELLTRAVERSAL_NOLINEWRAP) == CELLTRAVERSAL_NOLINEWRAP)  {
                  row++;
                  break;    // no wrap at start of column, i.e. no wrap at all
                }
                // get row count if not yet done
                if (rows < 0) {
                  rows = getRowCount();
                }
                row = rows - 1;    // wrap to end of column
                if ((cellTraversal & CELLTRAVERSAL_WRAPINLINE) != CELLTRAVERSAL_WRAPINLINE) {
                  col--;
                  if (col < 0)  {
                    // at start of first row
                    if ((cellTraversal & CELLTRAVERSAL_NOTABLEWRAP) == CELLTRAVERSAL_NOTABLEWRAP) {
                      col++;
                      row = 0;
                      if ((cellTraversal & CELLTRAVERSAL_TRANSFERFOCUS) == CELLTRAVERSAL_TRANSFERFOCUS) {
                        transferFocusBackward();
                      }
                      break;  // no wrap at end of table
                    }
                    // get column count if not yet done
                    if (cols < 0) {
                      cols = getColumnCount();
                    }
                    // wrap to end of table
                    col = cols - 1;
                  }
                }
              }
              if ((cellTraversal & CELLTRAVERSAL_SKIPNOEDIT) != CELLTRAVERSAL_SKIPNOEDIT ||
                  isCellEditable(row, col)) {
                if (isCellRectFixed() == false)  {
                  FormTableEntry<?> entry = ((AbstractFormTableModel)getModel()).getEntryAt(row);
                  if (entry.isCellVisible(row, col) == false) {
                    continue;
                  }
                }
                // found cell: select it
                found = true;
                break;  // done
              }
              // advance further
            } while (row != leadRow || col != leadCol); // loop only once
          }

          boolean edit = (cellTraversal & CELLTRAVERSAL_AUTOEDIT) == CELLTRAVERSAL_AUTOEDIT;
          fireTraversalRequested(new FormTableTraversalEvent(FormTable.this, !found, leadRow, leadCol, row, col, edit, false));
          if (found)  {
            changeSelection(row, col, false, false);
            if (edit) {
              // start editing cell if possible
              editCellAt(row, col);
            }
          }
        }
      }
      catch (FormTableTraversalVetoException ve) {
        LOGGER.fine("{0}", ve);
      }
    }
  }




  /**
   * Scrolls to the row, column so that it's visible.
   * @param row the row index
   * @param column the column index
   * @see JTable#scrollRectToVisible(java.awt.Rectangle)
   */
  public void scrollToCell(int row, int column) {
    Rectangle rect = getCellRect(row, column, true);
    scrollRectToVisible(rect);
    // do that twice because getCellRect sometimes returns values that
    // lie a little out of the displayed tables
    rect = getCellRect(row, column, true);
    scrollRectToVisible(rect);
  }


  /**
   * Returns whether sizes in the preferences are ignored.
   *
   * @return true if the sizes in the preferences are be ignored. Default is false.
   */
  public boolean isSizeInPreferencesIgnored() {
    return sizeInPreferencesIgnored;
  }

  /**
   * Returns whether sizes in the preferences are ignored.<br>
   * In apps with fixed window sizes (i.e. isResizable() == false) createDefaultColumnsFromPreferences()
   * can force an unwanted minimum window size because it will set the tablesize of the table from the preferences.
   * This can be turned off.
   *
   * @param sizeInPreferencesIgnored  true if don't setPreferredSize, default is false.
   */
  public void setSizeInPreferencesIgnored(boolean sizeInPreferencesIgnored) {
    this.sizeInPreferencesIgnored = sizeInPreferencesIgnored;
  }



  /**
   * Returns whether all cell editors are fixed or may change dynamically.
   *
   * @return true if cell editors don't change dynamically (default)
   */
  public boolean isCellEditorFixed() {
    return cellEditorFixed;
  }

  /**
   * Sets whether all cell editors are fixed or may change dynamically.
   *
   * @param cellEditorFixed  true if cell editors don't change dynamically (default)
   */
  public void setCellEditorFixed(boolean cellEditorFixed) {
    this.cellEditorFixed = cellEditorFixed;
  }


  /**
   * Returns whether all cell renderers are fixed or may change dynamically.
   *
   * @return true if cell renderers don't change dynamically (default)
   */
  public boolean isCellRendererFixed() {
    return cellRendererFixed;
  }

  /**
   * Sets whether all cell renderers are fixed or may change dynamically.
   *
   * @param cellRendererFixed  true if cell renderers don't change dynamically (default)
   */
  public void setCellRendererFixed(boolean cellRendererFixed) {
    this.cellRendererFixed = cellRendererFixed;
  }


  /**
   * Returns whether the cell rectangles are fixed or change dynamically.
   *
   * @return true if cells don't change sizes dynamically (default)
   */
  public boolean isCellRectFixed() {
    return cellRectFixed;
  }

  /**
   * Sets whether the cell rectangles are fixed or change dynamically.
   *
   * @param cellRectFixed  true if cells don't change sizes dynamically (default)
   */
  public void setCellRectFixed(boolean cellRectFixed) {
    this.cellRectFixed = cellRectFixed;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to invoke getCellRenderer in FormTableEntry if
   * the renderer is not fixed.
   */
  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    if (isCellRendererFixed() == false)  {
      FormTableEntry<?> entry = ((AbstractFormTableModel)getModel()).getEntryAt(row);
      TableCellRenderer renderer = entry.getCellRenderer(convertColumnIndexToModel(column));
      if (renderer != null) {
        return renderer;
      }
    }
    return super.getCellRenderer(row, column);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to invoke getCellEditor in FormTableEntry if
   * the editor is not fixed.
   */
  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    if (isCellEditorFixed() == false)  {
      FormTableEntry<?> entry = ((AbstractFormTableModel) getModel()).getEntryAt(row);
      TableCellEditor editor = entry.getCellEditor(convertColumnIndexToModel(column));
      if (editor != null) {
        return editor;
      }
    }
    return super.getCellEditor(row, column);
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to manage a map of interfaces and because of {@link #setClickCountToStart}.
   */
  @Override
  public void setDefaultEditor(Class<?> columnClass, TableCellEditor editor) {
    if (columnClass.isInterface()) {
      if (editor != null) {
        defaultEditorsByColumnInterface.put(columnClass, editor);
      }
      else  {
        defaultEditorsByColumnInterface.remove(columnClass);
      }
    }
    else  {
      super.setDefaultEditor(columnClass, editor);
    }

    if (editor instanceof DefaultCellEditor) {
      ((DefaultCellEditor)editor).setClickCountToStart(clickCountToStart);
    }
    else if (editor instanceof FormComponentCellEditor) {
      ((FormComponentCellEditor)editor).setClickCountToStart(clickCountToStart);
    }
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to keep a map of interfaces and to
   * return the FormFieldEditor for a String in case the
   * column-class isn't known yet. In this case a column-class of 'Object'
   * is passed which we will translate to a String right here because
   * it will be done by JTable's implementation anyway (see GenericEditor).
   * The GenericEditor should be avoided because it isn't aware of all
   * the FormTable-extensions. A couple of things won't work such as
   * celltraversal, formatting, etc...
   * If this is not what you want, please override getColumnClass in
   * the FormTableEntry and provide the correct class for columns
   * that may be initially null.
   */
  @Override
  public TableCellEditor getDefaultEditor(Class<?> columnClass)  {
    if (columnClass == Object.class)  {
      columnClass = String.class;
    }
    else if (columnClass != null && !defaultEditorsByColumnInterface.isEmpty()) {
      // try to match interfaces
      for (Entry<Class<?>,TableCellEditor> entry: defaultEditorsByColumnInterface.entrySet()) {
        if (entry.getKey().isAssignableFrom(columnClass)) {
          return entry.getValue();
        }
      }
    }
    return super.getDefaultEditor(columnClass);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to keep a map of interfaces.
   * <p>
   * Notice: the order in which interfaces are added is important
   */
  @Override
  public void setDefaultRenderer(Class<?> columnClass, TableCellRenderer renderer) {
    if (defaultRenderersByColumnInterface != null && columnClass != null && columnClass.isInterface()) {
      if (renderer != null) {
        defaultRenderersByColumnInterface.put(columnClass, renderer);
      }
      else  {
        defaultRenderersByColumnInterface.remove(columnClass);
      }
    }
    else  {
      super.setDefaultRenderer(columnClass, renderer);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to allow interfaces as well.<br>
   * If {@link #defaultRenderersByColumnClass} contains interfaces, they will be checked first.
   */
  @Override
  public TableCellRenderer getDefaultRenderer(Class<?> columnClass) {
    if (columnClass != null && defaultRenderersByColumnInterface != null && !defaultRenderersByColumnInterface.isEmpty()) {
      // try to match interfaces
      for (Entry<Class<?>,TableCellRenderer> entry: defaultRenderersByColumnInterface.entrySet()) {
        if (entry.getKey().isAssignableFrom(columnClass)) {
          return entry.getValue();
        }
      }
    }
    return super.getDefaultRenderer(columnClass);
  }





  /**
   * Returns whether cell drag is enabled.
   *
   * @return true if enabled
   */
  public boolean isCellDragEnabled() {
    return cellDragEnabled;
  }

  /**
   * Enables or disables cell dragging.<br>
   * The default TransferHandler in MetalLookAndFeel drags cells only if column-selection
   * is allowed. In such a case (which is the 99%-case of standard apps) the whole row
   * will be dragged. The FormTable, however, with the tentackle-plafs dragging will
   * default to the anchor-cell of the selection. If you need row-dragging you
   * must setCellDragEnabled(false).
   *
   * @param cellDragEnabled true to enable cell drag (default), false for row drag
   */
  public void setCellDragEnabled(boolean cellDragEnabled) {
    this.cellDragEnabled = cellDragEnabled;
  }




  /**
   * {@inheritDoc}
   * <p>
   * overridden to fix the bug in JTable that editable ComboBoxes don't
   * return the focus to the table. Furthermore transferFocusBackward is
   * implemented here to allow walk back in editing cells with Shift-ENTER.
   */
  @SuppressWarnings("deprecation")
  @Override
  public Component prepareEditor(TableCellEditor editor, int row, int column) {
    if (usingAbstractFormTableModel && editor instanceof FormComponentCellEditor) {
      ((FormComponentCellEditor)editor).prepare(((AbstractFormTableModel)getModel()).getEntryAt(row), column);
    }
    Component comp = super.prepareEditor(editor, row, column);
    if (comp instanceof FormFieldComboBox)  {
      FormFieldComboBox<?> box = (FormFieldComboBox<?>) comp;
      if (box.isEditable()) {   // override always even if getNextFocusableComponent() returns != null!
        AbstractFormField boxEditor = box.getEditorField();
        boxEditor.setNextFocusableComponent(this);   // still done that way in 1.6!
        setNextFocusableComponent(boxEditor);        // this will make tansferFocusBackward() work!
        return comp;
      }
    }
    setNextFocusableComponent(comp);    // this will make tansferFocusBackward() work!
    return comp;
  }


  /**
   * {@inheritDoc }
   * <p>
 Overridden to optimize excessive cell setViewValue
   */
  @Override
  protected void paintComponent(Graphics g) {
    paintCount++;
    super.paintComponent(g);
  }


  /**
   * Gets the current (re-)paint count.
   *
   * @return the count
   */
  public long getPaintCount() {
    return paintCount;
  }



  /**
   * event for a cell-traversal triggered at end of cell-editing
   */
  @SuppressWarnings("serial")
  private class CellTraversalEvent extends AWTEvent {

    private final boolean next;       // true = next, false = previous
    private final long serial;        // serial counter

    public CellTraversalEvent(boolean next)  {
      super(FormTable.this, AWTEvent.RESERVED_ID_MAX + 21);
      this.next = next;
      this.serial = ++cellEventSerial;
    }
  }


  /**
   * event for editing a given cell
   */
  @SuppressWarnings("serial")
  private class CellEditingEvent extends AWTEvent {

    private static final int EXACT    = 0;
    private static final int NEXT     = 1;
    private static final int PREVIOUS = -1;

    private final long serial;
    private final int row;
    private final int col;
    private final int mode;

    public CellEditingEvent(int mode, int row, int col) {
      super(FormTable.this, AWTEvent.RESERVED_ID_MAX + 22);
      this.mode = mode;
      this.row  = row;
      this.col  = col;
      this.serial = ++cellEventSerial;
    }
  }



}
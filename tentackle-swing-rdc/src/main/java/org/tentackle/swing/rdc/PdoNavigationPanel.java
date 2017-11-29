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


package org.tentackle.swing.rdc;


import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.PropertyChangeEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.tentackle.misc.ObjectFilter;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormButton;
import org.tentackle.swing.FormPanel;
import org.tentackle.swing.FormTable;
import org.tentackle.swing.FormTableEntry;
import org.tentackle.swing.FormTableModel;
import org.tentackle.swing.FormTableSorter;
import org.tentackle.swing.FormTableUtilityPopup;
import org.tentackle.swing.FormTree;
import org.tentackle.swing.FormUtilities;
import org.tentackle.swing.SumFormTableEntry;



/**
 * Panel for object navigation.
 * <p>
 * The {@code PdoNavigationPanel} provides object navigation in a tree
 * and a table in parallel. The user switches between the tree- and the
 * table view. The tree view is primarily for navigation through object
 * relations and operations on objects by means of a context-driven popup menu.
 * The table view is for sorting, building sums, cutting the list
 * and a table popup to export to spreadsheet, xml or print the table.
 * Furthermore, other navigation panels may be created on-the-fly,
 * for example to sum up the details (childs) of a given type for
 * some object within a tree.
 *
 * @param <T> the pdo type
 * @author harald
 */
public class PdoNavigationPanel<T extends PersistentDomainObject<T>> extends FormPanel implements KeyListener {

  // Buttonmodes
  /** show cancel button **/
  public static final int SHOW_CANCEL   = 0x01;
  /** show select button **/
  public static final int SHOW_SELECT   = 0x02;
  /** show close button **/
  public static final int SHOW_CLOSE    = 0x04;
  /** show default buttons: cancel and select **/
  public static final int SHOW_BUTTONS  = SHOW_CANCEL | SHOW_SELECT;
  /** don't show any button **/
  public static final int SHOW_NOBUTTON = 0x00;

  // action commands
  /** "select" action **/
  public static final String ACTION_SELECT = "select";
  /** "cancel" action **/
  public static final String ACTION_CANCEL = "cancel";
  /** "close" action **/
  public static final String ACTION_CLOSE  = "close";

  private static final long serialVersionUID = -5490593382006669760L;

  /**
   * The view mode.
   */
  public enum ViewMode {

    /** tree view */
    TREE,

    /** table view */
    TABLE
  }


  private ViewMode viewMode;                              // the view mode
  private List<T> list;                                   // object list
  private T templateProvider;                             // an optional template object to specify internal model configuration
  private SelectionFilter selectionFilter;                // the selection filter, null if nothing selectable
  private int buttonMode;                                 // one of SHOW_....
  private PersistentDomainObject<?> selectedObject;       // the selected object, null=none
  private List<PersistentDomainObject<?>> selectedObjects;  // List of selected objects if multi-selections
  private boolean disposeKeyEnabled;                      // true if ESCAPE in tree or table disposes the dialog (default is false).

  private int treeSelectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
  private int listSelectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

  // the tree
  private PdoTree naviTree;                    // navigation tree
  private boolean naviTreeUpdated;             // true if naviTree is up to date
  private ObjectFilter<T> treeFilter;          // filter the root objects visible in the tree

  // the table
  private Class<T> tableClass;                 // != null if table mode allowed, i.e. this is a PersistentDomainObject.class
  private FormTable<T> naviTable;              // the table object
  private boolean naviTableUpdated;            // true if naviTable is up to date
  private FormTableSorter<T> naviSorter;       // the table sorter
  private FormTableModel<T> naviModel;         // the table model
  private boolean packed;                      // true if pack() already done
  private boolean autoPack = true;             // true = automatically pack container window (default)
  private String tableName;                    // non default tablename
  private String tableIntro;                   // intro text (usually selection criteria) if table is printed, null = none
  private String tableTitle;                   // title text, if table is printed, null = default from context



  /**
   * Creates a navigation panel.
   *
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of SHOW_...
   * @param showTable true if initially show the table view, false = tree view
   * @param tableName the preferences tablename, null if preferences by getFormTableName() from 1st object in list
   */
  public PdoNavigationPanel(List<T> list, SelectionFilter selectionFilter, int buttonMode, boolean showTable, String tableName)  {
    setup(list, selectionFilter, buttonMode, showTable, tableName);
  }

  /**
   * Creates a navigation panel.<br>
   * The preferences table name is determined by the first object.
   *
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of SHOW_...
   * @param showTable true if initially show the table view, false = tree view
   */
  public PdoNavigationPanel(List<T> list, SelectionFilter selectionFilter, int buttonMode, boolean showTable)  {
    this(list, selectionFilter, buttonMode, showTable, null);
  }

  /**
   * Creates a navigation panel.<br>
   * The preferences table name is determined by the first object.
   * The default buttons are shown (select and cancel).
   * The initial view mode is tree-view.
   *
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   */
  public PdoNavigationPanel(List<T> list, SelectionFilter selectionFilter) {
    this(list, selectionFilter, SHOW_BUTTONS, false);
  }

  /**
   * Creates a navigation panel.<br>
   * The preferences table name is determined by the first object.
   * The default buttons are shown (select and cancel).
   * The initial view mode is tree-view.
   * Nothing to select.
   *
   * @param list the list of objects
   */
  public PdoNavigationPanel(List<T> list) {
    this(list, null, SHOW_BUTTONS, false);
  }

  /**
   * Creates a navigation panel for a single object.<br>
   *
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of SHOW_...
   * @param showTable true if initially show the table view, false = tree view
   * @param tableName the preferences tablename, null if preferences object
   */
  public PdoNavigationPanel(T obj, SelectionFilter selectionFilter, int buttonMode, boolean showTable, String tableName)  {
    List<T> objList = new ArrayList<>();
    objList.add(obj);
    setup(objList, selectionFilter, buttonMode, showTable, tableName);
  }

  /**
   * Creates a navigation panel for a single object.<br>
   * The preferences table name is determined by the object.
   *
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of SHOW_...
   * @param showTable true if initially show the table view, false = tree view
   */
  public PdoNavigationPanel(T obj, SelectionFilter selectionFilter, int buttonMode, boolean showTable)  {
    this(obj, selectionFilter, buttonMode, showTable, null);
  }

  /**
   * Creates a navigation panel for a single object.<br>
   * The preferences table name is determined by the object.
   * The default buttons are shown (select and cancel).
   * The initial view mode is tree-view.
   *
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   */
  public PdoNavigationPanel(T obj, SelectionFilter selectionFilter) {
    this(obj, selectionFilter, SHOW_BUTTONS, false);
  }

  /**
   * Creates a navigation panel for a single object.<br>
   * The preferences table name is determined by the object.
   * The default buttons are shown (select and cancel).
   * The initial view mode is tree-view.
   * Nothing to select.
   *
   * @param obj the database object
   */
  public PdoNavigationPanel(T obj) {
    this(obj, null, SHOW_BUTTONS, false);
  }


  /**
   * Creates an empty navigation panel.<br>
   * This constructor makes it a bean.
   */
  public PdoNavigationPanel() {
    this(new ArrayList<>(), null, SHOW_BUTTONS, false, null);
  }




  /**
   * Sets the table's intro-text that appears on the first page of the printout,
   * usually the selection criteria from a search parameter.
   * Must be invoked before setObjects().
   *
   * @param tableIntro the table intro text, null if none
   */
  public void setTableIntro(String tableIntro) {
    this.tableIntro = tableIntro;
  }

  /**
   * Gets the table intro text.
   *
   * @return the intro text, null if none
   */
  public String getTableIntro() {
    return tableIntro;
  }


  /**
   * Sets the table's title-text that appears on the printout.
   * Must be invoked before setObjects().
   *
   * @param tableTitle the table title, null if none
   */
  public void setTableTitle(String tableTitle) {
    this.tableTitle = tableTitle;
  }

  /**
   * Gets the table's title.
   *
   * @return the title, null if none
   */
  public String getTableTitle() {
    return tableTitle;
  }


  /**
   * Gets the class of objects which is shown in the table.
   *
   * @return the database object class, null if not determined yet
   */
  public Class<T> getTableClass()  {
    return tableClass;
  }


  /**
   * Gets the selection filter.
   *
   * @return the selection filter, null if all of PDO class are allowed
   */
  public SelectionFilter getSelectionFilter() {
    return selectionFilter;
  }


  /**
   * Sets the tree filter.<br>
   * Optional filter to reduce the root objects visible in the tree view.
   *
   * @param treeFilter filter for tree, null for none
   */
  public void setTreeFilter(ObjectFilter<T> treeFilter) {
    this.treeFilter = treeFilter;
    if (viewMode == ViewMode.TREE) {
      listUpdated();
    }
  }


  /**
   * Gets the tree filter.
   *
   * @return the tree filter, null if none
   */
  public ObjectFilter<T> getTreeFilter() {
    return treeFilter;
  }


  /**
   * Allow/disallow popup-menu in the tree view.
   *
   * @param enabled true if allow popup menu (default)
   */
  public void setPopupEnabled(boolean enabled)  {
    naviTree.setPopupEnabled(enabled);
  }

  /**
   * Returns whether popup menu is allowed in the tree view.
   *
   * @return true if allowed (default)
   */
  public boolean isPopupEnabled() {
    return naviTree.isPopupEnabled();
  }


  /**
   * Sets the visibility of the tree- and table view buttons.
   * <p>
   * The default is visible.
   *
   * @param visible true if visible
   */
  public void setViewModeButtonsVisible(boolean visible) {
    treeViewButton.setVisible(visible);
    tableViewButton.setVisible(visible);
  }


  /**
   * Gets the visibility of the tree- and table view buttons.
   *
   * @return true if buttons are visible
   */
  public boolean isViewModeButtonsVisible() {
    return treeViewButton.isVisible();
  }

  /**
   * Requests the focus for the first object,
   * whether list- or tree-view.
   */
  public void requestFocusForFirstItem()  {
    if (list != null && !list.isEmpty()) {
      if (viewMode == ViewMode.TABLE) {
        naviTable.setSelectedRow(0);
        naviTable.scrollToCell(0, 0);
        EventQueue.invokeLater(() -> {
          if (naviTable.isVisible()) {
            naviTable.requestFocus();
          }
        });
      }
      else if (viewMode == ViewMode.TREE) {
        naviTree.requestFocusForFirstItem();
      }
    }
  }


  /**
   * Scrolls to the first object, whether
   * list- or tree-view.
   */
  public void scrollToFirstItem()  {
    if (list != null && !list.isEmpty()) {
      if (naviTable != null) {
        naviTable.scrollToCell(0, 0);
      }
       if (naviTree != null) {
        naviTree.scrollRowToVisible(0);
      }
    }
  }


  /**
   * Gets the current list of objects shown.
   * @return the list of objects
   */
  public List<T> getObjects()  {
    return list;
  }


  /**
   * Replaces the current list of objects and updates the view.
   *
   * @param list the list of objects
   * @param viewMode is null to keep current view, else the new {@link ViewMode}
   *        to rebuild the view as a tree or table
   */
  @SuppressWarnings("unchecked")
  public void setObjects(List<T> list, ViewMode viewMode) {

    this.list = list;
    cutSelectedButton.setEnabled(false);

    if (list == null || list.isEmpty()) {
      treeViewButton.setEnabled(false);
      tableViewButton.setEnabled(false);
    }
    else  {
      if (tableClass == null) {
        // not yet determined
        determineTableClass(viewMode == ViewMode.TABLE);
      }
      treeViewButton.setEnabled(true);
      tableViewButton.setEnabled(true);
    }

    clearSelection();

    // if nothing is already displayed and viewMode is CURRENT: default to TREE
    if (this.viewMode == null && viewMode == null) {
      viewMode = ViewMode.TREE;
    }

    if (viewMode != null) {
      showView(viewMode, true);
      if (naviSorter != null && sumButton.isVisible() && sumButton.isSelected()) {
        naviSorter.setSumEntry(new SumFormTableEntry<>(naviModel));
      }
    }
    else  {
      listUpdated();
      scrollToFirstItem();
    }
  }


  /**
   * Replaces the current list of objects and updates the view.
   *
   * @param list the list of objects
   * @param rebuildView is true to rebuild the view, false if keep it
   */
  public void setObjects(List<T> list, boolean rebuildView) {
    setObjects(list, rebuildView ? viewMode : null);
  }

  /**
   * Replaces the current list of objects and updates the view.
   *
   * @param list the list of objects
   */
  public void setObjects(List<T> list) {
    setObjects(list, null);
  }


  /**
   * Returns the optional tempalte provider.
   * @return    The optional tempalte provider
   */
  public T getTemplateProvider() {
    return this.templateProvider;
  }

  /**
   * Sets the optional template provider.
   * @param templateProvider    The optional tempalte provider to set
   */
  public void setTemplateProvider(T templateProvider) {
    this.templateProvider = templateProvider;
  }


  /**
   * Gets the model row if navisorter and/or rowsorter is installed.
   *
   * @param viewRow the table's row
   * @return the model row
   */
  protected int getModelIndex(int viewRow) {
    int modelRow = viewRow;
    RowSorter<?> rowSorter = naviTable.getRowSorter();
    if (rowSorter != null && viewRow >= 0 && viewRow < rowSorter.getViewRowCount()) {
      modelRow = rowSorter.convertRowIndexToModel(modelRow);
    }
    if (naviSorter != null) {
      modelRow = naviSorter.getModelIndex(modelRow);
    }
    return modelRow;
  }

  /**
   * Gets the view row if navisorter and/or rowsorter is installed.
   *
   * @param modelRow the model's row
   * @return the table (view) row
   */
  protected int getViewIndex(int modelRow) {
    int viewRow = naviSorter != null ? naviSorter.getMappedIndex(modelRow) : modelRow;
    RowSorter<?> rowSorter = naviTable.getRowSorter();
    if (rowSorter != null && viewRow >= 0 && viewRow < rowSorter.getModelRowCount()) {
      viewRow = rowSorter.convertRowIndexToView(viewRow);
    }
    return viewRow;
  }


  /**
   * Gets the current viewmode
   *
   * @return the current view, null if nothing displayed so far
   */
  public ViewMode getViewMode() {
    return viewMode;
  }



  /**
   * Gets the pack mode.
   *
   * @return true if parent window is packed whenever the view is updated, default is false
   */
  public boolean isAutoPack() {
    return autoPack;
  }

  /**
   * Sets the pack mode.
   *
   * @param autoPack true to pack the parent window if view is updated, default is false
   */
  public void setAutoPack(boolean autoPack) {
    this.autoPack = autoPack;
  }


  /**
   * Sets the button mode.
   *
   * @param buttonMode sets the buttons (see {@code SHOW_...} above)
   */
  public void setButtonMode(int buttonMode) {
    this.buttonMode = buttonMode;
    selectButton.setVisible((buttonMode & SHOW_SELECT) != 0);
    cancelButton.setVisible((buttonMode & SHOW_CANCEL) != 0);
    closeButton.setVisible((buttonMode & SHOW_CLOSE) != 0);
  }

  /**
   * Gets the button mode.
   *
   * @return the buttonMode (see {@code SHOW_...} above)
   */
  public int getButtonMode()  {
    return buttonMode;
  }



  /**
   * Sets the visibility of the cancel button.
   *
   * @param visible is true if visible, false if not
   */
  public void setCancelButtonVisible(boolean visible) {
    buttonMode = visible ? (buttonMode | SHOW_CANCEL) : (buttonMode & ~SHOW_CANCEL);
    cancelButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the cancel button.
   *
   * @return true if visible, false if not
   */
  public boolean isCancelButtonVisible() {
    return (buttonMode & SHOW_CANCEL) != 0;
  }


  /**
   * Sets the visibility of the select button.
   *
   * @param visible is true if visible, false if not
   */
  public void setSelectButtonVisible(boolean visible) {
    buttonMode = visible ? (buttonMode | SHOW_SELECT) : (buttonMode & ~SHOW_SELECT);
    selectButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the select button.
   *
   * @return true if visible, false if not
   */
  public boolean isSelectButtonVisible() {
    return (buttonMode & SHOW_SELECT) != 0;
  }


  /**
   * Sets the visibility of the select button.
   *
   * @param visible is true if visible, false if not
   */
  public void setCloseButtonVisible(boolean visible) {
    buttonMode = visible ? (buttonMode | SHOW_CLOSE) : (buttonMode & ~SHOW_CLOSE);
    closeButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the select button.
   *
   * @return true if visible, false if not
   */
  public boolean isCloseButtonVisible() {
    return (buttonMode & SHOW_CLOSE) != 0;
  }



  /**
   * Creates the navigation table.
   *
   * @return the table
   */
  @SuppressWarnings("unchecked")
  public FormTable<T> createTable() {
    // create empty table
    FormTable<?> table = Rdc.createFormTable();
    table.setDragEnabled(true);
    table.setAutoResizeMode(FormTable.AUTO_RESIZE_OFF);
    table.setCreateDefaultColumnsFromPreferences(true);
    table.setEnterActionEnabled(true);
    table.setDefaultRenderer(PersistentDomainObject.class, new PdoTableCellRenderer());

    // add Listeners
    table.addActionListener(this::naviTable_actionPerformed);

    table.addListSelectionListener((ListSelectionEvent e) -> {
      if (!e.getValueIsAdjusting()) {
        naviTable_valueChanged(e);
      }
    });

    table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
      @Override
      public void columnAdded(TableColumnModelEvent e) {
        updateSumButton();
      }
      @Override
      public void columnMarginChanged(ChangeEvent e) {
      }
      @Override
      public void columnMoved(TableColumnModelEvent e) {
      }
      @Override
      public void columnRemoved(TableColumnModelEvent e) {
        updateSumButton();
      }
      @Override
      public void columnSelectionChanged(ListSelectionEvent e) {
      }
    });

    // same control-keys as in tree
    table.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e)  {
        int keyCode = e.getKeyCode();
        T obj = getFirstSelectedObjectInTable();
        if (obj != null && e.isControlDown()) {
          switch (keyCode) {
            case KeyEvent.VK_C:   // copy to clipboard
              try {
                Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                PdoTransferable<T> trans = new PdoTransferable<>(obj);
                clip.setContents(trans, trans);
              }
              catch (Exception ex) {
                // nothing to do
              }
              break;
            case KeyEvent.VK_E:   // edit
              editObjectInTable(true);
              break;
            case KeyEvent.VK_N:   // show
              editObjectInTable(false);
              break;
          }
          e.consume();
        }
      }
    });

    if (disposeKeyEnabled)  {
      table.addKeyListener(this);
    }

    return (FormTable<T>) table;
  }



  /**
   * Creates the navigation tree.
   *
   * @return the tree
   */
  public PdoTree createTree() {
    // create empty tree
    PdoTree tree = Rdc.createPdoTree();
    tree.addActionListener(this::naviTree_actionPerformed);

    // add listeners
    tree.addTreeSelectionListener(this::naviTree_valueChanged);

    tree.getSelectionModel().setSelectionMode(treeSelectionMode);

    return tree;
  }



  /**
   * setup panel
   *
   * @param list is the initial list of objects to be shown
   * @param selectionFilter are the classes allowed to be selected
   * @param buttonMode tells what buttons are visible/invisible (one of SHOW_...)
   * @param showTable is true if initially show the table view, false = tree view
   * @param tableName is != null if preferences not by getFormTableName() from 1st object in list
   */
  @SuppressWarnings("unchecked")
  public void setup (List<T> list, SelectionFilter selectionFilter, int buttonMode, boolean showTable, String tableName)  {

    this.list = list;
    this.selectionFilter = selectionFilter;
    this.tableName = tableName;

    setBindable(false);

    // create empty tree
    naviTree = createTree();

    // create empty table
    naviTable = createTable();


    tableClass = null;
    viewMode   = null;

    initComponents();

    if (selectionFilter == null)  {   // only search, no select
      buttonMode = SHOW_NOBUTTON;
    }
    setButtonMode(buttonMode);

    selectedObject = null;
    selectedObjects = null;
    selectButton.setEnabled(false);       // noch nichts ausgewaehlt
    cutSelectedButton.setEnabled(false);
    sumButton.setVisible(false);

    // check whether table view is allowed
    determineTableClass(showTable);

    // setup view but don't pack initially (this must be done in Container)
    packed = true;
    showView(showTable ? ViewMode.TABLE : ViewMode.TREE, false);
    packed = false;
  }




  /**
   * Gives access to cancel button (to modify the text, i.e.)
   * @return the cancel button
   */
  public FormButton getCancelButton()  {
    return cancelButton;
  }

  /**
   * Gives access to select button (to modify the text, i.e.)
   * @return the select button
   */
  public FormButton getSelectButton()  {
    return selectButton;
  }




  /**
   * Sets the list selection mode.
   * The tree selection mode is updated accordingly.
   *
   * @param mode the list selection mode from {@link ListSelectionModel}
   * @see ListSelectionModel
   * @see TreeSelectionModel
   */
  public void setListSelectionMode(int mode)  {

    listSelectionMode = mode;

    switch(mode)  {
      case ListSelectionModel.SINGLE_SELECTION:
        treeSelectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION;
        break;
      case ListSelectionModel.MULTIPLE_INTERVAL_SELECTION:
        treeSelectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
        break;
      case ListSelectionModel.SINGLE_INTERVAL_SELECTION:
        treeSelectionMode = TreeSelectionModel.CONTIGUOUS_TREE_SELECTION;
        break;
    }

    applySelectionMode();
  }



  /**
   * Sets the tree selection mode.
   * The list selection mode is updated accordingly.
   *
   * @param mode the list selection mode from {@link TreeSelectionModel}
   * @see ListSelectionModel
   * @see TreeSelectionModel
   */
  public void setTreeSelectionMode(int mode)  {

    treeSelectionMode = mode;

    switch(mode)  {
      case TreeSelectionModel.SINGLE_TREE_SELECTION:
        listSelectionMode = ListSelectionModel.SINGLE_SELECTION;
        break;
      case TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION:
        listSelectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;
        break;
      case TreeSelectionModel.CONTIGUOUS_TREE_SELECTION:
        listSelectionMode = ListSelectionModel.SINGLE_INTERVAL_SELECTION;
        break;
    }

    applySelectionMode();
  }



  /**
   * Gives access to the navigation tree.
   *
   * @return the tree, null if not yet created
   */
  public PdoTree getNaviTree()  {
    return naviTree;
  }


  /**
   * Gives access to the table list.
   *
   * @return the table, null if not yet created
   */
  public FormTable<T> getNaviTable() {
    return naviTable;
  }



  /**
   * Clears the selections.
   */
  public void clearSelection() {
    if (naviTable != null)  {
      naviTable.clearSelection();
    }
    if (naviTree != null) {
      naviTree.clearSelection();
    }
  }


  /**
   * Notifies the navigation panel that the list of objects or objects
   * within the list have changed and the view needs to be updated.
   */
  public void listUpdated() {
    if (viewMode == ViewMode.TABLE)  {
      naviModel.listChanged(list);
      naviTableUpdated = true;
      naviTreeUpdated  = false;
      if (naviSorter != null) {
        @SuppressWarnings("unchecked")
        SumFormTableEntry<T> sumEntry = (SumFormTableEntry<T>) naviSorter.getSumEntry();
        if (sumEntry != null) {
          sumEntry.sumUp(naviModel);          // sumup again
          naviSorter.setSumEntry(sumEntry);   // causes update
        }
      }
    }
    else if (viewMode == ViewMode.TREE) {
      buildTree();
      naviTableUpdated = false;
      naviTreeUpdated  = true;
    }
    updateRowCountLabel();
    updateSumButton();
  }



  /**
   * Adds a selection for a given range of objects.
   *
   * @param firstIndex the index of the first object in the current list of objects
   * @param lastIndex the index of the last object in the current list of objects
   */
  public void addSelectionInterval(int firstIndex, int lastIndex) {
    if (naviTable != null)  {
      for (int i=firstIndex; i <= lastIndex; i++) {
        int row = getViewIndex(i);
        naviTable.getSelectionModel().addSelectionInterval(row, row);
      }
    }
    for (int i=firstIndex; i <= lastIndex; i++) {
      naviTree.addSelectionRow(i);
    }
  }


  /**
   * Removes a selection for a given range of objects.
   *
   * @param firstIndex the index of the first object in the current list of objects
   * @param lastIndex the index of the last object in the current list of objects
   */
  public void removeSelectionInterval(int firstIndex, int lastIndex) {
    if (naviTable != null)  {
      for (int i=firstIndex; i <= lastIndex; i++) {
        int row = getViewIndex(i);
        naviTable.getSelectionModel().removeSelectionInterval(row, row);
      }
    }
    for (int i=firstIndex; i <= lastIndex; i++) {
      naviTree.removeSelectionRow(i);
    }
  }



  /**
   * Scrolls to object at given index.
   *
   * @param index the index of the object in the current list of objects
   */
  public void scrollToIndex(int index)  {
    if (viewMode == ViewMode.TABLE) {
      if (naviTable != null) {
        int row = getViewIndex(index);
        naviTable.scrollToCell(row, 0);
        naviTable.requestFocusInWindow();
      }
    }
    else if (viewMode == ViewMode.TREE) {
      naviTree.scrollRowToVisible(index);
      naviTree.requestFocusInWindow();
    }
  }



  /**
   * Sets whether the cancel/dispose-key is enabled.<br>
   * If this key is pressed when tree or table has the focus
   * the enclosing window is disposed.
   *
   * @param enabled true to enable, false to disable
   */
  public void setDisposeKeyEnabled(boolean enabled)  {
    if (enabled != disposeKeyEnabled)  {
      if (!enabled)  {
        // unregister
        naviTree.removeKeyListener(this);
        naviTable.removeKeyListener(this);
      }
      else {
        // register new listener
        naviTree.addKeyListener(this);
        naviTable.addKeyListener(this);
      }
      disposeKeyEnabled = enabled;
    }
  }

  /**
   * Returns whether the dispose key is enabled.
   *
   * @return true if enabled
   */
  public boolean isDisposeKeyEnabled()  {
    return disposeKeyEnabled;
  }





  /**
   * Adds a selection changed listener.
   *
   * @param listener the listener to add
   */
  public synchronized void addListSelectionListener (ListSelectionListener listener)  {
    listenerList.add (ListSelectionListener.class, listener);
  }

  /**
   * Removes a selection changed listener.
   *
   * @param listener the listener to remove
   */
  public synchronized void removeListSelectionListener (ListSelectionListener listener) {
     listenerList.remove (ListSelectionListener.class, listener);
  }



  /**
   * Gets the selected object.
   * If there are more than one object in the current list of selections,
   * the object "clicked last" is returned.
   *
   * @return the object, null if nothing selected
   */
  public PersistentDomainObject<?> getSelectedObject () {
    return selectedObject;
  }

  /**
   * Gets all selected objects.
   *
   * @return the list of selected objects, null if nothing selected
   */
  public List<PersistentDomainObject<?>> getSelectedObjects () {
    return selectedObjects;
  }



  /**
   * determine the table class
   */
  @SuppressWarnings("unchecked")
  private void determineTableClass(boolean showTable) {

    // check whether table view is allowed
    if (list != null) {
      // setup tableclass, if all object are of the same class
      try {
        for (T pdo: list) {
          if (tableClass == null) {
            tableClass = pdo.getEffectiveClass();
          }
          else  {
            if (!tableClass.equals(pdo.getClass()))  {
              tableClass = null;
              break;
            }
          }
        }
      }
      catch (Exception e) {
        tableClass = null;
      }
    }

    if (tableClass != null) {
      viewButtonPanel.setVisible(true);
      if (showTable) {
        tableViewButton.setSelected(true);
        treeViewButton.setSelected(false);
      }
      else {
        treeViewButton.setSelected(true);
        tableViewButton.setSelected(false);
      }
    }
    else  {
      viewButtonPanel.setVisible(false);
    }
  }



  /**
   * Updates the row count label
   */
  private void updateRowCountLabel()  {
    if (list != null) {
      rowCountLabel.setText("" + list.size());
    }
    else  {
      rowCountLabel.setText(null);
    }
  }


  /**
   * applis the selection mode for both table and tree
   */
  private void applySelectionMode() {
    naviTable.setSelectionMode(listSelectionMode);
    naviTree.getSelectionModel().setSelectionMode(treeSelectionMode);
    cutSelectedButton.setVisible(listSelectionMode != ListSelectionModel.SINGLE_SELECTION);
  }


  /**
   * notify all ActionListeners (usually only one!) that the field is
   * going to be displayed and thus needs the data to display
   */
  private void fireActionPerformed (String action) {
    fireActionPerformed(new ActionEvent (this, ActionEvent.ACTION_PERFORMED, action));
  }


  /**
   * notifies all Listeners that the selection has changed
   */
  private void fireValueChanged()  {
    if (viewMode == ViewMode.TREE || viewMode == ViewMode.TABLE) {
      Object[] listeners = listenerList.getListenerList();
      ListSelectionEvent e = null;

      for (int i = listeners.length - 2; i >= 0; i -= 2) {
        if (listeners[i] == ListSelectionListener.class) {
          if (e == null) {
            e = new ListSelectionEvent(viewMode == ViewMode.TREE ? naviTree : naviTable, 0, selectedObjects == null ? -1 : selectedObjects.size()-1, false);
          }
          ((ListSelectionListener)listeners[i+1]).valueChanged(e);
        }
      }
    }
  }


  /**
   * adds object to selection.
   */
  private boolean addToSelectedObjects(T obj) {
    if (isObjectAllowed(obj)) {
      // ok zum Auswaehlen, jeweils das letzte ist das aktuelle
      selectedObject = obj;
      if (selectedObjects == null) {
        selectedObjects = new ArrayList<>();
      }
      selectedObjects.add(selectedObject);
      return true;
    }
    return false;
  }



  /**
   * checks whether the selected object is allowed for selections
   */
  private boolean isObjectAllowed(Object obj)  {
    return selectionFilter != null && selectionFilter.isSelectable(obj);
  }



  private void naviTree_actionPerformed(ActionEvent e)  {
    // double click
    if (selectedObject != null && selectButton.isVisible() && selectButton.isEnabled()) {
      selectButton.doClick();
    }
    else if (e.getActionCommand().equals(FormTree.ENTER_ACTION)) {
      naviTree.showPopup();   // show at least the popup menu (for those users that don't know how to get it)
    }
  }

  @SuppressWarnings("unchecked")
  private void naviTree_valueChanged(TreeSelectionEvent e) {

    // ausgewaehltes Element ermitteln
    TreePath[] paths = naviTree.getSelectionPaths();

    selectedObject  = null;
    selectedObjects = null;
    int enableCut   = 0;

    if (paths != null)  {
      for (TreePath path : paths) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object obj = ((PdoTreeObject) node.getUserObject()).getObject();
        if (obj instanceof PersistentDomainObject<?>) {
          addToSelectedObjects((T) obj);
          if (path.getPathCount() == 2) {
            // root is always skipped
            enableCut++;
          }
        }
      }
    }
    cutSelectedButton.setEnabled(enableCut > 1);
    fireValueChanged();
    selectButton.setEnabled(selectedObject != null);
  }



  private int findRowInModel(T object) {
    return object == null ? -1 : list.indexOf(object);
  }

  private T getFirstSelectedObjectInTable() {
    int row = getModelIndex(naviTable.getSelectedRow());
    return row >= 0 ? list.get(row) : null;
  }


  /**
   * Determines whether the object should be edited modal or non-modal.
   * <br>
   * The default implementation returns true if the parent window is modal.
   *
   * @return true if modal
   */
  protected boolean isObjectEditedModal() {
    return FormUtilities.getInstance().isParentWindowModal(this);
  }


  /**
   * Edits the object currently selected in table.
   *
   * @param changeable true if edit, false if show only
   */
  @SuppressWarnings("unchecked")
  protected void editObjectInTable(boolean changeable) {
    T obj = getFirstSelectedObjectInTable();
    if (obj != null) {
      if (isObjectEditedModal()) {
        PdoEditDialogPool.getInstance().editModal(obj);
      }
      else  {
        // non-modal
        final PdoEditDialog<T> d = PdoEditDialogPool.getInstance().useNonModalDialog(obj, changeable, true);
        d.setLinkedPdoList(list);
        d.addActionListener((ActionEvent e) -> {
          if (e.getActionCommand() != null) {
            int modelRow;
            switch (e.getActionCommand()) {
              case PdoEditDialog.ACTION_SAVE:
                T obj1 = d.getLastPdo();
                // reload to get the real data stored in DB (in case of CANCEL)
                if (!obj1.isNew()) {
                  obj1 = obj1.reload();
                }
                if (obj1 != null) {
                  modelRow = findRowInModel(obj1);
                  if (modelRow >= 0 && modelRow < list.size()) {
                    list.set(modelRow, obj1);
                    naviModel.listUpdated(modelRow, modelRow);
                  }
                }
                break;

              case PdoEditDialog.ACTION_DELETE:
                T obj2 = d.getLastPdo();
                if (obj2 != null) {
                  modelRow = findRowInModel(obj2);
                  if (modelRow >= 0 && modelRow < list.size()) {
                    naviModel.listDeleted(modelRow, modelRow);
                    list.remove(modelRow);
                  }
                }
                break;

              case PdoEditDialog.ACTION_PREVIOUS:
                modelRow = findRowInModel(d.getLastPdo());
                if (modelRow > 0 && modelRow <= list.size()) {
                  naviTable.setSelectedRow(modelRow - 1);
                }
                break;

              case PdoEditDialog.ACTION_NEXT:
                modelRow = findRowInModel(d.getLastPdo());
                if (modelRow >= 0 && modelRow < list.size() - 1) {
                  naviTable.setSelectedRow(modelRow + 1);
                }
                break;
            }
          }
        });
      }
    }
  }


  private void naviTable_actionPerformed(ActionEvent e)  {
    // double click or ENTER
    if (selectionFilter != null) {
      if (selectedObject != null) {
        selectButton.doClick();
      }
    }
    else  {
      // no select button: we are usually in a non-modal search dialog.
      // try to edit the object in a PdoEditDialog.
      editObjectInTable(true);
    }
  }


  private void naviTable_valueChanged(ListSelectionEvent e) {

    int[] rows = naviTable.getSelectedRows();

    selectedObject  = null;
    selectedObjects = null;
    int enableCut   = 0;

    for (int i=0; i < rows.length; i++)  {
      int row = rows[i];
      if (row >= 0 && row < list.size()) {
        addToSelectedObjects(list.get(getModelIndex(row)));
        enableCut++;
      }
    }

    cutSelectedButton.setEnabled(enableCut > 1);
    fireValueChanged();
    selectButton.setEnabled(selectedObject != null);
  }



  private void updateSumButton()  {
    // check if at least one column can be numeric
    boolean sumUpEnabled = false;
    if (list != null && !list.isEmpty()) {
      if (naviModel != null)  {
        FormTableEntry<T> template = naviModel.getTemplate();
        int cols = template.getColumnCount();
        for (int col=0; col < cols; col++)  {
          if (naviTable.isColumnVisible(col) && !template.isColumnNotSummable(col))  {
            sumUpEnabled = true;
            break;
          }
        }
        if (!sumUpEnabled && naviSorter != null && naviSorter.getSumEntry() != null)  {
          naviSorter.setSumEntry(null);
          sumButton.setSelected(false);
        }
      }
    }
    sumButton.setVisible(sumUpEnabled);
  }




  /**
   * Builds up the new tree
   */
  @SuppressWarnings("unchecked")
  private void buildTree() {
    if (naviSorter != null && naviSorter.isSorted() ||
        naviTable.getRowSorter() != null)  {
      // build sorted list
      List<T> sortedList = new ArrayList<>();
      int size = list == null ? 0 : list.size();
      for (int viewRow=0; viewRow < size; viewRow++)  {
        int row = getModelIndex(viewRow);
        if (row >= 0)  {   // if valid and not the sumEntry
          T object = list.get(row);
          if (object != null && (treeFilter == null || treeFilter.isValid(object))) {
            sortedList.add(object);
          }
        }
      }
      naviTree.buildTree(sortedList);
    }
    else  {
      naviTree.buildTree(treeFilter != null ? treeFilter.filter(list) : list);   // unsorted
    }
  }



  /**
   * Creates the formtable model for a given object.
   * <p>
   * The default implementation just creates the model and binds it.
   *
   * @param template the formtable entry
   * @return the table model
   */
  public FormTableModel<T> createFormTableModel(FormTableEntry<T> template) {
    FormTableModel<T> model = new FormTableModel<>(template);
    model.bind();
    return model;
  }



  /**
   * updates the view
   *
   * @param mode one of VIEW_...
   * @param rebuildView true if rebuild the view always
   */
  @SuppressWarnings("unchecked")
  private void showView(ViewMode mode, boolean rebuildView)  {

    T pdo = getTemplateProvider();
    if (pdo == null && list != null && !list.isEmpty()) {
      pdo = list.iterator().next();
    }

    if (pdo != null && (rebuildView || viewMode != mode)) { // view changed

      boolean scrollToFirstSelection = false;    // true if a new view (table or tree) has been created

      if (mode == ViewMode.TABLE) {    // switch to table view

        if (naviModel == null || rebuildView)  {    // initialize view

          naviModel  = createFormTableModel(Rdc.createGuiProvider(pdo).getFormTableEntry());
          if (naviTable.getRowSorter() == null) {
            naviSorter = new FormTableSorter<>(naviModel);
          }
          else  {
            naviSorter = null;
          }
          naviTable.setName(tableName == null ? Rdc.createGuiProvider(pdo).getFormTableName() : tableName);

          String title = tableTitle;
          if (title == null) {
            title = pdo.getPlural();
            DomainContext context = pdo.getBaseContext();
            if (context != null) {
              String contextName = context.toString();
              if (contextName.length() > 0)  {
                title = MessageFormat.format(RdcSwingRdcBundle.getString("{0} IN {1}"), title, contextName);
              }
            }
          }

          naviTable.createDefaultColumnsFromModel();    // create column width from preferences. table height see below

          FormTableUtilityPopup popup = Rdc.createPopup();
          popup.setTitle(title);
          popup.setIntro(tableIntro);
          naviTable.setUtilityPopup(popup);

          // switch to table view
          naviScroll.setViewportView(naviTable);
          naviModel.listChanged(list);
          naviTable.setSelectionMode(listSelectionMode);
          naviTable.setModel(naviSorter == null ? naviModel : naviSorter);
          naviTableUpdated = true;
          scrollToFirstSelection = true;

          // check size if (use from preferences if available)
          int width = (int)(naviTable.getPreferredSize().getWidth() + naviScroll.getVerticalScrollBar().getPreferredSize().getWidth()) + 4;
          Dimension size = naviScroll.getPreferredSize();
          Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
          if (width > screenSize.width - 50) {
            width = screenSize.width - 50; // align
          }
          if (size.height < 50) {
            size.height = screenSize.height >> 1;
          }
          size.width = width;
          naviScroll.setPreferredSize(size);

          if (naviSorter != null) {
            naviSorter.addPropertyChangeListener((PropertyChangeEvent e) -> {
              if (e.getSource() instanceof FormTableSorter) {
                // set sorting string
                setSorting(((FormTableSorter) e.getSource()).getSortNames());
                naviTreeUpdated = false;
              }
            });
          }
        }

        else  {
           // switch to table view
          if (!naviTableUpdated)  {
            naviModel.listChanged(list);
            naviTableUpdated = true;
            naviTreeUpdated = false;
            scrollToFirstSelection = true;
          }
          naviScroll.setViewportView(naviTable);
        }


        // select objects in naviTable that are already selected in naviTree
        TreePath paths[] = naviTree.getSelectionPaths();

        naviTable.clearSelection();
        selectedObjects = null;
        selectedObject  = null;
        if (paths != null)  {
          for (TreePath path : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) (path.getLastPathComponent());
            // find index in object array
            Object so = ((PdoTreeObject)node.getUserObject()).getObject();
            int i = getViewIndex(list.indexOf(so));
            if (i >= 0) {
              naviTable.getSelectionModel().addSelectionInterval(i, i);
              if (scrollToFirstSelection) {
                naviTable.scrollToCell(i, 0);
                scrollToFirstSelection = false;
              }
            }
          }
        }

        if (scrollToFirstSelection) {
          // no previous selection: scroll to begin
          naviTable.scrollToCell(0, 0);
        }

        if (naviSorter != null) {
          sortBox.setVisible(true);
          setSorting(naviSorter.getSortNames());
        }
        else  {
          sortBox.setVisible(false);
        }

        updateSumButton();
      }

      else if (mode == ViewMode.TREE) {

        // select objects in naviTree that are already selected in naviTable
        int[] rows = naviTable.getSelectedRows();

        naviTree.clearSelection();
        selectedObject  = null;
        selectedObjects = null;

        if (!naviTreeUpdated || rebuildView) {
          treeFilter = Rdc.createGuiProvider(pdo).getTreeFilter();
          buildTree();
          naviTreeUpdated = true;
          naviTableUpdated = false;
          scrollToFirstSelection = list != null && !list.isEmpty();
        }

        // switch back to treeView
        naviScroll.setViewportView(naviTree);

        // mark selected
        for (int i=0; i < rows.length; i++)  {
          Object obj = naviTable.getObjectAt(rows[i]);
          TreePath path = naviTree.findPathInCollection(obj);
          if (path != null) {
            naviTree.getSelectionModel().addSelectionPath(path);
          }
          if (scrollToFirstSelection) {
            naviTree.scrollPathToVisible(path);
            scrollToFirstSelection = false;
          }
        }

        if (scrollToFirstSelection) {
          // no previous selection: scroll to begin
          naviTree.scrollRowToVisible(0);
        }

        if (naviSorter != null && naviSorter.isSorted())  {
          sortBox.setVisible(true);
          sortBox.setEnabled(false);
        }
        else  {
          sortBox.setVisible(false);
        }
        sumButton.setVisible(false);
      }

      viewMode = mode;

      if (autoPack && isShowing() && !packed) {
        Window w = getParentWindow();
        if (w != null)  {
          w.pack();
          packed = true;
        }
      }
    }

    // set number of rows
    updateRowCountLabel();
  }



  /**
   * sets the current sorting string
   */
  private void setSorting(String text)  {
    if (text == null) {
      sortBox.setText(RdcSwingRdcBundle.getString("UNSORTED"));
      sortBox.setSelected(false);
      sortBox.setEnabled(false);
    }
    else  {
      sortBox.setText(text);
      sortBox.setSelected(true);
      sortBox.setEnabled(true);
    }
  }




  // --------------- implemente KeyListener ---------------------------------

  @Override
  public void keyTyped(KeyEvent e)  {}

  @Override
  public void keyPressed(KeyEvent e)  {
    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
      getParentWindow().dispose();
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {}




  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    buttonGroup1 = new javax.swing.ButtonGroup();
    viewButtonPanel = new org.tentackle.swing.FormPanel();
    tableViewButton = new javax.swing.JToggleButton();
    treeViewButton = new javax.swing.JToggleButton();
    sortBox = new org.tentackle.swing.FormRadioButton();
    jLabel1 = new javax.swing.JLabel();
    rowCountLabel = new javax.swing.JLabel();
    cutSelectedButton = new javax.swing.JButton();
    sumButton = new javax.swing.JToggleButton();
    naviScroll = new javax.swing.JScrollPane();
    buttonPanel = new javax.swing.JPanel();
    selectButton = new org.tentackle.swing.FormButton();
    cancelButton = new org.tentackle.swing.FormButton();
    closeButton = new org.tentackle.swing.FormButton();

    setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
    setLayout(new java.awt.BorderLayout());

    viewButtonPanel.setLayout(new java.awt.GridBagLayout());

    buttonGroup1.add(tableViewButton);
    tableViewButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("table"));
    tableViewButton.setToolTipText(RdcSwingRdcBundle.getString("TABLE VIEW")); // NOI18N
    tableViewButton.setMargin(new java.awt.Insets(1, 1, 1, 1));
    tableViewButton.setName("table"); // NOI18N
    tableViewButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        tableViewButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 1, 0);
    viewButtonPanel.add(tableViewButton, gridBagConstraints);

    buttonGroup1.add(treeViewButton);
    treeViewButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("tree"));
    treeViewButton.setToolTipText(RdcSwingRdcBundle.getString("TREE VIEW")); // NOI18N
    treeViewButton.setMargin(new java.awt.Insets(1, 1, 1, 1));
    treeViewButton.setName("tree"); // NOI18N
    treeViewButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        treeViewButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 1, 0);
    viewButtonPanel.add(treeViewButton, gridBagConstraints);

    sortBox.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    sortBox.setName("sort"); // NOI18N
    sortBox.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sortBoxActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    viewButtonPanel.add(sortBox, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.ipadx = 80;
    gridBagConstraints.weightx = 1.0;
    viewButtonPanel.add(jLabel1, gridBagConstraints);

    rowCountLabel.setText("0"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
    viewButtonPanel.add(rowCountLabel, gridBagConstraints);

    cutSelectedButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("cut"));
    cutSelectedButton.setToolTipText(RdcSwingRdcBundle.getString("KEEP SELECTED ONLY")); // NOI18N
    buttonGroup1.add(cutSelectedButton);
    cutSelectedButton.setMargin(new java.awt.Insets(1, 1, 1, 1));
    cutSelectedButton.setName("cut"); // NOI18N
    cutSelectedButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cutSelectedButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 1, 0);
    viewButtonPanel.add(cutSelectedButton, gridBagConstraints);

    sumButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("sum"));
    sumButton.setToolTipText(RdcSwingRdcBundle.getString("COMPUTE SUMS")); // NOI18N
    sumButton.setMargin(new java.awt.Insets(1, 1, 1, 1));
    sumButton.setName("sum"); // NOI18N
    sumButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sumButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 1, 0);
    viewButtonPanel.add(sumButton, gridBagConstraints);

    add(viewButtonPanel, java.awt.BorderLayout.NORTH);
    add(naviScroll, java.awt.BorderLayout.CENTER);

    selectButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("ok"));
    selectButton.setText(RdcSwingRdcBundle.getString("SELECT")); // NOI18N
    selectButton.setActionCommand(ACTION_SELECT);
    selectButton.setName("select"); // NOI18N
    selectButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        selectButtonActionPerformed(evt);
      }
    });
    buttonPanel.add(selectButton);

    cancelButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("cancel"));
    cancelButton.setText(RdcSwingRdcBundle.getString("CANCEL")); // NOI18N
    cancelButton.setActionCommand(ACTION_CANCEL);
    cancelButton.setName("cancel"); // NOI18N
    cancelButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cancelButtonActionPerformed(evt);
      }
    });
    buttonPanel.add(cancelButton);

    closeButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("close"));
    closeButton.setText(RdcSwingRdcBundle.getString("CLOSE")); // NOI18N
    closeButton.setActionCommand(ACTION_CLOSE);
    closeButton.setName("close"); // NOI18N
    closeButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        closeButtonActionPerformed(evt);
      }
    });
    buttonPanel.add(closeButton);

    add(buttonPanel, java.awt.BorderLayout.SOUTH);
  }// </editor-fold>//GEN-END:initComponents

  private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
    selectedObject  = null;
    selectedObjects = null;
    fireActionPerformed (evt.getActionCommand());
  }//GEN-LAST:event_closeButtonActionPerformed

  private void sumButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sumButtonActionPerformed
    FormUtilities.getInstance().setWaitCursor(this);
    EventQueue.invokeLater(() -> {
      if (sumButton.isSelected()) {
        if (naviSorter != null) {
          naviSorter.setSumEntry(new SumFormTableEntry<>(naviModel));
        }
        naviTable.scrollToCell(naviModel.getRowCount(), 0);
      }
      else  {
        if (naviSorter != null) {
          naviSorter.setSumEntry(null);
        }
      }
      FormUtilities.getInstance().setDefaultCursor(PdoNavigationPanel.this);
    });
  }//GEN-LAST:event_sumButtonActionPerformed

  @SuppressWarnings("unchecked")
  private void cutSelectedButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cutSelectedButtonActionPerformed
    // select objects in naviTree or naviTable (NOT from selectedObjects!)
    List<T> objList = new ArrayList<>();
    if (viewMode == ViewMode.TABLE) {
      int[] rows = naviTable.getSelectedRows();
      for (int i=0; i < rows.length; i++)  {
        int row = getModelIndex(rows[i]);
        if (row >= 0 && row < list.size()) {
          objList.add(list.get(row));
        }
      }
    }
    else if (viewMode == ViewMode.TREE) {
      TreePath[] paths = naviTree.getSelectionPaths();
      if (paths != null)  {
        for (TreePath path : paths) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode) (path.getLastPathComponent());
          Object obj = ((PdoTreeObject) node.getUserObject()).getObject();
          if (path.getPathCount() == 2 && obj instanceof PersistentDomainObject) {
            objList.add((T) obj);
          }
        }
      }
    }

    if (objList.size() > 0)  {
      setObjects(objList);
      naviTable.clearSelection();
      naviTree.clearSelection();
    }
  }//GEN-LAST:event_cutSelectedButtonActionPerformed

  private void sortBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortBoxActionPerformed
    if (!sortBox.isSelected()) {
      if (naviSorter != null) {
        naviSorter.clearSorting();
        naviSorter.sort();
      }
      setSorting(null);
      naviTreeUpdated = false;
    }
  }//GEN-LAST:event_sortBoxActionPerformed

  private void treeViewButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_treeViewButtonActionPerformed
    if (treeViewButton.isSelected())  {
      FormUtilities.getInstance().setWaitCursor(this);
      EventQueue.invokeLater(() -> {
        showView(ViewMode.TREE, false);  // switch to tree
        if (naviTree != null) {
          FormUtilities.getInstance().setDefaultCursor(PdoNavigationPanel.this);
          naviTree.requestFocusInWindow();
        }
      });
    }
  }//GEN-LAST:event_treeViewButtonActionPerformed

  private void tableViewButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tableViewButtonActionPerformed
    if (tableViewButton.isSelected()) {
      FormUtilities.getInstance().setWaitCursor(this);
      EventQueue.invokeLater(() -> {
        showView(ViewMode.TABLE, false);    // switch to table view
        FormUtilities.getInstance().setDefaultCursor(PdoNavigationPanel.this);
        naviTable.requestFocusInWindow();
      });
    }
  }//GEN-LAST:event_tableViewButtonActionPerformed

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
    selectedObject  = null;
    selectedObjects = null;
    fireActionPerformed (evt.getActionCommand());
  }//GEN-LAST:event_cancelButtonActionPerformed

  private void selectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectButtonActionPerformed
    fireActionPerformed (evt.getActionCommand());
  }//GEN-LAST:event_selectButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.ButtonGroup buttonGroup1;
  private javax.swing.JPanel buttonPanel;
  private org.tentackle.swing.FormButton cancelButton;
  private org.tentackle.swing.FormButton closeButton;
  private javax.swing.JButton cutSelectedButton;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JScrollPane naviScroll;
  private javax.swing.JLabel rowCountLabel;
  private org.tentackle.swing.FormButton selectButton;
  private org.tentackle.swing.FormRadioButton sortBox;
  private javax.swing.JToggleButton sumButton;
  private javax.swing.JToggleButton tableViewButton;
  private javax.swing.JToggleButton treeViewButton;
  private org.tentackle.swing.FormPanel viewButtonPanel;
  // End of variables declaration//GEN-END:variables

}


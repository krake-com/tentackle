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

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.log.Logger;
import org.tentackle.prefs.PersistedPreferencesFactory;

import javafx.collections.ObservableList;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import java.io.File;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;


/**
 * Table popup.<br>
 * Provides a context menu with several handy features: a column chooser, auto-adjusting the column widths,
 * printing, exporting all or the selected rows to a spreadsheet or XML file, and saving or loading the
 * table preferences. For tree tables, the menu also provides items to expand and collapse the tree.<br>
 * The popup can be used for tables and tree tables. It installs its menu as the context menu of the
 * table and all its columns.
 *
 * @author harald
 * @param <S> the table element type
 */
public class TablePopup<S> {

  /*
   * TableView and TreeTableView don't inherit from each other and thus
   * each spawn their own related types such as TableColumn, TreeTableColumn, etc...
   * As a result, since TablePopup applies to both tables and tree tables,
   * a lot of semantically identical code is necessary.
   * However, it is not really duplicate code. There's not much we can do to reduce it.
   */

  private static final Logger LOGGER = Logger.get(TablePopup.class);

  private final FxTableView<S> table;           // the table
  private final FxTreeTableView<S> treeTable;   // tree table
  private String preferencesSuffix;             // suffix for the preferences
  private String title;                         // the title of the printed table
  private boolean columnMenuEnabled = true;     // true if user is allowed to hide/show columns (default)


  /**
   * Creates a table popup.
   *
   * @param table the table
   * @param preferencesSuffix the preferences suffix to load/save table preferences, null if from table config
   * @param title the title of the printed table, null if from table config
   */
  public TablePopup(FxTableView<S> table, String preferencesSuffix, String title) {
    this(Objects.requireNonNull(table), null, preferencesSuffix, title);
    ContextMenu menu = createContextMenu();
    table.setContextMenu(menu);
    for (TableColumn<S,?> column: table.getColumns())  {
      column.setContextMenu(menu);
    }
  }

  /**
   * Creates a table popup.
   *
   * @param treeTable the tree table
   * @param preferencesSuffix the preferences suffix to load/save table preferences, null if from table config
   * @param title the title of the printed table, null if from table config
   */
  public TablePopup(FxTreeTableView<S> treeTable, String preferencesSuffix, String title) {
    this(null, Objects.requireNonNull(treeTable), preferencesSuffix, title);
    ContextMenu menu = createContextMenu();
    treeTable.setContextMenu(menu);
    for (TreeTableColumn<S,?> column: treeTable.getColumns())  {
      column.setContextMenu(menu);
    }
  }

  private TablePopup(FxTableView<S> table, FxTreeTableView<S> treeTable, String preferencesSuffix, String title) {
    this.table = table;
    this.treeTable = treeTable;
    this.preferencesSuffix = preferencesSuffix;
    this.title = title;
  }


  /**
   * Gets the table.
   *
   * @return the table, null if tree table popup
   */
  public FxTableView<S> getTable() {
    return table;
  }

  /**
   * Gets the tree table.
   *
   * @return the tree table, null if table popup
   */
  public FxTreeTableView<S> getTreeTable() {
    return treeTable;
  }


  /**
   * Gets the printed title.
   *
   * @return the title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Sets the printed title.
   *
   * @param title the title for printing
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Gets the table preferences suffix.
   *
   * @return the suffix, null if none
   */
  public String getPreferencesSuffix() {
    return preferencesSuffix;
  }

  /**
   * Sets the table preferences suffix.
   *
   * @param preferencesSuffix the suffix
   */
  public void setPreferencesSuffix(String preferencesSuffix) {
    this.preferencesSuffix = Objects.requireNonNull(preferencesSuffix);
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
   * The column menu allows the user to show or hide columns.
   *
   * @param columnMenuEnabled  true to enable column menu (default)
   */
  public void setColumnMenuEnabled(boolean columnMenuEnabled) {
    this.columnMenuEnabled = columnMenuEnabled;
  }

  /**
   * Recursively expands the items of the tree table.
   *
   * @param selectedItem the selected item to start, null for the whole tree table
   */
  public void expandTreeTable(TreeItem<S> selectedItem) {
    if (treeTable != null) {
      if (selectedItem != null) {
        FxUtilities.getInstance().expandAll(selectedItem);
      }
      else {
        treeTable.expandAll();
      }
    }
  }

  /**
   * Recursively collapses the items of the tree table.
   *
   * @param selectedItem the selected item to start, null for the whole tree table
   */
  public void collapseTreeTable(TreeItem<S> selectedItem) {
    if (treeTable != null) {
      if (selectedItem != null) {
        FxUtilities.getInstance().collapseAll(selectedItem);
      }
      else {
        treeTable.collapseAll();
      }
    }
  }

  /**
   * Creates the context menu.
   *
   * @return the context menu
   */
  public ContextMenu createContextMenu() {

    ContextMenu menu = Fx.create(ContextMenu.class);

    MenuItem expandItem;
    MenuItem collapseItem;
    if (treeTable != null) {
      expandItem = Fx.create(MenuItem.class);
      menu.getItems().add(expandItem);
      collapseItem = Fx.create(MenuItem.class);
      menu.getItems().add(collapseItem);
      menu.getItems().add(Fx.create(SeparatorMenuItem.class));
    }
    else {
      expandItem = null;
      collapseItem = null;
    }

    ObservableList<TableColumn<S,?>> tableColumns = table == null ? null : table.getColumns();
    ObservableList<TreeTableColumn<S,?>> treeTableColumns = treeTable == null ? null : treeTable.getColumns();

    final Menu columnMenu = Fx.create(Menu.class);
    columnMenu.setText(RdcFxRdcBundle.getString("COLUMNS..."));
    menu.getItems().add(columnMenu);

    MenuItem autoWidthItem = Fx.create(MenuItem.class);
    autoWidthItem.setText(RdcFxRdcBundle.getString("AUTO ADJUST"));
    autoWidthItem.setOnAction(e -> autoWidth());
    menu.getItems().add(autoWidthItem);

    MenuItem printItem = Fx.create(MenuItem.class);
    printItem.setText(RdcFxRdcBundle.getString("PRINT"));
    printItem.setOnAction(e -> print());
    menu.getItems().add(printItem);

    MenuItem excelItem = Fx.create(MenuItem.class);
    excelItem.setText(RdcFxRdcBundle.getString("EXPORT TO SPREADSHEET"));
    excelItem.setOnAction(e -> toSpreadsheet(false));
    menu.getItems().add(excelItem);

    MenuItem selectedExcelItem = Fx.create(MenuItem.class);
    selectedExcelItem.setText(RdcFxRdcBundle.getString("EXPORT TO SPREADSHEET (SELECTED)"));
    selectedExcelItem.setOnAction(e -> toSpreadsheet(true));
    menu.getItems().add(selectedExcelItem);

    MenuItem xmlItem = Fx.create(MenuItem.class);
    xmlItem.setText(RdcFxRdcBundle.getString("EXPORT TO XML"));
    xmlItem.setOnAction(e -> toXml(false));
    menu.getItems().add(xmlItem);

    MenuItem selectedXmlItem = Fx.create(MenuItem.class);
    selectedXmlItem.setText(RdcFxRdcBundle.getString("EXPORT TO XML (SELECTED)"));
    selectedXmlItem.setOnAction(e -> toXml(true));
    menu.getItems().add(selectedXmlItem);

    if (PersistedPreferencesFactory.getInstance().isSystemOnly()) {
      if (!PersistedPreferencesFactory.getInstance().isReadOnly())  {
        MenuItem saveItem = Fx.create(MenuItem.class);
        saveItem.setText(RdcFxRdcBundle.getString("SAVE SYSTEM PREFERENCES"));
        saveItem.setOnAction(e -> savePreferences(true));
        menu.getItems().add(saveItem);
      }

      MenuItem restoreItem = Fx.create(MenuItem.class);
      restoreItem.setText(RdcFxRdcBundle.getString("LOAD SYSTEM PREFERENCES"));
      restoreItem.setOnAction(e -> loadPreferences(true));
      menu.getItems().add(restoreItem);
    }
    else  {
      if (!PersistedPreferencesFactory.getInstance().isReadOnly())  {
        MenuItem saveItem = Fx.create(MenuItem.class);
        saveItem.setText(RdcFxRdcBundle.getString("SAVE USER PREFERENCES"));
        saveItem.setOnAction(e -> savePreferences(false));
        menu.getItems().add(saveItem);
      }

      MenuItem restoreItem = Fx.create(MenuItem.class);
      restoreItem.setText(RdcFxRdcBundle.getString("LOAD USER PREFERENCES"));
      restoreItem.setOnAction(e -> loadPreferences(false));
      menu.getItems().add(restoreItem);

      MenuItem restoreSysItem = Fx.create(MenuItem.class);
      restoreSysItem.setText(RdcFxRdcBundle.getString("LOAD SYSTEM PREFERENCES"));
      restoreSysItem.setOnAction(e -> loadPreferences(true));
      menu.getItems().add(restoreSysItem);
    }
    menu.getItems().add(Fx.create(SeparatorMenuItem.class));
    final Label countLabel = new Label();
    CustomMenuItem labelItem = Fx.create(CustomMenuItem.class);
    labelItem.setContent(countLabel);
    labelItem.setHideOnClick(false);
    labelItem.setDisable(true);
    menu.getItems().add(labelItem);

    menu.setOnShowing(event -> {

      List<S> items = table == null ? treeTable.getItems() : table.getItems();
      int selectedIndex = table == null ?
                          treeTable.getSelectionModel().getSelectedIndex() :
                          table.getSelectionModel().getSelectedIndex();
      countLabel.setText(MessageFormat.format(RdcFxRdcBundle.getString("ROW {0} OF {1}"),
                                              selectedIndex + 1, items == null ? 0 : items.size()));

      if (treeTable != null) {
        TreeItem<S> selectedItem = treeTable.getSelectionModel().getSelectedItem();
        expandItem.setOnAction(e -> expandTreeTable(selectedItem));
        expandItem.setText(selectedItem != null && selectedItem.isExpanded() ?
                           RdcFxRdcBundle.getString("EXPAND_AGAIN") : RdcFxRdcBundle.getString("EXPAND"));
        expandItem.setDisable(selectedItem != null && selectedItem.isLeaf());
        collapseItem.setOnAction(e -> collapseTreeTable(selectedItem));
        collapseItem.setText(RdcFxRdcBundle.getString("COLLAPSE"));
        collapseItem.setDisable(selectedItem != null && !selectedItem.isExpanded());
      }

      if (isColumnMenuEnabled()) {
        columnMenu.getItems().clear();
        boolean allVisible = true;
        boolean allInvisible = true;
        if (tableColumns != null) {
          for (TableColumn<S, ?> column : tableColumns) {
            CheckMenuItem item = Fx.create(CheckMenuItem.class);
            item.setText(column.getText());
            item.setSelected(column.isVisible());
            if (column.isVisible()) {
              allInvisible = false;
            }
            else {
              allVisible = false;
            }
            item.setOnAction(e -> column.setVisible(item.isSelected()));
            columnMenu.getItems().add(item);
          }
          if (!allVisible) {
            MenuItem showAllItem = Fx.create(MenuItem.class);
            showAllItem.setText(RdcFxRdcBundle.getString("SHOW ALL"));
            showAllItem.setOnAction(e -> {
              for (TableColumn<S, ?> column : tableColumns) {
                column.setVisible(true);
              }
            });
            columnMenu.getItems().add(showAllItem);
          }
          if (!allInvisible) {
            MenuItem hideAllItem = Fx.create(MenuItem.class);
            hideAllItem.setText(RdcFxRdcBundle.getString("HIDE ALL"));
            hideAllItem.setOnAction(e -> {
              for (TableColumn<S, ?> column : tableColumns) {
                column.setVisible(false);
              }
            });
            columnMenu.getItems().add(hideAllItem);
          }
        }
        else if (treeTableColumns != null) {
          for (TreeTableColumn<S, ?> column : treeTableColumns) {
            CheckMenuItem item = Fx.create(CheckMenuItem.class);
            item.setText(column.getText());
            item.setSelected(column.isVisible());
            if (column.isVisible()) {
              allInvisible = false;
            }
            else {
              allVisible = false;
            }
            item.setOnAction(e -> column.setVisible(item.isSelected()));
            columnMenu.getItems().add(item);
          }
          if (!allVisible) {
            MenuItem showAllItem = Fx.create(MenuItem.class);
            showAllItem.setText(RdcFxRdcBundle.getString("SHOW ALL"));
            showAllItem.setOnAction(e -> {
              for (TreeTableColumn<S, ?> column : treeTableColumns) {
                column.setVisible(true);
              }
            });
            columnMenu.getItems().add(showAllItem);
          }
          if (!allInvisible) {
            MenuItem hideAllItem = Fx.create(MenuItem.class);
            hideAllItem.setText(RdcFxRdcBundle.getString("HIDE ALL"));
            hideAllItem.setOnAction(e -> {
              for (TreeTableColumn<S, ?> column : treeTableColumns) {
                column.setVisible(false);
              }
            });
            columnMenu.getItems().add(hideAllItem);
          }
        }
        columnMenu.setVisible(true);
      }
      else {
        columnMenu.setVisible(false);
      }
    });

    return menu;
  }


  /**
   * Resizes all columns to fit their content.
   */
  public void autoWidth() {
    if (table != null) {
      FxUtilities.getInstance().resizeColumnsToFitContent(table);
    }
    else {
      FxUtilities.getInstance().resizeColumnsToFitContent(treeTable);
    }
  }


  /**
   * Prints the table.
   */
  public void print() {
    if (table != null) {
      TableUtilities.getInstance().print(table, getTitle());
    }
    else {
      TableUtilities.getInstance().print(treeTable, getTitle());
    }
  }


  /**
   * Opens a dialog to export the table to a spreadsheet file.<br>
   * The file is opened in the associated application afterward.
   *
   * @param onlySelected true if export only selected rows
   */
  public void toSpreadsheet(boolean onlySelected) {
    try {
      if (table != null) {
        File file = TableUtilities.getInstance().selectSpreadsheetFile(table.getConfiguration().getName(), Fx.getStage(table));
        if (file != null) {
          TableUtilities.getInstance().toSpreadsheet(table, file, onlySelected);
          FxUtilities.getInstance().edit(table, file);
        }
      }
      else {
        File file = TableUtilities.getInstance().selectSpreadsheetFile(treeTable.getConfiguration().getName(), Fx.getStage(treeTable));
        if (file != null) {
          TableUtilities.getInstance().toSpreadsheet(treeTable, file, onlySelected);
          FxUtilities.getInstance().edit(treeTable, file);
        }
      }
    }
    catch (RuntimeException ex)  {
      LOGGER.severe("creating spreadsheet failed", ex);
      Fx.error(table != null ? table : treeTable, RdcFxRdcBundle.getString("COULD NOT CREATE SPREADSHEET FILE"), ex);
    }

  }


  /**
   * Opens a dialog to export the table to an XML file.<br>
   * The file is opened in the associated application afterward.
   *
   * @param onlySelected true if export only selected rows
   */
  public void toXml(boolean onlySelected) {
    try {
      if (table != null) {
        File file = TableUtilities.getInstance().selectXmlFile(table.getConfiguration().getName(), Fx.getStage(table));
        if (file != null) {
          TableUtilities.getInstance().toXml(table, file, onlySelected);
          FxUtilities.getInstance().edit(table, file);
        }
      }
      else {
        File file = TableUtilities.getInstance().selectXmlFile(treeTable.getConfiguration().getName(), Fx.getStage(treeTable));
        if (file != null) {
          TableUtilities.getInstance().toXml(treeTable, file, onlySelected);
          FxUtilities.getInstance().edit(treeTable, file);
        }
      }
    }
    catch (RuntimeException ex)  {
      LOGGER.severe("creating XML file failed", ex);
      Fx.error(table != null ? table : treeTable, RdcFxRdcBundle.getString("COULD NOT CREATE XML FILE"), ex);
    }
  }


  /**
   * Saves the table preferences.
   *
   * @param system true if system scope, else user
   */
  public void savePreferences(boolean system) {
    try {
      if (table != null) {
        table.savePreferences(getPreferencesSuffix(), system);
      }
      else {
        treeTable.savePreferences(getPreferencesSuffix(), system);
      }
    }
    catch (RuntimeException ex) {
      LOGGER.severe("saving table preferences failed", ex);
      Fx.error(table != null ? table : treeTable, RdcFxRdcBundle.getString("SAVING PREFERENCES FAILED"), ex);
    }
  }

  /**
   * Saves the table preferences.<br>
   * System or user scope is determined from {@link PersistedPreferencesFactory}.
   */
  public void savePreferences() {
    savePreferences(PersistedPreferencesFactory.getInstance().isSystemOnly());
  }


  /**
   * Loads the table preferences.
   *
   * @param system true if from system scope only, else try user first
   */
  public void loadPreferences(boolean system) {
    try {
      if (table != null) {
        table.loadPreferences(getPreferencesSuffix(), system);
      }
      else {
        treeTable.loadPreferences(getPreferencesSuffix(),system);
      }
    }
    catch (RuntimeException ex) {
      LOGGER.severe("loading table preferences failed", ex);
      Fx.error(table != null ? table : treeTable, RdcFxRdcBundle.getString("LOADING PREFERENCES FAILED"), ex);
    }
  }

  /**
   * Loads the table preferences.<br>
   * System or user scope is determined from {@link PersistedPreferencesFactory}.
   */
  public void loadPreferences() {
    loadPreferences(PersistedPreferencesFactory.getInstance().isSystemOnly());
  }

}

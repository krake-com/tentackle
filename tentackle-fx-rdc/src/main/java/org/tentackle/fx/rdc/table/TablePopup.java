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


// Created on January 31, 2003, 11:30 AM

package org.tentackle.fx.rdc.table;

import javafx.collections.ObservableList;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.rdc.FileUtilities;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.log.Logger;
import org.tentackle.prefs.PersistedPreferencesFactory;

import java.io.File;
import java.text.MessageFormat;


/**
 * Table popup.<br>
 * Provides a context menu with several handy features such as export to excel,
 * save and load table settings, etc...
 *
 * @author harald
 * @param <S> the table element type
 */
public class TablePopup<S> {

  private static final Logger LOGGER = Logger.get(TablePopup.class);

  private final FxTableView<S> table;           // the table
  private final String preferencesSuffix;       // suffix for the preferences
  private String title;                         // the title of the printed table
  private boolean columnMenuEnabled = true;     // true if user is allowed to hide/show columns (default)
  private final Label countLabel = new Label(); // shows the current row number and the number of rows



  /**
   * Creates a table popup.
   *
   * @param table the table
   * @param preferencesSuffix the preferences suffix to load/save table preferences, null if from table config
   * @param title the title of the printed table, null if from table config
   */
  public TablePopup(FxTableView<S> table, String preferencesSuffix, String title) {
    this.table = table;
    this.preferencesSuffix = preferencesSuffix;
    this.title = title;

    ContextMenu menu = createContextMenu();
    table.setContextMenu(menu);
    for (TableColumn<S,?> column: table.getColumns())  {
      column.setContextMenu(menu);
    }
  }


  /**
   * Gets the table.
   *
   * @return the table
   */
  public FxTableView<S> getTable() {
    return table;
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
   * @param title the title for printings
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Gets the table preferences suffix.
   *
   * @return the suffix
   */
  public String getPreferencesSuffix() {
    return preferencesSuffix;
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
   * Creates the context menu.
   *
   * @return the context menu
   */
  public ContextMenu createContextMenu() {

    ContextMenu menu = new ContextMenu();

    ObservableList<TableColumn<S,?>> columns = table.getColumns();

    final Menu columnMenu;

    if (columnMenuEnabled)  {
      // build columns-menu
      columnMenu = new Menu();
      columnMenu.setText(RdcFxRdcBundle.getString("COLUMNS..."));
      menu.getItems().add(columnMenu);
    }
    else {
      columnMenu = null;
    }

    MenuItem autoWidthItem = new MenuItem();
    autoWidthItem.setText(RdcFxRdcBundle.getString("AUTO ADJUST"));
    autoWidthItem.setOnAction(e -> autoWidth());
    menu.getItems().add(autoWidthItem);

    MenuItem printItem = new MenuItem();
    printItem.setText(RdcFxRdcBundle.getString("PRINT"));
    printItem.setOnAction(e -> print());
    menu.getItems().add(printItem);

    MenuItem excelItem = new MenuItem();
    excelItem.setText(RdcFxRdcBundle.getString("EXPORT TO SPREADSHEET"));
    excelItem.setOnAction(e -> toSpreadsheet(false));
    menu.getItems().add(excelItem);

    MenuItem selectedExcelItem = new MenuItem();
    selectedExcelItem.setText(RdcFxRdcBundle.getString("EXPORT TO SPREADSHEET (SELECTED)"));
    selectedExcelItem.setOnAction(e -> toSpreadsheet(true));
    menu.getItems().add(selectedExcelItem);

    MenuItem xmlItem = new MenuItem();
    xmlItem.setText(RdcFxRdcBundle.getString("EXPORT TO XML"));
    xmlItem.setOnAction(e -> toXml(false));
    menu.getItems().add(xmlItem);

    MenuItem selectedXmlItem = new MenuItem();
    selectedXmlItem.setText(RdcFxRdcBundle.getString("EXPORT TO XML (SELECTED)"));
    selectedXmlItem.setOnAction(e -> toXml(true));
    menu.getItems().add(selectedXmlItem);

    if (PersistedPreferencesFactory.getInstance().isSystemOnly()) {
      if (!PersistedPreferencesFactory.getInstance().isReadOnly())  {
        MenuItem saveItem = new MenuItem();
        saveItem.setText(RdcFxRdcBundle.getString("SAVE SYSTEM PREFERENCES"));
        saveItem.setOnAction(e -> savePreferences(true));
        menu.getItems().add(saveItem);
      }

      MenuItem restoreItem = new MenuItem();
      restoreItem.setText(RdcFxRdcBundle.getString("LOAD SYSTEM PREFERENCES"));
      restoreItem.setOnAction(e -> loadPreferences(true));
      menu.getItems().add(restoreItem);
    }
    else  {
      if (!PersistedPreferencesFactory.getInstance().isReadOnly())  {
        MenuItem saveItem = new MenuItem();
        saveItem.setText(RdcFxRdcBundle.getString("SAVE USER PREFERENCES"));
        saveItem.setOnAction(e -> savePreferences(false));
        menu.getItems().add(saveItem);
      }

      MenuItem restoreItem = new MenuItem();
      restoreItem.setText(RdcFxRdcBundle.getString("LOAD USER PREFERENCES"));
      restoreItem.setOnAction(e -> loadPreferences(false));
      menu.getItems().add(restoreItem);

      MenuItem restoreSysItem = new MenuItem();
      restoreSysItem.setText(RdcFxRdcBundle.getString("LOAD SYSTEM PREFERENCES"));
      restoreSysItem.setOnAction(e -> loadPreferences(true));
      menu.getItems().add(restoreSysItem);
    }
    menu.getItems().add(new SeparatorMenuItem());
    MenuItem labelItem = new CustomMenuItem(countLabel);
    labelItem.setDisable(true);
    menu.getItems().add(labelItem);

    menu.setOnShowing(event -> {

      countLabel.setText(MessageFormat.format(RdcFxRdcBundle.getString("ROW {0} OF {1}"),
                                              table.getSelectionModel().getSelectedIndex() + 1,
                                              table.getItems() == null ? 0 : table.getItems().size()));

      if (columnMenu != null) {
        columnMenu.getItems().clear();
        boolean allVisible = true;
        boolean allInvisible = true;
        for (TableColumn<S,?> column: columns)  {
          CheckMenuItem item = new CheckMenuItem(column.getText());
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
          MenuItem showAllItem = new MenuItem(RdcFxRdcBundle.getString("SHOW ALL"));
          showAllItem.setOnAction(e -> {
            for (TableColumn<S,?> column: columns)  {
              column.setVisible(true);
            }
          });
          columnMenu.getItems().add(showAllItem);
        }
        if (!allInvisible) {
          MenuItem hideAllItem = new MenuItem(RdcFxRdcBundle.getString("HIDE ALL"));
          hideAllItem.setOnAction(e -> {
            for (TableColumn<S,?> column: columns)  {
              column.setVisible(false);
            }
          });
          columnMenu.getItems().add(hideAllItem);
        }
      }
    });

    return menu;
  }


  /**
   * Resizes all columns to fit their content.
   */
  public void autoWidth() {
    FxUtilities.getInstance().resizeColumnsToFitContent(table);
  }


  /**
   * Prints the table.
   */
  public void print() {
    TableUtilities.getInstance().print(table, title);
  }


  /**
   * Opens a dialog to export a table to an Excel sheet.
   *
   * @param onlySelected true if export only selected rows
   */
  public void toSpreadsheet(boolean onlySelected) {
    try {
      File file = TableUtilities.getInstance().selectSpreadsheetFile(table.getConfiguration().getName(), Fx.getStage(table));
      if (file != null) {
        TableUtilities.getInstance().toSpreadsheet(table, file, onlySelected);
        FileUtilities.getInstance().editFile(file);
      }
    }
    catch (RuntimeException ex)  {
      LOGGER.severe("creating spreadsheet failed", ex);
      Fx.error(RdcFxRdcBundle.getString("COULD NOT CREATE SPREADSHEET FILE"), ex);
    }

  }


  /**
   * Opens a dialog to export a table to a spreadsheet.
   *
   * @param onlySelected true if export only selected rows
   */
  public void toXml(boolean onlySelected) {
    try {
      File file = TableUtilities.getInstance().selectXmlFile(table.getConfiguration().getName(), Fx.getStage(table));
      if (file != null) {
        TableUtilities.getInstance().toXml(table, file, onlySelected);
        FileUtilities.getInstance().editFile(file);
      }
    }
    catch (RuntimeException ex)  {
      LOGGER.severe("creating XML file failed", ex);
      Fx.error(RdcFxRdcBundle.getString("COULD NOT CREATE XML FILE"), ex);
    }
  }


  /**
   * Saves the table preferences.
   *
   * @param system true if system scope, else user
   */
  public void savePreferences(boolean system) {
    try {
      table.savePreferences(preferencesSuffix, system);
    }
    catch (RuntimeException ex) {
      LOGGER.severe("saving table preferences failed", ex);
      Fx.error(RdcFxRdcBundle.getString("SAVING PREFERENCES FAILED"), ex);
    }
  }

  /**
   * Saves the table preferences.<br>
   * System- or user scope is determined from {@link PersistedPreferencesFactory}.
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
      table.loadPreferences(preferencesSuffix, system);
    }
    catch (RuntimeException ex) {
      LOGGER.severe("loading table preferences failed", ex);
      Fx.error(RdcFxRdcBundle.getString("LOADING PREFERENCES FAILED"), ex);
    }
  }

  /**
   * Loads the table preferences.<br>
   * System- or user scope is determined from {@link PersistedPreferencesFactory}.
   */
  public void loadPreferences() {
    loadPreferences(PersistedPreferencesFactory.getInstance().isSystemOnly());
  }

}

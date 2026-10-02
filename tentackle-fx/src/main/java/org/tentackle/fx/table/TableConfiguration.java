/*
 * Tentackle - https://tentackle.org.
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

import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;

import org.tentackle.fx.bind.FxTableBinder;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.table.filter.TableFilterSupport;

import java.util.Collection;

/**
 * Configuration for table and treetable views.<br>
 * The main purpose is to provide a single object to configure the component.
 *
 * @param <S> the type of the objects contained within the table's or tree-table's items list
 * @author harald
 */
public interface TableConfiguration<S> {

  /**
   * Type of binding.
   */
  enum BINDING {

    /** no binding. */
    NO,

    /** standard binding. */
    YES,

    /** binding including inherited bindables. */
    INHERITED

  }


  /**
   * Edit mode.
   */
  enum EDITMODE {
    /** not editable. */
    NO,

    /** edit current cell only. */
    SIMPLE,

    /** edit next/previous cell after commit. */
    ROW,

    /** edit cell above/below after commit. */
    COLUMN
  }


  /**
   * Gets the row object class.
   *
   * @return the row object class
   */
  Class<S> getObjectClass();

  /**
   * Gets the configurations of the columns.
   *
   * @return the column configurations
   */
  Collection<TableColumnConfiguration<S,?>> getColumnConfigurations();

  /**
   * Gets a column configuration by its name.
   *
   * @param name the name
   * @return the config, null if no such column
   */
  TableColumnConfiguration<S,?> getColumnConfiguration(String name);

  /**
   * Gets a column configuration by its table column.
   *
   * @param column the table column
   * @return the config, null if no such column
   */
  TableColumnConfiguration<S,?> getColumnConfiguration(TableColumn<S,?> column);

  /**
   * Gets a column configuration by its treetable column.
   *
   * @param column the treetable column
   * @return the config, null if no such column
   */
  TableColumnConfiguration<S,?> getColumnConfiguration(TreeTableColumn<S,?> column);

  /**
   * Gets the name of the table.<br>
   * The name is also used as a key to the table preferences.
   *
   * @return the name, never null
   */
  String getName();

  /**
   * Adds a column via binding path.
   *
   * @param name the column's binding path
   * @param displayedName the displayed column name
   * @return the created column configuration
   */
  TableColumnConfiguration<S, ?> addColumn(String name, String displayedName);

  /**
   * Removes a column via binding path.
   *
   * @param name the column's binding path
   * @return the old column configuration, null if no such column
   */
  TableColumnConfiguration<S, ?> removeColumn(String name);

  /**
   * Adds a column configuration.
   *
   * @param columnConfiguration the config
   */
  void addColumnConfiguration(TableColumnConfiguration<S, ?> columnConfiguration);

  /**
   * Saves the column sizes, visibility, view size and sorting to the preferences.<br>
   * Sorting, view size and filters are included according to {@link #isSortingIncluded()},
   * {@link #isViewSizeIncluded()} and {@link #isFilterIncluded()}.
   * The preferences node is the table's name, the optional suffix and the current locale.
   *
   * @param table the table
   * @param suffix the configuration suffix, null if none
   * @param system true if save to system prefs, else user prefs
   */
  void savePreferences(FxTableView<S> table, String suffix, boolean system);

  /**
   * Loads the column sizes, visibility, view size and sorting from the preferences.
   *
   * @param table the table
   * @param suffix the configuration suffix, null if none
   * @param system true if load from system prefs only, else user prefs first
   */
  void loadPreferences(FxTableView<S> table, String suffix, boolean system);

  /**
   * Saves the column sizes, visibility, view size and sorting to the preferences.<br>
   * Sorting, view size and filters are included according to {@link #isSortingIncluded()},
   * {@link #isViewSizeIncluded()} and {@link #isFilterIncluded()}.
   * The preferences node is the table's name, the optional suffix and the current locale.
   *
   * @param treeTable the treetable
   * @param suffix the configuration suffix, null if none
   * @param system true if save to system prefs, else user prefs
   */
  void savePreferences(FxTreeTableView<S> treeTable, String suffix, boolean system);

  /**
   * Loads the column sizes, visibility, view size and sorting from the preferences.
   *
   * @param treeTable the treetable
   * @param suffix the configuration suffix, null if none
   * @param system true if load from system prefs only, else user prefs first
   */
  void loadPreferences(FxTreeTableView<S> treeTable, String suffix, boolean system);

  /**
   * Returns whether the preferences include the column sorting.
   *
   * @return true if sorting is restored from the preferences (default is false)
   */
  boolean isSortingIncluded();

  /**
   * Sets whether the preferences include the column sorting.
   *
   * @param sortingIncluded true if sorting is restored from the preferences (default is false)
   */
  void setSortingIncluded(boolean sortingIncluded);

  /**
   * Returns whether the columns provide a filter UI in their headers.
   *
   * @return true if the columns can be filtered by the user (default is false)
   * @see org.tentackle.fx.table.filter.TableFilter
   */
  default boolean isFilterEnabled() {
    return false;
  }

  /**
   * Sets whether the columns provide a filter UI in their headers.<br>
   * Must be set before the view is configured. To turn the feature on or off after the view has been
   * configured, use {@link FxTableView#setFilterEnabled(boolean)} respectively
   * {@link FxTreeTableView#setFilterEnabled(boolean)}.
   *
   * @param filterEnabled true if the columns can be filtered by the user (default is false)
   */
  default void setFilterEnabled(boolean filterEnabled) {
    // optional feature
  }

  /**
   * Installs a fresh column filter support on a configured table view.<br>
   * Any support currently installed on the view is uninstalled first. Invoked by
   * {@link FxTableView#setFilterEnabled(boolean)} to enable the feature after configuration.
   *
   * @param table the table view, already configured with this configuration
   * @return the installed filter support, null if filtering is not supported
   */
  default TableFilterSupport<S> installFilterSupport(FxTableView<S> table) {
    return null;    // optional feature
  }

  /**
   * Installs a fresh column filter support on a configured treetable view.<br>
   * Any support currently installed on the view is uninstalled first. Invoked by
   * {@link FxTreeTableView#setFilterEnabled(boolean)} to enable the feature after configuration.
   *
   * @param treeTable the treetable view, already configured with this configuration
   * @return the installed filter support, null if filtering is not supported
   */
  default TableFilterSupport<S> installFilterSupport(FxTreeTableView<S> treeTable) {
    return null;    // optional feature
  }

  /**
   * Returns whether the preferences include the column filters.
   *
   * @return true if the filters are restored from the preferences (default is false)
   */
  default boolean isFilterIncluded() {
    return false;
  }

  /**
   * Sets whether the preferences include the column filters.
   *
   * @param filterIncluded true if the filters are restored from the preferences (default is false)
   */
  default void setFilterIncluded(boolean filterIncluded) {
    // optional feature
  }

  /**
   * Returns whether the preferences include the view size.
   *
   * @return true if the preferred size is restored from the preferences (default is true)
   */
  boolean isViewSizeIncluded();

  /**
   * Sets whether the preferences include the view size.
   *
   * @param viewSizeIncluded true if the preferred size is restored from the preferences (default is true)
   */
  void setViewSizeIncluded(boolean viewSizeIncluded);

  /**
   * Gets the type of binding.
   *
   * @return the binding type
   */
  BINDING getBindingType();

  /**
   * Sets the type of binding.
   *
   * @param bindingType the binding type
   */
  void setBindingType(BINDING bindingType);

  /**
   * Gets the table binder.<br>
   * Don't mix up the table's binder with the FxController's binder!
   * The table binder is responsible to bind the cells to the model, whereas the
   * controller binder binds the data list to the table.<br>
   * As a consequence, binding properties such as the DomainContext, if necessary for cell editors,
   * must be applied to the table binder!
   *
   * @return the binder
   */
  FxTableBinder<S> getBinder();

  /**
   * Returns the edit mode.
   *
   * @return the edit mode, never null, default is NO
   */
  EDITMODE getEditMode();

  /**
   * Sets the edit mode.
   *
   * @param editMode the edit mode, null is equivalent to NO
   */
  void setEditMode(EDITMODE editMode);

  /**
   * Configures the table.
   *
   * @param table the table view
   */
  void configure(FxTableView<S> table);

  /**
   * Configures the treetable.
   *
   * @param treeTable the treetable view
   */
  void configure(FxTreeTableView<S> treeTable);

  /**
   * Gets the cell type according to the item type.
   *
   * @param <T> the item type
   * @param type the item class
   * @return the cell type, never null
   */
  <T> TableCellType<T> getTableCellType(Class<T> type);

  /**
   * Creates a tree item for a treetable.
   *
   * @param object the object to wrap
   * @return the tree item
   */
  TreeItem<S> createTreeItem(S object);

  /**
   * Gets the optional resource bundle name.<br>
   * Used for BundleMonkey navigation only.
   *
   * @return the bundle name, null if unknown
   */
  String getBaseBundleName();

  /**
   * Sets the optional resource bundle name.
   *
   * @param bundleName the bundle name, null if none
   */
  void setBaseBundleName(String bundleName);

}

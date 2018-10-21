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

import org.tentackle.fx.bind.FxTableBinder;
import org.tentackle.fx.component.FxTableView;

import java.util.Collection;

/**
 * Configuration for a table view.<br>
 * The main purpose is to provide a single object to configure the table view.
 *
 * @param <S> the type of the objects contained within the table's items list
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

    /** bindable inherited. */
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
   * @return the config, null if no such name and displayed name
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
   * @return the created configuration
   */
  TableColumnConfiguration<S, ?> addColumn(String name, String displayedName);

  /**
   * Adds a column configuration.
   *
   * @param columnConfiguration the config
   */
  void addColumnConfiguration(TableColumnConfiguration<S, ?> columnConfiguration);

  /**
   * Saves the column sizes, visability, view size and sorting to the preferences.
   *
   * @param table the table
   * @param suffix the configuration suffix, null if none
   * @param system true if save to system prefs, else user prefs
   */
  void savePreferences(FxTableView<S> table, String suffix, boolean system);

  /**
   * Loads the column sizes, visability, view size and sorting from the preferences.
   *
   * @param table the table
   * @param suffix the configuration suffix, null if none
   * @param system true if load from system prefs only, else user prefs first
   * @param noViewSize true if don't set the table view's size
   */
  void loadPreferences(FxTableView<S> table, String suffix, boolean system, boolean noViewSize);

  /**
   * Returns whether the preferences include the column sorting.
   *
   * @return true if included (default is false)
   */
  boolean isSortingIncluded();

  /**
   * Sets whether the preferences include the column sorting.
   *
   * @param sortingIncluded true if included (default is false)
   */
  void setSortingIncluded(boolean sortingIncluded);

  /**
   * Gets the type of binding.
   *
   * @return the binding type
   */
  BINDING getBindingType();

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
   * Gets the cell type according to the item type.
   *
   * @param <T> the item type
   * @param type the item class
   * @return the cell type, never null
   */
  <T> TableCellType<T> getCellType(Class<T> type);

}

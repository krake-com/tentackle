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

import java.util.List;
import javafx.scene.control.TableColumn;
import org.tentackle.fx.bind.FxTableBinder;
import org.tentackle.fx.component.FxTableView;

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
  List<TableColumnConfiguration<S,?>> getColumnConfigurations();

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
   * Adds a column.
   *
   * @param name the column name
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
   * Gets the type of binding.
   *
   * @return the binding type
   */
  BINDING getBindingType();

  /**
   * Gets the table binder.
   *
   * @return the binder
   */
  FxTableBinder<S> getBinder();

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

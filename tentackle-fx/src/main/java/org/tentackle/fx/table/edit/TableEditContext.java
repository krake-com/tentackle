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

package org.tentackle.fx.table.edit;

import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;

import java.util.List;

/**
 * The table as seen by a {@link TableEditHandler}.<br>
 * Provides access to the rows and columns, modifies the rows, and navigates between the cells,
 * regardless of whether the view is a table or a treetable (see {@link TreeTableEditContext} for the
 * operations specific to trees).
 * <p>
 * Rows are addressed by their row objects, which are compared by identity. The <em>displayed</em> rows are the rows
 * the user can scroll to: the sorted and filtered items of a table, respectively the items of a treetable
 * whose parents are expanded.
 * <p>
 * The context is also available to the application via
 * {@link org.tentackle.fx.component.FxTableView#getEditContext()} and
 * {@link org.tentackle.fx.component.FxTreeTableView#getEditContext()}, for example, to add a row
 * and start editing its first cell from a button.
 *
 * @param <S> type of the objects contained within the table's item list
 * @author harald
 */
public interface TableEditContext<S> {

  /**
   * Gets the table configuration.
   *
   * @return the configuration
   */
  TableConfiguration<S> getConfiguration();

  /**
   * Gets the edit handler.
   *
   * @return the edit handler of the configuration, null if none
   */
  TableEditHandler<S> getEditHandler();

  /**
   * Gets the visible columns in the order they are displayed.
   *
   * @return the column configurations
   */
  List<TableColumnConfiguration<S, ?>> getColumns();

  /**
   * Gets the reference to a cell.
   *
   * @param row the row object
   * @param columnName the name of the column
   * @return the cell
   * @throws org.tentackle.fx.FxRuntimeException if there is no such column
   */
  TableCellRef<S> cell(S row, String columnName);


  // ---------------------- rows --------------------------

  /**
   * Gets the number of displayed rows.
   *
   * @return the number of rows
   */
  int getRowCount();

  /**
   * Gets a displayed row.
   *
   * @param index the row index
   * @return the row object
   */
  S getRow(int index);

  /**
   * Gets the displayed rows.
   *
   * @return the unmodifiable list of row objects, in display order
   */
  List<S> getRows();

  /**
   * Gets the index of a displayed row.
   *
   * @param row the row object
   * @return the row index, -1 if the row is not displayed
   */
  int indexOf(S row);

  /**
   * Appends a row.<br>
   * In a sorted table, the row is displayed according to the sort order.
   * In a treetable, the row is appended to the children of the root.
   *
   * @param row the row object
   */
  void addRow(S row);

  /**
   * Inserts a row after another row.<br>
   * In a sorted table, the row is displayed according to the sort order.
   * In a treetable, the row becomes the next sibling.
   *
   * @param sibling the existing row
   * @param row the row object to insert
   * @throws org.tentackle.fx.FxRuntimeException if there is no such sibling
   */
  void insertRowAfter(S sibling, S row);

  /**
   * Inserts a row before another row.<br>
   * In a sorted table, the row is displayed according to the sort order.
   * In a treetable, the row becomes the previous sibling.
   *
   * @param sibling the existing row
   * @param row the row object to insert
   * @throws org.tentackle.fx.FxRuntimeException if there is no such sibling
   */
  void insertRowBefore(S sibling, S row);

  /**
   * Removes a row.<br>
   * In a treetable, the row is removed along with its children.
   *
   * @param row the row object
   * @return true if removed, false if there is no such row
   */
  boolean removeRow(S row);


  // ---------------------- navigation --------------------------

  /**
   * Returns whether a cell is editable.<br>
   * The column must be editable and the edit handler must not exclude the cell.
   *
   * @param cell the cell
   * @return true if editable
   * @see TableEditHandler#isCellEditable(TableEditContext, TableCellRef)
   */
  boolean isEditable(TableCellRef<S> cell);

  /**
   * Gets the next editable cell in the same row.
   *
   * @param cell the cell
   * @return the next cell, null if there is no more editable cell in the row
   */
  TableCellRef<S> next(TableCellRef<S> cell);

  /**
   * Gets the previous editable cell in the same row.
   *
   * @param cell the cell
   * @return the previous cell, null if there is no more editable cell in the row
   */
  TableCellRef<S> previous(TableCellRef<S> cell);

  /**
   * Gets the next editable cell below in the same column.
   *
   * @param cell the cell
   * @return the cell below, null if there is no more editable cell in the column
   */
  TableCellRef<S> below(TableCellRef<S> cell);

  /**
   * Gets the next editable cell above in the same column.
   *
   * @param cell the cell
   * @return the cell above, null if there is no more editable cell in the column
   */
  TableCellRef<S> above(TableCellRef<S> cell);

  /**
   * Gets the first editable cell of a row.
   *
   * @param row the row object
   * @return the cell, null if the row contains no editable cell
   */
  TableCellRef<S> firstEditable(S row);

  /**
   * Gets the last editable cell of a row.
   *
   * @param row the row object
   * @return the cell, null if the row contains no editable cell
   */
  TableCellRef<S> lastEditable(S row);

  /**
   * Gets the first editable cell of the next displayed rows.
   *
   * @param row the row object
   * @return the cell, null if there is no editable cell in any of the following rows
   */
  TableCellRef<S> firstEditableBelow(S row);

  /**
   * Gets the last editable cell of the previous displayed rows.
   *
   * @param row the row object
   * @return the cell, null if there is no editable cell in any of the preceding rows
   */
  TableCellRef<S> lastEditableAbove(S row);


  // ---------------------- actions --------------------------

  /**
   * Selects a cell and starts editing it.<br>
   * The row is scrolled into view if necessary. In a treetable, the parents of the row are expanded.
   * Does nothing if the row is not displayed, for example, because it is hidden by a column filter.
   *
   * @param cell the cell, null to do nothing
   */
  void edit(TableCellRef<S> cell);

  /**
   * Selects a cell.<br>
   * Does nothing if the row is not displayed.
   *
   * @param cell the cell, null to do nothing
   */
  void select(TableCellRef<S> cell);

  /**
   * Refreshes the view to show the current values of the row objects.<br>
   * Invoked automatically after the methods of the {@link TableEditHandler} returned.
   */
  void refresh();

}

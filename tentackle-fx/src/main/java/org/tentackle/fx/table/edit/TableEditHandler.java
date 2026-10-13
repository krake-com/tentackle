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

/**
 * Controls the editing of a table or treetable.<br>
 * The handler is associated with the view by its
 * {@link org.tentackle.fx.table.TableConfiguration#setEditHandler(TableEditHandler) table configuration}.
 * Whenever a value is entered in a cell, the handler decides whether the value is accepted, applies whatever
 * follows from it — modify other cells, add, or remove rows — and determines the cell to edit next.
 * <p>
 * Cell values are modified directly in the row objects, the view is refreshed afterward. Rows are added,
 * removed, and navigated via the {@link TableEditContext}, which works the same way for tables and tree-tables.
 * <p>
 * See {@link org.tentackle.fx.FxFactory} for the handlers covering the common use cases and {@link RuleBasedEditHandler}
 * for a handler that is configured by lambdas per column.
 *
 * @param <S> type of the objects contained within the table's item list
 * @author harald
 */
public interface TableEditHandler<S> {

  /**
   * Returns whether a cell is editable.<br>
   * Applies in addition to the column's {@link org.tentackle.fx.table.TableColumnConfiguration#isEditable() editable}
   * property and is invoked only for cells of editable columns. Cells that are not editable are skipped
   * by the navigation methods of the context.
   *
   * @param context the edit context
   * @param cell the cell
   * @return true if editable (default)
   */
  default boolean isCellEditable(TableEditContext<S> context, TableCellRef<S> cell) {
    return true;
  }

  /**
   * Returns whether the value of a cell is committed if its editor loses the focus.<br>
   * Otherwise, the editor remains open until the edit is canceled, for example, by selecting another cell.
   * Determined when the editing of the cell starts.
   * <p>
   * A value that fails to {@link #verify verify} cannot be committed when the focus is lost:
   * the edit is canceled instead.
   *
   * @param context the edit context
   * @param cell the cell
   * @return true to commit, false to keep the default behavior (default)
   */
  default boolean isCommitOnFocusLost(TableEditContext<S> context, TableCellRef<S> cell) {
    return false;
  }

  /**
   * Verifies an entered value before it is applied to the model.<br>
   * If the value is rejected, the model remains untouched, the editor stays open and shows the message as its error.
   *
   * @param context the edit context
   * @param edit the entered value
   * @return the error message, null if the value is accepted (default)
   */
  default String verify(TableEditContext<S> context, TableCellEdit<S, ?> edit) {
    return null;
  }

  /**
   * Invoked after an entered value has been applied to the model.<br>
   * This is the place to modify other cells and to add or remove rows.
   * The view is refreshed when the method returns.
   * <p>
   * The returned cell is ignored if the value was committed because of a
   * {@link TableEditTrigger#FOCUS_LOST lost focus}, since the user already moved on.
   *
   * @param context the edit context
   * @param edit the entered value
   * @return the cell to edit next, null to end editing
   */
  TableCellRef<S> committed(TableEditContext<S> context, TableCellEdit<S, ?> edit);

  /**
   * Invoked after the editing of a cell was canceled.<br>
   * The model remains unchanged. The view is refreshed when the method returns.
   *
   * @param context the edit context
   * @param cell the cell
   * @return the cell to edit next, null to end editing (default)
   */
  default TableCellRef<S> canceled(TableEditContext<S> context, TableCellRef<S> cell) {
    return null;
  }

}

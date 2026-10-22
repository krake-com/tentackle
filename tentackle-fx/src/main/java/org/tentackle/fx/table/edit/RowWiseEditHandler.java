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

import java.util.function.Supplier;

/**
 * Edit-handler that continues with the next or previous editable cell in the row.
 * <p>
 * By default, the handler is <em>wrapping</em>: at the end of a row, it continues with the first editable cell of
 * the next row, and at the beginning of a row with the last editable cell of the previous row. At either
 * end of the table, the cell remains in edit mode. Without wrapping, editing ends at either end of the row.
 * <p>
 * With a {@link #setRowFactory row factory}, leaving the last cell of the last row appends a new row
 * and continues with its first editable cell.
 *
 * @param <S> type of the objects contained within the table's item list
 * @author harald
 */
public class RowWiseEditHandler<S> implements TableEditHandler<S> {

  private boolean wrapping = true;
  private Supplier<? extends S> rowFactory;

  /**
   * Creates the handler.
   */
  public RowWiseEditHandler() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Returns whether editing continues in the next or previous row.
   *
   * @return true if wrapping (default)
   */
  public boolean isWrapping() {
    return wrapping;
  }

  /**
   * Sets whether editing continues in the next or previous row.
   *
   * @param wrapping true if wrapping (default), false to end editing at either end of the row
   * @return this handler
   */
  public RowWiseEditHandler<S> setWrapping(boolean wrapping) {
    this.wrapping = wrapping;
    return this;
  }

  /**
   * Gets the factory for new rows.
   *
   * @return the factory, null if no rows are appended (default)
   */
  public Supplier<? extends S> getRowFactory() {
    return rowFactory;
  }

  /**
   * Sets the factory for new rows.<br>
   * The factory is invoked when the last editable cell of the last row is left.
   * It may return null to append no row.
   *
   * @param rowFactory the factory, null if no rows are appended (default)
   * @return this handler
   */
  public RowWiseEditHandler<S> setRowFactory(Supplier<? extends S> rowFactory) {
    this.rowFactory = rowFactory;
    return this;
  }

  @Override
  public TableCellRef<S> committed(TableEditContext<S> context, TableCellEdit<S,?> edit) {
    return switch (edit.trigger()) {
      case NEXT -> next(context, edit.cell());
      case PREVIOUS -> previous(context, edit.cell());
      case FOCUS_LOST -> null;
    };
  }

  /**
   * Determines the cell following the given cell.
   *
   * @param context the edit context
   * @param cell the cell
   * @return the cell to edit next, null to end editing
   */
  protected TableCellRef<S> next(TableEditContext<S> context, TableCellRef<S> cell) {
    TableCellRef<S> next = context.next(cell);
    if (next == null) {
      boolean lastRow = context.indexOf(cell.row()) == context.getRowCount() - 1;
      if (wrapping && !lastRow) {
        next = context.firstEditableBelow(cell.row());
      }
      if (next == null && rowFactory != null && (lastRow || wrapping)) {
        S row = rowFactory.get();
        if (row != null) {
          context.addRow(row);
          next = context.firstEditable(row);
        }
      }
      if (next == null && wrapping) {
        next = cell;    // end of table
      }
    }
    return next;
  }

  /**
   * Determines the cell preceding the given cell.
   *
   * @param context the edit context
   * @param cell the cell
   * @return the cell to edit next, null to end editing
   */
  protected TableCellRef<S> previous(TableEditContext<S> context, TableCellRef<S> cell) {
    TableCellRef<S> previous = context.previous(cell);
    if (previous == null && wrapping) {
      previous = context.lastEditableAbove(cell.row());
      if (previous == null) {
        previous = cell;    // beginning of table
      }
    }
    return previous;
  }

}

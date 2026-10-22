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
 * Edit-handler that continues with the editable cell below or above in the same column.<br>
 * At either end of the column, the cell remains in edit mode.
 *
 * @param <S> type of the objects contained within the table's item list
 * @author harald
 */
public class ColumnWiseEditHandler<S> implements TableEditHandler<S> {

  /**
   * Creates the handler.
   */
  public ColumnWiseEditHandler() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public TableCellRef<S> committed(TableEditContext<S> context, TableCellEdit<S, ?> edit) {
    TableCellRef<S> cell = edit.cell();
    TableCellRef<S> next = switch (edit.trigger()) {
      case NEXT -> context.below(cell);
      case PREVIOUS -> context.above(cell);
      case FOCUS_LOST -> null;
    };
    return next == null && edit.trigger() != TableEditTrigger.FOCUS_LOST ? cell : next;
  }

}

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

import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;

import java.util.AbstractList;
import java.util.List;

/**
 * Base implementation of a {@link TableEditContext}.<br>
 * Implements the navigation on top of the rows and columns provided by the concrete context.
 * It does not refer to any JavaFX control and can therefore be used without a toolkit.
 *
 * @param <S> type of the objects contained within the table's item list
 * @author harald
 */
public abstract class AbstractTableEditContext<S> implements TableEditContext<S> {

  /**
   * Parent constructor.
   */
  public AbstractTableEditContext() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public TableEditHandler<S> getEditHandler() {
    TableConfiguration<S> configuration = getConfiguration();
    return configuration == null ? null : configuration.getEditHandler();
  }

  @Override
  public TableCellRef<S> cell(S row, String columnName) {
    TableConfiguration<S> configuration = getConfiguration();
    TableColumnConfiguration<S, ?> column = configuration == null ? null : configuration.getColumnConfiguration(columnName);
    if (column == null) {
      throw new FxRuntimeException("no such column '" + columnName + "'");
    }
    return new TableCellRef<>(row, column);
  }

  @Override
  public List<S> getRows() {
    return new AbstractList<>() {
      @Override
      public S get(int index) {
        return getRow(index);
      }

      @Override
      public int size() {
        return getRowCount();
      }
    };
  }

  @Override
  public int indexOf(S row) {
    int count = getRowCount();
    for (int index = 0; index < count; index++) {
      if (getRow(index) == row) {
        return index;
      }
    }
    return -1;
  }

  @Override
  public boolean isEditable(TableCellRef<S> cell) {
    if (cell == null || !cell.column().isEditable()) {
      return false;
    }
    TableEditHandler<S> handler = getEditHandler();
    return handler == null || handler.isCellEditable(this, cell);
  }

  @Override
  public TableCellRef<S> next(TableCellRef<S> cell) {
    List<TableColumnConfiguration<S, ?>> columns = getColumns();
    return findInRow(cell.row(), columns, columns.indexOf(cell.column()) + 1, 1);
  }

  @Override
  public TableCellRef<S> previous(TableCellRef<S> cell) {
    List<TableColumnConfiguration<S, ?>> columns = getColumns();
    int index = columns.indexOf(cell.column());
    return findInRow(cell.row(), columns, index < 0 ? columns.size() - 1 : index - 1, -1);
  }

  @Override
  public TableCellRef<S> below(TableCellRef<S> cell) {
    return findInColumn(cell, 1);
  }

  @Override
  public TableCellRef<S> above(TableCellRef<S> cell) {
    return findInColumn(cell, -1);
  }

  @Override
  public TableCellRef<S> firstEditable(S row) {
    return findInRow(row, getColumns(), 0, 1);
  }

  @Override
  public TableCellRef<S> lastEditable(S row) {
    List<TableColumnConfiguration<S, ?>> columns = getColumns();
    return findInRow(row, columns, columns.size() - 1, -1);
  }

  @Override
  public TableCellRef<S> firstEditableBelow(S row) {
    int index = indexOf(row);
    if (index >= 0) {
      int count = getRowCount();
      while (++index < count) {
        TableCellRef<S> cell = firstEditable(getRow(index));
        if (cell != null) {
          return cell;
        }
      }
    }
    return null;
  }

  @Override
  public TableCellRef<S> lastEditableAbove(S row) {
    int index = indexOf(row);
    while (--index >= 0) {
      TableCellRef<S> cell = lastEditable(getRow(index));
      if (cell != null) {
        return cell;
      }
    }
    return null;
  }


  /**
   * Finds the first editable cell in a row.
   *
   * @param row     the row object
   * @param columns the visible columns
   * @param start   the index of the first column to check
   * @param step    the direction: 1 to move to the end of the row, -1 to the beginning
   * @return the cell, null if none
   */
  private TableCellRef<S> findInRow(S row, List<TableColumnConfiguration<S, ?>> columns, int start, int step) {
    for (int index = start; index >= 0 && index < columns.size(); index += step) {
      TableCellRef<S> cell = new TableCellRef<>(row, columns.get(index));
      if (isEditable(cell)) {
        return cell;
      }
    }
    return null;
  }

  /**
   * Finds the next editable cell in a column.
   *
   * @param cell the cell to start at, excluded
   * @param step the direction: 1 to move down, -1 to move up
   * @return the cell, null if none
   */
  private TableCellRef<S> findInColumn(TableCellRef<S> cell, int step) {
    int index = indexOf(cell.row());
    if (index >= 0) {
      int count = getRowCount();
      for (index += step; index >= 0 && index < count; index += step) {
        TableCellRef<S> other = new TableCellRef<>(getRow(index), cell.column());
        if (isEditable(other)) {
          return other;
        }
      }
    }
    return null;
  }

}

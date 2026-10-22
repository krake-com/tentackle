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
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import java.util.ArrayList;
import java.util.List;

/**
 * The edit context of an {@link FxTableView}.<br>
 * Rows are added to and removed from the root source of the table's items, since the items themselves are
 * immutable if the table is sorted or filtered.
 *
 * @param <S> type of the objects contained within the table's item list
 * @author harald
 */
public class FxTableEditContext<S> extends AbstractTableEditContext<S> {

  private final FxTableView<S> table;

  /**
   * Creates the edit context.
   *
   * @param table the table view
   */
  public FxTableEditContext(FxTableView<S> table) {
    this.table = table;
  }

  /**
   * Gets the table view.
   *
   * @return the table view
   */
  public FxTableView<S> getTable() {
    return table;
  }

  @Override
  public TableConfiguration<S> getConfiguration() {
    return table.getConfiguration();
  }

  @Override
  public List<TableColumnConfiguration<S, ?>> getColumns() {
    List<TableColumnConfiguration<S, ?>> columns = new ArrayList<>();
    TableConfiguration<S> configuration = getConfiguration();
    if (configuration != null) {
      for (TableColumn<S, ?> column : table.getVisibleLeafColumns()) {
        TableColumnConfiguration<S, ?> columnConfiguration = configuration.getColumnConfiguration(column);
        if (columnConfiguration != null) {
          columns.add(columnConfiguration);
        }
      }
    }
    return columns;
  }

  @Override
  public int getRowCount() {
    ObservableList<S> items = table.getItems();
    return items == null ? 0 : items.size();
  }

  @Override
  public S getRow(int index) {
    return table.getItems().get(index);
  }

  @Override
  public void addRow(S row) {
    getSource().add(row);
  }

  @Override
  public void insertRowAfter(S sibling, S row) {
    ObservableList<S> source = getSource();
    source.add(indexOf(source, sibling) + 1, row);
  }

  @Override
  public void insertRowBefore(S sibling, S row) {
    ObservableList<S> source = getSource();
    source.add(indexOf(source, sibling), row);
  }

  @Override
  public boolean removeRow(S row) {
    ObservableList<S> source = getSource();
    for (int index = 0; index < source.size(); index++) {
      if (source.get(index) == row) {
        source.remove(index);
        return true;
      }
    }
    return false;
  }

  @Override
  public void edit(TableCellRef<S> cell) {
    if (cell != null) {
      select(cell);
      // the cells must have been laid out, especially if rows were added
      Platform.runLater(() -> {
        int index = indexOf(cell.row());
        if (index >= 0) {
          scrollTo(index);
          table.layout();
          table.edit(index, cell.column().getTableColumn());
        }
      });
    }
  }

  @Override
  public void select(TableCellRef<S> cell) {
    if (cell != null) {
      int index = indexOf(cell.row());
      if (index >= 0) {
        table.getSelectionModel().clearAndSelect(index, cell.column().getTableColumn());
        table.requestFocus();
      }
    }
  }

  @Override
  public void refresh() {
    table.refresh();
  }


  /**
   * Scrolls a row into view, unless it is already visible.
   *
   * @param index the row index
   */
  protected void scrollTo(int index) {
    if (!FxUtilities.getInstance().isRowVisible(table, index)) {
      table.scrollToCentered(index);
    }
  }

  /**
   * Gets the modifiable list of all rows.
   *
   * @return the root source of the table's items
   */
  protected ObservableList<S> getSource() {
    ObservableList<S> items = table.getItems();
    if (items == null) {
      throw new FxRuntimeException("table has no items");
    }
    return FxUtilities.getInstance().getRootSource(items);
  }


  private int indexOf(ObservableList<S> source, S row) {
    for (int index = 0; index < source.size(); index++) {
      if (source.get(index) == row) {
        return index;
      }
    }
    throw new FxRuntimeException("no such row: " + row);
  }

}

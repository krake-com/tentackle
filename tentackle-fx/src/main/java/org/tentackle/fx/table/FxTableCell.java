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

import javafx.scene.Node;
import javafx.scene.control.TableCell;

/**
 * Extended table cell.
 *
 * @param <S> type of the objects contained within the table's items list
 * @param <T> type of the content in all cells in this column
 * @author harald
 */
public class FxTableCell<S,T> extends TableCell<S,T> {

  /**
   * The column configuration.
   */
  private final TableColumnConfiguration<S,T> columnConfiguration;

  /**
   * Creates a table cell.
   *
   * @param columnConfiguration the column configuration
   */
  public FxTableCell(TableColumnConfiguration<S,T> columnConfiguration) {
    this.columnConfiguration = columnConfiguration;
  }


  /**
   * Gets the column configuration.
   *
   * @return the configuration
   */
  public TableColumnConfiguration<S, T> getColumnConfiguration() {
    return columnConfiguration;
  }


  @Override
  protected void updateItem(T item, boolean empty) {
    if (item == getItem()) {
      return;
    }
    super.updateItem(item, empty);

    if (item == null) {
      super.setText(null);
      super.setGraphic(null);
    }
    else if (item instanceof Node) {
      super.setText(null);
      super.setGraphic((Node) item);
    }
    else {
      getCellType().updateItem(this, item);
    }
  }


  /**
   * Gets the table cell type.
   *
   * @return the cell type, never null
   */
  protected TableCellType<T> getCellType() {
    return columnConfiguration.getTableConfiguration().getCellType(columnConfiguration.getType());
  }

}

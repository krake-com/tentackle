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

package org.tentackle.fx.table.type;

import javafx.geometry.Pos;

import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.fx.table.TableCellTypeService;
import org.tentackle.fx.table.TableColumnConfiguration;

import java.text.DecimalFormat;

/**
 * A number cell type.<br>
 * Provided as a fallback to application specific types that don't provide t
 *
 * @param <T> the item type
 * @author harald
 */
@TableCellTypeService(Number.class)
public class NumberTableCellType<T extends Number> extends AbstractTableCellType<T> {

  @Override
  public void updateItem(FxTableCell<?, T> tableCell, T item) {
    tableCell.setGraphic(null);
    DecimalFormat fmt = getFormat(tableCell.getColumnConfiguration(), item);
    String text;
    if (fmt != null) {
      text = fmt.format(item);
    }
    else {
      text = item.toString();
    }
    tableCell.setText(text);
    updateAlignment(tableCell, Pos.CENTER_RIGHT);
  }

  @Override
  public void updateItem(FxTreeTableCell<?, T> treeTableCell, T item) {
    treeTableCell.setGraphic(null);
    DecimalFormat fmt = getFormat(treeTableCell.getColumnConfiguration(), item);
    String text;
    if (fmt != null) {
      text = fmt.format(item);
    }
    else {
      text = item.toString();
    }
    treeTableCell.setText(text);
    updateAlignment(treeTableCell, Pos.CENTER_RIGHT);
  }

  /**
   * Gets the number format.
   *
   * @param config the column configuration
   * @param item the number
   * @return the format, null if default toString()
   */
  protected DecimalFormat getFormat(TableColumnConfiguration<?, T> config, T item) {
    return config.getNumberFormat();
  }
}

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

import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.fx.table.TableCellTypeService;
import org.tentackle.fx.table.TableColumnConfiguration;

import javafx.geometry.Pos;
import java.text.DecimalFormat;

/**
 * The {@link Number} cell type.<br>
 * Applies to all numeric types and serves as a fallback for application-specific
 * number types that don't provide a cell type of their own.
 *
 * @param <T> the item type
 * @author harald
 */
@TableCellTypeService(Number.class)
public class NumberTableCellType<T extends Number> extends AbstractTableCellType<T> {

  /**
   * Creates the cell type for {@link Number}.
   */
  public NumberTableCellType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getText(TableColumnConfiguration<?, T> columnConfiguration, T item) {
    DecimalFormat fmt = getFormat(columnConfiguration, item);
    return fmt == null ? item.toString() : fmt.format(item);
  }

  @Override
  public void updateItem(FxTableCell<?, T> tableCell, T item) {
    tableCell.setGraphic(null);
    tableCell.setText(getText(tableCell.getColumnConfiguration(), item));
    updateAlignment(tableCell, Pos.BASELINE_RIGHT);
  }

  @Override
  public void updateItem(FxTreeTableCell<?, T> treeTableCell, T item) {
    treeTableCell.setGraphic(null);
    treeTableCell.setText(getText(treeTableCell.getColumnConfiguration(), item));
    updateAlignment(treeTableCell, Pos.BASELINE_RIGHT);
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

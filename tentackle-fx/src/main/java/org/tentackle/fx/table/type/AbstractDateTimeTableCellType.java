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
import org.tentackle.fx.table.TableColumnConfiguration;

import javafx.geometry.Pos;
import java.time.format.DateTimeFormatter;

/**
 * Base cell type for all date- and time-related types.<br>
 * Formats the items with the column's formatter or, if none, the default formatter for the type.
 *
 * @param <T> the date or time type
 * @author harald
 */
public abstract class AbstractDateTimeTableCellType<T> extends AbstractTableCellType<T> {

  /**
   * Parent constructor.
   */
  public AbstractDateTimeTableCellType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getText(TableColumnConfiguration<?, T> columnConfiguration, T item) {
    return formatItem(getFormatter(columnConfiguration), item);
  }

  @Override
  public void updateItem(FxTableCell<?, T> tableCell, T item) {
    tableCell.setGraphic(null);
    tableCell.setText(getText(tableCell.getColumnConfiguration(), item));
    updateAlignment(tableCell, Pos.BASELINE_CENTER);
  }

  @Override
  public void updateItem(FxTreeTableCell<?, T> treeTableCell, T item) {
    treeTableCell.setGraphic(null);
    treeTableCell.setText(getText(treeTableCell.getColumnConfiguration(), item));
    updateAlignment(treeTableCell, Pos.BASELINE_CENTER);
  }

  /**
   * Gets the formatter.
   *
   * @param config the table column configuration
   * @return the formatter, null if default according to type
   */
  protected DateTimeFormatter getFormatter(TableColumnConfiguration<?, T> config) {
    return config.getDateTimeFormatter();
  }

  /**
   * Formats the item.
   *
   * @param fmt the formatter
   * @param item the item
   * @return the formatted string
   */
  abstract protected String formatItem(DateTimeFormatter fmt, T item);

}

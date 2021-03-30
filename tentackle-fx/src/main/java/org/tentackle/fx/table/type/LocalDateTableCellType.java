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
import org.tentackle.misc.FormatHelper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * LocalDate cell type.
 *
 * @author harald
 */
@TableCellTypeService(LocalDate.class)
public class LocalDateTableCellType extends AbstractTableCellType<LocalDate> {

  @Override
  public void updateItem(FxTableCell<?, LocalDate> tableCell, LocalDate item) {
    tableCell.setGraphic(null);
    DateTimeFormatter fmt = tableCell.getColumnConfiguration().getDateTimeFormatter();
    if (fmt == null) {
      fmt = FormatHelper.getLocalDateFormatter();
    }
    String text = fmt.format(item);
    tableCell.setText(text);
    updateAlignment(tableCell, Pos.CENTER);
  }

  @Override
  public void updateItem(FxTreeTableCell<?, LocalDate> treeTableCell, LocalDate item) {
    treeTableCell.setGraphic(null);
    DateTimeFormatter fmt = treeTableCell.getColumnConfiguration().getDateTimeFormatter();
    if (fmt == null) {
      fmt = FormatHelper.getLocalDateFormatter();
    }
    String text = fmt.format(item);
    treeTableCell.setText(text);
    updateAlignment(treeTableCell, Pos.CENTER);
  }

}

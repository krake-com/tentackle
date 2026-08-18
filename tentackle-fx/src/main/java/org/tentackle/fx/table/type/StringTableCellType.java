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

/**
 * String cell type.
 *
 * @author harald
 */
@TableCellTypeService(String.class)
public class StringTableCellType extends AbstractTableCellType<String> {

  /**
   * Creates the cell type for {@link String}.
   */
  public StringTableCellType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getText(TableColumnConfiguration<?, String> columnConfiguration, String text) {
    return applyCaseConversion(columnConfiguration.getCaseConversion(), text);
  }

  @Override
  public void updateItem(FxTableCell<?, String> tableCell, String text) {
    tableCell.setText(getText(tableCell.getColumnConfiguration(), text));
    tableCell.setGraphic(null);
    updateAlignment(tableCell, Pos.BASELINE_LEFT);
  }

  @Override
  public void updateItem(FxTreeTableCell<?, String> treeTableCell, String text) {
    treeTableCell.setText(getText(treeTableCell.getColumnConfiguration(), text));
    treeTableCell.setGraphic(null);
    updateAlignment(treeTableCell, Pos.BASELINE_LEFT);
  }

}

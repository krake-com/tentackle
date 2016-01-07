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

package org.tentackle.fx.table.type;

import javafx.geometry.Pos;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.TableCellTypeService;

/**
 * The most simple cell type.<br>
 * Fallback if type is not known.
 *
 * @author harald
 * @param <T> the item type
 */
@TableCellTypeService(String.class)
public class StringTableCellType extends AbstractTableCellType<String> {

  @Override
  public void updateItem(FxTableCell<?, String> tableCell, String text) {
    Boolean caseConversion = tableCell.getColumnConfiguration().getCaseConversion();
    if (Boolean.TRUE.equals(caseConversion)) {
      text = text.toUpperCase();
    }
    else if (Boolean.FALSE.equals(caseConversion)) {
      text = text.toLowerCase();
    }
    tableCell.setText(text);
    tableCell.setGraphic(null);
    updateAlignment(tableCell, Pos.CENTER_LEFT);
  }

}

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
import javafx.scene.control.ComboBox;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.fx.table.TableCellTypeService;

/**
 * A boolean cell type.
 *
 * @author harald
 */
@TableCellTypeService(Enum.class)
public class EnumTableCellType extends AbstractTableCellType<Enum> {

  @Override
  public void updateItem(FxTableCell<?, Enum> tableCell, Enum item) {
    tableCell.setText(item.toString());
    tableCell.setGraphic(null);
    updateAlignment(tableCell, Pos.CENTER);
  }

  @Override
  public void updateItem(FxTreeTableCell<?, Enum> treeTableCell, Enum item) {
    treeTableCell.setText(item.toString());
    treeTableCell.setGraphic(null);
    updateAlignment(treeTableCell, Pos.CENTER);
  }

  @Override
  public FxComponent getEditor() {
    if (editor == null) {
      editor = Fx.createNode(ComboBox.class);
    }
    return editor;
  }

  @Override
  public Class<?> getEditorType() {
    return Enum.class;
  }

}

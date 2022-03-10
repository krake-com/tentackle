/*
 * Tentackle - https://tentackle.org
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
 */

package org.tentackle.maven.plugin.wizard.browser;

import javafx.geometry.Pos;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.fx.table.TableCellTypeService;
import org.tentackle.fx.table.type.AbstractTableCellType;


/**
 * Cell type to decorate the name field of a DataNode in a (tree)table.<br>
 * The name field is not bindable, but instead the node itself is rendered in place of the name field.
 */
@TableCellTypeService(DataNode.class)
public class DataNodeCellType extends AbstractTableCellType<DataNode> {

  @Override
  public void updateItem(FxTableCell<?, DataNode> tableCell, DataNode item) {
    tableCell.setText(getName(item));
    tableCell.setGraphic(item.createIcon());
    updateAlignment(tableCell, Pos.BASELINE_LEFT);
  }

  @Override
  public void updateItem(FxTreeTableCell<?, DataNode> treeTableCell, DataNode item) {
    treeTableCell.setText(getName(item));
    treeTableCell.setGraphic(item.createIcon());
    updateAlignment(treeTableCell, Pos.BASELINE_LEFT);
  }

  private String getName(DataNode item) {
    String name = item.getName();
    if (StringHelper.isAllWhitespace(name)) {
      name = StringHelper.firstToLower(item.getType());
    }
    return name;
  }

}

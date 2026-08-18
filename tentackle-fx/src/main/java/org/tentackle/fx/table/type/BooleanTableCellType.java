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
import javafx.scene.Node;
import javafx.scene.control.CheckBox;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.fx.table.TableCellTypeService;
import org.tentackle.fx.table.TableColumnConfiguration;

/**
 * A {@link Boolean} cell type.
 *
 * @author harald
 */
@TableCellTypeService(Boolean.class)
public class BooleanTableCellType extends AbstractTableCellType<Boolean> {

  /**
   * Creates the cell type for {@link Boolean}.
   */
  public BooleanTableCellType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * {@inheritDoc}
   * <p>
   * Although a boolean is rendered as a graphic only, a textual representation is provided
   * for the column filters and the exports.
   */
  @Override
  public String getText(TableColumnConfiguration<?, Boolean> columnConfiguration, Boolean item) {
    return FxFxBundle.getString(item ? "YES" : "NO");
  }

  @Override
  public Node getGraphic(TableColumnConfiguration<?, Boolean> columnConfiguration, Boolean item) {
    return Fx.createGraphic(item ? "checked" : "unchecked");
  }

  @Override
  public void updateItem(FxTableCell<?, Boolean> tableCell, Boolean item) {
    tableCell.setText(null);
    tableCell.setGraphic(getGraphic(tableCell.getColumnConfiguration(), item));
    updateAlignment(tableCell, Pos.BASELINE_CENTER);
  }

  @Override
  public void updateItem(FxTreeTableCell<?, Boolean> tableCell, Boolean item) {
    tableCell.setText(null);
    tableCell.setGraphic(getGraphic(tableCell.getColumnConfiguration(), item));
    updateAlignment(tableCell, Pos.BASELINE_CENTER);
  }

  @Override
  public FxComponent getEditor() {
    if (editor == null) {
      editor = Fx.create(CheckBox.class);
    }
    return editor;
  }

  @Override
  public Class<?> getEditorType() {
    return Boolean.class;
  }

}

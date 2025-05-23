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

package org.tentackle.maven.plugin.wizard.fx;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.Fx;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.fx.table.TableCellTypeService;
import org.tentackle.fx.table.type.AbstractTableCellType;
import org.tentackle.maven.plugin.wizard.pdodata.DataItem;
import org.tentackle.maven.plugin.wizard.pdodata.DataNode;
import org.tentackle.maven.plugin.wizard.pdodata.DataObject;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;

/**
 * Cell type to decorate the name field of a DataNode in a (tree)table.<br>
 * The name field is not bindable, but instead the node itself is rendered in place of the name field.
 */
@TableCellTypeService(DataNode.class)
public class DataNodeCellType extends AbstractTableCellType<DataNode> {

  private static final String STYLE_IMPLICIT = "implicit-attribute";
  private static final String STYLE_HIDDEN = "hidden-component";
  private static final String STYLE_KEY = "key-attribute";

  @Override
  public void updateItem(FxTableCell<?, DataNode> tableCell, DataNode item) {
    tableCell.setText(getName(item));
    tableCell.setGraphic(getIcon(item));
    updateAlignment(tableCell, Pos.BASELINE_LEFT);
  }

  @Override
  public void updateItem(FxTreeTableCell<?, DataNode> treeTableCell, DataNode item) {
    treeTableCell.setText(getName(item));
    treeTableCell.setGraphic(getIcon(item));
    treeTableCell.setTooltip(item.getComment() == null ? null : new Tooltip(item.getComment()));
    updateAlignment(treeTableCell, Pos.BASELINE_LEFT);

    treeTableCell.getStyleClass().removeAll(STYLE_IMPLICIT, STYLE_HIDDEN, STYLE_KEY);
    if (item instanceof DataItem dataItem && (dataItem.getAttribute() == null || dataItem.getAttribute().isImplicit())) {
      // all implicit model attributes or attributes provided by the framework but not part of the model
      treeTableCell.getStyleClass().add(STYLE_IMPLICIT);
    }
    else if (item.isHidden()) {
      treeTableCell.getStyleClass().add(STYLE_HIDDEN);
    }

    if (item instanceof DataItem dataItem && dataItem.isKey()) {
      treeTableCell.getStyleClass().add(STYLE_KEY);
    }
  }

  private String getName(DataNode item) {
    String name = item.getName();
    if (StringHelper.isAllWhitespace(name)) {
      name = StringHelper.firstToLower(item.getType());
    }
    return name;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private Node getIcon(DataNode item) {
    if (item instanceof DataObject) {
      PersistentDomainObject pdo = ((DataObject) item).getPdo();
      if (pdo != null) {
        if (GuiProviderFactory.getInstance().isGuiProviderAvailable(pdo.getEffectiveClass())) {
          GuiProvider<?> guiProvider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
          if (guiProvider != null) {
            return guiProvider.createGraphic();
          }
        }
      }
    }
    return Fx.createGraphic(DataNodeGraphicProvider.REALM, item.getClass().getSimpleName());
  }

}

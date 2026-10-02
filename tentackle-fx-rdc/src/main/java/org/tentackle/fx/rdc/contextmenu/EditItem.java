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

package org.tentackle.fx.rdc.contextmenu;

import javafx.collections.ObservableList;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.stage.Modality;

import org.tentackle.fx.Fx;
import org.tentackle.fx.rdc.PdoTreeCell;
import org.tentackle.fx.rdc.PdoTreeContextMenuItem;
import org.tentackle.fx.rdc.PdoTreeContextMenuItemService;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Tree menu item to edit a PDO.<br>
 * Opens the PDO in a modal CRUD dialog and replaces the tree item afterwards.
 * Disabled if the GUI provider does not allow editing.
 *
 * @author harald
 * @param <T> the PDO type
 */
@PdoTreeContextMenuItemService(value = PersistentDomainObject.class, ordinal = 150)
public class EditItem<T extends PersistentDomainObject<T>> extends MenuItem implements PdoTreeContextMenuItem<T> {

  /**
   * Creates a menu item.
   *
   * @param cell the tree cell
   */
  public EditItem(PdoTreeCell<T> cell) {
    setText(RdcFxRdcBundle.getString("EDIT"));
    setGraphic(Fx.createGraphic("edit"));
    T pdo = cell.getItem();
    if (cell.getPdoTreeItem().getGuiProvider().isEditAllowed()) {
      T pDo = pdo.reload();
      setOnAction(e -> Rdc.displayCrudStage(pDo, true, Modality.APPLICATION_MODAL, Fx.getStage(cell), updatedPdo -> {
        if (updatedPdo != null) {
          TreeItem<T> root = cell.getTreeItem().getParent();
          if (root != null) {
            ObservableList<TreeItem<T>> items = root.getChildren();
            int i = 0;
            for (TreeItem<T> item : items) {
              if (updatedPdo.equals(item.getValue())) {
                boolean expanded = item.isExpanded();
                TreeItem<T> reloadedItem = Rdc.createGuiProvider(updatedPdo).createTreeItem();
                items.set(i, reloadedItem);
                cell.getTreeView().refresh();
                reloadedItem.setExpanded(expanded);
                break;
              }
              i++;
            }
          }
        }
      }));
    }
    else {
      setDisable(true);
    }
  }

}

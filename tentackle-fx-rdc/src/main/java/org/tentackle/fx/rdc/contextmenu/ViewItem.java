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

import javafx.scene.control.MenuItem;

import org.tentackle.fx.Fx;
import org.tentackle.fx.rdc.PdoTableCell;
import org.tentackle.fx.rdc.PdoTableContextMenuItem;
import org.tentackle.fx.rdc.PdoTableContextMenuItemService;
import org.tentackle.fx.rdc.PdoTreeCell;
import org.tentackle.fx.rdc.PdoTreeContextMenuItem;
import org.tentackle.fx.rdc.PdoTreeContextMenuItemService;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Tree and table menu item to view a PDO.<br>
 * Opens the PDO in a non-modal, read-only CRUD dialog.
 * Disabled if the GUI provider does not allow viewing.
 *
 * @author harald
 * @param <T> the PDO type
 */
@PdoTreeContextMenuItemService(value = PersistentDomainObject.class, ordinal = 140)
@PdoTableContextMenuItemService(value = PersistentDomainObject.class, ordinal = 140)
public class ViewItem<T extends PersistentDomainObject<T>> extends MenuItem implements PdoTreeContextMenuItem<T>, PdoTableContextMenuItem<T> {

  /**
   * Creates a menu item.
   *
   * @param cell the tree cell
   */
  public ViewItem(PdoTreeCell<T> cell) {
    setText(RdcFxRdcBundle.getString("VIEW"));
    setGraphic(Fx.createGraphic("view"));
    T pdo = cell.getItem();
    if (cell.getPdoTreeItem().getGuiProvider().isViewAllowed()) {
      T pDo = pdo.isImmutable() ? pdo.reload() : pdo;   // from cache?
      setOnAction(e -> Rdc.displayCrudStage(pDo, false, Fx.getStage(cell)));
    }
    else {
      setDisable(true);
    }
  }

  /**
   * Creates a menu item.
   *
   * @param cell the table cell
   */
  public ViewItem(PdoTableCell<?,T> cell) {
    setText(RdcFxRdcBundle.getString("VIEW"));
    setGraphic(Fx.createGraphic("view"));
    T pdo = cell.getItem();
    if (pdo != null && Rdc.createGuiProvider(pdo).isViewAllowed()) {
      T pDo = pdo.isImmutable() ? pdo.reload() : pdo;   // from cache?
      setOnAction(e -> Rdc.displayCrudStage(pDo, false, Fx.getStage(cell)));
    }
    else {
      setDisable(true);
    }
  }
}

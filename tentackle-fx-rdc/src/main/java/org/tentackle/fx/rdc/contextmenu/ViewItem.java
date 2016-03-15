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

package org.tentackle.fx.rdc.contextmenu;

import javafx.scene.control.MenuItem;
import javafx.stage.Modality;
import org.tentackle.fx.Fx;
import org.tentackle.fx.rdc.PdoTreeCell;
import org.tentackle.fx.rdc.PdoTreeContextMenuItem;
import org.tentackle.fx.rdc.PdoTreeContextMenuItemService;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Menu item to view a PDO.
 *
 * @author harald
 * @param <T> the PDO type
 */
@PdoTreeContextMenuItemService(value = PersistentDomainObject.class, ordinal = 140)
public class ViewItem<T extends PersistentDomainObject<T>> extends MenuItem implements PdoTreeContextMenuItem<T> {

  /**
   * Creates a menu item.
   *
   * @param cell the tree cell
   */
  public ViewItem(PdoTreeCell<T> cell) {
    super(RdcFxRdcBundle.getString("VIEW"), Fx.createImageView("view"));
    T pdo = cell.getItem();
    if (cell.getPdoTreeItem().getGuiProvider().editorExists() && pdo.isViewAllowed()) {
      T pDo = pdo.isImmutable() ? pdo.reload() : pdo;   // from cache?
      setOnAction(e -> Rdc.displayCrudStage(pDo, false, Modality.NONE, Fx.getStage(cell)));
    }
    else {
      setDisable(true);
    }
  }

}

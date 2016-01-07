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

package org.tentackle.fx.rdc;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableCell;
import javafx.scene.input.MouseEvent;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * A PDO tree cell.
 *
 * @author harald
 * @param <S> the table row's type
 * @param <T> the table cell's pdo type
 */
public class PdoTableCell<S,T extends PersistentDomainObject<T>> extends TableCell<S,T> {

  private T pdo;
  private ContextMenu contextMenu;    // != null if menu shown

  /**
   * Creates a pdo tree cell.
   */
  public PdoTableCell() {
    addEventHandler(MouseEvent.ANY, (MouseEvent event) -> {
      if (event.isPopupTrigger()) {
        showContextMenu(event);
      }
    });
  }

  @Override
  public void updateItem(T pdo, boolean empty) {
    super.updateItem(pdo, empty);
    this.pdo = pdo;

    if (empty) {
      setText(null);
      setGraphic(null);
      this.pdo = null;
    }
    else {
      setText(pdo.toString());
    }
  }

  /**
   * Shows the popup context menu for the current PDO.
   *
   * @param event the mouse event
   */
  public void showContextMenu(MouseEvent event) {
    if (contextMenu != null) {
      contextMenu.hide();
    }
    contextMenu = PdoContextMenuFactory.getInstance().create(this);
    if (contextMenu != null) {
      event.consume();
      contextMenu.setOnHidden(e -> contextMenu = null);   // -> GC
      contextMenu.show(this, event.getScreenX(), event.getScreenY());
    }
  }

}

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
import javafx.scene.control.TreeCell;
import javafx.scene.input.MouseEvent;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * A PDO tree cell.
 *
 * @author harald
 * @param <T> the pdo type
 */
public class PdoTreeCell<T extends PersistentDomainObject<T>> extends TreeCell<T> {

  private GuiProvider<T> provider;
  private ContextMenu contextMenu;    // != null if menu shown

  /**
   * Creates a pdo tree cell.
   */
  public PdoTreeCell() {
    addEventHandler(MouseEvent.ANY, (MouseEvent event) -> {
      if (event.isPopupTrigger()) {
        showContextMenu(event);
      }
    });
  }

  @Override
  public void updateItem(T pdo, boolean empty) {
    super.updateItem(pdo, empty);

    if (empty) {
      setText(null);
      setGraphic(null);
      provider = null;
    }
    else {
      provider = pdo == null ? null : GuiProviderFactory.getInstance().createGuiProvider(pdo);
      if (provider == null) {
        setText(null);
        setGraphic(getTreeItem().getGraphic());
      }
      else {
        setText(provider.getTreeText());
        setGraphic(provider.createIcon());
      }
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


  /**
   * Gets the PDO tree item.
   *
   * @return the PDO tree item
   */
  public PdoTreeItem<T> getPdoTreeItem() {
    return (PdoTreeItem<T>) getTreeItem();    // getTreeItem is final :(
  }

}

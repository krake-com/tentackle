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

package org.tentackle.fx.rdc;

import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;

import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * A PDO table cell.<br>
 * Used for cells <em>containing</em> a PDO.<br>
 * Displays the PDO's {@code toString()} and shows the PDO's context menu.
 *
 * @author harald
 * @param <S> the table row's type
 * @param <T> the table cell's PDO type
 */
public class PdoTableCell<S,T extends PersistentDomainObject<T>> extends FxTableCell<S,T> {

  private ContextMenu contextMenu;    // != null if menu shown

  /**
   * Creates a table cell.
   *
   * @param columnConfiguration the column configuration
   */
  public PdoTableCell(TableColumnConfiguration<S,T> columnConfiguration) {
    super(columnConfiguration);
    addEventHandler(MouseEvent.ANY, this::showContextMenu);
  }

  @Override
  public void updateItem(T pdo, boolean empty) {
    super.updateItem(pdo, empty);

    if (empty || pdo == null) {
      setText(null);
      setGraphic(null);
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
  private void showContextMenu(MouseEvent event) {
    if (event.isPopupTrigger()) {
      if (contextMenu != null) {
        contextMenu.hide();
      }
      if (getTableView().getContextMenu() == null) {
        contextMenu = PdoContextMenuFactory.getInstance().create(this);
        if (contextMenu != null) {
          event.consume();
          contextMenu.setOnHidden(e -> contextMenu = null);   // -> GC
          contextMenu.show(this, event.getScreenX(), event.getScreenY());
        }
      }
    }
  }

}

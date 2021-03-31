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

import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.input.MouseEvent;

import org.tentackle.common.StringHelper;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.Objects;

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

    setOnDragDetected(e -> {
      if (provider != null && provider.createDragboard(this) != null) {
        e.consume();
      }
    });

    setOnDragOver(e -> {
      if (e.getGestureSource() != this && provider != null && provider.isDragAccepted(e)) {
        e.consume();
      }
    });

    setOnDragDropped(e -> {
      if (provider != null) {
        provider.dropDragboard(e.getDragboard());
        e.setDropCompleted(true);
        T reloadedPdo = provider.getPdo().reload();
        TreeItem<T> root = getTreeItem().getParent();
        if (root != null) {
          ObservableList<TreeItem<T>> items = root.getChildren();
          int i=0;
          for (TreeItem<T> item: items) {
            if (reloadedPdo.equals(item.getValue())) {
              TreeItem<T> reloadedItem = Rdc.createGuiProvider(reloadedPdo).createTreeItem();
              items.set(i, reloadedItem);
              getTreeView().refresh();
              reloadedItem.setExpanded(true);
              break;
            }
            i++;
          }
        }
      }
      else {
        e.consume();
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
      provider = pdo == null ? null : Rdc.createGuiProvider(pdo);
      if (provider == null) {
        setText(null);
        setTooltip(null);
        setGraphic(getTreeItem().getGraphic());
      }
      else {
        PdoTreeItem<T> parentItem = getPdoTreeItem().getParentPdoItem();
        String treeText = provider.getTreeText(parentItem == null ? null : parentItem.getPdo());
        setText(treeText);
        String tooltipText = provider.getToolTipText(parentItem == null ? null : parentItem.getPdo());
        if (StringHelper.isAllWhitespace(tooltipText) || Objects.equals(treeText, tooltipText)) {
          tooltipText = null; // empty or same as treetext -> no tooltip (default)
        }
        setTooltip(tooltipText == null ? null : new Tooltip(tooltipText));
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
    if (getTreeView().getContextMenu() == null) {
      contextMenu = PdoContextMenuFactory.getInstance().create(this);
      if (contextMenu != null) {
        event.consume();
        contextMenu.setOnHidden(e -> contextMenu = null);   // -> GC
        contextMenu.show(this, event.getScreenX(), event.getScreenY());
      }
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

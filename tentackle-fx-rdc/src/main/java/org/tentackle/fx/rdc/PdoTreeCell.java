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

import org.tentackle.common.StringHelper;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A PDO tree cell.<br>
 * Displays the text, tooltip and graphic provided by the PDO's {@link GuiProvider}, shows the
 * PDO's context menu, and supports drag and drop.
 *
 * @author harald
 * @param <T> the PDO type
 */
public class PdoTreeCell<T extends PersistentDomainObject<T>> extends TreeCell<T> {

  private GuiProvider<T> provider;
  private ContextMenu contextMenu;    // != null if menu shown

  /**
   * Creates a PDO tree cell.
   */
  public PdoTreeCell() {
    addEventHandler(MouseEvent.ANY, this::showContextMenu);
    setOnDragDetected(this::handleDragDetected);
    setOnDragOver(this::handleDragOver);
    setOnDragDropped(this::handleDragDropped);
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
        setGraphic(null);
      }
      else {
        PdoTreeItem<T> parentItem = getPdoTreeItem().getParentPdoItem();
        String treeText = provider.getTreeText(parentItem == null ? null : parentItem.getPdo());
        setText(treeText);
        String tooltipText = provider.getToolTipText(parentItem == null ? null : parentItem.getPdo());
        if (StringHelper.isAllWhitespace(tooltipText) || Objects.equals(treeText, tooltipText)) {
          tooltipText = null; // empty or same as tree text -> no tooltip (default)
        }
        setTooltip(tooltipText == null ? null : new Tooltip(tooltipText));
        setGraphic(provider.createGraphic());
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

  /**
   * Gets the GUI provider.
   *
   * @return the provider, null if the item's value (the PDO) is null
   */
  protected GuiProvider<T> getGuiProvider() {
    return provider;
  }


  /**
   * Handles the drag detected mouse event.<br>
   * If PDOs are selected in the tree, all of them are dragged, else only the PDO of this cell.
   *
   * @param event the mouse event after drag has been detected
   */
  protected void handleDragDetected(MouseEvent event) {
    PersistentDomainObject<?>[] selectedPdos = null;    // != null if one or more PDOs selected in tree
    TreeView<T> treeView = getTreeView();
    if (treeView != null) {
      List<PersistentDomainObject<?>> pdoList = new ArrayList<>();
      for (TreeItem<T> selectedItem : treeView.getSelectionModel().getSelectedItems()) {
        if (selectedItem instanceof PdoTreeItem<?> pdoTreeItem) {
          PersistentDomainObject<?> pdo = pdoTreeItem.getPdo();
          if (pdo != null) {
            pdoList.add(pdo);
          }
        }
      }
      if (!pdoList.isEmpty()) {
        selectedPdos = pdoList.toArray(new PersistentDomainObject<?>[0]);
      }
    }

    if (selectedPdos != null && RdcUtilities.getInstance().createDragboard(this, selectedPdos) != null ||
        provider != null && provider.createDragboard(this) != null) {
      event.consume();
    }
  }

  /**
   * Handles the drag-over event.
   *
   * @param event the drag event
   */
  protected void handleDragOver(DragEvent event) {
    if (event.getGestureSource() != this && provider != null && provider.isDragAccepted(event)) {
      event.consume();
    }
  }

  /**
   * Handles the drag-dropped event.<br>
   * Drops the dragboard on the cell's PDO and replaces its tree item with a reloaded one.
   *
   * @param event the drag event
   */
  protected void handleDragDropped(DragEvent event) {
    if (provider != null) {
      provider.dropDragboard(event.getDragboard());
      event.setDropCompleted(true);
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
      event.consume();
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
      if (getTreeView().getContextMenu() == null) {
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

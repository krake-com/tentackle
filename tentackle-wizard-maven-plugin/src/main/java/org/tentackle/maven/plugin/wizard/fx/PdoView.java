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

import org.tentackle.bind.Bindable;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.container.FxBorderPane;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.maven.plugin.wizard.pdodata.DataDiff;
import org.tentackle.maven.plugin.wizard.pdodata.DataNode;

import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.WindowEvent;

/**
 * Shows the contents of a PDO with an optional diff to a memorized state of the PDO.
 */
@FxControllerService(url = FxControllerService.FXML_NONE,
                     resources = FxControllerService.RESOURCES_NONE,
                     cssDarkAware = true)
public class PdoView extends AbstractFxController {

  private static final String STYLE_MISMATCH = "diff-mismatch";     // CSS style class for rows with differences

  @Bindable
  private DataDiff diff;

  private final FxTreeTableView<DataDiff> diffNode;
  private TablePopup<DataDiff> diffNodePopup;
  private boolean diffOnly;   // true if only differences are shown


  /**
   * Creates an object diff view.
   */
  public PdoView() {
    diffNode = Fx.create(TreeTableView.class);
    diffNode.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    FxBorderPane pane = Fx.create(BorderPane.class);    // must be an FxContainer (otherwise binding doesn't work)
    pane.setCenter(diffNode);
    setView(pane);
  }

  /**
   * Gets the preferred width of the view.
   *
   * @return the preferred width
   */
  public double getPrefWidth() {
    return diffNode.getPrefWidth();
  }

  /**
   * Adds a selection listener for the currently selected data node.
   *
   * @param listener the change listener
   */
  public void addSelectedItemListener(ChangeListener<TreeItem<DataDiff>> listener) {
    diffNode.getSelectionModel().selectedItemProperty().addListener(listener);
  }

  @Override
  public void configure() {
    diffNode.setSortable(false);
    diffNode.setReorderable(false);
    diffNode.setRowFactory(treeTableView -> new TreeTableRow<>() {
      @Override
      protected void updateItem(DataDiff item, boolean empty) {
        super.updateItem(item, empty);
        if (item != null && item.isDifferent()) {
          if (!getStyleClass().contains(STYLE_MISMATCH)) {
            getStyleClass().add(STYLE_MISMATCH);
          }
        }
        else {
          getStyleClass().remove(STYLE_MISMATCH);
        }
      }
    });

    diffNodePopup = Rdc.createTablePopup(diffNode, "dataView", "Data");
    diffNodePopup.setColumnMenuEnabled(false);
    diffNodePopup.loadPreferences();

    configureContextMenu();
  }

  /**
   * Sets the comparison object and column titles.
   *
   * @param diff the comparison
   * @param leftSideName the title of the left (current) value column
   * @param rightSideName the title of the right (memorized) value column
   */
  public void setDiff(DataDiff diff, String leftSideName, String rightSideName) {
    this.diff = diff;
    if (diff == null || !diff.isRightPresent()) {
      diffOnly = false;
      ((DataNodeTableConfiguration<?>) diffNode.getConfiguration()).setPredicate(null);
    }
    diffNode.updateView();
    diffNode.getColumns().get(2).setText(leftSideName);   // columns are fixed!
    diffNode.getColumns().get(3).setText(rightSideName);
    if (diffOnly) {
      diffNode.expandAll();   // like updateDiffOnly
    }
    else {
      diffNode.getRoot().setExpanded(true);
    }
  }


  private void configureContextMenu() {
    ContextMenu contextMenu = diffNode.getContextMenu();
    MenuItem diffOnlyItem = Fx.create(MenuItem.class);
    diffOnlyItem.setOnAction(this::toggleDiffOnly);
    MenuItem copyFQCNItem = Fx.create(MenuItem.class);
    copyFQCNItem.setOnAction(e -> PdoBrowserUtils.copyFQCN(getSelectedDataNode()));
    MenuItem copyTableItem = Fx.create(MenuItem.class);
    copyTableItem.setOnAction(e -> PdoBrowserUtils.copyTableName(getSelectedDataNode()));
    MenuItem copySqlItem = PdoBrowserUtils.createCopySqlItem(e -> PdoBrowserUtils.copySql(getSelectedDataNode()));
    MenuItem findReferencesItem = PdoBrowserUtils.createFindReferencesItem(
                                    e -> PdoBrowserUtils.copyReferencingSql(getSelectedDataNode()));
    MenuItem copyClassIdItem = Fx.create(MenuItem.class);
    copyClassIdItem.setOnAction(e -> PdoBrowserUtils.copyClassId(getSelectedDataNode()));
    contextMenu.getItems().addFirst(copyClassIdItem);
    contextMenu.getItems().addFirst(findReferencesItem);
    contextMenu.getItems().addFirst(copySqlItem);
    contextMenu.getItems().addFirst(copyTableItem);
    contextMenu.getItems().addFirst(copyFQCNItem);
    contextMenu.getItems().addFirst(diffOnlyItem);
    contextMenu.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> {
      diffOnlyItem.setVisible(diff != null && diff.isRightPresent());
      diffOnlyItem.setText(diffOnly ? "show all lines" : "show differences only");
      PdoBrowserUtils.configureContextMenu(getSelectedDataNode(), copyFQCNItem, copyTableItem, copyClassIdItem,
                                           copySqlItem, findReferencesItem);
    });
  }


  private void toggleDiffOnly(ActionEvent event) {
    diffOnly = !diffOnly;
    updateDiffOnly();
  }

  private void updateDiffOnly() {
    if (diffOnly) {
      ((DataNodeTableConfiguration<?>) diffNode.getConfiguration()).setPredicate(node -> ((DataDiff) node).isDifferent());
    }
    else {
      ((DataNodeTableConfiguration<?>) diffNode.getConfiguration()).setPredicate(null);
    }
    diffNode.scrollTo(0);
    diffNode.updateView();
    if (diffOnly) {
      diffNode.expandAll();
    }
    else {
      diffNode.getRoot().setExpanded(true);
    }
  }

  private DataNode getSelectedDataNode() {
    DataNode node = null;
    TreeItem<DataDiff> selectedItem = diffNode.getSelectionModel().getSelectedItem();
    if (selectedItem != null) {
      DataDiff diff = selectedItem.getValue();
      node = diff.isLeftPresent() ? diff.getLeft() : diff.getRight();
    }
    return node;
  }

}

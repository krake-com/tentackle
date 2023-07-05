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
import org.tentackle.maven.plugin.wizard.pdodata.DataObject;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.stage.WindowEvent;

/**
 * Shows the contents of a PDO with optional diff to a savepoint.
 */
@FxControllerService(url = FxControllerService.FXML_NONE, resources = FxControllerService.RESOURCES_NONE)
public class PdoView extends AbstractFxController {

  private static final String STYLE_MISMATCH = "diff-mismatch";     // css style for differences

  @Bindable
  private DataDiff diff;

  private final FxTreeTableView<DataDiff> diffNode;
  private TablePopup<DataDiff> diffNodePopup;
  private boolean diffOnly;   // true if show differences only

  private record TypeInfo(String fqcn, String tableName, long classId) {}

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
   * @param leftSiteName the name of the left site
   * @param rightSiteName the name of the right site
   */
  public void setDiff(DataDiff diff, String leftSiteName, String rightSiteName) {
    this.diff = diff;
    if (diff == null || !diff.isRightPresent()) {
      diffOnly = false;
      ((DataNodeTableConfiguration<?>) diffNode.getConfiguration()).setPredicate(null);
    }
    diffNode.updateView();
    diffNode.getColumns().get(2).setText(leftSiteName);   // columns are fixed!
    diffNode.getColumns().get(3).setText(rightSiteName);
    diffNode.getRoot().setExpanded(true);
  }


  private void configureContextMenu() {
    ContextMenu contextMenu = diffNode.getContextMenu();
    MenuItem diffOnlyItem = Fx.create(MenuItem.class);
    diffOnlyItem.setOnAction(this::toggleDiffOnly);
    MenuItem copyFQCNItem = Fx.create(MenuItem.class);
    copyFQCNItem.setOnAction(this::copyFQCN);
    MenuItem copyTableItem = Fx.create(MenuItem.class);
    copyTableItem.setOnAction(this::copyTableName);
    MenuItem copyIdItem = Fx.create(MenuItem.class);
    copyIdItem.setOnAction(this::copyClassId);
    contextMenu.getItems().add(0, copyIdItem);
    contextMenu.getItems().add(0, copyTableItem);
    contextMenu.getItems().add(0, copyFQCNItem);
    contextMenu.getItems().add(0, diffOnlyItem);
    contextMenu.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> {
      diffOnlyItem.setText(diffOnly ? "show all lines" : "show differences only");
      TypeInfo typeInfo = getSelectedTypeInfo();
      copyFQCNItem.setVisible(false);
      copyTableItem.setVisible(false);
      copyIdItem.setVisible(false);
      if (typeInfo != null) {
        if (typeInfo.fqcn != null) {
          copyFQCNItem.setText("copy FQCN \"" + typeInfo.fqcn + "\" to clipboard");
          copyFQCNItem.setVisible(true);
        }
        if (typeInfo.tableName != null) {
          // replace _ by __ to avoid misinterpretation as a mnemonic (even if setMnemonicParsing=false!)
          copyTableItem.setText("copy table name \"" + typeInfo.tableName.replace("_", "__") + "\" to clipboard");
          copyTableItem.setMnemonicParsing(false);    // table names often contain underscores
          copyTableItem.setVisible(true);
        }
        if (typeInfo.classId != 0) {
          copyIdItem.setText("copy class-ID \"" + typeInfo.classId + "\" to clipboard");
          copyIdItem.setVisible(true);
        }
      }
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

  private void copyFQCN(ActionEvent event) {
    TypeInfo typeInfo = getSelectedTypeInfo();
    if (typeInfo != null) {
      copyToClipboard(typeInfo.fqcn);
    }
  }

  private void copyTableName(ActionEvent event) {
    TypeInfo typeInfo = getSelectedTypeInfo();
    if (typeInfo != null) {
      copyToClipboard(typeInfo.tableName);
    }
  }

  private void copyClassId(ActionEvent event) {
    TypeInfo typeInfo = getSelectedTypeInfo();
    if (typeInfo != null) {
      copyToClipboard(Long.toString(typeInfo.classId));
    }
  }

  private TypeInfo getSelectedTypeInfo() {
    TreeItem<DataDiff> selectedItem = diffNode.getSelectionModel().getSelectedItem();
    if (selectedItem != null) {
      DataDiff diff = selectedItem.getValue();
      DataNode data = diff.isLeftPresent() ? diff.getLeft() : diff.getRight();
      if (data instanceof DataObject object) {
        PersistentDomainObject<?> pdo = object.getPdo();
        if (pdo != null) {
          return new TypeInfo(pdo.getEffectiveClass().getName(), pdo.getTableName(), pdo.getClassId());
        }
      }
    }
    return null;
  }

  private void copyToClipboard(String text) {
    ClipboardContent cbc = new ClipboardContent();
    cbc.putString(text);
    Clipboard.getSystemClipboard().setContent(cbc);
  }

}

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

package org.tentackle.fx.table.edit;

import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;

import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The edit context of an {@link FxTreeTableView}.<br>
 * Rows are located among the displayed tree items, i.e. those whose parents are expanded. Tree items
 * loading their children lazily are never forced to do so just to find a row. Rows added by this context
 * are found even if they are not displayed yet.
 *
 * @param <S> type of the objects contained within the treetable's items
 * @author harald
 */
public class FxTreeTableEditContext<S> extends AbstractTableEditContext<S> implements TreeTableEditContext<S> {

  private final FxTreeTableView<S> treeTable;
  private final Map<S, TreeItem<S>> addedItems = new IdentityHashMap<>();   // items added by this context

  /**
   * Creates the edit context.
   *
   * @param treeTable the treetable view
   */
  public FxTreeTableEditContext(FxTreeTableView<S> treeTable) {
    this.treeTable = treeTable;
  }

  /**
   * Gets the treetable view.
   *
   * @return the treetable view
   */
  public FxTreeTableView<S> getTreeTable() {
    return treeTable;
  }

  @Override
  public TableConfiguration<S> getConfiguration() {
    return treeTable.getConfiguration();
  }

  @Override
  public List<TableColumnConfiguration<S, ?>> getColumns() {
    List<TableColumnConfiguration<S, ?>> columns = new ArrayList<>();
    TableConfiguration<S> configuration = getConfiguration();
    if (configuration != null) {
      for (TreeTableColumn<S, ?> column : treeTable.getVisibleLeafColumns()) {
        TableColumnConfiguration<S, ?> columnConfiguration = configuration.getColumnConfiguration(column);
        if (columnConfiguration != null) {
          columns.add(columnConfiguration);
        }
      }
    }
    return columns;
  }

  @Override
  public int getRowCount() {
    return treeTable.getExpandedItemCount();
  }

  @Override
  public S getRow(int index) {
    TreeItem<S> item = treeTable.getTreeItem(index);
    return item == null ? null : item.getValue();
  }

  @Override
  public void addRow(S row) {
    addChild(null, row);
  }

  @Override
  public void insertRowAfter(S sibling, S row) {
    insertSibling(sibling, row, 1);
  }

  @Override
  public void insertRowBefore(S sibling, S row) {
    insertSibling(sibling, row, 0);
  }

  @Override
  public boolean removeRow(S row) {
    TreeItem<S> item = findTreeItem(row);
    addedItems.remove(row);
    if (item != null) {
      TreeItem<S> parent = item.getParent();
      if (parent != null) {
        return parent.getChildren().remove(item);
      }
      if (item == treeTable.getRoot()) {
        treeTable.setRoot(null);
        return true;
      }
    }
    return false;
  }

  @Override
  public S getParent(S row) {
    TreeItem<S> item = findTreeItem(row);
    TreeItem<S> parent = item == null ? null : item.getParent();
    return parent == null || parent == treeTable.getRoot() && !treeTable.isShowRoot() ? null : parent.getValue();
  }

  @Override
  public List<S> getChildren(S row) {
    List<S> children = new ArrayList<>();
    TreeItem<S> item = row == null ? treeTable.getRoot() : findTreeItem(row);
    if (item != null) {
      for (TreeItem<S> child : item.getChildren()) {
        children.add(child.getValue());
      }
    }
    return children;
  }

  @Override
  public void addChild(S parent, S row) {
    TreeItem<S> parentItem = parent == null ? treeTable.getRoot() : findTreeItem(parent);
    if (parentItem == null) {
      throw new FxRuntimeException(parent == null ? "treetable has no root" : "no such row: " + parent);
    }
    parentItem.getChildren().add(createTreeItem(row));
  }

  @Override
  public void setExpanded(S row, boolean expanded) {
    TreeItem<S> item = findTreeItem(row);
    if (item != null) {
      item.setExpanded(expanded);
    }
  }

  @Override
  public void edit(TableCellRef<S> cell) {
    if (cell != null) {
      select(cell);
      // the cells must have been laid out, especially if rows were added
      Platform.runLater(() -> {
        int index = indexOf(cell.row());
        if (index >= 0) {
          scrollTo(index);
          treeTable.layout();
          treeTable.edit(index, cell.column().getTreeTableColumn());
        }
      });
    }
  }

  @Override
  public void select(TableCellRef<S> cell) {
    if (cell != null) {
      TreeItem<S> item = findTreeItem(cell.row());
      if (item != null) {
        for (TreeItem<S> parent = item.getParent(); parent != null; parent = parent.getParent()) {
          parent.setExpanded(true);
        }
        int index = treeTable.getRow(item);
        if (index >= 0) {
          treeTable.getSelectionModel().clearAndSelect(index, cell.column().getTreeTableColumn());
          treeTable.requestFocus();
        }
      }
    }
  }

  @Override
  public void refresh() {
    treeTable.refresh();
  }


  /**
   * Scrolls a row into view, unless it is already visible.
   *
   * @param index the row index
   */
  protected void scrollTo(int index) {
    if (!FxUtilities.getInstance().isRowVisible(treeTable, index)) {
      treeTable.scrollToCentered(index);
    }
  }

  /**
   * Finds the tree item of a row.
   *
   * @param row the row object
   * @return the tree item, null if not displayed and not added by this context
   */
  protected TreeItem<S> findTreeItem(S row) {
    int count = treeTable.getExpandedItemCount();
    for (int index = 0; index < count; index++) {
      TreeItem<S> item = treeTable.getTreeItem(index);
      if (item != null && item.getValue() == row) {
        return item;
      }
    }
    TreeItem<S> root = treeTable.getRoot();
    if (root != null && root.getValue() == row) {
      return root;    // hidden root
    }
    TreeItem<S> item = addedItems.get(row);
    if (item != null && !isAttached(item)) {
      addedItems.remove(row);
      item = null;
    }
    return item;
  }

  /**
   * Creates the tree item for a new row.
   *
   * @param row the row object
   * @return the tree item
   */
  protected TreeItem<S> createTreeItem(S row) {
    addedItems.values().removeIf(item -> !isAttached(item));    // don't keep items removed by the application
    TableConfiguration<S> configuration = getConfiguration();
    TreeItem<S> item = configuration == null ? new TreeItem<>(row) : configuration.createTreeItem(row);
    addedItems.put(row, item);
    return item;
  }


  private void insertSibling(S sibling, S row, int offset) {
    TreeItem<S> siblingItem = findTreeItem(sibling);
    TreeItem<S> parent = siblingItem == null ? null : siblingItem.getParent();
    if (parent == null) {
      throw new FxRuntimeException("no such sibling: " + sibling);
    }
    // the item is created first: creating it prunes the added items
    TreeItem<S> item = createTreeItem(row);
    parent.getChildren().add(parent.getChildren().indexOf(siblingItem) + offset, item);
  }

  private boolean isAttached(TreeItem<S> item) {
    TreeItem<S> root = treeTable.getRoot();
    while (item != null) {
      if (item == root) {
        return true;
      }
      item = item.getParent();
    }
    return false;
  }

}

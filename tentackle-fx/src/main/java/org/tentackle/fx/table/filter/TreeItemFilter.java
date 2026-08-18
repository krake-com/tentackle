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

package org.tentackle.fx.table.filter;

import org.tentackle.fx.FxUtilities;

import javafx.beans.property.ReadOnlyProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.TreeItem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a {@link TableFilter} to a tree of {@link TreeItem}s.<p>
 * Filtering is <em>lazy and per level</em>: an item is hidden if its own row does not pass the
 * filter, together with its whole subtree, and the children of an item are evaluated when the item
 * is expanded and not before. Lazily loading tree items — such as the PDO tree items of the rich
 * desktop client, which query the database on {@link TreeItem#getChildren()} — are thus never
 * forced to materialize the whole tree. Items whose value is already contained in their parent path
 * are left alone, so that node loops cannot lead to an endless recursion.
 * <p>
 * Hidden items are remembered along with the index they were displayed at and are re-inserted
 * there as soon as they pass the filter again. Items removed by the application meanwhile are
 * not resurrected, since the displayed children are always the starting point.
 * <p>
 * This is the tree counterpart of the {@link javafx.collections.transformation.FilteredList} used
 * for table views. It is used by the {@link TableFilterSupport}.
 *
 * @param <S> type of the objects contained within the treetable's items list
 * @author harald
 */
public class TreeItemFilter<S> {

  /**
   * A tree item hidden by the filter, along with the index it was displayed at.
   *
   * @param <S> the row type
   * @param index the index within the parent's children
   * @param item the hidden tree item
   */
  private record HiddenChild<S>(int index, TreeItem<S> item) { }


  private final TableFilter<S> filter;
  private final Map<TreeItem<S>, List<HiddenChild<S>>> hiddenChildren = new IdentityHashMap<>();
  private final Map<TreeItem<S>, Integer> filteredRevision = new IdentityHashMap<>();
  private final Map<TreeItem<S>, Boolean> hookedItems = new IdentityHashMap<>();
  private final ChangeListener<Boolean> expandedListener;

  private TreeItem<S> root;
  private boolean adjustingChildren;


  /**
   * Creates a tree filter.
   *
   * @param filter the table filter
   */
  public TreeItemFilter(TableFilter<S> filter) {
    this.filter = filter;
    this.expandedListener = (observable, oldValue, expanded) -> {
      if (expanded && observable instanceof ReadOnlyProperty<?> property) {
        filterChildren(asTreeItem(property.getBean()));
      }
    };
  }


  @Override
  public String toString() {
    return "tree filter [" + filter + "]";
  }


  /**
   * Gets the table filter.
   *
   * @return the filter
   */
  public TableFilter<S> getFilter() {
    return filter;
  }

  /**
   * Gets the tree's root item.
   *
   * @return the root, null if none
   */
  public TreeItem<S> getRoot() {
    return root;
  }

  /**
   * Sets the tree's root item.<br>
   * Everything known about the previous tree is discarded.
   *
   * @param root the root, null if none
   */
  public void setRoot(TreeItem<S> root) {
    reset();
    this.root = root;
  }

  /**
   * Applies the filter to the materialized part of the tree.<br>
   * Only the root's children and the children of expanded items are evaluated.
   */
  public void filterTree() {
    if (root == null) {
      return;
    }
    Deque<TreeItem<S>> pending = new ArrayDeque<>();
    pending.add(root);
    while (!pending.isEmpty()) {
      TreeItem<S> item = pending.poll();
      filterChildren(item);
      if (isMaterialized(item)) {
        for (TreeItem<S> child : item.getChildren()) {
          if (child.isExpanded()) {
            pending.add(child);
          }
        }
      }
    }
  }

  /**
   * Applies the filter to the children of a tree item.<p>
   * Invoking this method materializes the item's children, which is why it must only be invoked
   * for the root and for expanded items.
   *
   * @param parent the parent item, null to do nothing
   */
  public void filterChildren(TreeItem<S> parent) {
    if (parent == null || adjustingChildren) {
      return;
    }
    if (Integer.valueOf(filter.getRevision()).equals(filteredRevision.get(parent))) {
      return;   // already up to date
    }
    if (parent.getParent() != null && FxUtilities.getInstance().isValueInParentPath(parent)) {
      return;   // node loop: don't descend any further
    }

    List<TreeItem<S>> allChildren = collectAllChildren(parent);
    List<TreeItem<S>> keptChildren = new ArrayList<>(allChildren.size());
    List<HiddenChild<S>> nowHidden = new ArrayList<>();
    boolean active = filter.isActive();
    int index = 0;
    for (TreeItem<S> child : allChildren) {
      if (!active || filter.test(child.getValue())) {
        keptChildren.add(child);
      }
      else {
        nowHidden.add(new HiddenChild<>(index, child));
      }
      index++;
    }

    setChildren(parent, keptChildren);

    if (nowHidden.isEmpty()) {
      hiddenChildren.remove(parent);
    }
    else {
      hiddenChildren.put(parent, nowHidden);
    }
    filteredRevision.put(parent, filter.getRevision());

    for (TreeItem<S> child : keptChildren) {
      hookTreeItem(child);
    }
  }

  /**
   * Restores all items hidden by the filter.
   */
  public void showAll() {
    for (TreeItem<S> parent : new ArrayList<>(hiddenChildren.keySet())) {
      // collect first: the hidden children are the ones to bring back
      List<TreeItem<S>> allChildren = collectAllChildren(parent);
      hiddenChildren.remove(parent);
      filteredRevision.remove(parent);
      setChildren(parent, allChildren);
    }
  }

  /**
   * Discards what is known about a tree item's children.<br>
   * To be invoked by applications that replace the children of an item while a filter is active.
   *
   * @param item the tree item, null to invalidate the whole tree
   */
  public void invalidate(TreeItem<S> item) {
    if (item == null) {
      hiddenChildren.clear();
      filteredRevision.clear();
    }
    else {
      hiddenChildren.remove(item);
      filteredRevision.remove(item);
    }
  }

  /**
   * Removes all listeners and forgets the hidden items.<br>
   * The displayed items are left as they are, see {@link #showAll()}.
   */
  public void reset() {
    for (TreeItem<S> item : hookedItems.keySet()) {
      item.expandedProperty().removeListener(expandedListener);
    }
    hookedItems.clear();
    hiddenChildren.clear();
    filteredRevision.clear();
  }

  /**
   * Returns whether the children of a tree item have been filtered at least once.
   *
   * @param item the tree item
   * @return true if the item's children are materialized
   */
  public boolean isMaterialized(TreeItem<S> item) {
    return filteredRevision.containsKey(item);
  }

  /**
   * Gets the rows of the materialized part of the tree, hidden ones included.<br>
   * These are the rows the filter popup collects its distinct values from.
   *
   * @return the rows
   */
  public List<S> collectRows() {
    List<S> rows = new ArrayList<>();
    if (root != null) {
      Deque<TreeItem<S>> pending = new ArrayDeque<>();
      pending.add(root);
      while (!pending.isEmpty()) {
        TreeItem<S> item = pending.poll();
        if (isMaterialized(item)) {
          for (TreeItem<S> child : collectAllChildren(item)) {
            rows.add(child.getValue());
            if (child.isExpanded()) {
              pending.add(child);
            }
          }
        }
      }
    }
    return rows;
  }


  /**
   * Collects all children of a tree item, i.e. the displayed ones plus those hidden by the filter.<br>
   * Children hidden by the filter are re-inserted at the index they were displayed at, so that the
   * original order is restored as far as possible.
   *
   * @param parent the parent item
   * @return all children
   */
  protected List<TreeItem<S>> collectAllChildren(TreeItem<S> parent) {
    List<TreeItem<S>> allChildren = new ArrayList<>(parent.getChildren());
    List<HiddenChild<S>> hidden = hiddenChildren.get(parent);
    if (hidden != null) {
      for (HiddenChild<S> hiddenChild : hidden) {
        if (!containsIdentical(allChildren, hiddenChild.item())) {
          allChildren.add(Math.min(hiddenChild.index(), allChildren.size()), hiddenChild.item());
        }
      }
    }
    return allChildren;
  }

  /**
   * Sets the displayed children of a tree item.
   *
   * @param parent the parent item
   * @param children the children to display
   */
  protected void setChildren(TreeItem<S> parent, List<TreeItem<S>> children) {
    if (isSameOrder(parent.getChildren(), children)) {
      return;
    }
    adjustingChildren = true;
    try {
      parent.getChildren().setAll(children);
    }
    finally {
      adjustingChildren = false;
    }
  }

  /**
   * Registers this filter with a tree item, so that its children are filtered once it is expanded.
   *
   * @param item the tree item
   */
  protected void hookTreeItem(TreeItem<S> item) {
    if (hookedItems.put(item, Boolean.TRUE) == null) {
      item.expandedProperty().addListener(expandedListener);
    }
  }


  @SuppressWarnings("unchecked")
  private TreeItem<S> asTreeItem(Object bean) {
    return bean instanceof TreeItem ? (TreeItem<S>) bean : null;
  }

  private static <E> boolean containsIdentical(List<E> list, E element) {
    for (E item : list) {
      if (item == element) {
        return true;
      }
    }
    return false;
  }

  private static <E> boolean isSameOrder(List<E> first, List<E> second) {
    if (first.size() != second.size()) {
      return false;
    }
    for (int index = 0; index < first.size(); index++) {
      if (first.get(index) != second.get(index)) {
        return false;
      }
    }
    return true;
  }

}

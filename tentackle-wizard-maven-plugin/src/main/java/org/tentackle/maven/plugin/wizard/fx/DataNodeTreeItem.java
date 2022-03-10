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

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tree item for a {@link DataNode}.
 *
 * @param <T> the node type
 */
public class DataNodeTreeItem<T extends DataNode> extends TreeItem<T> {

  private final Predicate<T> predicate;
  private boolean childrenLoaded;

  /**
   * Creates the tree item.
   *
   * @param node the data node
   * @param predicate the optional predicate, null if none
   */
  public DataNodeTreeItem(T node, Predicate<T> predicate) {
    super(node);
    this.predicate = predicate;
  }

  @Override
  public boolean isLeaf() {
    return getValue() == null || getValue().getNodes() == null;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public ObservableList<TreeItem<T>> getChildren() {
    if (!childrenLoaded) {
      childrenLoaded = true;
      List<TreeItem<T>> childItems = new ArrayList<>();
      if (getValue() != null) {
        List<? extends DataNode> nodes = getValue().getNodes();
        if (nodes != null) {
          for (DataNode node : nodes) {
            if (predicate == null || predicate.test((T) node)) {
              childItems.add(new DataNodeTreeItem(node, predicate));
            }
          }
        }
      }
      super.getChildren().setAll(childItems);
    }
    return super.getChildren();
  }

}

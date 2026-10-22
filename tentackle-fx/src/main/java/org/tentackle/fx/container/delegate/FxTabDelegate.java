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

package org.tentackle.fx.container.delegate;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;

import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.FxControl;
import org.tentackle.fx.container.FxTab;

/**
 * Delegate for FxTab.<br>
 * Since a {@link javafx.scene.control.Tab} is not a node, the parent container is the tab pane,
 * and the content node is the only component.
 *
 * @author harald
 */
public class FxTabDelegate extends FxContainerDelegate {

  private final FxTab container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxTabDelegate(FxTab container) {
    this.container = container;
  }


  @Override
  public FxTab getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    ObservableList<Node> list = FXCollections.observableArrayList();
    Node node = container.getContent();
    if (node != null) {
      list.add(node);
    }
    return list;
  }

  @Override
  public FxContainer getParentContainer() {
    return (FxContainer) container.getTabPane();
  }

  @Override
  public boolean isViewModified() {
    Node node = container.getContent();
    if (node instanceof FxControl) {
      return ((FxControl) node).isViewModified();
    }
    return super.isViewModified();
  }

}

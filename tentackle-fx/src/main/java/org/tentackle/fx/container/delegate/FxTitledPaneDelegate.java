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

import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxTitledPane;

/**
 * Delegate for FxTitledPane.
 *
 * @author harald
 */
public class FxTitledPaneDelegate extends FxContainerDelegate {

  private final FxTitledPane container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxTitledPaneDelegate(FxTitledPane container) {
    this.container = container;
  }


  @Override
  public FxTitledPane getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    return FXCollections.observableArrayList(container.getContent());
  }

}

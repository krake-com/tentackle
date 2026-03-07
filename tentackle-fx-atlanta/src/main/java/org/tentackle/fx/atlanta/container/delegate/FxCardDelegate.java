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

package org.tentackle.fx.atlanta.container.delegate;

import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.atlanta.container.FxCard;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;

/**
 * Delegate for {@link FxCard}.
 *
 * @author harald
 */
public class FxCardDelegate extends FxContainerDelegate {

  private final FxCard container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxCardDelegate(FxCard container) {
    this.container = container;
    this.container.headerProperty().addListener(this::changed);
    this.container.subHeaderProperty().addListener(this::changed);
    this.container.bodyProperty().addListener(this::changed);
    this.container.footerProperty().addListener(this::changed);
  }


  @Override
  public FxCard getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    return container.getChildren();
  }


  private void changed(ObservableValue<? extends Node> observableValue, Node oldValue, Node newValue) {
    if (oldValue != null) {
      getComponents().remove(oldValue);
    }
    if (newValue != null) {
      getComponents().add(newValue);
    }
  }

}

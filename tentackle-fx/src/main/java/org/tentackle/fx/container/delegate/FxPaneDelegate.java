/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.ObservableList;
import javafx.scene.Node;

import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxPane;

/**
 * Delegate for FxPane.
 *
 * @author harald
 */
public class FxPaneDelegate extends FxContainerDelegate {

  private final FxPane container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxPaneDelegate(FxPane container) {
    this.container = container;
  }


  @Override
  public FxPane getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    return container.getChildren();
  }

}

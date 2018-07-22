/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.ObservableList;
import javafx.scene.Node;

import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxStackPane;

/**
 * Delegate for FxStackPane.
 *
 * @author harald
 */
public class FxStackPaneDelegate extends FxContainerDelegate {

  private final FxStackPane container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxStackPaneDelegate(FxStackPane container) {
    this.container = container;
  }


  @Override
  public FxStackPane getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    return container.getChildren();
  }

}

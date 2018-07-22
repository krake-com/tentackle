/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.ObservableList;
import javafx.scene.Node;

import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxBorderPane;

/**
 * Delegate for FxBorderPane.
 *
 * @author harald
 */
public class FxBorderPaneDelegate extends FxContainerDelegate {

  private final FxBorderPane container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxBorderPaneDelegate(FxBorderPane container) {
    this.container = container;
  }


  @Override
  public FxBorderPane getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    return container.getChildren();
  }

}

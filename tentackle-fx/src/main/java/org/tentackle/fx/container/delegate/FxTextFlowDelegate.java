/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.ObservableList;
import javafx.scene.Node;

import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxTextFlow;

/**
 * Delegate for FxTextFlow.
 *
 * @author harald
 */
public class FxTextFlowDelegate extends FxContainerDelegate {

  private final FxTextFlow container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxTextFlowDelegate(FxTextFlow container) {
    this.container = container;
  }


  @Override
  public FxTextFlow getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    return container.getChildren();
  }

}

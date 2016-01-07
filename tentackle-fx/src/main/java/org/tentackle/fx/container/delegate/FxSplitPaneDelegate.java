/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxSplitPane;

/**
 * Delegate for FxSplitPane.
 *
 * @author harald
 */
public class FxSplitPaneDelegate extends FxContainerDelegate {

  private final FxSplitPane container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxSplitPaneDelegate(FxSplitPane container) {
    this.container = container;
  }


  @Override
  public FxSplitPane getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    return container.getItems();
  }

}

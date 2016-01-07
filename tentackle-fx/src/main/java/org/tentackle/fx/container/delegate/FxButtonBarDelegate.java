/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxButtonBar;

/**
 * Delegate for FxButtonBar.
 *
 * @author harald
 */
public class FxButtonBarDelegate extends FxContainerDelegate {

  private final FxButtonBar container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxButtonBarDelegate(FxButtonBar container) {
    this.container = container;
  }


  @Override
  public FxButtonBar getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    return container.getButtons();
  }

}

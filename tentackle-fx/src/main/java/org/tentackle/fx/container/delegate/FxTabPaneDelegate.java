/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.ObservableList;
import javafx.scene.control.Tab;

import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxTabPane;

/**
 * Delegate for FxTabPane.
 *
 * @author harald
 */
public class FxTabPaneDelegate extends FxContainerDelegate {

  private final FxTabPane container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxTabPaneDelegate(FxTabPane container) {
    this.container = container;
  }


  @Override
  public FxTabPane getContainer() {
    return container;
  }

  @Override
  public ObservableList<Tab> getComponents() {
    return container.getTabs();
  }

}

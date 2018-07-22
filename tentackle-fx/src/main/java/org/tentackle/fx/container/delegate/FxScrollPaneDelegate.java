/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;

import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxScrollPane;

/**
 * Delegate for FxScrollPane.
 *
 * @author harald
 */
public class FxScrollPaneDelegate extends FxContainerDelegate {

  private final FxScrollPane container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxScrollPaneDelegate(FxScrollPane container) {
    this.container = container;
  }


  @Override
  public FxScrollPane getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    return FXCollections.observableArrayList(container.getContent());
  }

}

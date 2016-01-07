/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.FxControl;
import org.tentackle.fx.container.FxTab;

/**
 * Delegate for FxTab.
 *
 * @author harald
 */
public class FxTabDelegate extends FxContainerDelegate {

  private final FxTab container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxTabDelegate(FxTab container) {
    this.container = container;
  }


  @Override
  public FxTab getContainer() {
    return container;
  }

  @Override
  public ObservableList<Node> getComponents() {
    ObservableList<Node> list = FXCollections.observableArrayList();
    Node node = container.getContent();
    if (node != null) {
      list.add(node);
    }
    return list;
  }

  @Override
  public FxContainer getParentContainer() {
    return (FxContainer) container.getTabPane();
  }

  @Override
  public boolean isViewModified() {
    Node node = container.getContent();
    if (node instanceof FxControl) {
      return ((FxControl) node).isViewModified();
    }
    return super.isViewModified();
  }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.container.delegate;

import javafx.collections.ObservableList;
import javafx.scene.control.TitledPane;
import org.tentackle.fx.FxContainerDelegate;
import org.tentackle.fx.container.FxAccordion;

/**
 * Delegate for FxAccordion.
 *
 * @author harald
 */
public class FxAccordionDelegate extends FxContainerDelegate {

  private final FxAccordion container;   // the container

  /**
   * Creates the delegate.
   *
   * @param container the container
   */
  public FxAccordionDelegate(FxAccordion container) {
    this.container = container;
  }


  @Override
  public FxAccordion getContainer() {
    return container;
  }

  @Override
  public ObservableList<TitledPane> getComponents() {
    return container.getPanes();
  }

}

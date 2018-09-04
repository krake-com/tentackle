/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import org.tentackle.fx.component.FxTextArea;

/**
 * Delegate for FxTextArea.
 *
 * @author harald
 */
public class FxTextAreaDelegate extends AbstractTextFieldDelegate<FxTextArea> {

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxTextAreaDelegate(FxTextArea component) {
    super(component);
  }

  @Override
  public void setColumns(int columns) {
    getComponent().setPrefColumnCount(columns);
  }

  @Override
  public int getColumns() {
    return getComponent().getPrefColumnCount();
  }

}

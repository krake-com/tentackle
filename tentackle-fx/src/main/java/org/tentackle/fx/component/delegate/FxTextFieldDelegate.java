/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.geometry.Pos;

import org.tentackle.fx.component.FxTextField;

/**
 * Delegate for FxTextField.
 *
 * @author harald
 */
public class FxTextFieldDelegate extends AbstractTextFieldDelegate<FxTextField> {

  private boolean setColumnsInvoked;

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxTextFieldDelegate(FxTextField component) {
    super(component);
  }

  @Override
  public void setColumns(int columns) {
    getComponent().setPrefColumnCount(columns);
    setColumnsInvoked = true;
  }

  @Override
  public int getColumns() {
    return getComponent().getPrefColumnCount();
  }

  @Override
  public void setMaxColumns(int maxColumns) {
    super.setMaxColumns(maxColumns);
    if (!setColumnsInvoked) {
      getComponent().setPrefColumnCount(maxColumns);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
    super.setViewValue(value);
    Pos alignment = getTextAlignment();
    if (alignment != null && getComponent().getAlignment() != alignment) {
      getComponent().setAlignment(alignment);
    }
  }

}

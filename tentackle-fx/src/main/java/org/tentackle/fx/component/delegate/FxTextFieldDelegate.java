/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.TextFormatter;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxTextComponentDelegate;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxTextField;

/**
 * Delegate for FxTextField.
 *
 * @author harald
 */
public class FxTextFieldDelegate extends AbstractTextFieldDelegate<FxTextField> {

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
  }

  @Override
  public int getColumns() {
    return getComponent().getPrefColumnCount();
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.application.Platform;
import javafx.scene.control.TextFormatter;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxPasswordField;
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

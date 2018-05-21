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
public class FxTextFieldDelegate extends FxTextComponentDelegate {

  private final FxTextField component;   // the component

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxTextFieldDelegate(FxTextField component) {
    this.component = component;
    component.setTextFormatter(new TextFormatter<>(this));
  }

  @Override
  public FxTextField getComponent() {
    return component;
  }

  @Override
  public void setColumns(int columns) {
    component.setPrefColumnCount(columns);
  }

  @Override
  public int getColumns() {
    return component.getPrefColumnCount();
  }

  @Override
  @SuppressWarnings("unchecked")
  public String getViewObject() {
    String text = component.getText();
    return StringHelper.isAllWhitespace(text) ? null : text;
  }

  @Override
  public void setViewObject(Object viewObject) {
    component.setText((String) viewObject);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
    ValueTranslator translator = getValueTranslator();
    Pos alignment = component.getTextAlignment();
    if (alignment != null) {
      component.setAlignment(alignment);
    }
    setViewObject(translator.toView(value));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getViewValue() {
    ValueTranslator translator = getValueTranslator();
    return (T) translator.toModel(getViewObject());
  }

  @Override
  public void mapErrorOffsetToCaretPosition() {
    Integer errorOffset = getErrorOffset();
    if (errorOffset != null) {
      Platform.runLater(() -> {
        component.deselect();
        component.positionCaret(errorOffset);
      });
    }
  }

}

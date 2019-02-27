/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.application.Platform;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.FxTextComponentDelegate;
import org.tentackle.fx.ValueTranslator;

/**
 * Delegate for text input fields.
 *
 * @author harald
 * @param <T> the text component type
 */
public abstract class AbstractTextFieldDelegate<T extends TextInputControl & FxTextComponent> extends FxTextComponentDelegate {

  private final T component;   // the component

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public AbstractTextFieldDelegate(T component) {
    this.component = component;
    component.setTextFormatter(new TextFormatter<>(this));
  }

  @Override
  public T getComponent() {
    return component;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
    ValueTranslator translator = getValueTranslator();
    setViewObject(translator.toView(value));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getViewValue() {
    ValueTranslator translator = getValueTranslator();
    return (V) translator.toModel(getViewObject());
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

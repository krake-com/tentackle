/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.scene.Parent;
import javafx.scene.paint.Color;

import org.tentackle.fx.FxComponentDelegate;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxColorPicker;

import java.util.Objects;

/**
 * Delegate for FxColorPicker.
 *
 * @author harald
 */
public class FxColorPickerDelegate extends FxComponentDelegate {

  private final FxColorPicker component;   // the component

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxColorPickerDelegate(FxColorPicker component) {
    this.component = component;
  }

  @Override
  public FxColorPicker getComponent() {
    return component;
  }

  @Override
  public FxContainer getParentContainer() {
    Parent parent = component.getParent();
    return parent instanceof FxContainer ? (FxContainer) parent : null;
  }

  @Override
  public void setType(Class<?> type) {
    // important to set the type before creating the translator,
    // since the translator may need to know the type
    super.setType(type);
    if (getValueTranslator() == null) {    // if not already set by application
      setValueTranslator(FxFactory.getInstance().createValueTranslator(type, Color.class, getComponent()));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewObject(Object value) {
    if (!Objects.equals(getViewObject(), value)) {
      component.setValue((Color) value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Color getViewObject() {
    return component.getValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
    ValueTranslator translator = getValueTranslator();
    setViewObject(translator.toView(value));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getViewValue() {
    ValueTranslator translator = getValueTranslator();
    return (T) translator.toModel(getViewObject());
  }

}

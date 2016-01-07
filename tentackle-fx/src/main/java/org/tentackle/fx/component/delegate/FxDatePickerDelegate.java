/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import java.time.LocalDate;
import javafx.scene.Parent;
import org.tentackle.fx.FxComponentDelegate;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxDatePicker;

/**
 * Delegate for FxDatePicker.
 *
 * @author harald
 */
public class FxDatePickerDelegate extends FxComponentDelegate {

  private final FxDatePicker component;   // the component

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxDatePickerDelegate(FxDatePicker component) {
    this.component = component;
  }

  @Override
  public FxDatePicker getComponent() {
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
      setValueTranslator(FxFactory.getInstance().createValueTranslator(type, LocalDate.class, getComponent()));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewObject(Object value) {
    component.setValue((LocalDate) value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public LocalDate getViewObject() {
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

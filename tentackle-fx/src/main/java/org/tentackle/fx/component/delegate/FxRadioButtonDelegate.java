/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.scene.Parent;

import org.tentackle.fx.FxComponentDelegate;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxRadioButton;

/**
 * Delegate for FxRadioButton.
 *
 * @author harald
 */
public class FxRadioButtonDelegate extends FxComponentDelegate {

  private final FxRadioButton component;   // the component

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxRadioButtonDelegate(FxRadioButton component) {
    this.component = component;
  }

  @Override
  public FxRadioButton getComponent() {
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
      setValueTranslator(FxFactory.getInstance().createValueTranslator(type, Boolean.class, getComponent()));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Boolean getViewObject() {
    return component.isSelected();
  }

  @Override
  public void setViewObject(Object viewObject) {
    component.setSelected(viewObject != null && (Boolean) viewObject);
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

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.scene.Parent;
import javafx.scene.control.TreeItem;
import org.tentackle.fx.FxComponentDelegate;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxTreeView;

/**
 * Delegate for FxTreeView.
 *
 * @author harald
 */
public class FxTreeViewDelegate extends FxComponentDelegate {

  private final FxTreeView<?> component;   // the component

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxTreeViewDelegate(FxTreeView<?> component) {
    this.component = component;
  }

  @Override
  public FxTreeView<?> getComponent() {
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
      setValueTranslator(FxFactory.getInstance().createValueTranslator(type, TreeItem.class, getComponent()));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public TreeItem getViewObject() {
    return component.getRoot();
  }

  @Override
  public void setViewObject(Object viewObject) {
    component.setRoot((TreeItem) viewObject);
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

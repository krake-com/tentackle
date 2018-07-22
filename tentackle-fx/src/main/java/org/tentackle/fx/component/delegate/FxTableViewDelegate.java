/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.collections.ObservableList;
import javafx.scene.Parent;

import org.tentackle.fx.FxComponentDelegate;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.misc.TrackedList;

/**
 * Delegate for FxTableView.
 *
 * @author harald
 */
public class FxTableViewDelegate extends FxComponentDelegate {

  private final FxTableView<?> component;   // the component
  private TrackedList<?> modelList;         // tracked list from model

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxTableViewDelegate(FxTableView<?> component) {
    this.component = component;
  }

  @Override
  public FxTableView<?> getComponent() {
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
      setValueTranslator(FxFactory.getInstance().createValueTranslator(type, ObservableList.class, getComponent()));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public ObservableList getViewObject() {
    return component.getItems();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewObject(Object viewObject) {
    if (viewObject != component.getItems()) {   // equals makes sense here: means the same list
      component.setItems((ObservableList) viewObject);
      component.getSelectionModel().clearSelection();
      component.refresh();
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
    if (value instanceof TrackedList) {
      modelList = (TrackedList) value;
    }
    else {
      modelList = null;
    }
    ValueTranslator translator = getValueTranslator();
    setViewObject(translator.toView(value));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getViewValue() {
    ValueTranslator translator = getValueTranslator();
    return (T) translator.toModel(getViewObject());
  }

  @Override
  public boolean isViewModified() {
    if (modelList != null) {
      return modelList.isModified();
    }
    return super.isViewModified();
  }

  @Override
  public void triggerViewModified() {
    if (getParentContainer() != null) {
      getParentContainer().triggerViewModified();
    }
  }

  @Override
  public void updateModel() {
    // no conversion back to model because list is updated by ObservableList from the view.
  }

  @Override
  protected void updateChangeable(boolean changeable) {
    // doesn't make sense.
    // setEditable() makes the cell editable and setDisable makes
    // the table view unclickable.
  }

  @Override
  public void setContainerChangeable(boolean containerChangeable) {
    // dto.
  }

}

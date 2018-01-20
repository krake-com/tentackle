/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import java.util.Arrays;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.tentackle.fx.FxComponentDelegate;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.component.FxChoiceBox;


/**
 * Delegate for FxChoiceBox.
 *
 * @author harald
 */
public class FxChoiceBoxDelegate extends FxComponentDelegate {

  private final FxChoiceBox<?> component;   // the component

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxChoiceBoxDelegate(FxChoiceBox<?> component) {
    this.component = component;
  }

  @Override
  public FxChoiceBox<?> getComponent() {
    return component;
  }

  @Override
  public FxContainer getParentContainer() {
    Parent parent = component.getParent();
    return parent instanceof FxContainer ? (FxContainer) parent : null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setType(Class<?> type) {
    super.setType(type);

    // choice box does not need a value translator because the items are already of model type
    // and choice-box is not editable

    // deselect by DELETE- or BACKSPACE-key
    component.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (!event.isAltDown() && !event.isControlDown() && !event.isMetaDown() &&
          !event.isShiftDown() && !event.isShortcutDown()) {
        if ((event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) &&
            isDeselectAllowed()) {
          event.consume();
          component.getSelectionModel().clearSelection();
        }
      }
    });

    if (type.isEnum()) {
      ObservableList items = component.getItems();
      items.clear();
      items.addAll(Arrays.asList(type.getEnumConstants()));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewObject(Object value) {
    ((FxChoiceBox) component).getSelectionModel().select(value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object getViewObject() {
    return component.getSelectionModel().getSelectedItem();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
    // no value translator needed because items are of model's type already
    setViewObject(value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getViewValue() {
    // no value translator needed because items are of model's type already
    return (T) getViewObject();
  }


  /**
   * Returns whether deselect is allowed.
   *
   * @return true if allowed
   */
  public boolean isDeselectAllowed() {
    Boolean allowed = component.isDeselectAllowed();
    if (allowed != null) {
      return allowed;
    }
    // else determine from type and/or binding
    return !(getType() != null && getType().isPrimitive() ||
             component.isMandatory());
  }

}

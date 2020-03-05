/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.fx.component.delegate;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.tentackle.fx.FxComponentDelegate;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.component.FxChoiceBox;

import java.util.Arrays;


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
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void setType(Class<?> type) {
    super.setType(type);

    // choice box does not need a value translator because the items are already of model type
    // and choice-box is not editable

    // deselect by DELETE- or BACKSPACE-key (with or without shift)
    component.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (!event.isAltDown() && !event.isControlDown() && !event.isMetaDown() && !event.isShortcutDown() &&
          (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) &&
          !component.isDisabled() && isDeselectAllowed()) {
        
        event.consume();
        component.getSelectionModel().clearSelection();
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
  public <V> V getViewValue() {
    // no value translator needed because items are of model's type already
    return (V) getViewObject();
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

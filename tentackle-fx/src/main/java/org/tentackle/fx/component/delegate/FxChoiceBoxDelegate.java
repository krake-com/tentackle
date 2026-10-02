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
 * <p>
 * As a convenience for boolean model types the choice box can fake
 * the boolean with 2 items: the first for TRUE and the second for FALSE.
 * If the application already specified exactly 2 items, they will be kept.
 * If there are no items, they are created from the name of the model member.
 * Any other number of items is left unchanged and not treated as a boolean.
 * <p>
 * For enums, the items are set to the enum constants.
 *
 * @author harald
 */
public class FxChoiceBoxDelegate extends FxComponentDelegate {

  private final FxChoiceBox<?> component;   // the component
  private boolean fakeBool;                 // true if boolean type and 2 choices fake TRUE and FALSE

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

    fakeBool = false;

    if (type.isEnum()) {
      ObservableList items = component.getItems();
      items.setAll(Arrays.asList(type.getEnumConstants()));
    }
    else if (type == Boolean.TYPE || type == Boolean.class) {
      ObservableList items = component.getItems();
      if (items.isEmpty()) {
        fakeBool = true;
        items.add(getBinding().getMember().getMemberName() + " = " + Boolean.TRUE);
        items.add(getBinding().getMember().getMemberName() + " = " + Boolean.FALSE);
      }
      else if (items.size() == 2) {
        fakeBool = true;    // first = true, second = false, unselected = null
      }
      // else application did some other stuff: leave it unchanged
    }
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void setViewObject(Object value) {
    ((FxChoiceBox) component).getSelectionModel().select(value);
  }

  @Override
  public Object getViewObject() {
    if (fakeBool) {
      return switch (component.getSelectionModel().getSelectedIndex()) {
        case 0 -> Boolean.TRUE;
        case 1 -> Boolean.FALSE;
        default -> null;
      };
    }
    return component.getSelectionModel().getSelectedItem();
  }

  @Override
  public void setViewValue(Object value) {
    if (fakeBool) {
      if (Boolean.TRUE.equals(value)) {
        component.getSelectionModel().select(0);
      }
      else if (Boolean.FALSE.equals(value)) {
        component.getSelectionModel().select(1);
      }
      else {
        component.getSelectionModel().clearSelection();
      }
    }
    else {
      // no value translator needed because items are of model's type already
      setViewObject(value);
    }
    setLastViewObject(getViewObject());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getViewValue() {
    // no value translator needed because items are of model's type already
    return (V) getViewObject();
  }


  /**
   * Returns whether deselect is allowed.<br>
   * If not set explicitly via {@link FxChoiceBox#setDeselectAllowed(Boolean)},
   * deselect is allowed unless the type is primitive or the component is mandatory.
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

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

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.TextFormatter;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxTextComponentDelegate;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxComboBox;

import java.util.Arrays;
import java.util.Objects;


/**
 * Delegate for FxComboBox.
 *
 * @author harald
 */
public class FxComboBoxDelegate extends FxTextComponentDelegate {

  private final FxComboBox<?> component;   // the component

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxComboBoxDelegate(FxComboBox<?> component) {
    this.component = component;
    component.getEditor().setTextFormatter(new TextFormatter<>(this));
  }

  @Override
  public FxComboBox<?> getComponent() {
    return component;
  }

  @Override
  public FxContainer getParentContainer() {
    Parent parent = component.getParent();
    return parent instanceof FxContainer ? (FxContainer) parent : null;
  }

  @Override
  public void setColumns(int columns) {
    component.getEditor().setPrefColumnCount(columns);
  }

  @Override
  public int getColumns() {
    return component.getEditor().getPrefColumnCount();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setType(Class<?> type) {
    // important to set the type before creating the translator,
    // since the translator may need to know the type
    super.setType(type);

    if (type.isEnum()) {
      ObservableList items = component.getItems();
      items.setAll(Arrays.asList(type.getEnumConstants()));
    }
  }

  @Override
  public Object getViewObject() {
    if (component.isEditable()) {
      String text = component.getEditor().getText();
      return StringHelper.isAllWhitespace(text) ? null : text;
    }
    return component.getSelectionModel().getSelectedItem();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewObject(Object viewObject) {
    if (component.isEditable()) {
      Pos alignment = component.getTextAlignment();
      if (alignment != null) {
        component.getEditor().setAlignment(alignment);
      }
      component.getEditor().setText((String) viewObject);
    }
    else {
      if (!Objects.equals(getViewObject(), viewObject)) {
        ((FxComboBox) component).getSelectionModel().select(viewObject);
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
    if (component.isEditable()) {
      ValueTranslator translator = getValueTranslator();
      value = translator.toView(value);    // to string
    }
    setViewObject(value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> V getViewValue() {
    Object value = getViewObject();
    if (component.isEditable()) {
      ValueTranslator translator = getValueTranslator();
      return (V) translator.toModel(value);
    }
    return (V) value;
  }

  @Override
  public void mapErrorOffsetToCaretPosition() {
    Integer errorOffset = getErrorOffset();
    if (errorOffset != null && component.isEditable()) {
      Platform.runLater(() -> {
        component.getEditor().deselect();
        component.getEditor().positionCaret(errorOffset);
      });
    }
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

  @Override
  public void autoSelect() {
    if (component.isEditable()) {
      if (isAutoSelect()) {
        component.getEditor().selectAll();
      }
      else {
        component.getEditor().deselect();
      }
    }
  }

}

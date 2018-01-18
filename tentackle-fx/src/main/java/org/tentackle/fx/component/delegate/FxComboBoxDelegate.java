/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import java.util.Arrays;
import java.util.Objects;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxTextComponentDelegate;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.common.StringHelper;


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

    // deselect by DELETE- or BACKSPACE-key
    component.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (!event.isAltDown() && !event.isControlDown() && !event.isMetaDown() &&
          !event.isShiftDown() && !event.isShortcutDown()) {
        if ((event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) &&
            !component.isEditable() && isDeselectAllowed()) {
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
  public <T> T getViewValue() {
    Object value = getViewObject();
    if (component.isEditable()) {
      ValueTranslator translator = getValueTranslator();
      return (T) translator.toModel(value);
    }
    return (T) value;
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

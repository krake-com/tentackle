/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.geometry.Pos;
import javafx.scene.control.TextFormatter;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxPasswordField;


/**
 * Delegate for FxPasswordField.
 *
 * @author harald
 */
public class FxPasswordFieldDelegate extends AbstractTextFieldDelegate<FxPasswordField> {

  private boolean setColumnsInvoked;

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxPasswordFieldDelegate(FxPasswordField component) {
    super(component);
  }

  @Override
  protected ValueTranslator<?, ?> createValueTranslator(Class<?> type) {
    return char[].class == type ?
           FxFactory.getInstance().createValueTranslator(type, char[].class, getComponent()) :
           super.createValueTranslator(type);
  }

  @Override
  public TextFormatter.Change apply(TextFormatter.Change t) {
    if (isCharArray()) {
      return t;
    }
    return super.apply(t);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object getViewObject() {
    if (isCharArray()) {
      return getComponent().getPassword();
    }
    String text = getComponent().getText();
    return StringHelper.isAllWhitespace(text) ? null : text;
  }

  @Override
  public void setViewObject(Object viewObject) {
    if (viewObject instanceof char[]) {
      getComponent().setPassword((char[]) viewObject);
    }
    else {
      getComponent().setText((String) viewObject);
    }
  }

  @Override
  public void setColumns(int columns) {
    getComponent().setPrefColumnCount(columns);
    setColumnsInvoked = true;
  }

  @Override
  public int getColumns() {
    return getComponent().getPrefColumnCount();
  }

  @Override
  public void setMaxColumns(int maxColumns) {
    super.setMaxColumns(maxColumns);
    if (!setColumnsInvoked) {
      getComponent().setPrefColumnCount(maxColumns);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
    super.setViewValue(value);
    Pos alignment = getTextAlignment();
    if (alignment != null && getComponent().getAlignment() != alignment) {
      getComponent().setAlignment(alignment);
    }
  }

  @Override
  public void updateModel() {
    if (isCharArray() && getBinding() != null) {
      // fill the old model's buffer with blanks
      StringHelper.blank((char[]) getBinding().getModelValue());
    }
    super.updateModel();
  }

  private boolean isCharArray() {
    return getType() == null ? false : (char[].class == getType());
  }

}

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

import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxPasswordField;
import org.tentackle.misc.SubString;

import javafx.geometry.Pos;
import javafx.scene.control.TextFormatter;
import java.util.List;
import java.util.function.Function;

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

  @Override
  public void setAutoCompletion(Function<String, List<List<SubString>>> autoCompletion) {
    if (autoCompletion != null) {
      throw new FxRuntimeException("auto-completion is not allowed for password fields");
    }
  }

  private boolean isCharArray() {
    return char[].class == getType();
  }

}

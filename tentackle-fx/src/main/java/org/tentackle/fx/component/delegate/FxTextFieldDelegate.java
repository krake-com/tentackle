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

import javafx.geometry.Pos;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.component.FxTextField;

/**
 * Delegate for FxTextField.
 *
 * @author harald
 */
public class FxTextFieldDelegate extends AbstractTextFieldDelegate<FxTextField> {

  private boolean setColumnsInvoked;

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxTextFieldDelegate(FxTextField component) {
    super(component);
  }

  @Override
  @SuppressWarnings("unchecked")
  public String getViewObject() {
    String text = getComponent().getText();
    return StringHelper.isAllWhitespace(text) ? null : text;
  }

  @Override
  public void setViewObject(Object viewObject) {
    getComponent().setText((String) viewObject);
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

}

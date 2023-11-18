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
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxTextComponentDelegate;
import org.tentackle.fx.component.FxHTMLEditor;
import org.tentackle.misc.SubString;

import javafx.scene.Parent;
import java.util.List;
import java.util.function.Function;

/**
 * Delegate for FxHTMLEditor.
 *
 * @author harald
 */
public class FxHTMLEditorDelegate extends FxTextComponentDelegate {

  private final FxHTMLEditor component;   // the component

  private int columns;                    // dummy implementation, not supported by HTMLEditor

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxHTMLEditorDelegate(FxHTMLEditor component) {
    this.component = component;
  }

  @Override
  public FxHTMLEditor getComponent() {
    return component;
  }

  @Override
  public FxContainer getParentContainer() {
    Parent parent = component.getParent();
    return parent instanceof FxContainer ? (FxContainer) parent : null;
  }

  @Override
  public void setColumns(int columns) {
    this.columns = columns;
  }

  @Override
  public int getColumns() {
    return columns;
  }

  @Override
  public String getViewObject() {
    String text = component.getHtmlText();
    return StringHelper.isAllWhitespace(text) ? null : text;
  }

  @Override
  public void setViewObject(Object viewObject) {
    component.setHtmlText((String) viewObject);
  }

  @Override
  public void mapErrorOffsetToCaretPosition() {
    // @todo
  }

  @Override
  public void setAutoCompletion(Function<String, List<List<SubString>>> autoCompletion) {
    if (autoCompletion != null) {
      throw new FxRuntimeException("auto-completion is not supported by HTML editors");
    }
  }

}

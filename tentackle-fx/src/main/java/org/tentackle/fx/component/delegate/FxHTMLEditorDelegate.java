/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.component.delegate;

import javafx.scene.Parent;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxTextComponentDelegate;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxHTMLEditor;
import org.tentackle.common.StringHelper;

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
  @SuppressWarnings("unchecked")
  public String getViewObject() {
    String text = component.getHtmlText();
    return StringHelper.isAllWhitespace(text) ? null : text;
  }

  @Override
  public void setViewObject(Object viewObject) {
    component.setHtmlText((String) viewObject);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
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
  public void mapErrorOffsetToCaretPosition() {
    // @todo
  }

}

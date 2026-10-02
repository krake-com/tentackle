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

import org.tentackle.fx.FxComponentDelegate;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.misc.TrackedList;

/**
 * Delegate for FxTableView.
 *
 * @author harald
 */
public class FxTableViewDelegate extends FxComponentDelegate {

  private final FxTableView<?> component;   // the component
  private TrackedList<?> modelList;         // tracked list from model

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxTableViewDelegate(FxTableView<?> component) {
    this.component = component;
  }

  @Override
  public FxTableView<?> getComponent() {
    return component;
  }

  @Override
  public FxContainer getParentContainer() {
    Parent parent = component.getParent();
    return parent instanceof FxContainer ? (FxContainer) parent : null;
  }


  @Override
  public void setType(Class<?> type) {
    // important to set the type before creating the translator,
    // since the translator may need to know the type
    super.setType(type);
    if (getValueTranslator() == null) {    // if not already set by application
      setValueTranslator(FxFactory.getInstance().createValueTranslator(type, ObservableList.class, getComponent()));
    }
  }

  @Override
  @SuppressWarnings("rawtypes")
  public ObservableList getViewObject() {
    return component.getItems();
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void setViewObject(Object viewObject) {
    if (viewObject != component.getItems()) {   // identity is intended here: the same list
      component.setItems((ObservableList) viewObject);
      component.getSelectionModel().clearSelection();
      component.refresh();
    }
  }

  @Override
  public void setViewValue(Object value) {
    if (value instanceof TrackedList) {
      modelList = (TrackedList<?>) value;
    }
    else {
      modelList = null;
    }
    super.setViewValue(value);
  }

  @Override
  public boolean isViewModified() {
    if (modelList != null) {
      return modelList.isModified();
    }
    return super.isViewModified();
  }

  @Override
  public void triggerViewModified() {
    if (getParentContainer() != null) {
      getParentContainer().triggerViewModified();
    }
  }

  @Override
  public void updateModel() {
    // no conversion back to model because list is updated by ObservableList from the view.
  }

  @Override
  protected void updateChangeable(boolean changeable) {
    // doesn't make sense.
    // setEditable() makes the cell editable and setDisable makes
    // the table view not clickable.
  }

  @Override
  public void setContainerChangeable(boolean containerChangeable) {
    // ditto.
  }

}

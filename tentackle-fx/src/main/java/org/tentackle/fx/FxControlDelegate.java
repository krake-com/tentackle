/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Base implementation for all fx delegates.
 *
 * @author harald
 */
public abstract class FxControlDelegate implements FxControl {

  private boolean viewModified;
  private BooleanProperty viewModifiedProperty;

  /**
   * The changeable property in fact is a read-write property,
   * but exported as readonly.
   */
  private boolean changeable;
  private ReadOnlyBooleanWrapper changeableProperty;

  private boolean containerChangeableIgnored;

  private boolean bindable;

  private String helpUrl;

  /**
   * Low-level model-to-view listeners.
   */
  private Collection<ModelToViewListener> modelToViewListeners;

  /**
   * Low-level view-to-model listeners.
   */
  private Collection<ViewToModelListener> viewToModelListeners;



  /**
   * Creates a control delegate.
   */
  public FxControlDelegate() {
    changeable = true;
    bindable = true;
  }

  @Override
  public FxControlDelegate getDelegate() {
    return this;
  }


  @Override
  public void setViewModified(boolean viewModified) {
    if (viewModifiedProperty != null) {
      viewModifiedProperty.set(viewModified);
    }
    else {
      this.viewModified = viewModified;
    }
  }

  @Override
  public boolean isViewModified() {
    return viewModifiedProperty != null ? viewModifiedProperty.get() : viewModified;
  }

  @Override
  public BooleanProperty viewModifiedProperty() {
    if (viewModifiedProperty == null) {
      viewModifiedProperty = new SimpleBooleanProperty(viewModified);
    }
    return viewModifiedProperty;
  }

  @Override
  public void setChangeable(boolean changeable) {
    if (changeableProperty != null && !changeableProperty.isBound()) {
      // If the property is bound, it follows the control's disabled- or editable property
      // (see createChangeableProperty in the component delegates) and setting it would throw.
      // updateChangeable() below drives that property instead, which propagates to the binding.
      changeableProperty.set(changeable);
    }
    this.changeable = changeable;   // always keep the last setting (see isControlChangeable())
    updateChangeable(changeable);
  }

  /**
   * Gets the last intended changeability for this control.<br>
   * Might differ from effective changeability if the container changed its changeability.
   *
   * @return the control's changeability regardless of the effective changeability
   */
  protected boolean isControlChangeable() {
    return changeable;
  }


  @Override
  public boolean isChangeable() {
    if (changeableProperty != null) {
      return changeableProperty.get();
    }
    return changeable;
  }

  @Override
  public ReadOnlyBooleanProperty changeableProperty() {
    if (changeableProperty == null) {
      changeableProperty = createChangeableProperty(changeable);
    }
    return changeableProperty.getReadOnlyProperty();
  }

  /**
   * Updates other properties if changeable is bound to something else.
   *
   * @param changeable the flag
   */
  protected void updateChangeable(boolean changeable) {
    // the default does nothing
  }

  /**
   * Creates the changeable property.<br>
   * Implementations may bind the property to the property the changeability is mapped to,
   * such as the disabled- or the editable property. {@link #setChangeable(boolean)} then
   * leaves the property alone and updates the mapped property via {@link #updateChangeable(boolean)}.
   *
   * @param changeable the initial value
   * @return the property
   */
  protected ReadOnlyBooleanWrapper createChangeableProperty(boolean changeable) {
    return new ReadOnlyBooleanWrapper(changeable);
  }

  @Override
  public void setContainerChangeableIgnored(boolean containerChangeableIgnored) {
    this.containerChangeableIgnored = containerChangeableIgnored;
  }

  @Override
  public boolean isContainerChangeableIgnored() {
    return containerChangeableIgnored;
  }

  @Override
  public void setBindable(boolean bindable) {
    this.bindable = bindable;
  }

  @Override
  public boolean isBindable() {
    return bindable;
  }

  @Override
  public void setHelpUrl(String helpUrl) {
    this.helpUrl = helpUrl;
  }

  @Override
  public String getHelpUrl() {
    return helpUrl;
  }

  @Override
  public void showHelp() {
    FxUtilities.getInstance().showHelp(this);
  }

  @Override
  public void addModelToViewListener(ModelToViewListener listener) {
    getModelToViewListeners().add(listener);
  }

  @Override
  public void removeModelToViewListener(ModelToViewListener listener) {
    getModelToViewListeners().remove(listener);
  }

  @Override
  public void addViewToModelListener(ViewToModelListener listener) {
    getViewToModelListeners().add(listener);
  }

  @Override
  public void removeViewToModelListener(ViewToModelListener listener) {
    getViewToModelListeners().remove(listener);
  }

  /**
   * Gets the model-to-view listeners with lazy collection creation.
   *
   * @return the listeners, never null
   */
  public Collection<ModelToViewListener> getModelToViewListeners() {
    if (modelToViewListeners == null) {
      modelToViewListeners = new ArrayList<>();
    }
    return modelToViewListeners;
  }

  /**
   * Fires all model-to-view listeners.
   */
  public void fireModelToViewListeners() {
    if (modelToViewListeners != null) {
      for (ModelToViewListener listener: modelToViewListeners) {
        listener.modelChanged();
      }
    }
    FxContainer parentContainer = getParentContainer();
    if (parentContainer != null) {
      parentContainer.getDelegate().fireModelToViewListeners();
    }
  }

  /**
   * Gets the view-to-model listeners with lazy collection creation.
   *
   * @return the listeners, never null
   */
  public Collection<ViewToModelListener> getViewToModelListeners() {
    if (viewToModelListeners == null) {
      viewToModelListeners = new ArrayList<>();
    }
    return viewToModelListeners;
  }

  /**
   * Fires all model-to-view listeners.
   */
  public void fireViewToModelListeners() {
    if (viewToModelListeners != null) {
      for (ViewToModelListener listener: viewToModelListeners) {
        listener.viewChanged();
      }
    }
    FxContainer parentContainer = getParentContainer();
    if (parentContainer != null) {
      parentContainer.getDelegate().fireViewToModelListeners();
    }
  }

}

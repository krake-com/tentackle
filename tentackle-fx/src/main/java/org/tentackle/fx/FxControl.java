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

/**
 * Parent interface of all FxComponents and FxContainers.
 *
 * @author harald
 */
public interface FxControl {

  /**
   * Gets the lazily created component delegate.
   *
   * @return the delegate
   */
  FxControlDelegate getDelegate();

  /**
   * Gets the parent container.
   *
   * @return the parent, null if not a component of a container
   */
  FxContainer getParentContainer();

  /**
   * Sets the control to be changeable.<br>
   * This is a unified abstraction and will be translated to setEditable or
   * setDisabled, whatever is appropriate to this control.
   *
   * @param changeable true the user can edit the data, false if show only
   */
  void setChangeable(boolean changeable);

  /**
   * Returns if this control is changeable.
   *
   * @return true the user can edit the data, false if show only
   */
  boolean isChangeable();

  /**
   * Gets the changeable property.
   *
   * @return the property
   */
  ReadOnlyBooleanProperty changeableProperty();

  /**
   * Invoked when setChangeable() is invoked on the container of this component.
   *
   * @param containerChangeable true if container is changeable, false if all components readonly
   */
  void setContainerChangeable(boolean containerChangeable);

  /**
   * Sets whether to ignore setContainerChangeable.
   *
   * @param containerChangeableIgnored true if ignore, default is false
   */
  void setContainerChangeableIgnored(boolean containerChangeableIgnored);

  /**
   * Returns whether setContainerChangeable is ignored by this control.
   *
   * @return true if ignore
   */
  boolean isContainerChangeableIgnored();

  /**
   * Updates the view according to the model.
   */
  void updateView();

  /**
   * Updates the model according to the view.
   */
  void updateModel();

  /**
   * Creates a copy of the value shown to the user.
   */
  void saveView();

  /**
   * Invalidates the saved view.
   */
  void invalidateSavedView();

  /**
   * Walks up the component hierarchy and updates the viewModified property.<br>
   * This is done by comparing the saved view value with the current view.
   */
  void triggerViewModified();

  /**
   * Sets whether the view has been modified.
   *
   * @param viewModified true if user has modified the visual representation
   */
  void setViewModified(boolean viewModified);

  /**
   * Returns whether the user has modified the view since the last {@link #saveView}.
   *
   * @return true if user changed the visual representation
   */
  boolean isViewModified();

  /**
   * Gets the viewModified property.
   *
   * @return the property
   */
  BooleanProperty viewModifiedProperty();

  /**
   * Sets whether this control is bindable, i.e. should be
   * checked within a binding hierarchy.
   *
   * @param bindable true if bindable, else not eligible for the binding process
   */
  void setBindable(boolean bindable);

  /**
   * Determines whether this control is bindable.
   *
   * @return true if bindable, else not eligible for the binding process
   */
  boolean isBindable();

  /**
   * Sets the online-help URL.<br>
   * If help is requested for a control without a help URL,
   * the URL of the nearest parent container providing one is shown in the browser.
   *
   * @param helpUrl the help url
   */
  void setHelpUrl(String helpUrl);

  /**
   * Gets the online-help URL.
   *
   * @return the help url, default is null
   */
  String getHelpUrl();

  /**
   * Displays online help if configured.
   */
  void showHelp();

  /**
   * Returns the classname plus optional fx:id.
   *
   * @return the generic string for diagnostics and logging
   */
  String toGenericString();

  /**
   * Adds a model-to-view listener.<br>
   * The listener will be invoked before the binding operation.<br>
   * If the control is a container, the listener will be invoked if any of
   * the container's components get updated.
   *
   * @param listener the value listener to add
   */
  void addModelToViewListener (ModelToViewListener listener);

  /**
   * Removes a model-to-view listener.
   *
   * @param listener the value listener to remove
   */
  void removeModelToViewListener (ModelToViewListener listener);

  /**
   * Adds a view-to-model listener.<br>
   * The listener will be invoked after the binding operation.<br>
   * If the control is a container, the listener will be invoked if any of
   * the container's components update the model.
   *
   * @param listener the value listener to add
   */
  void addViewToModelListener (ViewToModelListener listener);

  /**
   * Removes a view-to-model listener.
   *
   * @param listener the value listener to remove
   */
  void removeViewToModelListener (ViewToModelListener listener);

}

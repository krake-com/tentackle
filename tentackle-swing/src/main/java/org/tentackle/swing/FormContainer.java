/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.swing;

import java.awt.*;

import org.tentackle.swing.bind.FormComponentBinder;


/**
 * Tentackle GUI-containers must implement this interface.
 * <p>
 * FormContainers are aware of the data binding and provide
 * some more features like online help.
 *
 * @author harald
 */
public interface FormContainer extends FormChangeable {

  /**
   * set the (optional) title.<br>
   * If the container is a window, the title is the window-title.
   * If the container is a panel, the title is "internal", i.e. may be used
   * by the application to trigger setting the window title, for example.
   *
   * @param title the container's title, null if none
   */
  void setTitle(String title);

  /**
   * Gets the container's title.
   *
   * @return the container's title, null if none
   */
  String getTitle();


  /**
   * Sets the values in all form components in this container and all
   * sub containers.<br>
   * Used to update the view from the data according to the data binding.
   */
  void setFormValues();


  /**
   * Retrieves the values from all components in this container and
   * all sub containers.<br>
   * Used to update the data from the view according to the data binding.
   */
  void getFormValues();


  /**
   * Sets the values in all unchanged form components in this container and
   * all sub containers.<br>
   * All changed components are left untouched.
   *
   * @see FormComponent#isValueChanged()
   */
  void setFormValuesKeepChanged();


  /**
   * Sets a savepoint for all components in this container and
   * all sub containers.
   * @see FormComponent#saveValue()
   */
  void saveValues();


  /**
   * Checks whether the contents of some components in this or sub containers
   * have changed since the last {@link #saveValues()}.
   * <p>
   * Notice that only components with ({@link FormComponent#isTriggerValueChangedEnabled()} == true)
   * will be checked.
   *
   * @return true if changed
   */
  boolean areValuesChanged();


  /**
   * Signals that values in the components of the container may have changed.
   * The container typically uses that to invoke areValuesChanged() and enabling/disabling
   * a button (e.g. "save", see PdoEditDialog).
   */
  void triggerValuesChanged();


  /**
   * Enables triggering value changes.<br>
   * Disabling this feature may be necessary for model-agnostic containers.
   *
   * @param enabled true if invoke {@link #triggerValuesChanged()} on each change (default), false if not.
   */
  void setTriggerValuesChangedEnabled(boolean enabled);

  /**
   * Determines whether triggering value changes is enabled.
   *
   * @return true if invoke {@link #triggerValuesChanged()} on each change (default), false if not.
   */
  boolean isTriggerValuesChangedEnabled();


  /**
   * Sets the auto-update feature for all components in this container and all sub containers.<br>
   *
   * @param autoUpdate true if auto update the data (default), false if not
   * @see FormComponent#setAutoUpdate(boolean)
   */
  void setAutoUpdate (boolean autoUpdate);

  /**
   * Returns whether the auto-update feature is enabled.
   *
   * @return true if auto update the data (default), false if not
   * @see FormComponent#isAutoUpdate()
   */
  boolean isAutoUpdate();


  /**
   * Gets the parent window.
   *
   * @return the parent window, null if none
   */
  Window getParentWindow();


  /**
   * Invalidates all container-relevant (cached) information
   * for the hierarchy of this container's parents.<br>
   * For optimization purposes.
   */
  void invalidateParentInfo();


  /**
   * Sets the online-help URL.<br>
   * If a help url is set for this container, a browser will
   * be launched to show that url if there is no help url
   * for the component the help was requested for.
   *
   * @param helpURL the help url
   * @see FormUtilities#openHelpURL(Component) 
   */
  void setHelpURL(String helpURL);

  /**
   * Gets the online-help URL.
   * @return the help url, default is null
   */
  String getHelpURL();

  /**
   * Displays online help for this container.
   */
  void showHelp();



  // ------------------ binding --------------------------------------

  /**
   * Gets the binder of this container.
   *
   * @return the binder, never null.
   */
  FormComponentBinder getBinder();

  /**
   * Sets whether this container is bindable, i.e. should be
   * checked within a binding hierarchy.
   * <p>
   * FormContainers are bindable by default.
   *
   * @param bindable true if bindable, else not eligible for the binding process
   */
  void setBindable(boolean bindable);

  /**
   * Determines whether this container is bindable.
   *
   * @return true if bindable, else not eligible for the binding process
   */
  boolean isBindable();

}
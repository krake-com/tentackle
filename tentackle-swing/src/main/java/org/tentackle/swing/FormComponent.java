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

import org.tentackle.swing.bind.FormComponentBinding;

/**
 * Interface all Tentackle form components must implement.
 * <p>
 * Tentackle beans derived from Swing components that the user interacts
 * with in order to edit some data are {@code FormComponent}s.
 * FormComponents are aware of the binding between the data and the GUI
 * and provide a standardized way to update the view and the data, vice versa.
 * This simplifies the development and design of the GUI significantly.
 *
 * @author harald
 */
public interface FormComponent extends FormChangeableComponent {

  /**
   * Sets a value in this component.<br>
   * The method is used to update the view if the data has changed.
   *
   * @param object is the object to set
   */
  void setFormValue (Object object);

  /**
   * Retrieves the value from this component.<br>
   * The method is used to update the data model if the view has changed because
   * the user edited it.
   *
   * @return  the object from the form
   */
  Object getFormValue();


  /**
   * Gets the value shown to the user.<br>
   * This is the visual representation, not necessarily
   * the model's value. Used to implemement smartValueEntered.
   *
   * @return the value visible to the user
   */
  Object getValueShown();

  /**
   * Checks whether the currently shown value differs
   * from the initially shown.<br>
   * Used to implement smartValueEntered.
   *
   * @return true if user changed the visual representation
   */
  boolean isValueShownModified();

  /**
   * Clears the value shown status.<br>
   * May be used by components that update the value shown
   * programmatically.
   */
  void clearValueShownModified();

  /**
   * Sets whether the model should only be updated
   * if the component's value really changed.<br>
   * The default is true.
   *
   * @param smartValueEntered true if optimize, false if always update the model
   */
  void setSmartValueEntered(boolean smartValueEntered);

  /**
   * Returns whether the model should only be updated
   * if the component's value really changed.<br>
   * The default is true.
   *
   * @return true if optimize, false if always update the model
   */
  boolean isSmartValueEntered();



  /**
   * Sets a savepoint for the data of this component.<br>
   * Allows to decide whether the user has changed the data associated
   * with this component or not.
   * @see #isValueChanged()
   */
  void saveValue();


  /**
   * Checks whether the contents have been changed since the last savepoint.
   *
   * @return true if changed
   * @see #saveValue()
   */
  boolean isValueChanged();


  /**
   * Promotes the fact that value changed to all {@link FormContainer}s this
   * component is a child of.
   */
  void triggerValueChanged();


  /**
   * Enables triggering value changes.<br>
   * Disabling this feature may be necessary for model-agnostic components.
   *
   * @param enabled true if invoke {@link #triggerValueChanged()} on each change (default), false if not.
   */
  void setTriggerValueChangedEnabled(boolean enabled);

  /**
   * Determines whether triggering value changes is enabled.
   *
   * @return true if invoke {@link #triggerValueChanged()} on each change (default), false if not.
   */
  boolean isTriggerValueChangedEnabled();


  /**
   * Sets the auto-update feature.<br>
   * By default, all components update the data model if
   * the view changes (due to certain events, for example focus lost).
   *
   * @param autoUpdate true if auto update the data (default), false if not
   */
  void setAutoUpdate (boolean autoUpdate);

  /**
   * Returns whether the auto-update feature is enabled.
   *
   * @return true if auto update the data (default), false if not
   */
  boolean isAutoUpdate();


  /**
   * Adds a value listener that implements the binding between this component
   * and the data.
   *
   * @param listener the value listener to add
   */
  void addValueListener (ValueListener listener);

  /**
   * Remove a value listener.
   *
   * @param listener the value listener to remove
   */
  void removeValueListener (ValueListener listener);


  /**
   * Fires all value listeners registered on this component
   * invoking their {@code valueChanged}-handler.<br>
   * This method is invoked whenever the data has been changed
   * and that change should be reflected in the view.
   */
  void fireValueChanged();


  /**
   * Fires all listeners registered on this component
   * invoking their {@code valueEntered}-handler.<br>
   * This method is invoked whenever the user edited the view
   * and that change should be reflected in the data.
   */
  void fireValueEntered();


  /**
   * Sets the mandatory option.<br>
   * Mandatory fields are differently visualized.
   * There is no further functional difference.
   *
   * @param mandatory true if mandatory
   */
  void setMandatory(boolean mandatory);

  /**
   * Gets the mandatory option.
   * @return true if mandatory
   */
  boolean isMandatory();


  /**
   * Sets a component's flag that a fire-method is currently running.<br>
   * Used by the framework to minimize valueChanged/valueEntered processing.
   *
   * @param fireRunning true if some fire-method is running
   */
  void setFireRunning(boolean fireRunning);

  /**
   * Checks whether a fire-method is running.
   *
   * @return true if some fire-method is running
   */
  boolean isFireRunning();


  /**
   * Returns whether this component lost the keyboard focus due to {@link #transferFocus()}.
   * @return true if focus lost due to transferFocus
   */
  boolean wasTransferFocus();


  /**
   * Returns whether this component lost the keyboard focus due to {@link #transferFocusBackward()}.
   * @return true if focus lost due to transferFocusBackward
   */
  boolean wasTransferFocusBackward();


  /**
   * Returns whether this component lost the keyboard focus due to {@link java.awt.event.KeyEvent#VK_ENTER}.
   * @return true if pressing the Enter/Return key caused a focus lost
   */
  boolean wasTransferFocusByEnter();


  /**
   * Returns whether the keyboard focus was gained due to {@link #transferFocus()}
   * in the opposite component.
   * @return true if this component gained the focus from the logically previous component
   * @see FormFocusTraversalPolicy
   */
  boolean wasFocusGainedFromTransfer();

  /**
   * Returns whether the keyboard focus was gained due to {@link #transferFocusBackward()}
   * in the opposite component.
   *
   * @return true if this component gained the focus from the logically next component
   * @see FormFocusTraversalPolicy
   */
  boolean wasFocusGainedFromTransferBackward();


  /**
   * Requests the keyboard focus for this component by appending
   * an event to the end of the event queue.<br>
   * Applications should not use requestFocus() or requestFocusInWindow()
   * unless the order in which the events are processed is unimportant.
   * This method guarantees that this component gets the focus when
   * all events queued so far have been processed.
   */
  void requestFocusLater();


  /**
   * Sets the window that will fire a {@link FormWrapEvent} when this
   * components loses the keyboard focus. When fired the reference
   * to the window is cleared.
   *
   * @param window the window (usually the parent window of this component).
   */
  void setFormWrapWindow(FormWindow window);


  /**
   * Gets the parent window of this component.
   *
   * @return the parent window, null if none
   */
  Window getParentWindow();


  /**
   * Invalidates all container-relevant (cached) information
   * for the hierarchy of this component's parents.<br>
   * For optimization purposes.
   */
  void invalidateParentInfo();


  /**
   * Sets the online-help URL.<br>
   * If a help url is set for this component, a browser will
   * be launched to show that url. If there is no url
   * the next higher level container will determine the url,
   * and so forth.
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
   * Displays online help for this component.<br>
   * Usually triggered by KeyEvent.VK_F1 or VK_HELP or a button.
   * If help is not configured for this component, the parent's
   * help is used.
   */
  void showHelp();


  /**
   * Shows the error popup for a given message.<br>
   * The popup component used is an {@link ErrorToolTip}.
   *
   * @param message the message to be displayed, null to remove
   */
  void showErrorPopup(String message);


  /**
   * Sets a hint that this component is used as a cell editor.<br>
   * Some components behave differently in tables than in forms, for example.
   *
   * @param cellEditorUsage true if component is a cell editor, false if not.
   */
  void setCellEditorUsage(boolean cellEditorUsage);

  /**
   * Returns whether this component is used as a cell editor.
   *
   * @return true true if component is a cell editor, false if not.
   */
  boolean isCellEditorUsage();


  /**
   * Prepares for FOCUS_LOST.<br>
   * Gives this component the opportunity to invoke fireValueEntered() earlier
   * than FOCUS_LOST is really received. Useful because the next focused component
   * is determined before FOCUS_LOST is sent to this component. Thus, if this component
   * changes the focusability of the next component, the focus policy would
   * probably pick the wrong component otherwise.
   */
  void prepareFocusLost();


  /**
   * Sets whether this component is form traversable or not, i.e.
   * gets the focus or is skipped.
   *
   * @param formTraversable true if traversable, false if not
   */
  void setFormTraversable(boolean formTraversable);

  /**
   * Returns whether this component is form traversable or not.
   *
   * @return the focus traversable flag
   */
  boolean isFormTraversable();


  /**
   * Sets the focus traversal group for this component.
   *
   * @param group the focus traversal group
   */
  void setFocusTraversalGroup(FocusTraversalGroup group);

  /**
   * Gets the focus traversal group for this component.
   *
   * @return the focus traversal group
   */
  FocusTraversalGroup getFocusTraversalGroup();


  /**
   * Sets the property group for this component.
   *
   * @param group the property group
   */
  void setPropertyGroup(PropertyGroup group);

  /**
   * Gets the property group for this component.
   *
   * @return the property group
   */
  PropertyGroup getPropertyGroup();



  // ------------------- binding -------------------------------------

  /**
   * Sets the path to bind this component to.
   * <p>
   * Usually the binding path will be determined from the components declared name.
   * However, it can be set programatically as well.
   *
   * @param bindingPath the binding path, for ex. <tt>"invoiceNumber"</tt>, null if autobinding
   * @see org.tentackle.bind.Binder
   */
  void setBindingPath(String bindingPath);

  /**
   * Gets the binding path.
   *
   * @return the field path, null if autobinding (default)
   */
  String getBindingPath();

  /**
   * Sets the component path.
   * <p>
   * The path is <tt>&lt;declaring-class-name&gt;.&lt;field-name&gt;</tt>.
   * Example <tt>"de.krake.invoicer.InvoicePanel.customerNumberField"</tt>.
   * <p>
   * Notice: the component path is set during binding.
   *
   * @param componentPath the component path
   */
  void setComponentPath(String componentPath);

  /**
   * Gets the component path.
   * @return the component path, null if not bound
   */
  String getComponentPath();


  /**
   * Sets the binding.
   *
   * @param binding the binding, null if none.
   */
  void setBinding(FormComponentBinding binding);

  /**
   * Gets the binding.
   *
   * @return the binding, null if none
   */
  FormComponentBinding getBinding();

  /**
   * Sets whether this component is bindable.
   * <p>
   * FormComponents are bindable by default.
   *
   * @param bindable true if bindable, else not eligible for the binding process
   */
  void setBindable(boolean bindable);

  /**
   * Determines whether this component is bindable.
   *
   * @return true if bindable, else not eligible for the binding process
   */
  boolean isBindable();


  // ------------------- Swing standard methods ----------------------


  /**
   * Returns whether this component has the keyboard focus.
   * @return true if has focus
   */
  boolean hasFocus();


  /**
   * Transfers keyboard focus forward to the logically next component.
   * @see FormFocusTraversalPolicy
   */
  void transferFocus();


  /**
   * Transfers keyboard focus forward to the logically previous component.
   * @see FormFocusTraversalPolicy
   */
  void transferFocusBackward();


  /**
   * Returns the baseline of this component.<br>
   *
   * @param width the width to get the baseline for
   * @param height the height to get the baseline for
   * @return the baseline
   * @since 1.6
   */
  int getBaseline(int width, int height);

}
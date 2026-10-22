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


package org.tentackle.bind;

import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.Validator;

import java.util.List;


/**
 * Binding between a class member and some related component.
 *
 * @author harald
 */
public interface Binding {

  /**
   * Gets the binder managing this binding.
   *
   * @return the binder
   */
  Binder getBinder();

  /**
   * Gets the members building the declaration chain to this binding member.
   *
   * @return the parents, null if this is a member of the container
   */
  BindingMember[] getParents();

  /**
   * Gets the member field which is bound to the component.
   *
   * @return the field, never null
   */
  BindingMember getMember();

  /**
   * Gets the object containing the declaration of the field.
   *
   * @return the object, null if binding path contains null references
   */
  Object getParentObject ();

  /**
   * Gets the value of the model to be set into the view.
   *
   * @return the field's value
   */
  Object getModelValue();

  /**
   * Sets the model value.
   *
   * @param value the value from the view
   */
  void setModelValue(Object value);

  /**
   * Gets the view's value to be set into the model.
   *
   * @return the view's value
   */
  Object getViewValue();

  /**
   * Sets the view's value.
   *
   * @param value the value from the model
   */
  void setViewValue(Object value);

  /**
   * Adds a to-view binding listener.<br>
   * The binding listener will be invoked before each
   * binding operation from the model to the view.
   *
   * @param l the listener
   */
  void addToViewListener(ToViewListener l);

  /**
   * Removes a to-view binding listener.
   *
   * @param l the listener
   */
  void removeToViewListener(ToViewListener l);

  /**
   * Gets the list of all to-view binding listeners.
   *
   * @return the listeners
   */
  List<ToViewListener> getToViewListeners();

  /**
   * Adds a to-model binding listener.<br>
   * The binding listener will be invoked before each
   * binding operation from the view to the model.
   *
   * @param l the listener
   */
  void addToModelListener(ToModelListener l);

  /**
   * Removes a to-model binding listener.
   *
   * @param l the listener
   */
  void removeToModelListener(ToModelListener l);

  /**
   * Gets the list of all to-model binding listeners.
   *
   * @return the listeners
   */
  List<ToModelListener> getToModelListeners();

  /**
   * Invokes {@link ToViewListener#toView(org.tentackle.bind.BindingEvent) }
   * for all registered listeners.<br>
   * If one of the listeners fires a {@link BindingVetoException}
   * the value will not be set into the view.
   *
   * @param parent the parent object the value is retrieved from
   * @param modelValue the model value
   * @throws BindingVetoException if the value should not be set into the view
   */
  void fireToView(Object parent, Object modelValue) throws BindingVetoException;

  /**
   * Invokes {@link ToModelListener#toModel(org.tentackle.bind.BindingEvent) }
   * for all registered listeners.<br>
   * If one of the listeners fires a {@link BindingVetoException}
   * the value will not be set into the model.
   *
   * @param parent the parent object containing the value to set
   * @param viewValue the view value
   * @throws BindingVetoException if the value should not be set into the model
   */
  void fireToModel(Object parent, Object viewValue) throws BindingVetoException;

  /**
   * Creates a binding event.
   *
   * @param toView true if binding operation from model to view, false if opposite
   * @param parent the parent object containing the value
   * @param value the value to be transferred
   * @return the created event
   */
  BindingEvent createBindingEvent(boolean toView, Object parent, Object value);

  /**
   * Adds a validation listener.<br>
   * The validation listener will be invoked if validations
   * are present for this binding (annotations at the field
   * or getter-method) and the validation has failed or
   * has messages.
   *
   * @param l the listener
   */
  void addValidationListener(ValidationListener l);

  /**
   * Removes a validation listener.
   *
   * @param l the listener
   */
  void removeValidationListener(ValidationListener l);

  /**
   * Gets the list of all validation listeners.
   *
   * @return the listeners
   */
  List<ValidationListener> getValidationListeners();

  /**
   * Adds a validator.
   *
   * @param validator the validator to add
   * @return true if added, false if already added
   */
  boolean addValidator(Validator validator);

  /**
   * Removes a validator.
   *
   * @param validator the validator to remove
   * @return true if removed
   */
 boolean removeValidator(Validator validator);

  /**
   * Gets the list of validators.
   *
   * @return the validators, null if none
   */
  List<Validator> getValidators();

  /**
   * Validates the binding.<br>
   * Applies the validators to the bound member and fires the validation events.
   *
   * @return the validation results, never null, empty if no validators or nothing to validate
   */
  List<ValidationResult> validate();

  /**
   * Invokes {@link ValidationListener#validated(ValidationEvent)}
   * for all registered listeners.
   *
   * @param results the validation results
   */
  void fireValidated(List<ValidationResult> results);

  /**
   * Creates a validation event.
   *
   * @param results the validation results (never null)
   * @return the created event
   */
  ValidationEvent createValidationEvent(List<ValidationResult> results);

  /**
   * Updates the mandatory attribute of the bound element.
   *
   * @param mandatory true if mandatory, else false
   */
  void setMandatory(boolean mandatory);

  /**
   * Gets the current mandatory state of the bound element.
   *
   * @return true if bound element is mandatory
   */
  boolean isMandatory();

  /**
   * Updates the changeable attribute of the bound element.
   *
   * @param changeable true if changeable, else false
   */
  void setChangeable(boolean changeable);

  /**
   * Gets the current changeable state of the bound element.
   *
   * @return true if bound element is changeable
   */
  boolean isChangeable();

}

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

import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.validate.DefaultScope;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.Validator;
import org.tentackle.validate.ValidatorCache;
import org.tentackle.validate.scope.AllScope;
import org.tentackle.validate.scope.ChangeableScope;
import org.tentackle.validate.scope.MandatoryScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Common implementation of a binding.
 * <p>
 * Transfers the values between model and view. The listeners of the binding are fired before
 * those of the binder. If a listener vetoes, the transfer is skipped and, if requested, the
 * other side is resynchronized.
 *
 * @author harald
 */
public abstract class AbstractBinding implements Binding {

  private static final Logger LOGGER = Logger.get(AbstractBinding.class);



  // used to grab both kinds of validators: binder's validation scope and MandatoryScope
  private class BindingScopeImpl implements ValidationScope {

    @Override
    public boolean appliesTo(Class<?>... scopes) {
      if (scopes.length == 1 && DefaultScope.class.isAssignableFrom(scopes[0])) {
        // the default scope applies
        return true;
      }
      for (Class<?> scope: scopes) {
        if (scope.isAssignableFrom(AllScope.class) ||
            scope.isAssignableFrom(MandatoryScope.class) ||
            scope.isAssignableFrom(ChangeableScope.class) ||
            scope.isAssignableFrom(binder.getValidationScope().getClass())) {
          return true;
        }
      }
      return false;
    }

    @Override
    public String getName() {
      return "binding";
    }

    @Override
    public String toString() {
      return getName() + "-scope";
    }
  }



  private final Binder binder;                            // the binder managing this binding
  private final BindingMember[] parents;                  // the fields building the declaration chain to this field, null if container
  private final BindingMember member;                     // the bound member
  private List<ToViewListener> toViewListeners;           // to-view binding event listener list
  private List<ToModelListener> toModelListeners;         // to-model binding event listener list
  private List<ValidationListener> validationListeners;   // validation event listener list
  private List<Validator> validators;                     // the validators, null if none

  /**
   * Creates a binding.
   *
   * @param binder the binder managing this binding
   * @param parents the members building the declaration chain to this member, null if this binding's member is in container
   * @param member the member field to bind
   */
  public AbstractBinding(Binder binder, BindingMember[] parents, BindingMember member) {
    this.binder  = Objects.requireNonNull(binder, "binder");
    this.parents = parents;
    this.member  = Objects.requireNonNull(member, "member");
  }

  @Override
  public String toString() {
    return member + " <-> " + viewComponentToString();
  }

  @Override
  public Binder getBinder() {
    return binder;
  }

  @Override
  public BindingMember[] getParents() {
    return parents;
  }

  @Override
  public BindingMember getMember() {
    return member;
  }

  @Override
  public void setViewValue(Object value) {
    updateView(getParentObject(), value, true);
  }

  @Override
  public void setModelValue(Object value) {
    setModelValue(getParentObject(), value, true);
  }

  @Override
  public BindingEvent createBindingEvent(boolean toView, Object parent, Object value) {
    return new BindingEvent(this,
                            (toView ?
                             (member.getGetter() != null ?
                              BindingEvent.Type.TO_VIEW_VIA_GETTER : BindingEvent.Type.TO_VIEW_VIA_FIELD) :
                             (member.getSetter() != null ?
                              BindingEvent.Type.TO_MODEL_VIA_SETTER :
                              (member.getFrom() != null ?
                               BindingEvent.Type.TO_MODEL_VIA_FROM_METHOD : BindingEvent.Type.TO_MODEL_VIA_FIELD))
                            ),
                            parent, value);
  }

  @Override
  public synchronized void addToViewListener(ToViewListener l) {
    getToViewListeners().add(l);
  }

  @Override
  public synchronized void removeToViewListener(ToViewListener l) {
    getToViewListeners().remove(l);
  }

  @Override
  public List<ToViewListener> getToViewListeners() {
    if (toViewListeners == null) {
      toViewListeners = new ArrayList<>();
    }
    return toViewListeners;
  }

  @Override
  public synchronized void addToModelListener(ToModelListener l) {
    getToModelListeners().add(l);
  }

  @Override
  public synchronized void removeToModelListener(ToModelListener l) {
    getToModelListeners().remove(l);
  }

  @Override
  public List<ToModelListener> getToModelListeners() {
    if (toModelListeners == null) {
      toModelListeners = new ArrayList<>();
    }
    return toModelListeners;
  }

  @Override
  public synchronized void addValidationListener(ValidationListener l) {
    getValidationListeners().add(l);
  }

  @Override
  public synchronized void removeValidationListener(ValidationListener l) {
    getValidationListeners().remove(l);
  }

  @Override
  public List<ValidationListener> getValidationListeners() {
    if (validationListeners == null) {
      validationListeners = new ArrayList<>();
    }
    return validationListeners;
  }

  @Override
  public boolean addValidator(Validator validator) {
    if (validators == null) {
      validators = new ArrayList<>();
    }
    else if (validators.contains(validator)) {
      return false;
    }
    validators.add(validator);
    return true;
  }

  @Override
  public boolean removeValidator(Validator validator) {
    return validators != null && validators.remove(validator);
  }

  @Override
  public List<Validator> getValidators() {
    return validators;
  }

  @Override
  public void fireToView(Object parent, Object modelValue) throws BindingVetoException {
    BindingEvent evt = null;
    if (toViewListeners != null) {
      for (ToViewListener l: toViewListeners) {
        if (evt == null) {
          evt = createBindingEvent(true, parent, modelValue);
        }
        l.toView(evt);
      }
    }
  }

  @Override
  public void fireToModel(Object parent, Object viewValue) throws BindingVetoException {
    BindingEvent evt = null;
    if (toModelListeners != null) {
      for (ToModelListener l: toModelListeners) {
        if (evt == null) {
          evt = createBindingEvent(false, parent, viewValue);
        }
        l.toModel(evt);
      }
    }
  }

  @Override
  public Object getParentObject () {
    return getParentObject(null);
  }

  @Override
  public Object getModelValue() {
    return getModelValue(getParentObject());
  }

  @Override
  public ValidationEvent createValidationEvent(List<ValidationResult> results) {
    return new ValidationEvent(this, results);
  }

  @Override
  public void fireValidated(List<ValidationResult> results) {
    if (validationListeners != null) {
      ValidationEvent evt = null;
      for (ValidationListener l: validationListeners) {
        if (evt == null) {
          evt = createValidationEvent(results);
        }
        l.validated(evt);
      }
    }
  }

  @Override
  public List<ValidationResult> validate() {
    Object parentObject = getParentObject();
    if (validators != null && parentObject != null && isValidationRequired()) {
      List<ValidationResult> results = ValidationUtilities.getInstance().validateObject(
              validators, binder.getValidationScope(), member.getMemberPath(),
              parentObject, getModelValue(), member.getType());
      fireValidated(results);
      binder.fireValidated(this, results);
      return results;
    }
    return Collections.emptyList();
  }


  /**
   * Sets the model value.
   *
   * @param parent the parent object
   * @param value the value from the view
   * @param honourResyncByVetoException true if {@link BindingVetoException#isResyncRequested()} should be honoured
   */
  protected void setModelValue(Object parent, Object value, boolean honourResyncByVetoException) {

    LOGGER.fine(() -> member + " <- " + viewComponentToString() + " = " + StringHelper.objectToLoggableString(value));

    try {
      // set the value into the view
      if (parent != null) {
        // check listeners for a veto
        fireToModel(parent, value);
        binder.fireToModel(this, parent, value);
        // if all references are valid
        Object updatedParent = member.setObject(parent, value);
        if (updatedParent != null) {
          // value was updated via from-method creating a new parent object: we must walk up parent objects until we find a setter
          boolean updated = false;
          int parentIndex = parents.length - 1;
          while (parentIndex >= 0) {
            BindingMember parentMember = parents[parentIndex];
            if (parentMember.isReadOnly()) {
              // check that in order not to ignore missing setters/fields since the from-method worked initially
              break;
            }
            updatedParent = parentMember.setObject(getParentObject(parentMember), updatedParent);
            if (updatedParent == null) {
              updated = true;
              break;
            }
            parentIndex--;
          }
          if (!updated) {
            throw new BindingException(member + ": could not update parent object created by from-method");
          }
        }
      }
      else  {
        LOGGER.fine("(some) object reference was null, {0} not changed", member);
      }
    }
    catch (BindingVetoException veto) {
      LOGGER.fine(() -> {
        String msg = "veto for value " + StringHelper.objectToLoggableString(value) +
                     " from " + viewComponentToString() +
                     " to " + member;
        if (veto.getMessage() != null) {
          msg += ": " + veto.getMessage();
        }
        return msg;
      });

      if (honourResyncByVetoException && veto.isResyncRequested()) {
        // update the view with the old value from the model
        LOGGER.fine("view resynced from model");
        updateView(parent, getModelValue(parent), false);
      }

      if (veto.isRethrowEnabled()) {
        throw new BindingException(veto.getLocalizedMessage(), veto);
      }
    }
    catch (BindingException ex) {
      // improve exception message
      throw new BindingException(member + ": " + ex.getLocalizedMessage(), ex);
    }
  }


  /**
   * Updates the view.
   *
   * @param parent the parent object
   * @param value the value from the model
   * @param honourResyncByVetoException true if {@link BindingVetoException#isResyncRequested()} should be honoured
   */
  protected void updateView(Object parent, Object value, boolean honourResyncByVetoException) {
    // note: if parent == null value will also be null,
    // so the component gets its value cleared
    try {
      // check listeners for a veto
      fireToView(parent, value);
      binder.fireToView(this, parent, value);
      // update the view
      updateView(value);
    }
    catch (BindingVetoException veto) {
      LOGGER.fine(() -> {
        String msg = "veto for value " + StringHelper.objectToLoggableString(value) +
                     " from " + member + " to " + viewComponentToString();
        if (veto.getMessage() != null) {
          msg += ": " + veto.getMessage();
        }
        return msg;
      });

      if (honourResyncByVetoException && veto.isResyncRequested()) {
        // update the model with the current value of the view
        LOGGER.fine("model resynced from view");
        setModelValue(parent, getViewValue(), false);
      }

      if (veto.isRethrowEnabled()) {
        throw new BindingException(veto.getLocalizedMessage(), veto);
      }
    }
  }


  /**
   * Gets the value of the field to be set into the component.
   *
   * @param parentObject the parent object
   * @return the field's value
   */
  protected Object getModelValue(Object parentObject) {
    try {
      Object modelValue = parentObject == null ? null : member.getObject(parentObject);
      LOGGER.fine(() -> viewComponentToString() + " <- " + member + " = " + StringHelper.objectToLoggableString(modelValue) +
                            (parentObject == null ? "\n(some) object reference was null, " + viewComponentToString() + " not changed" : ""));
      return modelValue;
    }
    catch (BindingException ex) {
      // improve exception message
      throw new BindingException(member + ": " + ex.getLocalizedMessage(), ex);
    }
  }


  /**
   * Determines the view's component type.
   *
   * @return the component's type
   */
  protected abstract Class<?> getViewType();


  /**
   * Gets the root object bound to the view.
   *
   * @return the root object
   */
  protected abstract Object getBoundRootObject();


  /**
   * Determines whether the view is changed in such a way that it
   * requires validation.
   *
   * @return true if validation required
   */
  protected abstract boolean isValidationRequired();


  /**
   * Gets the string representation of the bound view element.
   *
   * @return the string
   */
  protected abstract String viewComponentToString();


  /**
   * Updates the view.
   *
   * @param value the value to be shown in the view
   */
  protected abstract void updateView(Object value);


  /**
   * Determines the validators for this member.<br>
   * Only validators applying to the binder's validation scope, the {@link MandatoryScope},
   * the {@link ChangeableScope} or the {@link AllScope} are kept.
   */
  protected void determineValidators() {
    List<Validator> fieldValidators = ValidatorCache.getInstance().getFieldValidators(member.getField(), member.getGetter());
    // filter scope (scope will not change)
    validators = new ArrayList<>();
    ValidationScope scope = new BindingScopeImpl();
    for (Validator fieldValidator: fieldValidators) {
      if (scope.appliesTo(fieldValidator.getConfiguredScopes(null))) {
        validators.add(fieldValidator);
      }
    }
    if (validators.isEmpty()) {
      validators = null;  // free to GC
    }
  }


  /**
   * Retrieves the parent object by walking along the member path starting at the root object.
   *
   * @param stopMember the optional member where to stop (necessary if from-methods are used)
   * @return the parent object
   */
  protected Object getParentObject (BindingMember stopMember) {
    Object parentObject = getBoundRootObject();
    if (parents != null) {
      for (BindingMember parentMember: getParents()) {
        if (parentMember == stopMember) {
          break;
        }
        if (parentObject == null) {
          break;    // some binding reference is null
        }
        try {
          parentObject = parentMember.getObject(parentObject);
        }
        catch (RuntimeException ex) {
          throw new BindingException("can't walk down to " + parentMember, ex);
        }
      }
    }
    return parentObject;
  }

}

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
import org.tentackle.validate.ValidationScope;

import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * The Binder that associates components with object binding paths.
 *
 * @author harald
 */
public interface Binder extends Comparable<Binder> {

  /**
   * Gets the unique instance number.
   *
   * @return the instance number to make binders comparable
   */
  long getInstanceNumber();

  /**
   * Tests the binding.<br>
   * The method verifies that all objects of the GUI are bound
   * and throws a {@link BindingException} if it finds at least one
   * unbound object.<br>
   * All unbound objects are logged.
   *
   * @throws BindingException if at least one unbound component found
   */
  void assertAllBound();

  /**
   * Binds objects.
   * <p>
   * The controller will be scanned for members or sub members
   * annotated with the {@link Bindable}-annotation.
   *
   * @return number of members bound
   */
  int bind();

  /**
   * Binds objects with inheritance.
   * <p>
   * The controller will be scanned for members or sub members
   * annotated with the {@link Bindable}-annotation.
   * Inherited bindables and components will be included as well.
   * <p>
   * CAUTION: expensive operation!
   *
   * @return number of members bound
   */
  int bindAllInherited();

  /**
   * Gets a binding by its binding path.
   *
   * @param bindingPath the object path (for ex.: "....customer.customerNumber")
   * @return the binding, null if no such binding
   */
  Binding getBinding(String bindingPath);

  /**
   * Programmatically adds a binding to this view.
   * <p>
   * Notice that adding a binding for a component or a binding path
   * more than once is not allowed.
   *
   * @param binding the binding to add
   * @throws BindingException if the component or binding path is already bound
   */
  void addBinding(Binding binding);

  /**
   * Gets a binding property.<br>
   * Some components need additional info which is not provided by the bound field.
   * <p>
   * Example: context = getBindingProperty(DomainContext.class);
   *
   * @param key the key to locate the property
   * @return the property value, null if no such key
   */
  Object getBindingProperty(Object key);

  /**
   * Gets a binding property stored with its class as the key.<br>
   * Some components need additional info which is not provided by the bound field.
   * <p>
   * Example: context = getBindingProperty(DomainContext.class);
   *
   * @param <T> the property type
   * @param clazz the property class
   * @return the property value, null if no such property
   */
  <T> T getBindingProperty(Class<T> clazz);

  /**
   * Sets a binding property that can be used by components.
   * <p>
   * Example: putBindingProperty(DomainContext.class, context);
   *
   * @param key the key to locate the property
   * @param value the property value
   * @return null if property added, else the replaced property
   */
  Object putBindingProperty(Object key, Object value);

  /**
   * Sets more than one property at once.
   *
   * @param properties the property map <code>key:value</code>
   * @return true if at least one property has changed
   */
  boolean putBindingProperties(Map<Object, Object> properties);

  /**
   * Gets the list of all bindings for the view.
   *
   * @return the bindings, never null
   */
  Collection<? extends Binding> getBindings();

  /**
   * Programmatically removes a binding from the view.
   *
   * @param bindingPath the object's binding path
   *
   * @return the removed binding, null if no such binding found
   */
  Binding removeBinding(String bindingPath);

  /**
   * Removes all bindings of the view.
   */
  void unbind();

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
   * @param binding the binding
   * @param parent the parent object the value is retrieved from
   * @param modelValue the model value
   * @throws BindingVetoException if the value should not be set into the view
   */
  void fireToView(Binding binding, Object parent, Object modelValue) throws BindingVetoException;

  /**
   * Invokes {@link ToModelListener#toModel(org.tentackle.bind.BindingEvent) }
   * for all registered listeners.<br>
   * If one of the listeners fires a {@link BindingVetoException}
   * the value will not be set into the model.
   *
   * @param binding the binding
   * @param parent the parent object containing the value to set
   * @param viewValue the view value
   * @throws BindingVetoException if the value should not be set into the model
   */
  void fireToModel(Binding binding, Object parent, Object viewValue) throws BindingVetoException;

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
   * Invokes {@link ValidationListener#validated(ValidationEvent)}
   * for all registered listeners.
   *
   * @param binding the binding
   * @param results the validation results
   */
  void fireValidated(Binding binding, List<ValidationResult> results);

  /**
   * Sets the validation scope.<br>
   * The scope is used to validate the bindings.
   *
   * @param validationScope the validation scope
   */
  void setValidationScope(ValidationScope validationScope);

  /**
   * Gets the validation scope.
   *
   * @return the validation scope, the {@link org.tentackle.validate.scope.InteractiveScope} by default
   */
  ValidationScope getValidationScope();

}

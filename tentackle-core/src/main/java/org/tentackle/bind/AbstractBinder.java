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
import org.tentackle.validate.ValidationScopeFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base implementation of a binder.<br>
 * Manages the binder's listeners, the binding properties, and the validation scope.
 * Binders are compared by their unique instance number.
 *
 * @author harald
 */
public abstract class AbstractBinder implements Binder {

  /** to make binder comparable */
  private static final AtomicLong INSTANCE_COUNT = new AtomicLong();


  private final long instanceNumber;                        // unique binder number
  private final Map<Object,Object> bindingProperties;       // any additional properties needed by special components
  private List<ToViewListener> toViewListeners;             // to-view binding event listener list
  private List<ToModelListener> toModelListeners;           // to-model binding event listener list
  private List<ValidationListener> validationListeners;     // validation event listener list
  private ValidationScope validationScope;                  // validation scope


  /**
   * Creates a binder.<br>
   * The validation scope defaults to the {@link org.tentackle.validate.scope.InteractiveScope}.
   */
  public AbstractBinder() {
    this.instanceNumber = INSTANCE_COUNT.incrementAndGet();
    bindingProperties = new HashMap<>();
    validationScope = ValidationScopeFactory.getInstance().getInteractiveScope();
  }


  @Override
  public Object putBindingProperty(Object key, Object value) {
    return bindingProperties.put(key, value);
  }

  @Override
  public boolean putBindingProperties(Map<Object, Object> properties) {
    boolean changed = false;
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      Object newValue = entry.getValue();
      Object oldValue = bindingProperties.put(entry.getKey(), newValue);
      changed |= !Objects.equals(newValue, oldValue);
    }
    return changed;
  }

  @Override
  public Object getBindingProperty(Object key) {
    return bindingProperties.get(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getBindingProperty(Class<T> clazz) {
    return (T) bindingProperties.get(clazz);
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
  public void setValidationScope(ValidationScope validationScope) {
    this.validationScope = validationScope;
  }

  @Override
  public ValidationScope getValidationScope() {
    return validationScope;
  }

  @Override
  public void fireToView(Binding binding, Object parent, Object modelValue) throws BindingVetoException {
    if (toViewListeners != null) {
      BindingEvent evt = null;
      for (ToViewListener l: toViewListeners) {
        if (evt == null) {
          evt = binding.createBindingEvent(true, parent, modelValue);
        }
        l.toView(evt);
      }
    }
  }

  @Override
  public void fireToModel(Binding binding, Object parent, Object viewValue) throws BindingVetoException {
    if (toModelListeners != null) {
      BindingEvent evt = null;
      for (ToModelListener l: toModelListeners) {
        if (evt == null) {
          evt = binding.createBindingEvent(false, parent, viewValue);
        }
        l.toModel(evt);
      }
    }
  }

  @Override
  public void fireValidated(Binding binding, List<ValidationResult> results) {
    if (validationListeners != null) {
      ValidationEvent evt = null;
      for (ValidationListener l: validationListeners) {
        if (evt == null) {
          evt = binding.createValidationEvent(results);
        }
        l.validated(evt);
      }
    }
  }

  @Override
  public long getInstanceNumber() {
    return instanceNumber;
  }

  /**
   * Compares two binders.<br>
   * Binders are distinguished by their unique instance number.
   * <p>
   * {@inheritDoc}
   * @param other the binder to compare with
   */
  @Override
  public int compareTo(Binder other) {
    return Long.compare(instanceNumber, other.getInstanceNumber());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final AbstractBinder other = (AbstractBinder) obj;
    return this.instanceNumber == other.instanceNumber;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(instanceNumber);
  }


  /**
   * Binds an object to a view.
   * <p>
   * The object will be scanned for members or sub members
   * annotated with the {@link Bindable}-annotation and their
   * corresponding setter- and getter methods.
   * <p>
   * More than one object can be bound to a view.
   * <p>
   * Notice that the object reference is <em>not</em> kept in the binding, i.e.
   * the binding works <em>only</em> by declaration (reflection).
   *
   * @param parents the fields building the declaration chain to this field, null if container
   * @param parentMemberPath the object path prefix, null if root
   * @param parentClass the object's class
   * @param declaredOnly true if search for methods and field which are declared only,
   *        false to include inherited as well
   * @return number of members bound
   */
  protected abstract int doBind(BindingMember[] parents, String parentMemberPath, Class<?> parentClass, boolean declaredOnly);

}

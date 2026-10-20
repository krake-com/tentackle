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

package org.tentackle.trip.impl;

import org.tentackle.log.Logger;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * A TripComponent implementation for getter/setter pairs.<br>
 * Used for getters annotated with {@link org.tentackle.reflect.CanonicalGetter} that are not backed
 * by a serializable field, and by {@link org.tentackle.trip.TripComponentProvider}s such as the
 * {@link org.tentackle.trip.provider.ThrowableComponentProvider}.
 *
 * @param <P> the parent type
 * @param <T> the component type
 */
public class MethodTripComponent<P, T> implements TripComponent<P, T> {

  private static final Logger LOGGER = Logger.get(MethodTripComponent.class);

  private final TripType<P> parentType;
  private final int ordinal;
  private final Method getter;
  private final Method setter;
  private final boolean hasDefaultValue;
  private final T defaultValue;
  private final Class<T> componentClass;
  private final String componentName;
  private final MethodHandle getterHandle;    // typed (Object)Object, null if not bindable
  private final MethodHandle setterHandle;    // typed (Object,Object)void, null if no setter or not bindable
  private TripType<T> tripType;

  /**
   * Creates a new MethodTripComponent.
   *
   * @param parentType the parent type
   * @param ordinal the ordinal starting from 0
   * @param getter the getter method
   * @param setter the setter method, null if not applicable
   * @param hasDefaultValue true if the component has a default value, false otherwise
   * @param defaultValue the default value
   * @throws TripRuntimeException if the getter is not a getter or the setter is not a setter
   */
  @SuppressWarnings("unchecked")
  public MethodTripComponent(TripType<P> parentType, int ordinal, Method getter, Method setter, boolean hasDefaultValue, T defaultValue) {
    this.parentType = Objects.requireNonNull(parentType);
    this.ordinal = ordinal;
    this.getter = Objects.requireNonNull(getter);
    this.setter = setter;
    this.hasDefaultValue = hasDefaultValue;
    this.defaultValue = defaultValue;
    componentClass = (Class<T>) getter.getReturnType();
    componentName = ReflectionHelper.getFieldName(getter);
    if (!ReflectionHelper.isGetter(getter)) {
      throw new TripRuntimeException("method is not a getter: " + getter);
    }
    if (setter != null && !ReflectionHelper.isSetter(setter)) {
      throw new TripRuntimeException("method is not a setter: " + setter);
    }
    // invokeExact on a handle avoids the varargs array and the argument checks of Method#invoke
    getterHandle = bind(getter, MethodType.methodType(Object.class, Object.class));
    setterHandle = bind(setter, MethodType.methodType(void.class, Object.class, Object.class));
  }

  /**
   * Binds a getter or setter to a method handle.
   *
   * @param method the method, null if none
   * @param type the type of the handle
   * @return the handle, null if no method or the method cannot be bound (reflection is used instead)
   */
  private MethodHandle bind(Method method, MethodType type) {
    if (method == null) {
      return null;
    }
    Module module = MethodTripComponent.class.getModule();
    Module targetModule = method.getDeclaringClass().getModule();
    if (!module.canRead(targetModule)) {
      // MethodHandles.lookup() is caller-sensitive: make the method's module readable by this module
      module.addReads(targetModule);
    }
    try {
      return MethodHandles.lookup().unreflect(method).asType(type);
    }
    catch (IllegalAccessException | RuntimeException e) {
      // not accessible from here: fall back to reflection
      LOGGER.fine("cannot bind " + method + ", falling back to reflection", e);
      return null;
    }
  }

  @Override
  public TripType<P> getParentType() {
    return parentType;
  }

  @Override
  public Class<T> getComponentClass() {
    return componentClass;
  }

  @Override
  public int getOrdinal() {
    return ordinal;
  }

  @Override
  public TripType<T> getType() {
    // getting the trip type must be delayed since TripComponents may reference themselves due to cyclic object graphs
    if (tripType == null) {
      tripType = TripFactory.getInstance().getType(getComponentClass());
    }
    return tripType;
  }

  /**
   * Returns the underlying getter method.
   *
   * @return the getter method, never null
   */
  public Method getGetter() {
    return getter;
  }

  /**
   * Gets the setter method.
   *
   * @return the setter method, null if not applicable
   */
  public Method getSetter() {
    return setter;
  }

  @Override
  public String getName() {
    return componentName;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T getValue(P parent) {
    if (getterHandle != null) {
      try {
        return (T) getterHandle.invokeExact((Object) parent);
      }
      catch (Throwable t) {
        throw new TripRuntimeException("cannot retrieve the value via getter for component " + this, t);
      }
    }
    try {
      try {
        return (T) getter.invoke(parent);
      }
      catch (IllegalAccessException e) {
        getter.setAccessible(true);
        return (T) getter.invoke(parent);
      }
    }
    catch (InaccessibleObjectException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
      throw new TripRuntimeException("cannot retrieve the value via getter for component " + this, e);
    }
  }

  @Override
  public void setValue(P parent, T value) {
    if (setter == null) {
      throw new TripRuntimeException("no setter provided for component " + this);
    }
    if (setterHandle != null) {
      try {
        setterHandle.invokeExact((Object) parent, (Object) value);
      }
      catch (Throwable t) {
        throw new TripRuntimeException("cannot set the value via setter for component " + this, t);
      }
      return;
    }
    try {
      try {
        setter.invoke(parent, value);
      }
      catch (IllegalAccessException e) {
        setter.setAccessible(true);
        setter.invoke(parent, value);
      }
    }
    catch (InaccessibleObjectException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
      throw new TripRuntimeException("cannot set the value via setter for component " + this, e);
    }
  }

  @Override
  public boolean hasDefaultValue() {
    return hasDefaultValue;
  }

  @Override
  public T getDefaultValue() {
    return defaultValue;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("getter ").append(getter);
    if (setter != null) {
      buf.append(", setter ").append(setter);
    }
    buf.append(" of ").append(parentType)
       .append(" at ordinal ").append(ordinal);
    return buf.toString();
  }

}

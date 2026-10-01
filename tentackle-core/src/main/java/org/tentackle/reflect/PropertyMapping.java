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

package org.tentackle.reflect;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.ImmutableException;
import org.tentackle.misc.ObjectUtilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Describes the mapping of a property.<br>
 * Properties can be either is/set-pairs or get/set-pairs of methods or some other readonly method.
 * <p>
 * Equality refers to the property name only.
 *
 * @param <T> the type of the bean providing the property
 */
public class PropertyMapping<T> {

  private final String name;
  private final Class<?> valueType;
  private final Method getter;
  private final Method setter;

  /**
   * Creates a property mapping.
   *
   * @param name the name of the property
   * @param getter the getter
   * @param setter the setter, null if readonly
   * @throws NullPointerException if name or getter is null
   */
  public PropertyMapping(String name, Method getter, Method setter) {
    this.name = Objects.requireNonNull(name);
    this.getter = Objects.requireNonNull(getter);
    this.setter = setter;
    // return type may be supertype of the setter's parameter -> setter takes precedence
    this.valueType = setter != null ? setter.getParameterTypes()[0] : getter.getReturnType();
  }

  /**
   * Gets the name of the property.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the getter.
   *
   * @return the getter
   */
  public Method getGetter() {
    return getter;
  }

  /**
   * Gets the setter.
   *
   * @return the setter, null if read-only
   */
  public Method getSetter() {
    return setter;
  }

  /**
   * Returns whether the property is readonly.
   *
   * @return true if readonly
   */
  public boolean isReadOnly() {
    return setter == null;
  }

  /**
   * Gets the type of the value held by the property.<br>
   * This is the parameter type of the setter, if any, else the return type of the getter.
   *
   * @return the value type
   */
  public Class<?> getValueType() {
    return valueType;
  }

  /**
   * Gets the property value.
   *
   * @param object the object containing the property
   * @return the value
   * @throws TentackleRuntimeException if invoking the getter failed
   */
  public Object get(T object) {
    try {
      return getter.invoke(object);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new TentackleRuntimeException("getting property " + this + " failed", e);
    }
  }

  /**
   * Sets the property value.<br>
   * The value will be converted to the type of the property, if necessary.
   *
   * @param object the object containing the property
   * @param value the value
   * @throws ImmutableException if the property is readonly
   * @throws IllegalArgumentException if the value cannot be converted to the type of the property
   * @throws TentackleRuntimeException if invoking the setter failed
   * @see org.tentackle.misc.ObjectUtilities#convert(Class, Object)
   */
  public void set(T object, Object value) {
    if (isReadOnly()) {
      throw new ImmutableException("property " + this + " is read-only");
    }
    try {
      setter.invoke(object, ObjectUtilities.getInstance().convert(getValueType(), value));
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new TentackleRuntimeException("setting property " + this + " failed", e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PropertyMapping<?> that = (PropertyMapping<?>) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return name;
  }
}

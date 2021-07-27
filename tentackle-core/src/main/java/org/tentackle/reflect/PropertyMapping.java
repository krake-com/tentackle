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

import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.ImmutableException;
import org.tentackle.misc.ObjectUtilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Describes the mapping of a property.<br>
 * Properties can be either is/set-pairs or get/set-pairs of methods or some other readonly method.
 *
 * @param <T> the type of the bean providing the property
 */
public class PropertyMapping<T> {

  // lookup cache per class
  private static final Map<Class<?>, Map<String,PropertyMapping<?>>> classPropertyMapping = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Set<String>> classPropertyNames = new ConcurrentHashMap<>();

  /**
   * Gets the property map for a given bean type.<br>
   * Property maps are cached and evaluated only once per bean type.
   *
   * @param beanType the bean type
   * @return the property map
   */
  private static Map<String, PropertyMapping<?>> getPropertyMap(Class<?> beanType) {
    return classPropertyMapping.computeIfAbsent(beanType, type -> {
      Map<String, PropertyMapping<?>> propertyMap = new HashMap<>();
      Set<Method> getters = new HashSet<>();
      Map<String, Method> setters = new HashMap<>();
      for (Method method : type.getMethods()) {
        if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()) &&
            method.getDeclaringClass() != Object.class) {
          if (method.getReturnType() == Void.TYPE) {
            if (method.getParameterCount() == 1) {
              setters.put(method.getName(), method);
            }
          }
          else if (method.getParameterCount() == 0) {
            getters.add(method);
          }
        }

        for (Method getter : getters) {
          String propertyName;
          Method setter = null;
          String getterName = getter.getName();
          if (getterName.startsWith("get") && getterName.length() > 3 && Character.isUpperCase(getterName.charAt(3))) {
            String suffix = getterName.substring(3);
            propertyName = StringHelper.firstToLower(suffix);
            setter = setters.get("set" + suffix);
          }
          else if ((getter.getReturnType() == Boolean.class || getter.getReturnType() == Boolean.TYPE) &&
                   getterName.startsWith("is") && getterName.length() > 2 && Character.isUpperCase(getterName.charAt(2))) {
            String suffix = getterName.substring(2);
            propertyName = StringHelper.firstToLower(suffix);
            setter = setters.get("set" + suffix);
          }
          else {
            propertyName = getterName;
          }
          if (setter != null && !setter.getParameterTypes()[0].isAssignableFrom(getter.getReturnType())) {
            setter = null;    // types do not match -> readonly
          }
          propertyMap.put(propertyName, new PropertyMapping<>(propertyName, getter, setter));
        }
      }
      return propertyMap;
    });
  }


  /**
   * Gets a mapping for a given bean type and property name.
   *
   * @param beanType the class the property belongs to
   * @param name the name of the property
   * @param <T> the class type
   * @return the property mapping, null if no such property
   */
  @SuppressWarnings("unchecked")
  public static <T> PropertyMapping<T> getMapping(Class<T> beanType, String name) {
    return (PropertyMapping<T>) getPropertyMap(beanType).get(name);
  }


  /**
   * Gets all property names for a given class.
   *
   * @param beanType the class the properties belongs to
   * @return the property names
   */
  public static Set<String> getPropertyNames(Class<?> beanType) {
    return classPropertyNames.computeIfAbsent(beanType, t -> Set.copyOf(getPropertyMap(beanType).keySet()));
  }




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
   */
  public PropertyMapping(String name, Method getter, Method setter) {
    this.name = Objects.requireNonNull(name);
    this.getter = Objects.requireNonNull(getter);
    this.setter = setter;
    // returntype may be supertype of the setter's parameter -> setter takes precedence
    this.valueType = setter != null ? setter.getParameterTypes()[0] : getter.getReturnType();
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
   * Gets the type of the value held by the property.
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
   * <p>
   * Throws a {@link ImmutableException} if property is readonly.<br>
   * Throws a {@link IllegalArgumentException} if value cannot be converted to the type of the property.
   *
   * @param object the object containing the property
   * @param value the value
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

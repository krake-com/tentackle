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
import org.tentackle.misc.ImmutableException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A property map.<br>
 * Provides the functionality of a map for the properties of a bean.
 * <p>
 * The properties are determined by the public non-static methods of the bean type (except those of {@link Object}):
 * {@code getXxx()} and {@code isXxx()} (for booleans) map to the property {@code xxx}, which is writable if there is
 * a matching {@code setXxx(...)}. Any other method without parameters returning a value is a readonly property named
 * after the method.<br>
 * The mappings are evaluated once per bean type and cached.
 * <p>
 * Properties can be read and written, but not added or removed.
 *
 * @param <T> the bean type
 */
public class PropertyMap<T> extends AbstractMap<String, Object> {

  private final T bean;
  private final Class<T> beanType;


  /**
   * Creates a map of properties for a given bean.<br>
   * The bean type may be a superclass of the bean.
   *
   * @param bean the bean
   * @param beanType the bean's type
   * @throws NullPointerException if bean or beanType is null
   */
  public PropertyMap(T bean, Class<T> beanType) {
    this.bean = Objects.requireNonNull(bean);
    this.beanType = Objects.requireNonNull(beanType);
  }

  /**
   * Creates a map of properties for a given bean.<br>
   * The bean's type is derived from the bean.
   *
   * @param bean the bean
   */
  @SuppressWarnings("unchecked")
  public PropertyMap(T bean) {
    this(bean, (Class<T>) bean.getClass());
  }

  /**
   * Gets the bean.
   *
   * @return the bean
   */
  public T getBean() {
    return bean;
  }

  /**
   * Gets the bean's type.
   *
   * @return the type
   */
  public Class<T> getBeanType() {
    return beanType;
  }

  @Override
  public Object get(Object key) {
    PropertyMapping<T> mapping = getMapping((String) key);
    return mapping == null ? null : mapping.get(bean);
  }

  /**
   * Gets a mapping for a property name.
   *
   * @param key the name of the property
   * @return the property mapping, null if no such property
   */
  public PropertyMapping<T> getMapping(String key) {
    return getMapping(beanType, key);
  }

  /**
   * Sets the property value.<br>
   * The value will be converted to the type of the property, if necessary.
   *
   * @param key key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @return always null
   * @throws IllegalArgumentException if there is no such property or the value cannot be converted
   * @throws ImmutableException if the property is readonly
   */
  @Override
  public Object put(String key, Object value) {
    PropertyMapping<T> mapping = getMapping(key);
    if (mapping == null) {
      throw new IllegalArgumentException("no such property '" + key + "'");
    }
    mapping.set(bean, value);
    return null;    // null to avoid unnecessary get, makes no sense in this context at all
  }

  /**
   * Properties cannot be removed.
   *
   * @param key the property name
   * @return never
   * @throws ImmutableException always
   */
  @Override
  public Object remove(Object key) {
    throw new ImmutableException("properties cannot be removed");
  }

  @Override
  public boolean containsKey(Object key) {
    return getMapping((String) key) != null;
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    Set<Entry<String, Object>> propSet = new HashSet<>();
    for (String name : getPropertyNames(beanType)) {
      propSet.add(new PropertyEntry(name, get(name)));
    }
    return propSet;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PropertyMap<?> that = (PropertyMap<?>) o;
    return bean.equals(that.bean);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + bean.hashCode();
    return result;
  }


  /**
   * Creates a property mapping.<br>
   * Notice that the mappings are cached per bean type for all property maps.
   *
   * @param name the name of the property
   * @param getter the getter
   * @param setter the setter, null if readonly
   * @return the mapping
   */
  protected PropertyMapping<T> createMapping(String name, Method getter, Method setter) {
    return new PropertyMapping<>(name, getter, setter);
  }



  private static class PropertyEntry implements Entry<String, Object> {

    private final String key;
    private final Object value;

    public PropertyEntry(String key, Object value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public Object getValue() {
      return value;
    }

    @Override
    public Object setValue(Object value) {
      throw new ImmutableException();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PropertyEntry that = (PropertyEntry) o;
      if (!key.equals(that.key)) return false;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      int result = key.hashCode();
      result = 31 * result + (value != null ? value.hashCode() : 0);
      return result;
    }
  }



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
  private Map<String, PropertyMapping<?>> getPropertyMap(Class<?> beanType) {
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
        propertyMap.put(propertyName, createMapping(propertyName, getter, setter));
      }
      return propertyMap;
    });
  }


  /**
   * Gets a mapping for a given bean type and property name.
   *
   * @param beanType the class the property belongs to
   * @param name the name of the property
   * @param <C> the class type
   * @return the property mapping, null if no such property
   */
  @SuppressWarnings("unchecked")
  private <C> PropertyMapping<C> getMapping(Class<C> beanType, String name) {
    return (PropertyMapping<C>) getPropertyMap(beanType).get(name);
  }


  /**
   * Gets all property names for a given class.
   *
   * @param beanType the class the properties belongs to
   * @return the property names
   */
  private Set<String> getPropertyNames(Class<?> beanType) {
    return classPropertyNames.computeIfAbsent(beanType, t -> Set.copyOf(getPropertyMap(beanType).keySet()));
  }

}

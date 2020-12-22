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

import org.tentackle.misc.ImmutableException;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A property map.<br>
 * Provides the functionality of a map for the properties of a bean.
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
    PropertyMapping<T> mapping = PropertyMapping.getMapping(beanType, (String) key);
    return mapping == null ? null : mapping.get(bean);
  }

  /**
   * Sets the property value.<br>
   * The value will be converted to the type of the property, if necessary.
   *
   * @param key key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @return always null
   */
  @Override
  public Object put(String key, Object value) {
    PropertyMapping<T> mapping = PropertyMapping.getMapping(beanType, key);
    if (mapping == null) {
      throw new IllegalArgumentException("no such property '" + key + "'");
    }
    mapping.set(bean, value);
    return null;    // null to avoid unnecessary get, makes no sense in this context at all
  }

  @Override
  public Object remove(Object key) {
    throw new ImmutableException("properties cannot be removed");
  }

  @Override
  public boolean containsKey(Object key) {
    return PropertyMapping.getMapping(beanType, (String) key) != null;
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    Set<Entry<String, Object>> propSet = new HashSet<>();
    for (String name : PropertyMapping.getPropertyNames(beanType)) {
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

}

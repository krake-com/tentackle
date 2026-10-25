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

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * A bindable element.
 * <p>
 * Bindable elements are annotated by the @{@link Bindable}-annotation.
 *
 * @author harald
 */
public class DefaultBindableElement implements BindableElement {

  private String camelName;             // the member name in camelCase
  private int ordinal;                  // the ordinal for sorting
  private Field field;                  // the field, null if none
  private Method getter;                // the getter, null if none
  private Method setter;                // the setter, null if none
  private Method from;                  // the "from"-method, null if none
  private Set<String> bindingOptions;   // the binding options
  private Class<?> type;                // the class type of the bound member
  private Type genericType;             // the generic type of the bound member

  /**
   * Creates a bindable element.
   *
   * @param camelName the member name in camelCase
   */
  public DefaultBindableElement(String camelName) {
    this.camelName = camelName;
  }


  @Override
  public String getCamelName() {
    return camelName;
  }

  @Override
  public void setCamelName(String camelName) {
    this.camelName = camelName;
  }

  @Override
  public int getOrdinal() {
    return ordinal;
  }

  @Override
  public void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  @Override
  public String getBindingOptions() {
    if (bindingOptions != null && !bindingOptions.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      boolean needComma = false;
      for (String option : bindingOptions) {
        if (needComma) {
          buf.append(',');
        }
        else {
          needComma = true;
        }
        buf.append(option);
      }
      return buf.toString();
    }
    return null;
  }

  @Override
  public void setBindingOptions(String options) {
    if (options == null || options.isBlank()) {
      bindingOptions = null;
    }
    else {
      if (bindingOptions != null) {
        bindingOptions.clear();
      }
      addBindingOptions(options);
    }
  }

  @Override
  public void addBindingOptions(String options) {
    if (options != null && !options.isBlank()) {
      if (bindingOptions == null) {
        bindingOptions = new LinkedHashSet<>();
      }
      StringTokenizer stok = new StringTokenizer(options, ",");
      while (stok.hasMoreTokens()) {
        bindingOptions.add(stok.nextToken());
      }
    }
  }

  @Override
  public Field getField() {
    return field;
  }

  @Override
  public void setField(Field field) {
    this.field = field;
  }

  @Override
  public Method getGetter() {
    return getter;
  }

  @Override
  public void setGetter(Method getter) {
    this.getter = getter;
  }

  @Override
  public Method getSetter() {
    return setter;
  }

  @Override
  public void setSetter(Method setter) {
    this.setter = setter;
  }

  @Override
  public Method getFrom() {
    return from;
  }

  @Override
  public void setFrom(Method from) {
    this.from = from;
  }

  @Override
  public Object getObject(Object parent) {
    try {
      // getter takes precedence over field
      if (getter != null) {
        try {
          return getter.invoke(parent);
        }
        catch (IllegalAccessException ex) {
          getter.setAccessible(true);
          return getter.invoke(parent);
        }
      }
      else {
        try {
          return field.get(parent);
        }
        catch (IllegalAccessException ex) {
          field.setAccessible(true);
          return field.get(parent);
        }
      }
    }
    catch (InaccessibleObjectException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
      throw new BindingException("cannot get " + camelName, ex);
    }
  }

  @Override
  public Object setObject(Object parent, Object value) {
    getType();
    Object updatedParent = null;
    try {
      // handle null values if type is a primitive
      if (value == null && type.isPrimitive()) {
        if (type == Boolean.TYPE) {
          value = Boolean.FALSE;
        }
        else if (type == Character.TYPE) {
          value = ' ';
        }
        else  {
          value = (byte) 0;
        }
      }

      // precedence: setter -> from-method -> field
      if (setter != null) {
        try {
          setter.invoke(parent, value);
        }
        catch (IllegalAccessException ex) {
          setter.setAccessible(true);
          setter.invoke(parent, value);
        }
      }
      else if (from != null) {
        updatedParent = from.invoke(parent, value);
      }
      else if (field != null && !Modifier.isFinal(field.getModifiers())) {
        // final fields are read-only: never mutate them reflectively (JEP-500)
        try {
          field.set(parent, value);
        }
        catch (IllegalAccessException ex) {
          field.setAccessible(true);
          field.set(parent, value);
        }
      }
      // it is okay that there's no setter, no from-method and no field (for example, read-only methods)
      return updatedParent;
    }
    catch (InaccessibleObjectException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
      throw new BindingException("cannot set " + camelName, ex);
    }
  }

  @Override
  public boolean isReadOnly() {
    return (field == null || Modifier.isFinal(field.getModifiers())) && setter == null && from == null;
  }

  @Override
  public Class<?> getType() {
    if (type == null) {
      if (getter != null) {
        type = getter.getReturnType();
      }
      else if (field != null) {
        type = field.getType();
      }
      else {
        throw new BindingException("no getter or field for " + camelName);
      }
      if (type.equals(Void.TYPE)) {
        throw new BindingException("void type is not allowed");
      }
    }
    return type;
  }

  @Override
  public Type getGenericType() {
    if (genericType == null) {
      if (getter != null) {
        genericType = getter.getGenericReturnType();
      }
      else if (field != null) {
        genericType = field.getGenericType();
      }
      else {
        throw new BindingException("no getter or field for " + camelName);
      }
    }
    return genericType;
  }

}

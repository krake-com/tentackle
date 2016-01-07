/**
 * Tentackle - http://www.tentackle.org
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
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * A bindable element.
 * <p>
 * Bindable elements are annotated by the @{@link Bindable}-annotation.
 *
 * @author harald
 */
public class DefaultBindableElement implements BindableElement {

  private String camelName;         // the member name in camelCase
  private Field field;              // the field, null if none
  private Method getter;            // the getter, null if none
  private Method setter;            // the setter, null if none
  private String bindingOptions;    // the binding options
  private Class<?> type;            // the class type of the bound member
  private Type genericType;         // the generic type of the bound member

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
  public String getBindingOptions() {
    return bindingOptions;
  }

  @Override
  public void setBindingOptions(String bindingOptions) {
    this.bindingOptions = bindingOptions;
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
  public void addBindingOptions(String options) {
    if (options != null && options.length() > 0) {
      if (bindingOptions == null || bindingOptions.isEmpty()) {
        bindingOptions = options;
      }
      else {
        bindingOptions += "," + options;
      }
    }
  }

  @Override
  public Object getObject(Object parent) {
    try {
      // getter takes precedence over field
      if (getter != null) {
        return getter.invoke(parent);
      }
      else {
        return field.get(parent);
      }
    }
    catch (Exception ex) {
      throw new BindingException("cannot get " + camelName, ex);
    }
  }

  @Override
  public void setObject(Object parent, Object value) {
    getType();
    try {
      // handle null values if type is a primitive
      if (value == null && type.isPrimitive()) {
        if (type.equals(Boolean.TYPE)) {
          value = Boolean.FALSE;
        }
        else if (type == Character.TYPE) {
          value = ' ';
        }
        else  {
          value = (byte) 0;   // will be upcasted
        }
      }
      // setter takes precedence over field
      if (setter != null) {
        setter.invoke(parent, value);
      }
      else if (field != null) {
        field.set(parent, value);
      }
      // it is okay that there's no setter and no field (for example read-only methods)
    }
    catch (Exception ex) {
      throw new BindingException("cannot set " + camelName, ex);
    }
  }

  @Override
  public boolean isReadOnly() {
    return field == null && setter == null;
  }

  @Override
  public boolean isWriteOnly() {
    return field == null && getter == null;
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

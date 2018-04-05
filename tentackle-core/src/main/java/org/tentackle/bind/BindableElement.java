/*
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
public interface BindableElement {

  /**
   * Gets the camelcase name of this element.
   *
   * @return the camelcase name
   */
  String getCamelName();

  /**
   * Sets the camelcase name of this element.
   *
   * @param camelName the name in camelcase
   */
  void setCamelName(String camelName);

  /**
   * Gets the binding options.
   *
   * @return the binding options
   */
  String getBindingOptions();

  /**
   * Sets the binding options.
   *
   * @param bindingOptions the options (separated by commas)
   */
  void setBindingOptions(String bindingOptions);

  /**
   * Gets the bound field.
   *
   * @return the field, null if none
   */
  Field getField();

  /**
   * Sets the bound field.
   * @param field the field, null to clear
   */
  void setField(Field field);

  /**
   * Gets the getter method.
   * @return the method, null if none
   */
  Method getGetter();

  /**
   * Sets the getter method.
   *
   * @param getter the getter method, null to clear
   */
  void setGetter(Method getter);

  /**
   * Gets the setter method.
   *
   * @return the setter method, null if none
   */
  Method getSetter();

  /**
   * Sets the setter method.
   *
   * @param setter the setter, null to clear
   */
  void setSetter(Method setter);

  /**
   * Adds binding options.
   *
   * @param options the options to add
   * @see Bindable
   */
  void addBindingOptions(String options);

  /**
   * Gets the object of this element.
   *
   * @param parent the parent containing this element
   * @return the object of this member
   */
  Object getObject(Object parent);

  /**
   * Sets the object of this member.
   *
   * @param parent the parent containing this member
   * @param value the object to set
   */
  void setObject(Object parent, Object value);

  /**
   * Gets the type of the member.
   *
   * @return the class
   */
  Class<?> getType();

  /**
   * Gets the generic type of the member.
   *
   * @return the generic type
   */
  Type getGenericType();

  /**
   * Returns whether element is read only.
   *
   * @return true if not a field and no setter
   */
  boolean isReadOnly();

  /**
   * Returns whether element is write only.
   *
   * @return true if not a field and no getter
   */
  boolean isWriteOnly();

}

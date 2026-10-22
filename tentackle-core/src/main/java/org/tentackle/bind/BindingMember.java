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
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * A member in the binding chain.
 *
 * @author harald
 */
public interface BindingMember {

  /**
   * Gets the parent class of this member.<br>
   * This is the class in which this member is declared.
   *
   * @return the declaring parent class
   */
  Class<?> getParentClass();

  /**
   * Gets the parent's member.
   *
   * @return the parent member, null if container root
   */
  BindingMember getParentMember();

  /**
   * Gets the declared name of this member.
   *
   * @return the declared name
   */
  String getMemberName();

  /**
   * Gets the member path.
   *
   * @return the member path as a string
   */
  String getMemberPath();

  /**
   * Gets the bound field.
   *
   * @return the field, null if none
   */
  Field getField();

  /**
   * Gets the getter method.
   *
   * @return the method, null if none
   */
  Method getGetter();

  /**
   * Gets the setter method.
   *
   * @return the setter method, null if none
   */
  Method getSetter();

  /**
   * Gets the "from"-method.<br>
   * Members of immutable objects may provide a from method that
   * takes the new member value as its argument and returns a
   * new immutable object that will be set as the parent member's value.
   *
   * @return the "from"-method, null if none
   */
  Method getFrom();

  /**
   * Returns whether member is read only.
   *
   * @return true if readonly
   */
  boolean isReadOnly();

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
   * @return the new parent if value was updated via "from"- or "with"-method, else null
   */
  Object setObject(Object parent, Object value);

  /**
   * Gets the type of the field.
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

}

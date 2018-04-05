/*
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2009 Harald Krake, harald@krake.de, +49 7722 9508-0
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
 * Default implementation of {@link BindingMember}.
 *
 * @author harald
 */
public class DefaultBindingMember implements BindingMember {

  private final Class<?> parentClass;         // declaring parent class
  private final BindingMember parentMember;   // parent member
  private final String memberName;            // the member's name
  private final String memberPath;            // the path up to and including this member
  private final BindableElement element;      // the bound element


  /**
   * Creates a binding member.
   * <p>
   * Notice: the bindable element is used as a delegate by this member and is hidden
   * because it belongs to the model and must not be exposed to the UI.
   *
   * @param parentClass the declaring parent class
   * @param parentMember the parent member, null if root
   * @param memberName the member name
   * @param memberPath the object path
   * @param element the bound element
   */
  public DefaultBindingMember(Class<?> parentClass, BindingMember parentMember,
                              String memberName, String memberPath,
                              BindableElement element) {

    this.parentClass  = parentClass;
    this.parentMember = parentMember;
    this.memberName   = memberName;
    this.memberPath   = memberPath;
    this.element      = element;
  }

  @Override
  public Class<?> getParentClass() {
    return parentClass;
  }

  @Override
  public BindingMember getParentMember() {
    return parentMember;
  }

  @Override
  public String getMemberName() {
    return memberName;
  }

  @Override
  public String getMemberPath() {
    return memberPath;
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just delegates to the {@link BindableElement}.
   */
  @Override
  public Field getField() {
    return element.getField();
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just delegates to the {@link BindableElement}.
   */
  @Override
  public Method getGetter() {
    return element.getGetter();
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just delegates to the {@link BindableElement}.
   */
  @Override
  public Method getSetter() {
    return element.getSetter();
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just delegates to the {@link BindableElement}.
   */
  @Override
  public boolean isReadOnly() {
    return element.isReadOnly();
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just delegates to the {@link BindableElement}.
   */
  @Override
  public boolean isWriteOnly() {
    return element.isWriteOnly();
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just delegates to the {@link BindableElement}.
   */
  @Override
  public Object getObject(Object parent) {
    return element.getObject(parent);
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just delegates to the {@link BindableElement}.
   */
  @Override
  public void setObject(Object parent, Object value) {
    element.setObject(parent, value);
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just delegates to the {@link BindableElement}.
   */
  @Override
  public Class<?> getType() {
    return element.getType();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just delegates to the {@link BindableElement}.
   */
  @Override
  public Type getGenericType() {
    return element.getGenericType();
  }


  @Override
  public String toString() {
    return memberPath;
  }

}

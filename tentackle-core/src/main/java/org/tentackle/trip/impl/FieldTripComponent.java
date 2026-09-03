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

import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * A TripComponent implementation for fields.
 *
 * @param <P> the parent type
 * @param <T> the component type
 */
public class FieldTripComponent<P, T> implements TripComponent<P, T> {

  private final TripType<P> parentType;
  private final int ordinal;
  private final Field field;
  private final boolean hasDefaultValue;
  private final T defaultValue;
  private final Class<T> componentClass;
  private final String componentName;
  private final VarHandle handle;
  private final boolean isFinalField;
  private TripType<T> tripType;

  /**
   * Creates a new FieldTripComponent.
   *
   * @param parentType the parent type
   * @param ordinal the ordinal starting from 0
   * @param field the field
   * @param hasDefaultValue true if the component has a default value, false otherwise
   * @param defaultValue the default value
   */
  @SuppressWarnings("unchecked")
  public FieldTripComponent(TripType<P> parentType, int ordinal, Field field, boolean hasDefaultValue, T defaultValue) {
    this.parentType = Objects.requireNonNull(parentType);
    this.ordinal = ordinal;
    this.field = Objects.requireNonNull(field);
    this.hasDefaultValue = hasDefaultValue;
    this.defaultValue = defaultValue;
    componentClass = (Class<T>) field.getType();
    componentName = field.getName();
    isFinalField = Modifier.isFinal(field.getModifiers());
    // CAUTION: Java >= 26 requires "--enable-final-field-mutation=org.tentackle.core"
    if (!this.getClass().getModule().canRead(field.getDeclaringClass().getModule())) {
      // MethodHandles.lookup() is caller-sensitive: make the field's module readable by this module.
      this.getClass().getModule().addReads(field.getDeclaringClass().getModule());
    }
    // VarHandle is faster than reflection
    try {
      handle = Modifier.isPublic(field.getModifiers()) ?
                 MethodHandles
                   .lookup()
                   .in(field.getDeclaringClass())
                   .findVarHandle(field.getDeclaringClass(), field.getName(), componentClass) :
                 MethodHandles
                   .privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup())
                   .findVarHandle(field.getDeclaringClass(), field.getName(), componentClass);
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      throw new TripRuntimeException("cannot get VarHandle for " + field, e);
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
   * Gets the underlying Java field.
   *
   * @return the field
   */
  public Field getField() {
    return field;
  }

  /**
   * Gets the handle to access the field.
   *
   * @return the var handle, never null
   */
  protected VarHandle getHandle() {
    return handle;
  }

  /**
   * Returns whether the field is final.
   * <p>
   * A final field cannot be written through the {@link VarHandle}, see {@link #setValue}.
   *
   * @return true if the field is final
   */
  protected boolean isFinalField() {
    return isFinalField;
  }

  @Override
  public String getName() {
    return componentName;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T getValue(P parent) {
    return (T) handle.get(parent);
  }

  @Override
  public void setValue(P parent, T value) {
    if (isFinalField) {
      // VarHandle throws UnsupportedOperationException for final fields, while Field#set does not.
      // TODO: this must be replaced in Java26 with jdk.internal.reflect.FieldAccessor due to JEP-500!
      try {
        try {
          field.set(parent, value);
        }
        catch (IllegalAccessException e) {
          field.setAccessible(true);
          field.set(parent, value);
        }
      }
      catch (InaccessibleObjectException | IllegalArgumentException | IllegalAccessException e) {
        throw new TripRuntimeException(e);
      }
    }
    else {
      handle.set(parent, value);
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
    return "field " + field + " of " + parentType + " at ordinal " + ordinal;
  }

}

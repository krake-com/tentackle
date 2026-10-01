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

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.types.ClassTripType;

import java.util.Objects;

/**
 * A TripComponent implementation for a class component type.<br>
 * Used for the interfaces of dynamic proxies and the component type of arrays.
 * The component's value is the class itself.
 *
 * @param <P> the parent type
 */
public class ClassTripComponent<P> implements TripComponent<P, Class<?>> {

  private static final ClassTripType tripType = new ClassTripType();

  private final TripType<P> parentType;
  private final Class<?> componentClass;
  private final int ordinal;
  private String serializableClassName;

  /**
   * Creates a new class component.
   *
   * @param parentType the parent type that this component belongs to
   * @param ordinal the ordinal of the component
   * @param componentClass the component class
   */
  public ClassTripComponent(TripType<P> parentType, int ordinal, Class<?> componentClass) {
    this.parentType = Objects.requireNonNull(parentType);
    this.ordinal = ordinal;
    this.componentClass = Objects.requireNonNull(componentClass);
  }

  @Override
  public TripType<P> getParentType() {
    return parentType;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Class<Class<?>> getComponentClass() {
    return (Class) componentClass;
  }

  @Override
  public int getOrdinal() {
    return ordinal;
  }

  @Override
  public ClassTripType getType() {
    return tripType;
  }

  @Override
  public String getName() {
    if (serializableClassName == null) {
      serializableClassName = ReflectionHelper.getSerializableClassName(componentClass);
    }
    return serializableClassName;
  }

  @Override
  public boolean hasDefaultValue() {
    return true;
  }

  @Override
  public Class<?> getDefaultValue() {
    return componentClass;
  }

  @Override
  public Class<?> getValue(Object parent) {
    throw new TripRuntimeException("ClassTripComponent does not support getValue()");
  }

  @Override
  public void setValue(P parent, Class<?> value) {
    throw new TripRuntimeException("ClassTripComponent does not support setValue()");
  }

  @Override
  public String toString() {
    return componentClass + " of " + parentType + " at ordinal " + ordinal;
  }

}

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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Objects;

/**
 * A TripComponent implementation for record types.
 *
 * @param <T> the component type
 */
public class RecordTripComponent<P extends Record, T> implements TripComponent<P, T> {

  private final TripType<P> parentType;
  private final int ordinal;
  private final RecordComponent recordComponent;
  private final T defaultValue;
  private final Class<T> componentClass;
  private final String componentName;
  private TripType<T> tripType;

  /**
   * Creates a new RecordTripComponent.
   *
   * @param parentType the parent type
   * @param ordinal the ordinal starting from 0
   * @param recordComponent the record component
   */
  @SuppressWarnings("unchecked")
  public RecordTripComponent(TripType<P> parentType, int ordinal, RecordComponent recordComponent) {
    this.parentType = Objects.requireNonNull(parentType);
    this.ordinal = ordinal;
    this.recordComponent = Objects.requireNonNull(recordComponent);
    defaultValue = null;
    componentClass = (Class<T>) recordComponent.getType();
    componentName = recordComponent.getName();
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
   * Gets the underlying record component.
   *
   * @return the record component
   */
  public RecordComponent getRecordComponent() {
    return recordComponent;
  }

  @Override
  public String getName() {
    return componentName;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T getValue(P parent) {
    try {
      return (T) recordComponent.getAccessor().invoke(parent);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new TripRuntimeException(e);
    }
  }

  @Override
  public void setValue(P parent, T value) {
    throw new TripRuntimeException("RecordTripComponent does not support setValue()");
  }

  @Override
  public boolean hasDefaultValue() {
    return false;
  }

  @Override
  public T getDefaultValue() {
    return defaultValue;
  }

  @Override
  public String toString() {
    return "component " + recordComponent + " of " + parentType + " at ordinal " + ordinal;
  }

}

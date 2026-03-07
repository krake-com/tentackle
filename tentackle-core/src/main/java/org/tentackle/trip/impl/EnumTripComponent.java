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
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

import java.util.Objects;

/**
 * A TripComponent implementation for enums.
 *
 * @param <T> the enum type
 */
public class EnumTripComponent<T extends Enum<T>> implements TripComponent<T, T> {

  private final Class<T> componentClass;
  private final int ordinal;
  private final String componentName;
  private final TripType<T> tripType;
  private final T defaultValue;

  /**
   * Creates a new EnumTripComponent.
   *
   * @param tripType the enum type
   * @param enumConstant the enum constant
   */
  public EnumTripComponent(TripType<T> tripType, T enumConstant) {
    this.tripType = Objects.requireNonNull(tripType);
    this.defaultValue = Objects.requireNonNull(enumConstant);
    ordinal = enumConstant.ordinal();
    componentClass = enumConstant.getDeclaringClass();
    componentName = enumConstant.name();
  }

  @Override
  public TripType<T> getParentType() {
    return tripType;
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
    return tripType;
  }

  @Override
  public String getName() {
    return componentName;
  }

  @Override
  public boolean hasDefaultValue() {
    return true;
  }

  @Override
  public T getDefaultValue() {
    return defaultValue;
  }

  @Override
  public T getValue(T parent) {
    throw new TripRuntimeException("EnumTripComponent does not support getValue()");
  }

  @Override
  public void setValue(T parent, T value) {
    throw new TripRuntimeException("EnumTripComponent does not support setValue()");
  }

  @Override
  public String toString() {
    return componentClass + " of " + tripType + " at ordinal " + ordinal;
  }

}

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

import org.tentackle.log.Logger;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.types.BooleanTripType;
import org.tentackle.trip.types.ByteTripType;
import org.tentackle.trip.types.CharacterTripType;
import org.tentackle.trip.types.DoubleTripType;
import org.tentackle.trip.types.FloatTripType;
import org.tentackle.trip.types.IntegerTripType;
import org.tentackle.trip.types.LongTripType;
import org.tentackle.trip.types.ShortTripType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Objects;

/**
 * A TripComponent implementation for record types.
 *
 * @param <T> the component type
 */
public class RecordTripComponent<P extends Record, T> implements TripComponent<P, T> {

  private static final Logger LOGGER = Logger.get(RecordTripComponent.class);

  private final TripType<P> parentType;
  private final int ordinal;
  private final RecordComponent recordComponent;
  private final T defaultValue;
  private final Class<T> componentClass;
  private final String componentName;

  /**
   * The accessor, bound to {@code (Object)<component type>}, null if it could not be bound.
   * <p>
   * A method handle instead of {@link java.lang.reflect.Method#invoke}, which is what the accessor
   * of a record component is: records carry the remote call and its response, so this runs on every
   * remote invocation. For a primitive component the handle's return type stays primitive, so
   * reading it does not box either.
   */
  private final MethodHandle accessor;

  /** the primitive kind of the component, null if it is a reference type. */
  private final PrimitiveFieldTripComponent.Kind kind;

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
    kind = PrimitiveFieldTripComponent.kindOf(componentClass);
    accessor = bindAccessor();
  }

  /**
   * Binds the record component's accessor to a method handle.
   *
   * @return the handle typed {@code (Object)<component type>}, null if it could not be bound
   */
  private MethodHandle bindAccessor() {
    Class<?> recordClass = recordComponent.getDeclaringRecord();
    if (!getClass().getModule().canRead(recordClass.getModule())) {
      // MethodHandles.lookup() is caller-sensitive: make the record's module readable by this module
      getClass().getModule().addReads(recordClass.getModule());
    }
    try {
      return MethodHandles.lookup()
                          .unreflect(recordComponent.getAccessor())
                          .asType(MethodType.methodType(componentClass.isPrimitive() ? componentClass : Object.class,
                                                        Object.class));
    }
    catch (IllegalAccessException | RuntimeException e) {
      // the record is not accessible from here: fall back to reflection, exactly as before
      LOGGER.fine("cannot bind accessor for " + recordComponent + ", falling back to reflection", e);
      return null;
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
    if (accessor != null && kind == null) {
      try {
        return (T) accessor.invokeExact((Object) parent);
      }
      catch (Throwable t) {
        throw new TripRuntimeException("cannot read " + this, t);
      }
    }
    try {
      return (T) recordComponent.getAccessor().invoke(parent);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new TripRuntimeException(e);
    }
  }

  @Override
  public int write(P parent, TripStream stream, int lastOrdinal) {
    if (accessor == null || kind == null) {
      return TripComponent.super.write(parent, stream, lastOrdinal);
    }
    // A record component never has a default value, so it is always written: no value is read
    // here that could be skipped, and the primitive stays unboxed all the way to the serializer.
    int ordinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
    TripType<T> type = getType();
    try {
      switch (kind) {
        case BOOLEAN -> ((BooleanTripType) type).writeBoolean((boolean) accessor.invokeExact((Object) parent), stream);
        case BYTE -> ((ByteTripType) type).writeByte((byte) accessor.invokeExact((Object) parent), stream);
        case CHAR -> ((CharacterTripType) type).writeChar((char) accessor.invokeExact((Object) parent), stream);
        case SHORT -> ((ShortTripType) type).writeShort((short) accessor.invokeExact((Object) parent), stream);
        case INT -> ((IntegerTripType) type).writeInt((int) accessor.invokeExact((Object) parent), stream);
        case LONG -> ((LongTripType) type).writeLong((long) accessor.invokeExact((Object) parent), stream);
        case FLOAT -> ((FloatTripType) type).writeFloat((float) accessor.invokeExact((Object) parent), stream);
        case DOUBLE -> ((DoubleTripType) type).writeDouble((double) accessor.invokeExact((Object) parent), stream);
      }
    }
    catch (TripRuntimeException tx) {
      throw tx;
    }
    catch (Throwable t) {
      throw new TripRuntimeException("cannot read " + this, t);
    }
    return ordinal;
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

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

import org.tentackle.trip.OpCode;
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

import java.lang.reflect.Field;

/**
 * A TripComponent implementation for fields of a primitive type.
 * <p>
 * Serializing a primitive field through the generic {@link FieldTripComponent} boxes it twice on
 * the way out &mdash; once to read it off the {@link java.lang.invoke.VarHandle}, whose erased call
 * site is typed {@code (Object)Object}, and once more to compare it with the default value through
 * {@link java.util.Objects#equals} &mdash; and allocates a box per field on the way in. For a
 * {@code long} or a {@code double} that is an allocation every time; for an {@code int} it is one
 * outside the {@link Integer} cache. An entity with a dozen numeric columns pays that a dozen times
 * per instance, on both sides, for every object of a result list.
 * <p>
 * This implementation keeps the value primitive end to end: the {@code VarHandle} is invoked with
 * a primitive signature, the default is compared as a primitive, and the value goes straight to the
 * primitive codec of its trip type.
 * <p>
 * A single class with a {@link Kind} switch rather than one class per primitive: the loop in
 * {@link org.tentackle.trip.types.ObjectTripType} walks a {@code TripComponent[]}, so eight
 * implementations would make that call site megamorphic, while one keeps it to two receivers.
 *
 * @param <P> the parent type
 * @param <T> the component type, the wrapper of the primitive
 */
public class PrimitiveFieldTripComponent<P, T> extends FieldTripComponent<P, T> {

  /**
   * The primitive kinds a field can have.
   */
  public enum Kind {
    /** {@code boolean}. */
    BOOLEAN,
    /** {@code byte}. */
    BYTE,
    /** {@code char}. */
    CHAR,
    /** {@code short}. */
    SHORT,
    /** {@code int}. */
    INT,
    /** {@code long}. */
    LONG,
    /** {@code float}. */
    FLOAT,
    /** {@code double}. */
    DOUBLE
  }

  private final Kind kind;

  // the default value, unboxed once, so that the default check needs no boxing at all
  private final long defaultAsLong;      // boolean, byte, char, short, int and long
  private final double defaultAsDouble;  // float and double

  /**
   * Creates a new PrimitiveFieldTripComponent.
   *
   * @param parentType the parent type
   * @param ordinal the ordinal starting from 0
   * @param field the field, of a primitive type
   * @param hasDefaultValue true if the component has a default value, false otherwise
   * @param defaultValue the default value, the wrapper of the primitive
   */
  public PrimitiveFieldTripComponent(TripType<P> parentType, int ordinal, Field field,
                                     boolean hasDefaultValue, T defaultValue) {
    super(parentType, ordinal, field, hasDefaultValue, defaultValue);
    kind = kindOf(field.getType());
    if (kind == null) {
      throw new TripRuntimeException("not a primitive field: " + field);
    }
    if (defaultValue instanceof Number number) {
      defaultAsLong = number.longValue();
      defaultAsDouble = number.doubleValue();
    }
    else if (defaultValue instanceof Boolean bool) {
      defaultAsLong = bool ? 1 : 0;
      defaultAsDouble = 0;
    }
    else if (defaultValue instanceof Character character) {
      defaultAsLong = character;
      defaultAsDouble = 0;
    }
    else {
      // no default value provided (hasDefaultValue is false, so it is never compared)
      defaultAsLong = 0;
      defaultAsDouble = 0;
    }
  }

  /**
   * Gets the primitive kind of this component.
   *
   * @return the kind, never null
   */
  public Kind getKind() {
    return kind;
  }

  @Override
  public int write(P parent, TripStream stream, int lastOrdinal) {
    TripType<T> type = getType();
    switch (kind) {
      case BOOLEAN -> {
        boolean value = (boolean) getHandle().get(parent);
        if (isDefault(value ? 1 : 0)) {
          return lastOrdinal;
        }
        lastOrdinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
        ((BooleanTripType) type).writeBoolean(value, stream);
      }
      case BYTE -> {
        byte value = (byte) getHandle().get(parent);
        if (isDefault(value)) {
          return lastOrdinal;
        }
        lastOrdinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
        ((ByteTripType) type).writeByte(value, stream);
      }
      case CHAR -> {
        char value = (char) getHandle().get(parent);
        if (isDefault(value)) {
          return lastOrdinal;
        }
        lastOrdinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
        ((CharacterTripType) type).writeChar(value, stream);
      }
      case SHORT -> {
        short value = (short) getHandle().get(parent);
        if (isDefault(value)) {
          return lastOrdinal;
        }
        lastOrdinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
        ((ShortTripType) type).writeShort(value, stream);
      }
      case INT -> {
        int value = (int) getHandle().get(parent);
        if (isDefault(value)) {
          return lastOrdinal;
        }
        lastOrdinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
        ((IntegerTripType) type).writeInt(value, stream);
      }
      case LONG -> {
        long value = (long) getHandle().get(parent);
        if (isDefault(value)) {
          return lastOrdinal;
        }
        lastOrdinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
        ((LongTripType) type).writeLong(value, stream);
      }
      case FLOAT -> {
        float value = (float) getHandle().get(parent);
        if (isDefault(value)) {
          return lastOrdinal;
        }
        lastOrdinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
        ((FloatTripType) type).writeFloat(value, stream);
      }
      case DOUBLE -> {
        double value = (double) getHandle().get(parent);
        if (isDefault(value)) {
          return lastOrdinal;
        }
        lastOrdinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
        ((DoubleTripType) type).writeDouble(value, stream);
      }
    }
    return lastOrdinal;
  }

  @Override
  public void read(P parent, TripStream stream, OpCode opCode) {
    if (isFinalField()) {
      // a VarHandle refuses to write a final field; setValue() knows the way around, at the
      // price of boxing the value. Final primitive fields are rare enough for that to be fine.
      super.read(parent, stream, opCode);
      return;
    }
    TripType<T> type = getType();
    switch (kind) {
      case BOOLEAN -> getHandle().set(parent, ((BooleanTripType) type).readBoolean(stream, opCode));
      case BYTE -> getHandle().set(parent, ((ByteTripType) type).readByte(stream, opCode));
      case CHAR -> getHandle().set(parent, ((CharacterTripType) type).readChar(stream, opCode));
      case SHORT -> getHandle().set(parent, ((ShortTripType) type).readShort(stream, opCode));
      case INT -> getHandle().set(parent, ((IntegerTripType) type).readInt(stream, opCode));
      case LONG -> getHandle().set(parent, ((LongTripType) type).readLong(stream, opCode));
      case FLOAT -> getHandle().set(parent, ((FloatTripType) type).readFloat(stream, opCode));
      case DOUBLE -> getHandle().set(parent, ((DoubleTripType) type).readDouble(stream, opCode));
    }
  }

  /**
   * Gets the primitive kind of the given class.
   *
   * @param clazz the class to map
   * @return the kind, null if the class is not primitive
   */
  public static Kind kindOf(Class<?> clazz) {
    if (clazz == Boolean.TYPE) {
      return Kind.BOOLEAN;
    }
    if (clazz == Byte.TYPE) {
      return Kind.BYTE;
    }
    if (clazz == Character.TYPE) {
      return Kind.CHAR;
    }
    if (clazz == Short.TYPE) {
      return Kind.SHORT;
    }
    if (clazz == Integer.TYPE) {
      return Kind.INT;
    }
    if (clazz == Long.TYPE) {
      return Kind.LONG;
    }
    if (clazz == Float.TYPE) {
      return Kind.FLOAT;
    }
    if (clazz == Double.TYPE) {
      return Kind.DOUBLE;
    }
    return null;
  }


  private boolean isDefault(long value) {
    return hasDefaultValue() && value == defaultAsLong;
  }

  private boolean isDefault(double value) {
    // Double.compare, not ==, so that the boxed comparison this replaces keeps its semantics
    // for NaN and for the two zeros
    return hasDefaultValue() && Double.compare(value, defaultAsDouble) == 0;
  }

}

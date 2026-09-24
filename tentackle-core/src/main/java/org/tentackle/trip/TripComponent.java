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

package org.tentackle.trip;

import org.tentackle.trip.impl.DefaultOpCode;

import java.util.Objects;

/**
 * Describes a component within a class, record, or enum.
 *
 * @param <P> the parent type
 * @param <T> the component type
 */
public interface TripComponent<P, T> {

  /**
   * Gets the trip type of the parent.
   *
   * @return the parent type
   */
  TripType<P> getParentType();

  /**
   * Gets the class of the component.
   *
   * @return the class
   */
  Class<T> getComponentClass();

  /**
   * Gets the ordinal of the component within the parent class.
   * <p>
   * For the sender, the ordinal is always identical to the dictionary
   * entry component's ordinal.<br>
   * For the receiver, the ordinal is the dictionary entry component's ordinal,
   * and the trip component's ordinal may be different!
   *
   * @return the ordinal starting from 0
   */
  int getOrdinal();

  /**
   * Gets the compile-time trip type of the field.<br>
   * If the type is not final, the runtime type may differ!
   *
   * @return the type
   */
  TripType<T> getType();

  /**
   * Gets the field- or record component name.
   *
   * @return the name
   */
  String getName();

  /**
   * Gets the value of the field or record component.
   *
   * @param parent the parent object or record
   * @return the value
   */
  T getValue(P parent);

  /**
   * Sets the value of the field or record component.
   *
   * @param parent the parent object or record
   * @param value the value to be set
   */
  void setValue(P parent, T value);

  /**
   * Returns true if the component has a default value.<br>
   * Records don't have default values.
   *
   * @return true if the component has a default value, else false
   */
  boolean hasDefaultValue();

  /**
   * Gets the default value.<br>
   * For enums, the method returns the enum constant.<br>
   * For proxies, the method returns the interface class.
   *
   * @return the default value
   */
  T getDefaultValue();

  /**
   * Writes this component of the given parent object to the stream, unless it holds its
   * default value.
   * <p>
   * The component's ordinal precedes the value, unless it simply follows the one written before
   * (see {@link TripStream#writeNextComponentOrdinal}), or it is the first component written and
   * its value is null. A component without a default value is always written, which is what records
   * and classes with a canonical constructor rely on.
   * <p>
   * The value is read from the parent exactly once, which is why the default check lives here
   * rather than in a predicate of its own: for a component backed by a getter, reading it twice
   * would mean invoking the getter twice, and for a primitive one it would mean boxing twice.
   *
   * @param parent the parent object
   * @param stream the stream to write to
   * @param lastOrdinal the ordinal of the component written before, -1 if none
   * @return the ordinal written, or {@code lastOrdinal} if the component was skipped
   */
  default int write(P parent, TripStream stream, int lastOrdinal) {
    T value = getValue(parent);
    if (hasDefaultValue() && Objects.equals(value, getDefaultValue())) {
      return lastOrdinal;
    }
    int ordinal;
    if (value == null && lastOrdinal < 0) {
      // A null code right after the parent's header would be taken for a null parent,
      // hence the first component's ordinal must be written explicitly.
      ordinal = getOrdinal();
      stream.writeComponentOrdinal(ordinal);
    }
    else {
      ordinal = stream.writeNextComponentOrdinal(lastOrdinal, this);
    }
    writeValue(value, stream);
    return ordinal;
  }

  /**
   * Writes a value of this component's type to the stream.
   *
   * @param value the value to write, null to write the null opcode
   * @param stream the stream to write to
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  default void writeValue(T value, TripStream stream) {
    if (value == null) {
      // the default is not null -> send the null code
      stream.getSerializer().serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      TripType type = getType();
      if (!type.isFinal()) {
        // if the component type is not final: determine the type dynamically
        type = stream.getType(value.getClass());
      }
      type.write(value, stream);
    }
  }

  /**
   * Reads this component's value from the stream and assigns it to the given parent object.
   *
   * @param parent the parent object
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   */
  default void read(P parent, TripStream stream, OpCode opCode) {
    setValue(parent, readValue(stream, opCode));
  }

  /**
   * Reads a value of this component's type from the stream, without assigning it.
   * <p>
   * Used where the value cannot be assigned yet, because the parent object does not exist until
   * its canonical constructor or builder has all of them.
   *
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the value read
   */
  @SuppressWarnings("unchecked")
  default T readValue(TripStream stream, OpCode opCode) {
    return (T) readValue(getType(), stream, opCode);
  }

  /**
   * Reads a value of the given type from the stream.
   * <p>
   * Static, because a component the receiver does not know must still be consumed from the
   * stream, using the type named by the sender's dictionary entry.
   *
   * @param type the compile-time type of the value
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the value read
   */
  static Object readValue(TripType<?> type, TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    if (!type.isFinal()) {
      // if the component type is not final: determine the type dynamically
      type = stream.deriveType(opCode);
      if (type.isDictionaryBased()) {
        // the next opcode is a reference or the component ordinal
        opCode = stream.receiveOpCode();
      }
    }
    return type.read(stream, opCode);
  }

}

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

import org.tentackle.misc.Convertible;

import java.io.Serializable;

/**
 * Operation Code.<br>
 * The op-code is the first byte of a serialized block and describes how
 * to process the following bytes, up to the next op-code.
 * <p>
 * Since enums cannot be extended, we're using this interface instead.
 * Application-specific modification types can be introduced as follows:
 * <ul>
 *   <li>Create another enum implementing {@link OpCode}.
 *   Make sure to use other values than in {@link org.tentackle.trip.impl.DefaultOpCode}.
 *   Write a test!</li>
 *   <li>Create a new {@link TripFactory}, annotated with {@link org.tentackle.common.Service}
 *   and override the {@link TripFactory#getOpCode(int)} and {@link TripFactory#getAllOpCodes()} methods.</li>
 * </ul>
 * Codes below <code>0x80</code> are reserved for Tentackle. Applications can define their
 * own codes starting at <code>0x80</code> up to and including <code>0xFF</code>.
 * <p>
 * To pick up the right {@link TripType} when reading from a stream, the following rules apply:
 * <ul>
 *   <li>If {@link OpCode#isFixedType()} is true: use {@link OpCode#getFixedType()}.</li>
 *   <li>If {@link OpCode#isDictionaryBased()} is true: the opcode is followed by the dictionary entry
 *   or its ordinal if the entry was already sent. Then use {@link DictionaryEntry#getType()}.</li>
 * </ul>
 *
 */
public interface OpCode extends Serializable, Convertible<Integer> {

  /**
   * Returns true if this OpCode is a reference.
   *
   * @return true if reference-code, else false
   * @see org.tentackle.trip.impl.DefaultOpCode#REFERENCE
   * @see org.tentackle.trip.impl.DefaultOpCode#REFERENCE1
   * @see org.tentackle.trip.impl.DefaultOpCode#REFERENCE2
   * @see org.tentackle.trip.impl.DefaultOpCode#REFERENCE3
   */
  boolean isReference();

  /**
   * Returns true if this OpCode is a component ordinal.
   *
   * @return true if ordinal-code, else false
   * @see org.tentackle.trip.impl.DefaultOpCode#COMPONENT_ORDINAL
   * @see org.tentackle.trip.impl.DefaultOpCode#COMPONENT_ORDINAL1
   * @see org.tentackle.trip.impl.DefaultOpCode#COMPONENT_ORDINAL2
   * @see org.tentackle.trip.impl.DefaultOpCode#COMPONENT_ORDINAL3
   * @see org.tentackle.trip.impl.DefaultOpCode#COMPONENT_ORDINAL_SKIP1
   * @see org.tentackle.trip.impl.DefaultOpCode#COMPONENT_ORDINAL_SKIP15
   */
  boolean isComponentOrdinal();

  /**
   * Returns true if this OpCode is a dictionary ordinal.
   *
   * @return true if ordinal-code, else false
   * @see org.tentackle.trip.impl.DefaultOpCode#DICTIONARY_ORDINAL
   * @see org.tentackle.trip.impl.DefaultOpCode#DICTIONARY_ORDINAL1
   * @see org.tentackle.trip.impl.DefaultOpCode#DICTIONARY_ORDINAL2
   * @see org.tentackle.trip.impl.DefaultOpCode#DICTIONARY_ORDINAL3
   */
  boolean isDictionaryOrdinal();

  /**
   * Returns true if this OpCode is dictionary-based.<br>
   * Such OpCodes belong to a trip type that creates, sends, and receives dictionary entries.
   *
   * @return true if dictionary-based, else false
   */
  boolean isDictionaryBased();

  /**
   * Returns true if this OpCode is a predefined array.
   *
   * @return true if a predefined array, else false
   */
  boolean isPredefinedArray();

  /**
   * Gets the predefined array type.
   *
   * @param arrayType the subtype of the predefined array
   * @return the predefined array type
   */
  TripType<?> getPredefinedArrayType(PredefinedArrayType arrayType);

  /**
   * Returns whether the trip type is an error type.
   *
   * @return true if error-type, false otherwise
   */
  boolean isError();

  /**
   * Returns whether the trip type can be determined directly from the op-code.
   *
   * @return true if fixed type, else false
   */
  boolean isFixedType();

  /**
   * Gets the fixed type of the trip type.<br>
   *
   * @return the fixed type
   * @throws TripRuntimeException if {@link #isFixedType()} is false
   */
  TripType<?> getFixedType();

  /**
   * Gets the unsigned byte value of this OpCode.
   *
   * @return the unsigned byte value
   */
  int getCode();

  /**
   * Converts an external Integer value to a {@link OpCode}.
   *
   * @param code the unsigned byte value (0 ... 255)
   * @return the opcode, never null
   * @throws TripRuntimeException if no valid type for the given Integer
   */
  static OpCode toInternal(Integer code) {
    return TripFactory.getInstance().getOpCode(code);
  }

  /**
   * Gets the default opcode.
   *
   * @return always throws a {@link TripRuntimeException} because there is no default
   */
  static OpCode getDefault() {
    throw new TripRuntimeException("there is no default OpCode");
  }

}

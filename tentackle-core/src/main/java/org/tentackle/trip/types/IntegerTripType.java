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

package org.tentackle.trip.types;

import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

/**
 * Trip type for {@link Integer}.
 */
@TripTypeService(Integer.class)
public sealed class IntegerTripType extends AbstractTripType<Integer> permits IntegerPrimitiveTripType {

  /**
   * Creates the type.
   */
  public IntegerTripType() {
    this(Integer.class);
  }

  /**
   * Super constructor for the primitive type.
   *
   * @param clazz the class of the type
   */
  protected IntegerTripType(Class<Integer> clazz) {
    super(clazz);
  }

  @Override
  public void write(Integer value, TripStream stream) {
    if (value == null) {
      stream.getSerializer().serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      writeInt(value, stream);
    }
  }

  @Override
  public Integer read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    // Narrowed to DefaultOpCode before switching: with the OpCode interface as the selector
    // type, javac compiles the switch to an invokedynamic typeSwitch, whose generated body is
    // a linear chain of comparisons instead of a jump table. An opcode of another
    // implementation is not one of ours and takes the same path as an unknown one.
    if (opCode instanceof DefaultOpCode code) {
      return switch (code) {
        case INTEGER -> deserializer.deserializeInt();
        case INTEGER3 -> deserializer.deserializeInt3();
        case INTEGER2 -> (int) deserializer.deserializeShort();
        case INTEGER1 -> (int) deserializer.deserializeByte();
        default -> super.read(stream, opCode);
      };
    }
    return super.read(stream, opCode);
  }


  /**
   * Writes a int value.
   * <p>
   * The primitive counterpart of {@link #write(Integer, TripStream)}, so that a primitive component
   * can be written without boxing it first.
   *
   * @param value the value to write
   * @param stream the stream to write to
   */
  public void writeInt(int value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
      if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
        serializer.serializeOpCode(DefaultOpCode.INTEGER1);
        serializer.serializeByte((byte) value);
      }
      else {
        serializer.serializeOpCode(DefaultOpCode.INTEGER2);
        serializer.serializeShort((short) value);
      }
    }
    else {
      if (value >= TripConstants.INT3_MIN && value <= TripConstants.INT3_MAX) {
        serializer.serializeOpCode(DefaultOpCode.INTEGER3);
        serializer.serializeInt3(value);
      }
      else {
        serializer.serializeOpCode(DefaultOpCode.INTEGER);
        serializer.serializeInt(value);
      }
    }
  }

  /**
   * Reads a int value.
   * <p>
   * The primitive counterpart of {@link #read(TripStream, OpCode)}, so that a primitive component
   * can be read without boxing it. The opcode switch is deliberately repeated instead of delegating:
   * going through the boxed method is exactly the allocation this avoids.
   *
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the value
   * @throws TripRuntimeException if the opcode does not denote a int
   */
  public int readInt(TripStream stream, OpCode opCode) {
    if (opCode instanceof DefaultOpCode code) {
      Deserializer deserializer = stream.getDeserializer();
      return switch (code) {
        case INTEGER -> deserializer.deserializeInt();
        case INTEGER3 -> deserializer.deserializeInt3();
        case INTEGER2 -> deserializer.deserializeShort();
        case INTEGER1 -> deserializer.deserializeByte();
        default -> throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
      };
    }
    throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
  }
}

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
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

/**
 * Trip type for {@link Short}.
 */
@TripTypeService(Short.class)
public sealed class ShortTripType extends AbstractTripType<Short> permits ShortPrimitiveTripType {

  /**
   * Creates the type.
   */
  public ShortTripType() {
    this(Short.class);
  }

  /**
   * Super constructor for the primitive type.
   *
   * @param clazz the class of the type
   */
  protected ShortTripType(Class<Short> clazz) {
    super(clazz);
  }

  @Override
  public void write(Short value, TripStream stream) {
    if (value == null) {
      stream.getSerializer().serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      writeShort(value, stream);
    }
  }

  @Override
  public Short read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    // Narrowed to DefaultOpCode before switching: with the OpCode interface as the selector
    // type, javac compiles the switch to an invokedynamic typeSwitch, whose generated body is
    // a linear chain of comparisons instead of a jump table. An opcode of another
    // implementation is not one of ours and takes the same path as an unknown one.
    if (opCode instanceof DefaultOpCode code) {
      return switch (code) {
        case SHORT -> deserializer.deserializeShort();
        case SHORT1 -> (short) deserializer.deserializeByte();
        default -> super.read(stream, opCode);
      };
    }
    return super.read(stream, opCode);
  }


  /**
   * Writes a short value.
   * <p>
   * The primitive counterpart of {@link #write(Short, TripStream)}, so that a primitive component
   * can be written without boxing it first.
   *
   * @param value the value to write
   * @param stream the stream to write to
   */
  public void writeShort(short value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
      serializer.serializeOpCode(DefaultOpCode.SHORT1);
      serializer.serializeByte((byte) value);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.SHORT);
      serializer.serializeShort(value);
    }
  }

  /**
   * Reads a short value.
   * <p>
   * The primitive counterpart of {@link #read(TripStream, OpCode)}, so that a primitive component
   * can be read without boxing it. The opcode switch is deliberately repeated instead of delegating:
   * going through the boxed method is exactly the allocation this avoids.
   *
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the value
   * @throws TripRuntimeException if the opcode does not denote a short
   */
  public short readShort(TripStream stream, OpCode opCode) {
    if (opCode instanceof DefaultOpCode code) {
      Deserializer deserializer = stream.getDeserializer();
      return switch (code) {
        case SHORT -> deserializer.deserializeShort();
        case SHORT1 -> (short) deserializer.deserializeByte();
        default -> throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
      };
    }
    throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
  }
}

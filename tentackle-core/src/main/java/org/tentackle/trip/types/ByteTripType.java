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
 * Trip type for {@link Byte}.
 */
@TripTypeService(Byte.class)
public sealed class ByteTripType extends AbstractTripType<Byte> permits BytePrimitiveTripType {

  /**
   * Creates the type.
   */
  public ByteTripType() {
    this(Byte.class);
  }

  /**
   * Creates the type for a subclass.
   *
   * @param clazz the object class, {@code Byte.class} or {@code byte.class}
   */
  protected ByteTripType(Class<Byte> clazz) {
    super(clazz);
  }

  @Override
  public void write(Byte value, TripStream stream) {
    if (value == null) {
      stream.getSerializer().serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      writeByte(value, stream);
    }
  }

  @Override
  public Byte read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    return opCode == DefaultOpCode.BYTE ? Byte.valueOf(deserializer.deserializeByte()) : super.read(stream, opCode);
  }


  /**
   * Writes a byte value.
   * <p>
   * The primitive counterpart of {@link #write(Byte, TripStream)}, so that a primitive component
   * can be written without boxing it first.
   *
   * @param value the value to write
   * @param stream the stream to write to
   */
  public void writeByte(byte value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    serializer.serializeOpCode(DefaultOpCode.BYTE);
    serializer.serializeByte(value);
  }

  /**
   * Reads a byte value.
   * <p>
   * The primitive counterpart of {@link #read(TripStream, OpCode)}, so that a primitive component
   * can be read without boxing it. The opcode switch is deliberately repeated instead of delegating:
   * going through the boxed method is exactly the allocation this avoids.
   *
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the value
   * @throws TripRuntimeException if the opcode does not denote a byte
   */
  public byte readByte(TripStream stream, OpCode opCode) {
    if (opCode instanceof DefaultOpCode code) {
      Deserializer deserializer = stream.getDeserializer();
      return switch (code) {
        case BYTE -> deserializer.deserializeByte();
        default -> throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
      };
    }
    throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
  }
}

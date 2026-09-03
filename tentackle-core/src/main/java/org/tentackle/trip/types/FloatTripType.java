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
 * Trip type for {@link Float}.
 */
@TripTypeService(Float.class)
public sealed class FloatTripType extends AbstractTripType<Float> permits FloatPrimitiveTripType {

  /**
   * Creates the type.
   */
  public FloatTripType() {
    this(Float.class);
  }

  /**
   * Super constructor for the primitive type.
   *
   * @param clazz the class of the type
   */
  protected FloatTripType(Class<Float> clazz) {
    super(clazz);
  }

  @Override
  public void write(Float value, TripStream stream) {
    if (value == null) {
      stream.getSerializer().serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      writeFloat(value, stream);
    }
  }

  @Override
  public Float read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    return opCode == DefaultOpCode.FLOAT ? Float.valueOf(deserializer.deserializeFloat()) : super.read(stream, opCode);
  }


  /**
   * Writes a float value.
   * <p>
   * The primitive counterpart of {@link #write(Float, TripStream)}, so that a primitive component
   * can be written without boxing it first.
   *
   * @param value the value to write
   * @param stream the stream to write to
   */
  public void writeFloat(float value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    serializer.serializeOpCode(DefaultOpCode.FLOAT);
    serializer.serializeFloat(value);
  }

  /**
   * Reads a float value.
   * <p>
   * The primitive counterpart of {@link #read(TripStream, OpCode)}, so that a primitive component
   * can be read without boxing it. The opcode switch is deliberately repeated instead of delegating:
   * going through the boxed method is exactly the allocation this avoids.
   *
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the value
   * @throws TripRuntimeException if the opcode does not denote a float
   */
  public float readFloat(TripStream stream, OpCode opCode) {
    if (opCode instanceof DefaultOpCode code) {
      Deserializer deserializer = stream.getDeserializer();
      return switch (code) {
        case FLOAT -> deserializer.deserializeFloat();
        default -> throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
      };
    }
    throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
  }
}

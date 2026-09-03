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
 * Trip type for {@link Double}.
 */
@TripTypeService(Double.class)
public sealed class DoubleTripType extends AbstractTripType<Double> permits DoublePrimitiveTripType {

  /**
   * Creates the type.
   */
  public DoubleTripType() {
    this(Double.class);
  }

  /**
   * Super constructor for the primitive type.
   *
   * @param clazz the class of the type
   */
  protected DoubleTripType(Class<Double> clazz) {
    super(clazz);
  }

  @Override
  public void write(Double value, TripStream stream) {
    if (value == null) {
      stream.getSerializer().serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      writeDouble(value, stream);
    }
  }

  @Override
  public Double read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    // Narrowed to DefaultOpCode before switching: with the OpCode interface as the selector
    // type, javac compiles the switch to an invokedynamic typeSwitch, whose generated body is
    // a linear chain of comparisons instead of a jump table. An opcode of another
    // implementation is not one of ours and takes the same path as an unknown one.
    if (opCode instanceof DefaultOpCode code) {
      return switch (code) {
        case DOUBLE -> deserializer.deserializeDouble();
        case DOUBLE4 -> (double) deserializer.deserializeFloat();
        default -> super.read(stream, opCode);
      };
    }
    return super.read(stream, opCode);
  }


  /**
   * Writes a double value.
   * <p>
   * The primitive counterpart of {@link #write(Double, TripStream)}, so that a primitive component
   * can be written without boxing it first.
   *
   * @param value the value to write
   * @param stream the stream to write to
   */
  public void writeDouble(double value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    float floatValue = (float) value;
    if ((double) floatValue == value) {
      serializer.serializeOpCode(DefaultOpCode.DOUBLE4);
      serializer.serializeFloat(floatValue);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.DOUBLE);
      serializer.serializeDouble(value);
    }
  }

  /**
   * Reads a double value.
   * <p>
   * The primitive counterpart of {@link #read(TripStream, OpCode)}, so that a primitive component
   * can be read without boxing it. The opcode switch is deliberately repeated instead of delegating:
   * going through the boxed method is exactly the allocation this avoids.
   *
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the value
   * @throws TripRuntimeException if the opcode does not denote a double
   */
  public double readDouble(TripStream stream, OpCode opCode) {
    if (opCode instanceof DefaultOpCode code) {
      Deserializer deserializer = stream.getDeserializer();
      return switch (code) {
        case DOUBLE -> deserializer.deserializeDouble();
        case DOUBLE4 -> (double) deserializer.deserializeFloat();
        default -> throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
      };
    }
    throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
  }
}

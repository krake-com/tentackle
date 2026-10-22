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
 * Trip type for {@link Long}.
 */
@TripTypeService(Long.class)
public sealed class LongTripType extends AbstractTripType<Long> permits LongPrimitiveTripType {

  /**
   * Creates the type.
   */
  public LongTripType() {
    this(Long.class);
  }

  /**
   * Super constructor for the primitive type.
   *
   * @param clazz the class of the type
   */
  protected LongTripType(Class<Long> clazz) {
    super(clazz);
  }

  @Override
  public void write(Long value, TripStream stream) {
    if (value == null) {
      stream.getSerializer().serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      writeLong(value, stream);
    }
  }

  @Override
  public Long read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    // Narrowed to DefaultOpCode before switching: with the OpCode interface as the selector
    // type, javac compiles the switch to an invokedynamic typeSwitch, whose generated body is
    // a linear chain of comparisons instead of a jump table. An opcode of another
    // implementation is not one of ours and takes the same path as an unknown one.
    if (opCode instanceof DefaultOpCode code) {
      return switch (code) {
        case LONG -> deserializer.deserializeLong();
        case LONG7 -> deserializer.deserializeLong7();
        case LONG6 -> deserializer.deserializeLong6();
        case LONG5 -> deserializer.deserializeLong5();
        case LONG4 -> (long) deserializer.deserializeInt();
        case LONG3 -> (long) deserializer.deserializeInt3();
        case LONG2 -> (long) deserializer.deserializeShort();
        case LONG1 -> (long) deserializer.deserializeByte();
        default -> super.read(stream, opCode);
      };
    }
    return super.read(stream, opCode);
  }


  /**
   * Writes a long value.
   * <p>
   * The primitive counterpart of {@link #write(Long, TripStream)}, so that a primitive component
   * can be written without boxing it first.
   *
   * @param value the value to write
   * @param stream the stream to write to
   */
  public void writeLong(long value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
      int intValue = (int) value;
      if (intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE) {
        if (intValue >= Byte.MIN_VALUE && intValue <= Byte.MAX_VALUE) {
          serializer.serializeOpCode(DefaultOpCode.LONG1);
          serializer.serializeByte((byte) intValue);
        }
        else {
          serializer.serializeOpCode(DefaultOpCode.LONG2);
          serializer.serializeShort((short) intValue);
        }
      }
      else {
        if (intValue >= TripConstants.INT3_MIN && intValue <= TripConstants.INT3_MAX) {
          serializer.serializeOpCode(DefaultOpCode.LONG3);
          serializer.serializeInt3(intValue);
        }
        else {
          serializer.serializeOpCode(DefaultOpCode.LONG4);
          serializer.serializeInt(intValue);
        }
      }
    }
    else {
      if (value >= TripConstants.LONG6_MIN && value <= TripConstants.LONG6_MAX) {
        if (value >= TripConstants.LONG5_MIN && value <= TripConstants.LONG5_MAX) {
          serializer.serializeOpCode(DefaultOpCode.LONG5);
          serializer.serializeLong5(value);
        }
        else {
          serializer.serializeOpCode(DefaultOpCode.LONG6);
          serializer.serializeLong6(value);
        }
      }
      else {
        if (value >= TripConstants.LONG7_MIN && value <= TripConstants.LONG7_MAX) {
          serializer.serializeOpCode(DefaultOpCode.LONG7);
          serializer.serializeLong7(value);
        }
        else {
          serializer.serializeOpCode(DefaultOpCode.LONG);
          serializer.serializeLong(value);
        }
      }
    }
  }

  /**
   * Reads a long value.
   * <p>
   * The primitive counterpart of {@link #read(TripStream, OpCode)}, so that a primitive component
   * can be read without boxing it. The opcode switch is deliberately repeated instead of delegating:
   * going through the boxed method is exactly the allocation this avoids.
   *
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the value
   * @throws TripRuntimeException if the opcode does not denote a long
   */
  public long readLong(TripStream stream, OpCode opCode) {
    if (opCode instanceof DefaultOpCode code) {
      Deserializer deserializer = stream.getDeserializer();
      return switch (code) {
        case LONG -> deserializer.deserializeLong();
        case LONG7 -> deserializer.deserializeLong7();
        case LONG6 -> deserializer.deserializeLong6();
        case LONG5 -> deserializer.deserializeLong5();
        case LONG4 -> (long) deserializer.deserializeInt();
        case LONG3 -> (long) deserializer.deserializeInt3();
        case LONG2 -> (long) deserializer.deserializeShort();
        case LONG1 -> (long) deserializer.deserializeByte();
        default -> throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
      };
    }
    throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
  }
}

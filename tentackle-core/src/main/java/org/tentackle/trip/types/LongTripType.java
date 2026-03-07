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
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      long longValue = value;
      if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
        int intValue = (int) longValue;
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
        if (longValue >= TripConstants.LONG6_MIN && longValue <= TripConstants.LONG6_MAX) {
          if (longValue >= TripConstants.LONG5_MIN && longValue <= TripConstants.LONG5_MAX) {
            serializer.serializeOpCode(DefaultOpCode.LONG5);
            serializer.serializeLong5(longValue);
          }
          else {
            serializer.serializeOpCode(DefaultOpCode.LONG6);
            serializer.serializeLong6(longValue);
          }
        }
        else {
          if (longValue >= TripConstants.LONG7_MIN && longValue <= TripConstants.LONG7_MAX) {
            serializer.serializeOpCode(DefaultOpCode.LONG7);
            serializer.serializeLong7(longValue);
          }
          else {
            serializer.serializeOpCode(DefaultOpCode.LONG);
            serializer.serializeLong(longValue);
          }
        }
      }
    }
  }

  @Override
  public Long read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    return switch (opCode) {
      case DefaultOpCode.LONG -> deserializer.deserializeLong();
      case DefaultOpCode.LONG7 -> deserializer.deserializeLong7();
      case DefaultOpCode.LONG6 -> deserializer.deserializeLong6();
      case DefaultOpCode.LONG5 -> deserializer.deserializeLong5();
      case DefaultOpCode.LONG4 -> (long) deserializer.deserializeInt();
      case DefaultOpCode.LONG3 -> (long) deserializer.deserializeInt3();
      case DefaultOpCode.LONG2 -> (long) deserializer.deserializeShort();
      case DefaultOpCode.LONG1 -> (long) deserializer.deserializeByte();
      default -> super.read(stream, opCode);
    };
  }

}

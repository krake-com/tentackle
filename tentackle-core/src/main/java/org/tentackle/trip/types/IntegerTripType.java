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
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      int intValue = value;
      if (intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE) {
        if (intValue >= Byte.MIN_VALUE && intValue <= Byte.MAX_VALUE) {
          serializer.serializeOpCode(DefaultOpCode.INTEGER1);
          serializer.serializeByte((byte) intValue);
        }
        else {
          serializer.serializeOpCode(DefaultOpCode.INTEGER2);
          serializer.serializeShort((short) intValue);
        }
      }
      else {
        if (intValue >= TripConstants.INT3_MIN && intValue <= TripConstants.INT3_MAX) {
          serializer.serializeOpCode(DefaultOpCode.INTEGER3);
          serializer.serializeInt3(intValue);
        }
        else {
          serializer.serializeOpCode(DefaultOpCode.INTEGER);
          serializer.serializeInt(intValue);
        }
      }
    }
  }

  @Override
  public Integer read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    return switch (opCode) {
      case DefaultOpCode.INTEGER -> deserializer.deserializeInt();
      case DefaultOpCode.INTEGER3 -> deserializer.deserializeInt3();
      case DefaultOpCode.INTEGER2 -> (int) deserializer.deserializeShort();
      case DefaultOpCode.INTEGER1 -> (int) deserializer.deserializeByte();
      default -> super.read(stream, opCode);
    };
  }

}

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
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      float floatValue = value.floatValue();
      if ((double) floatValue == value) {
        serializer.serializeOpCode(DefaultOpCode.DOUBLE4);
        serializer.serializeFloat(floatValue);
      }
      else {
        serializer.serializeOpCode(DefaultOpCode.DOUBLE);
        serializer.serializeDouble(value);
      }
    }
  }

  @Override
  public Double read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    return switch (opCode) {
      case DefaultOpCode.DOUBLE -> deserializer.deserializeDouble();
      case DefaultOpCode.DOUBLE4 -> (double) deserializer.deserializeFloat();
      default -> super.read(stream, opCode);
    };
  }

}

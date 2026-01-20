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
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.FLOAT);
      serializer.serializeFloat(value);
    }
  }

  @Override
  public Float read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    return opCode == DefaultOpCode.FLOAT ? Float.valueOf(deserializer.deserializeFloat()) : super.read(stream, opCode);
  }

}

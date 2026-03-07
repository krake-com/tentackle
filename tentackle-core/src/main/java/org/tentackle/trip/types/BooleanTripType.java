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
 * Trip type for {@link Boolean}.
 */
@TripTypeService(Boolean.class)
public sealed class BooleanTripType extends AbstractTripType<Boolean> permits BooleanPrimitiveTripType {

  /**
   * Creates the type.
   */
  public BooleanTripType() {
    this(Boolean.class);
  }

  /**
   * Super constructor for the primitive type.
   *
   * @param clazz the class of the type
   */
  protected BooleanTripType(Class<Boolean> clazz) {
    super(clazz);
  }

  @Override
  public void write(Boolean value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.BOOLEAN);
      serializer.serializeBoolean(value);
    }
  }

  @Override
  public Boolean read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    return opCode == DefaultOpCode.BOOLEAN ? Boolean.valueOf(deserializer.deserializeBoolean()) : super.read(stream, opCode);
  }

}

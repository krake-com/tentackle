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

import java.util.UUID;

/**
 * Trip type for {@link UUID}.
 */
@TripTypeService(UUID.class)
public final class UUIDTripType extends AbstractTripType<UUID> {

  /**
   * Creates the type.
   */
  public UUIDTripType() {
    super(UUID.class);
  }

  @Override
  public void write(UUID value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.UUID);
      serializer.serializeLong(value.getMostSignificantBits());
      serializer.serializeLong(value.getLeastSignificantBits());
    }
  }

  @Override
  public UUID read(TripStream stream, OpCode opCode) {
    UUID value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.UUID) {
      value = new UUID(deserializer.deserializeLong(),
                       deserializer.deserializeLong());
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}

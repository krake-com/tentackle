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

import java.time.Instant;

/**
 * Trip type for {@link Instant}.
 */
@TripTypeService(Instant.class)
public final class InstantTripType extends AbstractTripType<Instant> {

  /**
   * Creates the type.
   */
  public InstantTripType() {
    super(Instant.class);
  }

  @Override
  public void write(Instant value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.INSTANT);
      serializer.serializeLong(value.getEpochSecond());
      serializer.serializeInt(value.getNano());
    }
  }

  @Override
  public Instant read(TripStream stream, OpCode opCode) {
    Instant value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.INSTANT) {
      value = Instant.ofEpochSecond(deserializer.deserializeLong(),
                                    deserializer.deserializeInt());
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}

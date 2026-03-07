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

import java.time.OffsetTime;
import java.time.ZoneOffset;

/**
 * Trip type for {@link OffsetTime}.
 */
@TripTypeService(OffsetTime.class)
public final class OffsetTimeTripType extends AbstractTripType<OffsetTime> {

  /**
   * Creates the type.
   */
  public OffsetTimeTripType() {
    super(OffsetTime.class);
  }

  @Override
  public void write(OffsetTime value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.OFFSETTIME);
      serializer.serializeUnsignedAsByte(value.getHour());
      serializer.serializeUnsignedAsByte(value.getMinute());
      serializer.serializeUnsignedAsByte(value.getSecond());
      serializer.serializeInt(value.getNano());
      serializer.serializeInt(value.getOffset().getTotalSeconds());
    }
  }

  @Override
  public OffsetTime read(TripStream stream, OpCode opCode) {
    OffsetTime value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.OFFSETTIME) {
      value = OffsetTime.of(deserializer.deserializeByteAsUnsigned(),
                            deserializer.deserializeByteAsUnsigned(),
                            deserializer.deserializeByteAsUnsigned(),
                            deserializer.deserializeInt(),
                            ZoneOffset.ofTotalSeconds(deserializer.deserializeInt()));
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}

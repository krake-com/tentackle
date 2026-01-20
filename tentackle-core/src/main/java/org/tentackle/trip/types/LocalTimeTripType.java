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

import java.time.LocalTime;

/**
 * Trip type for {@link LocalTime}.
 */
@TripTypeService(LocalTime.class)
public final class LocalTimeTripType extends AbstractTripType<LocalTime> {

  /**
   * Creates the type.
   */
  public LocalTimeTripType() {
    super(LocalTime.class);
  }

  @Override
  public void write(LocalTime value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.LOCALTIME);
      serializer.serializeUnsignedAsByte(value.getHour());
      serializer.serializeUnsignedAsByte(value.getMinute());
      serializer.serializeUnsignedAsByte(value.getSecond());
      serializer.serializeInt(value.getNano());
    }
  }

  @Override
  public LocalTime read(TripStream stream, OpCode opCode) {
    LocalTime value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.LOCALTIME) {
      value = LocalTime.of(deserializer.deserializeByteAsUnsigned(),
                           deserializer.deserializeByteAsUnsigned(),
                           deserializer.deserializeByteAsUnsigned(),
                           deserializer.deserializeInt());
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}

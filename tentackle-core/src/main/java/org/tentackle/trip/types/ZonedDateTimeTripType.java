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
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.time.ZoneId;
import java.time.ZonedDateTime;


interface ZonedDateTimeTypeHolder {
  TripType<String> STRING_INSTANCE = TripFactory.getInstance().getType(String.class);
}


/**
 * Trip type for {@link ZonedDateTime}.
 */
@TripTypeService(ZonedDateTime.class)
public final class ZonedDateTimeTripType extends AbstractTripType<ZonedDateTime> {

  /**
   * Creates the type.
   */
  public ZonedDateTimeTripType() {
    super(ZonedDateTime.class);
  }

  @Override
  public void write(ZonedDateTime value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.ZONEDDATETIME);
      serializer.serializeInt(value.getYear());
      serializer.serializeUnsignedAsByte(value.getMonthValue());
      serializer.serializeUnsignedAsByte(value.getDayOfMonth());
      serializer.serializeUnsignedAsByte(value.getHour());
      serializer.serializeUnsignedAsByte(value.getMinute());
      serializer.serializeUnsignedAsByte(value.getSecond());
      serializer.serializeInt(value.getNano());
      String zoneId = value.getZone().getId();
      ZonedDateTimeTypeHolder.STRING_INSTANCE.write(zoneId, stream);
    }
  }

  @Override
  public ZonedDateTime read(TripStream stream, OpCode opCode) {
    ZonedDateTime value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.ZONEDDATETIME) {
      value = ZonedDateTime.of(deserializer.deserializeInt(),
                               deserializer.deserializeByteAsUnsigned(),
                               deserializer.deserializeByteAsUnsigned(),
                               deserializer.deserializeByteAsUnsigned(),
                               deserializer.deserializeByteAsUnsigned(),
                               deserializer.deserializeByteAsUnsigned(),
                               deserializer.deserializeInt(),
                               ZoneId.of(ZonedDateTimeTypeHolder.STRING_INSTANCE.read(stream, stream.receiveOpCode())));
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}

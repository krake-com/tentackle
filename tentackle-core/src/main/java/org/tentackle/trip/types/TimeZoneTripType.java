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

import java.util.TimeZone;


interface TimeZoneTypeHolder {
  TripType<String> STRING_INSTANCE = TripFactory.getInstance().getType(String.class);
}


/**
 * Trip type for {@link TimeZone}.
 */
@TripTypeService(TimeZone.class)
public final class TimeZoneTripType extends AbstractTripType<TimeZone> {

  /**
   * Creates the type.
   */
  public TimeZoneTripType() {
    super(TimeZone.class);
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public void write(TimeZone value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.LOCALE);
      TimeZoneTypeHolder.STRING_INSTANCE.write(value.getID(), stream);
    }
  }

  @Override
  public TimeZone read(TripStream stream, OpCode opCode) {
    TimeZone value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.LOCALE) {
      value = TimeZone.getTimeZone(TimeZoneTypeHolder.STRING_INSTANCE.read(stream, deserializer.deserializeOpCode()));
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}

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

import org.tentackle.common.DateHelper;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.sql.Time;
import java.util.Calendar;

import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.SECOND;

/**
 * Trip type for {@link Time}.
 * <p>
 * Times are treated as value objects and thus not referenceable.
 * In Tentackle, org.tentackle.common.Times are usually frozen, but java.sql.Times are
 * mutable and therefore technically no real value objects. However, since it is very bad practice to modify
 * such objects or share their references, we treat them as non-referenceable value objects.
 */
@TripTypeService(Time.class)
public final class TimeTripType extends AbstractTripType<Time> {

  private static final byte SQL_TIME = 'S';
  private static final byte TENTACKLE_TIME = 'T';
  private static final byte TENTACKLE_FROZEN_TIME = 'F';

  /**
   * Creates the type.
   */
  public TimeTripType() {
    super(Time.class);
  }

  @Override
  public boolean isFinal() {
    // Time is not final, but this type provides its own class types to
    // distinguish between sql- and Tentackle times.
    return true;
  }

  @Override
  public void write(Time value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.TIME);
      if (value instanceof org.tentackle.common.Time ttt) {
        serializer.serializeByte(ttt.isFrozen() ? TENTACKLE_FROZEN_TIME : TENTACKLE_TIME);
        Calendar calendar = DateHelper.today();
        calendar.setTime(value);
        serializer.serializeUnsignedAsByte(calendar.get(HOUR_OF_DAY));
        serializer.serializeUnsignedAsByte(calendar.get(MINUTE));
        serializer.serializeUnsignedAsByte(calendar.get(SECOND));
        serializer.serializeUnsignedAsShort(calendar.get(MILLISECOND));
      }
      else {
        serializer.serializeByte(SQL_TIME);
        serializer.serializeLong(value.getTime());
      }
    }
  }

  @Override
  public Time read(TripStream stream, OpCode opCode) {
    Time value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.TIME) {
      byte subType = deserializer.deserializeByte();
      if (subType == SQL_TIME) {
        value = new Time(deserializer.deserializeLong());
      }
      else {
        int hours = deserializer.deserializeByteAsUnsigned();
        int minutes = deserializer.deserializeByteAsUnsigned();
        int seconds = deserializer.deserializeByteAsUnsigned();
        int millis = deserializer.deserializeShortAsUnsigned();
        Calendar cal = DateHelper.today();
        cal.set(1970, 0, 1, hours, minutes, seconds);
        cal.set(MILLISECOND, millis);
        org.tentackle.common.Time tttime = new org.tentackle.common.Time(cal.getTimeInMillis());
        if (subType == TENTACKLE_FROZEN_TIME) {
          tttime.freeze();
        }
        value = tttime;
      }
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}

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

import java.sql.Timestamp;
import java.util.Calendar;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;

/**
 * Trip type for {@link Timestamp}.
 * <p>
 * Timestamps are treated as value objects and thus not referenceable.
 * In Tentackle, org.tentackle.common.Timestamps are usually frozen, but java.sql.Timestamps are
 * mutable and therefore technically no real value objects. However, since it is very bad practice to modify
 * such objects or share their references, we treat them as non-referenceable value objects.
 */
@TripTypeService(Timestamp.class)
public final class TimestampTripType extends AbstractTripType<Timestamp> {

  private static final byte SQL_TIMESTAMP = 'S';
  private static final byte TENTACKLE_TIMESTAMP = 'T';
  private static final byte TENTACKLE_FROZEN_TIMESTAMP = 'F';
  private static final byte TENTACKLE_UTC_TIMESTAMP = 'U';
  private static final byte TENTACKLE_FROZEN_UTC_TIMESTAMP = 'X';

  /**
   * Creates the type.
   */
  public TimestampTripType() {
    super(Timestamp.class);
  }

  @Override
  public boolean isFinal() {
    // Timestamp is not final, but this type provides its own class types to
    // distinguish between sql- and Tentackle timestamps.
    return true;
  }

  @Override
  public void write(Timestamp value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.TIMESTAMP);
      if (value instanceof org.tentackle.common.Timestamp tts) {
        if (tts.isFrozen()) {
          serializer.serializeByte(tts.isUTC() ? TENTACKLE_FROZEN_UTC_TIMESTAMP : TENTACKLE_FROZEN_TIMESTAMP);
        }
        else {
          serializer.serializeByte(tts.isUTC() ? TENTACKLE_UTC_TIMESTAMP : TENTACKLE_TIMESTAMP);
        }
        if (tts.isUTC()) {
          Calendar calendar = DateHelper.toCalendar(tts);
          serializer.serializeUnsignedAsShort(calendar.get(YEAR));
          serializer.serializeUnsignedAsByte(calendar.get(MONTH));
          serializer.serializeUnsignedAsByte(calendar.get(DAY_OF_MONTH));
          serializer.serializeUnsignedAsByte(calendar.get(HOUR_OF_DAY));
          serializer.serializeUnsignedAsByte(calendar.get(MINUTE));
          serializer.serializeUnsignedAsByte(calendar.get(SECOND));
          serializer.serializeUnsignedAsShort(calendar.get(MILLISECOND));
        }
        else {
          serializer.serializeLong(value.getTime());
        }
      }
      else {
        serializer.serializeByte(SQL_TIMESTAMP);
        serializer.serializeLong(value.getTime());
      }
      serializer.serializeInt(value.getNanos());
    }
  }

  @Override
  public Timestamp read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    Timestamp value;
    if (opCode == DefaultOpCode.TIMESTAMP) {
      byte subType = deserializer.deserializeByte();
      if (subType != SQL_TIMESTAMP) {    // if Tentackle timestamp
        if (subType == TENTACKLE_UTC_TIMESTAMP || subType == TENTACKLE_FROZEN_UTC_TIMESTAMP) {
          int year = deserializer.deserializeShortAsUnsigned();
          int month = deserializer.deserializeByteAsUnsigned();
          int day = deserializer.deserializeByteAsUnsigned();
          int hours = deserializer.deserializeByteAsUnsigned();
          int minutes = deserializer.deserializeByteAsUnsigned();
          int seconds = deserializer.deserializeByteAsUnsigned();
          int millis = deserializer.deserializeShortAsUnsigned();
          Calendar cal = DateHelper.today();
          cal.set(year, month, day, hours, minutes, seconds);
          cal.set(MILLISECOND, millis);
          value = new org.tentackle.common.Timestamp(cal.getTimeInMillis(), true);
        }
        else {
          value = new org.tentackle.common.Timestamp(deserializer.deserializeLong());
        }
        value.setNanos(deserializer.deserializeInt());
        if (subType == TENTACKLE_FROZEN_TIMESTAMP || subType == TENTACKLE_FROZEN_UTC_TIMESTAMP) {
          ((org.tentackle.common.Timestamp) value).freeze();
        }
      }
      else {  // sql timestamp
        value = new Timestamp(deserializer.deserializeLong());
        value.setNanos(deserializer.deserializeInt());
      }
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}

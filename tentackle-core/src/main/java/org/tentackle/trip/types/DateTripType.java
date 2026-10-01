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

import java.util.Calendar;
import java.util.Date;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;

/**
 * Trip type for {@link Date}.
 * <p>
 * Dates are treated as value objects and thus not referenceable.
 * In Tentackle, org.tentackle.common.Dates are usually frozen, but java.util.Dates are
 * mutable and therefore technically no real value objects. However, since it is very bad practice to modify
 * such objects or share their references, we treat them as non-referenceable value objects.
 */
@TripTypeService(Date.class)
public final class DateTripType extends AbstractTripType<Date> {

  private static final byte UTIL_DATE = 'U';
  private static final byte SQL_DATE = 'S';
  private static final byte TENTACKLE_DATE = 'T';
  private static final byte TENTACKLE_FROZEN_DATE = 'F';

  /**
   * Creates the type.
   */
  public DateTripType() {
    super(Date.class);
  }

  @Override
  public boolean isFinal() {
    // Date is not final, but this type provides its own class types to
    // distinguish between util-, sql- and Tentackle dates.
    return true;
  }

  @Override
  public void write(Date value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.DATE);
      if (value instanceof org.tentackle.common.Date ttd) {
        serializer.serializeByte(ttd.isFrozen() ? TENTACKLE_FROZEN_DATE : TENTACKLE_DATE);
        Calendar calendar = DateHelper.today();
        calendar.setTime(value);
        serializer.serializeUnsignedAsShort(calendar.get(YEAR));
        serializer.serializeUnsignedAsByte(calendar.get(MONTH));
        serializer.serializeUnsignedAsByte(calendar.get(DAY_OF_MONTH));
      }
      else if (value instanceof java.sql.Date) {
        serializer.serializeByte(SQL_DATE);
        serializer.serializeLong(value.getTime());
      }
      else {
        serializer.serializeByte(UTIL_DATE);
        serializer.serializeLong(value.getTime());
      }
    }
  }

  @Override
  public Date read(TripStream stream, OpCode opCode) {
    Date value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.DATE) {
      byte subType = deserializer.deserializeByte();
      if (subType == UTIL_DATE) {
        value = new Date(deserializer.deserializeLong());
      }
      else if (subType == SQL_DATE) {
        value = new java.sql.Date(deserializer.deserializeLong());
      }
      else {
        int year = deserializer.deserializeShortAsUnsigned();
        int month = deserializer.deserializeByteAsUnsigned();
        int day = deserializer.deserializeByteAsUnsigned();
        Calendar cal = DateHelper.today();
        cal.set(year, month, day, 0, 0, 0);
        cal.set(MILLISECOND, 0);
        long timeInMillis = cal.getTimeInMillis();
        if (subType == TENTACKLE_FROZEN_DATE) {
          org.tentackle.common.Date ttdate = new org.tentackle.common.Date(timeInMillis);
          ttdate.freeze();
          value = ttdate;
        }
        else {
          value = new org.tentackle.common.Date(timeInMillis);
        }
      }
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}

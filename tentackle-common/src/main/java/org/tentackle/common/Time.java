/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.common;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Calendar;

/**
 * Time with database semantics.
 * <p>
 * Differs from {@code java.sql.Time} when
 * serialized/deserialized in different timezones. Databases don't provide a
 * date and no timezone for a time, i.e. when sent over wire to different
 * locations a time should always remain the same for all timezones. In
 * {@code java.sql.Time}, however, the epochal time is serialized which may lead
 * to a different time when deserialized in another timezone. In {@link Time}
 * the effective date is serialized as HHMMSSmmm to provide the same semantics
 * as databases do.
 *
 * @author harald
 */
public class Time extends java.sql.Time {

  private static final long serialVersionUID = 8397324403548013688L;

  private transient Calendar cal;       // lazy calendar


  /**
   * Constructs a
   * <code>Time</code> object using a milliseconds time value.
   *
   * @param time milliseconds since January 1, 1970, 00:00:00 GMT; a negative
   * number is milliseconds before January 1, 1970, 00:00:00 GMT
   */
  public Time(long time) {
    super(time);
  }


  /**
   * Creates the current time.
   *
   */
  public Time() {
    this(System.currentTimeMillis());
  }



  /**
   * Gets the calendar for this date.
   *
   * @return the calendar
   */
  public Calendar getCalendar() {
    if (cal == null) {
      cal = Calendar.getInstance();
      cal.setTime(this);
    }
    return cal;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to clear the lazy calendar.
   */
  @Override
  public void setTime(long time) {
    super.setTime(time);
    cal = null;
  }


  /**
   * Save the state of this object to a stream.
   *
   * @serialData the value HHMMSSsss is emitted. This allows serializing
   * and deserializing in different timezones without changing the time.
   * @param s the stream
   * @throws IOException if writing to stream failed
   */
  private void writeObject(ObjectOutputStream s)
          throws IOException {
    s.writeByte(getCalendar().get(Calendar.HOUR_OF_DAY));
    s.writeByte(getCalendar().get(Calendar.MINUTE));
    s.writeByte(getCalendar().get(Calendar.SECOND));
    s.writeShort(getCalendar().get(Calendar.MILLISECOND));
  }


  /**
   * Reconstitute this object from a stream.
   *
   * @param s the stream
   * @throws IOException if reading from stream failed
   * @throws ClassNotFoundException if class contained in stream is not found
   */
  private void readObject(ObjectInputStream s)
          throws IOException, ClassNotFoundException {
    int hours = s.readByte();
    int minutes = s.readByte();
    int seconds = s.readByte();
    int millis = s.readShort();
    cal = Calendar.getInstance();
    cal.set(1970, 0, 1, hours, minutes, seconds);
    cal.set(Calendar.MILLISECOND, millis);
    super.setTime(cal.getTimeInMillis());
  }

}

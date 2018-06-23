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
 * Timestamp with UTC option.
 * <p>
 * Differs from {@code java.sql.Timestamp} when
 * serialized/deserialized in different timezones.
 * By default, if timestamps are serialized and deserialized in different timezones,
 * their representation may differ according to the GMT-offset and/or daylight saving.
 * This is okay for most cases. In some cases, however, a timestamp
 * should not be converted, i.e. regarded as <em>fixed</em> and related to some
 * timezone by convention, for example UTC. This is what the UTC-attribute
 * and the tentackle model's [UTC]-option is provided for.
 * <p>
 * Furthermore, tentackle timestamps can be freezed, i.e. made immutable. This is used,
 * for example, when the value is fetched from the database and stored in a PDO.
 *
 * @author harald
 */
@SuppressWarnings("deprecation")
public class Timestamp extends java.sql.Timestamp {

  private static final long serialVersionUID = 2745179027874758503L;


  /**
   * Creates a freezed timestamp.
   *
   * @param epoch epochal milliseconds since January 1, 1970, 00:00:00 GMT
   * @param nanos the new fractional seconds component
   * @return the freezed timestamp
   */
  public static Timestamp createFreezed(long epoch, int nanos) {
    Timestamp timestamp = new Timestamp(epoch);
    timestamp.setNanos(nanos);
    timestamp.freeze();
    return timestamp;
  }


  private boolean utc;                  // true if timestamp is always UTC
  private boolean freezed;              // true if timestamp cannot be modified anymore
  private transient Calendar cal;       // lazy calendar

  /**
   * Constructs a
   * <code>Timestamp</code> object using a milliseconds time value. The integral
   * seconds are stored in the underlying date value; the fractional seconds are
   * stored in the
   * <code>nanos</code> field of the
   * <code>Timestamp</code> object.
   *
   * @param time milliseconds since January 1, 1970, 00:00:00 GMT. A negative
   * number is the number of milliseconds before January 1, 1970, 00:00:00 GMT.
   * @see java.util.Calendar
   */
  public Timestamp(long time) {
    super(time);
  }

  /**
   * Creates the current timestamp.
   */
  public Timestamp() {
    this(System.currentTimeMillis());
  }

  /**
   * {@inheritDoc}
   * <p>
   * Cloned timestamps are always not freezed.
   */
  @Override
  public Timestamp clone() {
    Timestamp timestamp = (Timestamp) super.clone();
    timestamp.freezed = false;
    return timestamp;
  }

  /**
   * Sets the UTC flag.
   * <p>
   * Timestamps with UTC set are serialized and deserialized without timezone.
   *
   * @param utc the UTC flag
   */
  public void setUTC(boolean utc) {
    this.utc = utc;
  }

  /**
   * Gets the UTC flag.
   *
   * @return true if timestamp is UTC
   */
  public boolean isUTC() {
    return utc;
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
   * Freeze the state.<br>
   * Any attempt to modify the state results in a {@link TentackleRuntimeException}.
   */
  public void freeze() {
    freezed = true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to clear the lazy calendar and to check for freezed.
   */
  @Override
  public void setTime(long timestamp) {
    assertNotFreezed();
    super.setTime(timestamp);
    cal = null;
  }

  @Override
  public void setNanos(int n) {
    assertNotFreezed();
    super.setNanos(n);
  }

  @Override
  public void setYear(int year) {
    assertNotFreezed();
    super.setYear(year);
  }

  @Override
  public void setMonth(int month) {
    assertNotFreezed();
    super.setMonth(month);
  }

  @Override
  public void setDate(int date) {
    assertNotFreezed();
    super.setDate(date);
  }

  @Override
  public void setHours(int hours) {
    assertNotFreezed();
    super.setHours(hours);
  }

  @Override
  public void setMinutes(int minutes) {
    assertNotFreezed();
    super.setMinutes(minutes);
  }

  @Override
  public void setSeconds(int seconds) {
    assertNotFreezed();
    super.setSeconds(seconds);
  }

  private void assertNotFreezed() {
    if (freezed) {
      throw new TentackleRuntimeException("timestamp is freezed");
    }
  }


  /**
   * Save the state of this object to a stream.
   *
   * @serialData the value YYYYMMDDHHMMSS is emitted. This allows serializing
   * and deserializing in different timezones without changing the timestamp.
   *
   * @param s the stream
   * @throws IOException if writing to stream failed
   */
  private void writeObject(ObjectOutputStream s)
          throws IOException {
    s.writeBoolean(utc);
    if (utc) {
      s.writeShort(getCalendar().get(Calendar.YEAR));
      s.writeByte(getCalendar().get(Calendar.MONTH));
      s.writeByte(getCalendar().get(Calendar.DAY_OF_MONTH));
      s.writeByte(getCalendar().get(Calendar.HOUR_OF_DAY));
      s.writeByte(getCalendar().get(Calendar.MINUTE));
      s.writeByte(getCalendar().get(Calendar.SECOND));
      s.writeInt(getNanos());
    }
    else  {
      s.writeLong(getTime());
      s.writeInt(getNanos());
    }
    s.writeBoolean(freezed);
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
    utc = s.readBoolean();
    if (utc) {
      int year = s.readShort();
      int month = s.readByte();
      int day = s.readByte();
      int hours = s.readByte();
      int minutes = s.readByte();
      int seconds = s.readByte();
      int nanos = s.readInt();
      cal = Calendar.getInstance();
      cal.set(year, month, day, hours, minutes, seconds);
      super.setTime(cal.getTimeInMillis());
      setNanos(nanos);
    }
    else  {
      super.setTime(s.readLong());
      setNanos(s.readInt());
    }
    freezed = s.readBoolean();
  }

}

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

package org.tentackle.common;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Calendar;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;


/**
 * Timestamp with a UTC option.
 * <p>
 * Differs from {@code java.sql.Timestamp} when
 * serialized/deserialized in different timezones.
 * By default, if timestamps are serialized and deserialized in different timezones,
 * their representation may differ, according to the GMT offset and/or daylight saving.
 * This is okay for most cases. In some cases, however, a timestamp
 * should not be converted, i.e., regarded as <em>fixed</em> and related to some
 * timezone by convention, for example, UTC. This is what the UTC attribute
 * and the tentackle model's [UTC]-option are provided for.
 * <p>
 * Furthermore, tentackle timestamps can be frozen, i.e., made immutable (see {@link Freezable}).
 *
 * @author harald
 */
public class Timestamp extends java.sql.Timestamp implements Freezable {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Creates a frozen timestamp.<br>
   * The UTC flag is false.
   *
   * @param epoch epochal milliseconds since January 1, 1970, 00:00:00 GMT
   * @param nanos the new fractional seconds component
   * @return the frozen timestamp
   */
  public static Timestamp createFrozen(long epoch, int nanos) {
    Timestamp timestamp = new Timestamp(epoch);
    timestamp.setNanos(nanos);
    timestamp.freeze();
    return timestamp;
  }

  /**
   * Sets the UTC flag null-safe.<br>
   * Method provided to reduce generated code.
   *
   * @param timestamp the timestamp (ignored if null)
   * @param utc the UTC flag
   */
  public static void setUTC(Timestamp timestamp, boolean utc) {
    if (timestamp != null) {
      timestamp.setUTC(utc);
    }
  }

  /**
   * Converts a string into a timestamp.<br>
   * Same as {@link java.sql.Timestamp#valueOf(String)}, but returns a tentackle timestamp
   * (not frozen, UTC flag false).
   *
   * @param str the string in JDBC timestamp escape format {@code yyyy-[m]m-[d]d hh:mm:ss[.f...]}
   * @return the timestamp
   * @throws IllegalArgumentException if the string is not in the expected format
   */
  public static Timestamp valueOf(String str) {
    java.sql.Timestamp sqlTimestamp = java.sql.Timestamp.valueOf(str);
    Timestamp timestamp = new Timestamp(sqlTimestamp.getTime());
    timestamp.setNanos(sqlTimestamp.getNanos());    // keep the fraction below milliseconds as well
    return timestamp;
  }


  /** true if the timestamp is always UTC. */
  private boolean utc;

  /** true if the timestamp cannot be modified anymore. */
  private boolean frozen;


  /**
   * Constructs a <code>Timestamp</code> object based on the epochal time based in milliseconds.
   *
   * @param time milliseconds since January 1, 1970, 00:00:00 GMT
   * @param utc true if the timestamp is not translated to the local timezone when serialized
   * @see #setUTC(boolean)
   */
  public Timestamp(long time, boolean utc) {
    super(time);
    setUTC(utc);
  }

  /**
   * Constructs a <code>Timestamp</code> object based on the epochal time based in milliseconds.
   *
   * @param time milliseconds since January 1, 1970, 00:00:00 GMT
   */
  public Timestamp(long time) {
    this(time, false);
  }

  /**
   * Creates the current timestamp.
   *
   * @param utc true if the timestamp is not translated to the local timezone when serialized
   * @see #setUTC(boolean)
   */
  public Timestamp(boolean utc) {
    this(System.currentTimeMillis());
    setUTC(utc);
  }

  /**
   * Creates the current timestamp.
   */
  public Timestamp() {
    this(false);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Cloned timestamps are always not frozen.
   */
  @Override
  public Timestamp clone() {
    Timestamp timestamp = (Timestamp) super.clone();
    timestamp.frozen = false;
    return timestamp;
  }

  /**
   * Sets the UTC flag.
   * <p>
   * Timestamps with UTC=true are serialized and deserialized without being
   * adjusted to the local timezone.
   *
   * @param utc the UTC flag
   */
  public void setUTC(boolean utc) {
    this.utc = utc;
  }

  /**
   * Gets the UTC flag.
   *
   * @return true if the timestamp is UTC
   */
  public boolean isUTC() {
    return utc;
  }


  @Override
  public void freeze() {
    frozen = true;
  }

  @Override
  public boolean isFrozen() {
    return frozen;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to check for frozen.
   */
  @Override
  public void setTime(long timestamp) {
    assertNotFrozen();
    super.setTime(timestamp);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to check for frozen.
   */
  @Override
  public void setNanos(int n) {
    assertNotFrozen();
    super.setNanos(n);
  }

  // others overridden to check for frozen.
  // those methods are deprecated anyway but may still be used by an application

  @Deprecated
  @Override
  public void setYear(int year) {
    assertNotFrozen();
    super.setYear(year);
  }

  @Deprecated
  @Override
  public void setMonth(int month) {
    assertNotFrozen();
    super.setMonth(month);
  }

  @Deprecated
  @Override
  public void setDate(int date) {
    assertNotFrozen();
    super.setDate(date);
  }

  @Deprecated
  @Override
  public void setHours(int hours) {
    assertNotFrozen();
    super.setHours(hours);
  }

  @Deprecated
  @Override
  public void setMinutes(int minutes) {
    assertNotFrozen();
    super.setMinutes(minutes);
  }

  @Deprecated
  @Override
  public void setSeconds(int seconds) {
    assertNotFrozen();
    super.setSeconds(seconds);
  }

  private void assertNotFrozen() {
    if (frozen) {
      throw new TentackleRuntimeException("timestamp is frozen");
    }
  }


  /**
   * Saves the state of this object to a stream.
   * <p>
   * If utc, the value {@code YYYYMMDDHHMMSS} is emitted. This allows serializing
   * and deserializing in different timezones without changing the timestamp.
   *
   * @param s the stream
   * @throws IOException if writing to stream failed
   */
  @Serial
  private void writeObject(ObjectOutputStream s)
          throws IOException {
    s.writeBoolean(utc);
    if (utc) {
      Calendar calendar = DateHelper.toCalendar(this);
      s.writeShort(calendar.get(YEAR));
      s.writeByte(calendar.get(MONTH));
      s.writeByte(calendar.get(DAY_OF_MONTH));
      s.writeByte(calendar.get(HOUR_OF_DAY));
      s.writeByte(calendar.get(MINUTE));
      s.writeByte(calendar.get(SECOND));
    }
    else  {
      s.writeLong(getTime());
    }
    s.writeInt(getNanos());
    s.writeBoolean(frozen);
  }


  /**
   * Reconstitutes this object from a stream.
   *
   * @param s the stream
   * @throws IOException if reading from stream failed
   * @throws ClassNotFoundException if class contained in stream is not found
   */
  @Serial
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
      Calendar cal = DateHelper.today();
      cal.set(year, month, day, hours, minutes, seconds);
      super.setTime(cal.getTimeInMillis());
      setNanos(nanos);
    }
    else  {
      super.setTime(s.readLong());
      setNanos(s.readInt());
    }
    frozen = s.readBoolean();
  }

}

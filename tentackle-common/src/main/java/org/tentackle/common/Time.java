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

import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.SECOND;


/**
 * Time with database semantics.
 * <p>
 * Differs from {@code java.sql.Time} when
 * serialized/deserialized in different timezones. Databases don't provide a
 * date and no timezone for a time, i.e., when sent over wire to different
 * locations, the time should always remain the same for all timezones. In
 * {@code java.sql.Time}, however, the epochal time is serialized which may lead
 * to a different time when deserialized in another timezone. In {@link Time}
 * the effective time is serialized as {@code HHMMSSmmm} (milliseconds included) to provide the same semantics
 * as databases do.
 * <p>
 * Furthermore, tentackle times can be frozen, i.e., made immutable (see {@link Freezable}).
 *
 * @author harald
 */
public class Time extends java.sql.Time implements Freezable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** true if time cannot be modified anymore. */
  private boolean frozen;

  /**
   * Creates a frozen time.
   *
   * @param epoch epochal milliseconds since January 1, 1970, 00:00:00 GMT
   * @return the frozen time
   */
  public static Time createFrozen(long epoch) {
    Time time = new Time(epoch);
    time.freeze();
    return time;
  }

  /**
   * Converts a string into a time.<br>
   * Same as {@link java.sql.Time#valueOf(String)}, but returns a tentackle time (not frozen).
   *
   * @param str the string in JDBC time escape format {@code hh:mm:ss}
   * @return the time
   * @throws IllegalArgumentException if the string is not in the expected format
   */
  public static Time valueOf(String str) {
    return new Time(java.sql.Time.valueOf(str).getTime());
  }


  /**
   * Constructs a
   * <code>Time</code> object using a millisecond time value.
   *
   * @param time milliseconds since January 1, 1970, 00:00:00 GMT; a negative
   * number is milliseconds before January 1, 1970, 00:00:00 GMT
   */
  public Time(long time) {
    super(time);
  }


  /**
   * Creates the current time.
   */
  public Time() {
    this(System.currentTimeMillis());
  }


  /**
   * {@inheritDoc}
   * <p>
   * Cloned times are always not frozen.
   */
  @Override
  public Time clone() {
    Time time = (Time) super.clone();
    time.frozen = false;
    return time;
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
  public void setTime(long time) {
    assertNotFrozen();
    super.setTime(time);
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
      throw new TentackleRuntimeException("time is frozen");
    }
  }

  /**
   * Saves the state of this object to a stream.
   * <p>
   * The value {@code HHMMSSmmm} is emitted. This allows serializing
   * and deserializing in different timezones without changing the time.
   *
   * @param s the stream
   * @throws IOException if writing to stream failed
   */
  @Serial
  private void writeObject(ObjectOutputStream s)
          throws IOException {
    Calendar calendar = DateHelper.toCalendar(this);
    s.writeByte(calendar.get(HOUR_OF_DAY));
    s.writeByte(calendar.get(MINUTE));
    s.writeByte(calendar.get(SECOND));
    s.writeShort(calendar.get(MILLISECOND));
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
    int hours = s.readByte();
    int minutes = s.readByte();
    int seconds = s.readByte();
    int millis = s.readShort();
    Calendar cal = DateHelper.today();
    cal.set(1970, 0, 1, hours, minutes, seconds);
    cal.set(MILLISECOND, millis);
    super.setTime(cal.getTimeInMillis());
    frozen = s.readBoolean();
  }

}

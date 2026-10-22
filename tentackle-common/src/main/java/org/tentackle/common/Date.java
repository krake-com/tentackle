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
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;

/**
 * Date with database semantics.
 * <p>
 * Differs from {@code java.sql.Date} when
 * serialized/deserialized in different timezones. Databases don't provide a
 * time and therefore no timezone for a date, i.e., when sent over wire to different
 * locations, a date should always remain the same for all timezones. In
 * {@code java.sql.Date}, however, the epochal time is serialized which may lead
 * to a different date when deserialized in another timezone. In {@link Date}
 * the effective date is serialized as {@code YYYYMMDD} to provide the same semantics
 * as databases do.
 * <p>
 * Furthermore, tentackle dates can be frozen, i.e., made immutable (see {@link Freezable}).
 *
 * @author harald
 */
public class Date extends java.sql.Date implements Freezable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a frozen date.
   *
   * @param epoch epochal milliseconds since January 1, 1970, 00:00:00 GMT
   * @return the frozen date
   */
  public static Date createFrozen(long epoch) {
    Date date = new Date(epoch);
    date.freeze();
    return date;
  }

  /**
   * Converts a string into a date.<br>
   * Same as {@link java.sql.Date#valueOf(String)}, but returns a tentackle date (not frozen).
   *
   * @param str the string in JDBC date escape format {@code yyyy-[m]m-[d]d}
   * @return the date
   * @throws IllegalArgumentException if the string is not in the expected format
   */
  public static Date valueOf(String str) {
    return new Date(java.sql.Date.valueOf(str).getTime());
  }


  /** true if date cannot be modified anymore. */
  private boolean frozen;


  /**
   * Constructs a <code>Date</code> object using the given milliseconds time value.<br>
   * The time components are kept as is, but get lost when the date is serialized.
   *
   * @param date epochal milliseconds since January 1, 1970, 00:00:00 GMT
   */
  public Date(long date) {
    super(date);
  }


  /**
   * Creates the current date.
   */
  public Date() {
    this(System.currentTimeMillis());
  }


  /**
   * {@inheritDoc}
   * <p>
   * Cloned dates are always not frozen.
   */
  @Override
  public Date clone() {
    Date date = (Date) super.clone();
    date.frozen = false;
    return date;
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
  public void setTime(long date) {
    assertNotFrozen();
    super.setTime(date);
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
      throw new TentackleRuntimeException("date is frozen");
    }
  }


  /**
   * Saves the state of this object to a stream.
   * <p>
   * The value {@code YYYYMMDD} is emitted. This allows serializing
   * and deserializing in different timezones without changing the date.
   *
   * @param s the stream
   * @throws IOException if writing to stream failed
   */
  @Serial
  private void writeObject(ObjectOutputStream s) throws IOException {
    Calendar calendar = DateHelper.toCalendar(this);
    s.writeShort(calendar.get(YEAR));
    s.writeByte(calendar.get(MONTH));
    s.writeByte(calendar.get(DAY_OF_MONTH));
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
  private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
    int year = s.readShort();
    int month = s.readByte();
    int day = s.readByte();
    Calendar cal = DateHelper.today();
    cal.set(year, month, day, 0, 0, 0);
    cal.set(MILLISECOND, 0);
    super.setTime(cal.getTimeInMillis());
    frozen = s.readBoolean();
  }

}

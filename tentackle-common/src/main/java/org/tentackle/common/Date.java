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
 * Date with database semantics.
 * <p>
 * Differs from {@code java.sql.Date} when
 * serialized/deserialized in different timezones. Databases don't provide a
 * time and therefore no timezone for a date, i.e. when sent over wire to different
 * locations a date should always remain the same for all timezones. In
 * {@code java.sql.Data}, however, the epochal time is serialized which may lead
 * to a different date when deserialized in another timezone. In {@link Date}
 * the effective date is serialized as YYYYMMDD to provide the same semantics
 * as databases do.
 * <p>
 * Furthermore, tentackle dates can be frozen, i.e. made immutable.
 *
 * @author harald
 */
@SuppressWarnings("deprecation")
public class Date extends java.sql.Date implements Freezable {

  private static final long serialVersionUID = 1511598038487230101L;

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


  private boolean frozen;               // true if date cannot be modified anymore
  private transient Calendar cal;       // lazy calendar


  /**
   * Constructs a
   * <code>Date</code> object using the given milliseconds time value. If the
   * given milliseconds value contains time information, the driver will set the
   * time components to the time in the default time zone (the time zone of the
   * Java virtual machine running the application) that corresponds to zero GMT.
   *
   * @param date milliseconds since January 1, 1970, 00:00:00 GMT not to exceed
   * the milliseconds representation for the year 8099. A negative number
   * indicates the number of milliseconds before January 1, 1970, 00:00:00 GMT.
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


  @Override
  public void freeze() {
    frozen = true;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to clear the lazy calendar and to check for frozen.
   */
  @Override
  public void setTime(long date) {
    assertNotFrozen();
    super.setTime(date);
    cal = null;
  }

  @Override
  public void setYear(int year) {
    assertNotFrozen();
    super.setYear(year);
  }

  @Override
  public void setMonth(int month) {
    assertNotFrozen();
    super.setMonth(month);
  }

  @Override
  public void setDate(int date) {
    assertNotFrozen();
    super.setDate(date);
  }

  @Override
  public void setHours(int hours) {
    assertNotFrozen();
    super.setHours(hours);
  }

  @Override
  public void setMinutes(int minutes) {
    assertNotFrozen();
    super.setMinutes(minutes);
  }

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
   * Save the state of this object to a stream.
   *
   * @serialData the value YYYYMMDD is emitted. This allows serializing
   * and deserializing in different timezones without changing the date.
   * @param s the stream
   * @throws IOException if writing to stream failed
   */
  private void writeObject(ObjectOutputStream s) throws IOException {
    s.writeShort(getCalendar().get(Calendar.YEAR));
    s.writeByte(getCalendar().get(Calendar.MONTH));
    s.writeByte(getCalendar().get(Calendar.DAY_OF_MONTH));
    s.writeBoolean(frozen);
  }


  /**
   * Reconstitute this object from a stream.
   *
   * @param s the stream
   * @throws IOException if reading from stream failed
   * @throws ClassNotFoundException if class contained in stream is not found
   */
  private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
    int year = s.readShort();
    int month = s.readByte();
    int day = s.readByte();
    cal = Calendar.getInstance();
    cal.set(year, month, day, 0, 0, 0);
    cal.set(Calendar.MILLISECOND, 0);
    super.setTime(cal.getTimeInMillis());
    frozen = s.readBoolean();
  }

}

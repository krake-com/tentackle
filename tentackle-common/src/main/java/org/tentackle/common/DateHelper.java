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

// Created on September 19, 2002, 10:44 AM

package org.tentackle.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;


/**
 * Some common methods for date and time.
 *
 * @author harald
 */
public final class DateHelper {

  /**
   * epochal date zero: 1.1.1970 00:00:00 UTC
   */
  public static final Date MIN_DATE = new Date(0);

  /**
   * epochal date zero: 1.1.1970 00:00:00 UTC
   */
  public static final LocalDate MIN_LOCALDATE = MIN_DATE.toLocalDate();

  /**
   * epochal timestamp zero: 1.1.1970 00:00:00.000 UTC
   */
  public static final Timestamp MIN_TIMESTAMP = new Timestamp(0);

  /**
   * epochal timestamp zero: 1.1.1970 00:00:00.000 UTC
   */
  public static final LocalDateTime MIN_LOCALDATETIME = MIN_TIMESTAMP.toLocalDateTime();


  /**
   * Converts a date to the epochal time in millis with null-check.
   *
   * @param date the date, time or timestamp, may be null
   * @return the epochal time in ms, 0 if date is null
   */
  public static long toMillis(java.util.Date date) {
    return date == null ? 0 : date.getTime();
  }

  /**
   * Converts a <code>java.util.Date</code> into an <code>org.tentackle.misc.Date</code>.
   *
   * @param date the util date
   * @return the sql date, null if date was null
   */
  public static Date toDate(java.util.Date date)  {
    return date == null ? null : new Date(date.getTime());
  }

  /**
   * Converts a Calendar into a org.tentackle.misc.Date.
   *
   * @param cal the calendar
   * @return the date, null if cal was null
   */
  public static Date toDate(Calendar cal)  {
    return cal == null ? null : new Date(cal.getTime().getTime());
  }


  /**
   * Converts a java.util.Date into a org.tentackle.misc.Time.
   *
   * @param date the date
   * @return the time of the day in date, null if date was null
   */
  public static Time toTime(java.util.Date date)  {
    return date == null ? null : new Time(date.getTime());
  }


  /**
   * Converts a Calendar into a org.tentackle.misc.Time.
   *
   * @param cal the calendar
   * @return the time of day, null if cal was null
   */
  public static Time toTime(Calendar cal)  {
    return cal == null ? null : new Time(cal.getTime().getTime());
  }


  /**
   * Converts a java.util.Date into a org.tentackle.misc.Timestamp.
   *
   * @param date the date
   * @return the timestamp, null if date was null
   */
  public static Timestamp toTimestamp(java.util.Date date)  {
    return date == null ? null : new Timestamp(date.getTime());
  }


  /**
   * Converts a Calendar into a org.tentackle.misc.Timestamp.
   *
   * @param cal the calendar
   * @return the timestamp, null if cal was null
   */
  public static Timestamp toTimestamp(Calendar cal)  {
    return cal == null ? null : new Timestamp(cal.getTime().getTime());
  }


  /**
   * Gets the current date.
   *
   * @return current Date
   */
  public static Date today()  {
    return new Date(System.currentTimeMillis());
  }

  /**
   * Gets the current date with an offset.
   *
   * @param dayOffset the offset in days
   * @param monthOffset the offset in months
   * @param yearOffset the offset in years
   * @return the date
   */
  public static Date today(int dayOffset, int monthOffset, int yearOffset)  {
    GregorianCalendar cal = new GregorianCalendar();
    cal.add(GregorianCalendar.DATE, dayOffset);
    cal.add(GregorianCalendar.MONTH, monthOffset);
    cal.add(GregorianCalendar.YEAR, yearOffset);
    return new Date(cal.getTimeInMillis());
  }


  /**
   * Gets the current time.
   *
   * @return current Time
   */
  public static Time daytime()  {
    return new Time(System.currentTimeMillis());
  }


  /**
   * Gets the current time with an offset.
   *
   * @param hourOffset the offset in hours
   * @param minuteOffset the offset in minutes
   * @param secondOffset the offset in seconds
   * @return the time
   */
  public static Time daytime(int secondOffset, int minuteOffset, int hourOffset)  {
    GregorianCalendar cal = new GregorianCalendar();
    cal.add(GregorianCalendar.SECOND, secondOffset);
    cal.add(GregorianCalendar.MINUTE, minuteOffset);
    cal.add(GregorianCalendar.HOUR, hourOffset);
    return new Time(cal.getTimeInMillis());
  }


  /**
   * Gets the current system time plus an optional offset.
   *
   * @param offsetMillis the offset to the current system time in milliseconds
   * @return current Timestamp
   */
  public static Timestamp now(long offsetMillis)  {
    return new Timestamp(System.currentTimeMillis() + offsetMillis);
  }


  /**
   * Gets the current system time.
   *
   * @return current Timestamp
   */
  public static Timestamp now()  {
    return now(0);
  }


  /**
   * Sets the given calendar to midnight.
   *
   * @param greg the calendar
   */
  public static void setMidnight(GregorianCalendar greg)  {
    greg.set(HOUR_OF_DAY, 0);
    greg.set(MINUTE, 0);
    greg.set(SECOND, 0);
    greg.set(MILLISECOND, 0);
  }


  /**
   * Converts a 2-digit year to a 4-digit year.
   * <p>
   * Finds the closest distance to the current, past or next century according
   * to a given current year.
   *
   * @param year2 the 2-digit year
   * @param currentYear4 the current 4 digit year
   * @return the closest 4-digit year
   */
  public static int convert2DigitYearTo4DigitYear(int year2, int currentYear4) {
    int year = year2 % 100;
    int current2Year   = currentYear4 % 100;            // e.g. 02
    int currentCentury = currentYear4 - current2Year;   // e.g. 2000
    int pastCentury    = currentCentury - 100;          // e.g. 1900
    int nextCentury    = currentCentury + 100;          // e.g. 2100

    // find the closest century
    int currentDistance = Math.abs(currentCentury + year - currentYear4);
    int pastDistance = Math.abs(pastCentury + year - currentYear4);
    int nextDistance = Math.abs(nextCentury + year - currentYear4);

    if (currentDistance <= pastDistance) {
      if (currentDistance <= nextDistance) {
        year += currentCentury;
      }
      else  {
        year += nextCentury;
      }
    }
    else  {
      if (pastDistance <= nextDistance) {
        year += pastCentury;
      }
      else  {
        year += nextCentury;
      }
    }

    return year;
  }

  /**
   * Converts a 2-digit year to a 4-digit year.
   * <p>
   * Finds the closest distance to the current, past or next century according
   * to the current year.
   *
   * @param year2 the 2-digit year
   * @return the closest 4-digit year
   */
  public static int convert2DigitYearTo4DigitYear(int year2) {
    return convert2DigitYearTo4DigitYear(year2, new GregorianCalendar().get(YEAR));
  }


  /**
   * prevent instantiation.
   */
  private DateHelper() { }

}

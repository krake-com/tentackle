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

import java.util.Calendar;

import static java.util.Calendar.DATE;
import static java.util.Calendar.HOUR;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;


/**
 * Some common methods for date- and time-related types based on {@link java.util.Date}.
 */
public final class DateHelper {

  /**
   * Epochal date zero: 1.1.1970 00:00:00 GMT.<br>
   * The instance is frozen, i.e., it cannot be modified.
   */
  public static final Date MIN_DATE = Date.createFrozen(0);

  /**
   * Epochal timestamp zero: 1.1.1970 00:00:00.000 GMT.<br>
   * The instance is frozen, i.e., it cannot be modified.
   */
  public static final Timestamp MIN_TIMESTAMP = Timestamp.createFrozen(0, 0);


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
   * Converts a <code>java.util.Date</code> into a {@link Date}.
   *
   * @param date the util date
   * @return the date, null if date was null
   */
  public static Date toDate(java.util.Date date)  {
    return date == null ? null : new Date(date.getTime());
  }

  /**
   * Converts a Calendar into {@link Date}.
   *
   * @param cal the calendar
   * @return the date, null if cal was null
   */
  public static Date toDate(Calendar cal)  {
    return cal == null ? null : new Date(cal.getTimeInMillis());
  }


  /**
   * Converts a java.util.Date into {@link Time}.
   *
   * @param date the date
   * @return the time of the day in date, null if date was null
   */
  public static Time toTime(java.util.Date date)  {
    return date == null ? null : new Time(date.getTime());
  }


  /**
   * Converts a Calendar into {@link Time}.
   *
   * @param cal the calendar
   * @return the time of day, null if cal was null
   */
  public static Time toTime(Calendar cal)  {
    return cal == null ? null : new Time(cal.getTimeInMillis());
  }


  /**
   * Converts a java.util.Date into {@link Timestamp}.
   *
   * @param date the date
   * @return the timestamp, null if date was null
   */
  public static Timestamp toTimestamp(java.util.Date date)  {
    return date == null ? null : new Timestamp(date.getTime());
  }


  /**
   * Converts a Calendar into {@link Timestamp}.
   *
   * @param cal the calendar
   * @return the timestamp, null if cal was null
   */
  public static Timestamp toTimestamp(Calendar cal)  {
    return cal == null ? null : new Timestamp(cal.getTimeInMillis());
  }


  /**
   * Creates a calendar of the current date and time.<br>
   * The calendar is created using the thread-local locale, if set, else the default locale.
   *
   * @return the current calendar instance
   */
  public static Calendar today()  {
    return Calendar.getInstance(LocaleProvider.getInstance().getLocale());
  }


  /**
   * Creates a calendar for the given date.<br>
   * The calendar is created using the thread-local locale, if set, else the default locale.
   *
   * @param date the date
   * @return the calendar representing the date
   */
  public static Calendar toCalendar(Date date) {
    Calendar calendar = today();
    calendar.setTime(date);
    return calendar;
  }


  /**
   * Creates a calendar for the given timestamp.<br>
   * The calendar is created using the thread-local locale, if set, else the default locale.
   * <p>
   * Notice: org.tentackle.common.Timestamp does <em>not</em> extend org.tentackle.common.Date!
   *
   * @param timestamp the timestamp
   * @return the calendar representing the timestamp
   */
  public static Calendar toCalendar(Timestamp timestamp) {
    Calendar calendar = today();
    calendar.setTime(timestamp);
    return calendar;
  }


  /**
   * Creates a calendar for the given time.<br>
   * The calendar is created using the thread-local locale, if set, else the default locale.
   * <p>
   * Notice: org.tentackle.common.Time does <em>not</em> extend org.tentackle.common.Date!
   *
   * @param time the time
   * @return the calendar representing the time
   */
  public static Calendar toCalendar(Time time) {
    Calendar calendar = today();
    calendar.setTime(time);
    return calendar;
  }


  /**
   * Gets the current date with an offset.<br>
   * Same as {@link #today()} with the offsets added. Notice that the time of day is kept, i.e., not set to midnight.
   *
   * @param dayOffset the offset in days
   * @param monthOffset the offset in months
   * @param yearOffset the offset in years
   * @return the date
   */
  public static Date today(int dayOffset, int monthOffset, int yearOffset)  {
    Calendar cal = today();
    cal.add(DATE, dayOffset);
    cal.add(MONTH, monthOffset);
    cal.add(YEAR, yearOffset);
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
   * @param secondOffset the offset in seconds
   * @param minuteOffset the offset in minutes
   * @param hourOffset the offset in hours
   * @return the time
   */
  public static Time daytime(int secondOffset, int minuteOffset, int hourOffset)  {
    Calendar cal = today();
    cal.add(SECOND, secondOffset);
    cal.add(MINUTE, minuteOffset);
    cal.add(HOUR, hourOffset);
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
   * @param calendar the calendar
   */
  public static void setMidnight(Calendar calendar)  {
    calendar.set(HOUR_OF_DAY, 0);
    calendar.set(MINUTE, 0);
    calendar.set(SECOND, 0);
    calendar.set(MILLISECOND, 0);
  }

  /**
   * Gets the current year.
   *
   * @return the current 4-digit year
   */
  public static int getCurrentYear() {
    return today().get(YEAR);
  }

  /**
   * Gets the current month.
   *
   * @return the current month (0 - 11)
   */
  public static int getCurrentMonth() {
    return today().get(MONTH);
  }

  /**
   * Gets the current day.
   *
   * @return the current day (1 - 31)
   */
  public static int getCurrentDay() {
    return today().get(DATE);
  }

  /**
   * Derives a 4-digit year from a 2-digit year.
   * <p>
   * Finds the closest distance to the current, past or next century according
   * to a given current year.
   *
   * @param year the year, will be cut to 2 digits via modulo 100
   * @param currentYear the current 4-digit year
   * @param yearWindow the year window in number of years in the future (0-99, any other value always returns the year in the current century)
   * @return the closest 4-digit year
   */
  public static int derive4DigitYearFrom2DigitYear(int year, int currentYear, int yearWindow) {
    int current2Year = currentYear % 100;              // e.g. 02
    int currentCentury = currentYear - current2Year;   // e.g. 2000
    year %= 100;

    if (yearWindow >= 0 && yearWindow < 100) {
      int diff = year - current2Year;
      if (diff > 0) {
        if (diff > yearWindow) {    // current 2010, input 50, window 30 -> 1950
          return currentCentury - 100 + year;
        }
      }
      else if (diff < 0) {
        if (diff < yearWindow - 100) {    // current 1990, input 10, window 30 -> 2010
          return currentCentury + 100 + year;
        }
      }
    }

    return currentCentury + year;
  }

  /**
   * Derives a 4-digit year from a 2-digit year referring to the current year and using a year window of 50.
   * <p>
   * Finds the closest distance to the current, past or next century according
   * to the current year.
   *
   * @param year the year, will be cut to 2 digits via modulo 100
   * @return the closest 4-digit year
   */
  public static int derive4DigitYearFrom2DigitYear(int year) {
    return derive4DigitYearFrom2DigitYear(year, today().get(YEAR), 50);
  }


  /**
   * Derives the year from a month.<br>
   * Works like {@link #deriveMonthFromDay(int, int, int, int)}, but for months and years.
   *
   * @param month the month (0-11)
   * @param currentMonth the current month (0-11)
   * @param currentYear the current year (4 digits)
   * @param monthWindow the month window, (0-11, any other value returns the current year)
   * @return the closest year (4 digits)
   */
  public static int deriveYearFromMonth(int month, int currentMonth, int currentYear, int monthWindow) {
    if (monthWindow >= 0 && monthWindow < 12) {
      month %= 12;
      int diff = month - currentMonth;
      if (diff > 0) {
        if (diff > monthWindow) {
          return currentYear - 1;
        }
      }
      else if (diff < 0) {
        if (diff < monthWindow - 12) {
          return currentYear + 1;
        }
      }
    }
    return currentYear;
  }

  /**
   * Derives the year from a month referring to the current date and a monthWindow of 6.
   *
   * @param month the month (0-11)
   * @return the closest year (4 digits)
   */
  public static int deriveYearFromMonth(int month) {
    Calendar calendar = today();
    return deriveYearFromMonth(month, calendar.get(MONTH), calendar.get(YEAR), 6);
  }

  /**
   * Derives the month from a day.
   * <p>
   * The current month is kept as long as the day is at most {@code dayWindow} days ahead of
   * and at most {@code 31 - dayWindow} days behind the current day. A day further ahead
   * refers to the previous month, a day further behind to the next month.
   * If {@code day} or {@code dayWindow} is out of range, the current month is returned.
   *
   * @param day the day (1-31)
   * @param currentDay the current day of the month (1-31)
   * @param currentMonth the current month (0-11)
   * @param dayWindow the day window (0-30)
   * @return the closest month (0-11 for current year, -1 for December of last year, 12 for January of next year)
   */
  public static int deriveMonthFromDay(int day, int currentDay, int currentMonth, int dayWindow) {
    if (dayWindow >= 0 && dayWindow < 31 && day >= 1 && day <= 31) {
      int diff = day - currentDay;
      if (diff > 0) {
        if (diff > dayWindow) {
          return currentMonth - 1;
        }
      }
      else if (diff < 0) {
        if (diff < dayWindow - 31) {
          return currentMonth + 1;
        }
      }
    }
    return currentMonth;
  }

  /**
   * Derives the month from a day referring to the current date and a day window of 15.
   *
   * @param day the day (1-31)
   * @return the closest month (0-11 for current year, -1 for December of last year, 12 for January of next year)
   */
  public static int deriveMonthFromDay(int day) {
    Calendar calendar = today();
    return deriveMonthFromDay(day, calendar.get(DATE), calendar.get(MONTH), 15);
  }


  /**
   * Prevents instantiation.
   */
  private DateHelper() { }

}

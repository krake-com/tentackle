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

package org.tentackle.misc.time;

import org.tentackle.common.DateHelper;
import org.tentackle.common.TentackleRuntimeException;

import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shortcut to derive a {@link Temporal} from a numeric input.<br>
 * The corresponding part is updated and the remaining parts (to the right) are set to their minimum value.
 * <p>
 * Examples:<br>
 * {@code 5h} will set the time to 05:00:00.000.<br>
 * {@code 7M} will set the month to July, day of month to the 1st and the time to midnight (if the type contains a time component).
 * <p>
 * If the unit is missing (e.g. {@code "10"}), it defaults to {@code d} (days) for types containing a date
 * or to {@code h} (hours) otherwise. The units are the same as for the {@link AddShortcut}.
 * <p>
 * Numbers with 4 or 6 digits are interpreted according to the order of the fields in the pattern,
 * for example as {@code ddMM}, {@code MMdd}, {@code ddMMyy} or {@code HHmm}, {@code HHmmss}.
 * <p>
 * For the year, month and day of month a sliding window can be provided (see {@link WindowProvider}) to
 * automatically adjust the values. For example, entering 010120 will be expanded to 2020-01-01, whereas
 * 010199 is expanded to 1999-01-01 (and not 2099-01-01!).
 */
public class NumberShortcut implements SmartDateTimeParser.Shortcut {

  /**
   * Provides the input windows for year, month and day.
   */
  public interface WindowProvider {

    /**
     * Gets the year input window.
     *
     * @return the year window, -1 to disable windowing
     * @see NumberShortcut#getYearWindow()
     */
    int getYearWindow();

    /**
     * Gets the month input window.
     *
     * @return the month window, -1 to disable windowing
     * @see NumberShortcut#getMonthWindow()
     */
    int getMonthWindow();

    /**
     * Gets the day input window.
     *
     * @return the day window, -1 to disable windowing
     * @see NumberShortcut#getDayWindow()
     */
    int getDayWindow();

  }


  /**
   * The pattern for a simple number, optionally followed by the type letter.
   */
  private static final Pattern pattern = Pattern.compile("^[0-9]+[ymMdhs]?$");

  /**
   * The optional input window provider.
   */
  private final WindowProvider windowProvider;


  /**
   * Creates a shortcut.
   *
   * @param windowProvider the window provider, null if none
   */
  public NumberShortcut(WindowProvider windowProvider) {
    this.windowProvider = windowProvider;
  }

  /**
   * Creates a shortcut without a window provider.
   */
  public NumberShortcut() {
    this(null);
  }

  /**
   * Gets the window provider.
   *
   * @return the provider, null if none
   */
  public WindowProvider getWindowProvider() {
    return windowProvider;
  }

  /**
   * Gets the number of years after the current year to automatically determine the century.<br>
   * If the {@link WindowProvider} is missing, the value 50 is used. Supposed we're in 2020 and the user enters 99,
   * then the year would be expanded to 1999 and 30 would be expanded to 2030, accordingly.<br>
   * For special date fields, for example dates of birth, the value should be 0,
   * since it's rather unlikely to know the date in advance.<br>
   * A negative value disables this feature.
   *
   * @return the year window
   */
  public int getYearWindow() {
    return windowProvider == null ? 50 : windowProvider.getYearWindow();
  }

  /**
   * Same as {@link #getYearWindow()}, but for months.<br>
   * Valid values are 0 - 11.<br>
   * Example: If we're in April and enter 1225, it will be expanded to 12/25/&lt;previous year&gt;<br>
   * Again, for special date fields, for example the booking part of a financial accounting system, values of 1 or 2 may be
   * appropriate.<br>
   * A negative value disables this feature, which is the default if there is no {@link WindowProvider}.
   *
   * @return the month window
   */
  public int getMonthWindow() {
    return windowProvider == null ? -1 : windowProvider.getMonthWindow();
  }

  /**
   * Same as {@link #getYearWindow()}, but for days.<br>
   * Valid values are 0 - 30.<br>
   * A negative value disables this feature, which is the default if there is no {@link WindowProvider}.
   *
   * @return the day window
   */
  public int getDayWindow() {
    return windowProvider == null ? -1 : windowProvider.getDayWindow();
  }

  /**
   * Converts a short 2-digit year to a 4-digit year.
   *
   * @param year2 the 2-digit year
   * @return the 4-digit year
   * @see #getYearWindow()
   */
  public int derive4DigitYearFrom2DigitYear(int year2) {
    return DateHelper.derive4DigitYearFrom2DigitYear(year2, DateHelper.getCurrentYear(), getYearWindow());
  }

  /**
   * Derives the year, if only month and day are given.
   *
   * @param month the month (1-12)
   * @return the default year according to the month window
   * @see #getMonthWindow()
   */
  public int deriveYearFromMonth(int month) {
    return DateHelper.deriveYearFromMonth((month - 1) % 12, DateHelper.getCurrentMonth(), DateHelper.getCurrentYear(), getMonthWindow());
  }

  /**
   * Derives the month from a day input.
   *
   * @param day the day (1-31)
   * @return the closest month (0-11 for current year, -1 for December of last year, 12 for January of next year)
   * @see #getDayWindow()
   */
  public int deriveMonthFromDay(int day) {
    return DateHelper.deriveMonthFromDay(day, DateHelper.getCurrentDay(), DateHelper.getCurrentMonth(), getDayWindow());
  }

  @Override
  public SmartDateTimeParser.TextRange test(SmartDateTimeParser<? extends Temporal> parser, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? new SmartDateTimeParser.TextRange(matcher.start(), matcher.end()) : null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V extends Temporal> V parse(SmartDateTimeParser<V> parser, V currentValue, String part) {
    char last = part.charAt(part.length() - 1);
    TemporalField field;
    String numberString;
    if (Character.isDigit(last)) {
      numberString = part;
      field = parser.isWithDate() ? ChronoField.DAY_OF_MONTH : ChronoField.HOUR_OF_DAY;
    }
    else {
      field = switch (last) {
        case 'y' -> ChronoField.YEAR;
        case 'M' -> parser.isWithDate() ? ChronoField.MONTH_OF_YEAR : ChronoField.MINUTE_OF_HOUR;
        case 'm' -> parser.isWithTime() ? ChronoField.MINUTE_OF_HOUR : ChronoField.MONTH_OF_YEAR;
        case 'd' -> ChronoField.DAY_OF_MONTH;
        case 'h' -> ChronoField.HOUR_OF_DAY;
        case 's' -> ChronoField.SECOND_OF_MINUTE;
        default -> throw new TentackleRuntimeException("unexpected unit '" + last + "'");   // regex problem?
      };
      numberString = part.substring(0, part.length() - 1);
    }

    long value = Long.parseLong(numberString);
    boolean truncate = true;

    if (field.isDateBased() && value == 0) {
      field = ChronoField.HOUR_OF_DAY;    // user forgot 'h' (ex. +10d0)
    }
    else if (numberString.length() == 2 && field == ChronoField.YEAR) {
      value = derive4DigitYearFrom2DigitYear((int) value);
    }
    else if (numberString.length() == 4) {
      if (field.isDateBased()) {    // ddMM or MMdd
        int dayIndex = parser.getPattern().indexOf('d');
        int monthIndex = parser.getPattern().indexOf('M');
        if (dayIndex >= 0 && monthIndex >= 0) {
          if (dayIndex < monthIndex) {
            long day = value / 100L;
            currentValue = (V) currentValue.with(ChronoField.DAY_OF_MONTH, day);
            field = ChronoField.MONTH_OF_YEAR;
            currentValue = (V) currentValue.with(ChronoField.YEAR, deriveYearFromMonth((int) value % 100));
          }
          else {
            long month = value / 100L;
            currentValue = (V) currentValue.with(ChronoField.MONTH_OF_YEAR, month);
            currentValue = (V) currentValue.with(ChronoField.YEAR, deriveYearFromMonth((int) month));
            field = ChronoField.DAY_OF_MONTH;
          }
          value %= 100L;
        }
      }
      else if (parser.isWithTime()) {   // HHmm
        long hour = value / 100L;
        value %= 100L;
        currentValue = (V) currentValue.with(ChronoField.HOUR_OF_DAY, hour);
        field = ChronoField.MINUTE_OF_HOUR;
      }
    }
    else if (numberString.length() == 6) {
      if (field.isDateBased()) {    // ddMMyy or yyMMdd
        truncate = false;
        int dayIndex = parser.getPattern().indexOf('d');
        int monthIndex = parser.getPattern().indexOf('M');
        int yearIndex = parser.getPattern().indexOf('y');
        if (dayIndex >= 0 && monthIndex >= 0 && yearIndex >= 0) {
          long subVal = value / 10000L;
          value %= 10000L;
          if (dayIndex < monthIndex && dayIndex < yearIndex) {    // day comes first
            currentValue = (V) currentValue.with(ChronoField.DAY_OF_MONTH, subVal);
            subVal = value / 100L;
            if (monthIndex < yearIndex) {
              currentValue = (V) currentValue.with(ChronoField.MONTH_OF_YEAR, subVal);
              field = ChronoField.YEAR;
            }
            else {
              currentValue = (V) currentValue.with(ChronoField.YEAR, derive4DigitYearFrom2DigitYear((int) subVal));
              field = ChronoField.MONTH_OF_YEAR;
            }
          }
          else if (monthIndex < dayIndex && monthIndex < yearIndex) {   // month comes first
            currentValue = (V) currentValue.with(ChronoField.MONTH_OF_YEAR, subVal);
            subVal = value / 100L;
            if (dayIndex < yearIndex) {
              currentValue = (V) currentValue.with(ChronoField.DAY_OF_MONTH, subVal);
              field = ChronoField.YEAR;
            }
            else {
              currentValue = (V) currentValue.with(ChronoField.YEAR, derive4DigitYearFrom2DigitYear((int) subVal));
              field = ChronoField.DAY_OF_MONTH;
            }
          }
          else {    // year comes first
            currentValue = (V) currentValue.with(ChronoField.YEAR, derive4DigitYearFrom2DigitYear((int) subVal));
            subVal = value / 100L;
            if (dayIndex < monthIndex) {
              currentValue = (V) currentValue.with(ChronoField.DAY_OF_MONTH, subVal);
              field = ChronoField.MONTH_OF_YEAR;
            }
            else {
              currentValue = (V) currentValue.with(ChronoField.MONTH_OF_YEAR, subVal);
              field = ChronoField.DAY_OF_MONTH;
            }
          }
          value %= 100L;
          if (field == ChronoField.YEAR) {
            value = derive4DigitYearFrom2DigitYear((int) value);
          }
        }
      }
      else if (field.isTimeBased()) {   // HHmmss
        long hour = value / 10000L;
        currentValue = (V) currentValue.with(ChronoField.HOUR_OF_DAY, hour);
        long minute = value / 100L % 100L;
        currentValue = (V) currentValue.with(ChronoField.MINUTE_OF_HOUR, minute);
        field = ChronoField.SECOND_OF_MINUTE;
        value %= 100L;
      }
    }
    else if (field == ChronoField.DAY_OF_MONTH) {
      long month = deriveMonthFromDay((int) value);
      if (month == -1) {
        month = 11;
        currentValue = (V) currentValue.with(ChronoField.YEAR, DateHelper.getCurrentYear() - 1);
      }
      else if (month == 12) {
        month = 0;
        currentValue = (V) currentValue.with(ChronoField.YEAR, DateHelper.getCurrentYear() + 1);
      }
      currentValue = (V) currentValue.with(ChronoField.MONTH_OF_YEAR, month + 1);
    }

    currentValue = (V) currentValue.with(field, value);

    if (truncate) {
      if (field.isDateBased()) {
        // there is no truncatedTo for date fields
        if (field == ChronoField.YEAR) {
          currentValue = (V) currentValue.with(ChronoField.MONTH_OF_YEAR, 1)
                                         .with(ChronoField.DAY_OF_MONTH, 1);
        }
        else if (field == ChronoField.MONTH_OF_YEAR) {
          currentValue = (V) currentValue.with(ChronoField.DAY_OF_MONTH, 1);
        }
        if (parser.isWithTime()) {
          // truncate to midnight
          field = ChronoField.HOUR_OF_DAY;
          currentValue = (V) currentValue.with(field, 0);
        }
      }

      if (field.isTimeBased()) {
        currentValue = parser.getTruncate().apply(currentValue, field.getBaseUnit());
      }
    }
    else if (field.isDateBased() && parser.isWithTime()) {
      currentValue = parser.getTruncate().apply(currentValue, ChronoUnit.DAYS);
    }

    return currentValue;
  }

}

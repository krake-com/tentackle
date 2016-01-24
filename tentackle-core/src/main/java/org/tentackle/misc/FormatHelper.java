/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.misc;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.tentackle.common.BMoney;
import org.tentackle.common.LocaleProvider;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.HOUR;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.WEEK_OF_YEAR;
import static java.util.Calendar.YEAR;



/**
 * Formatting helper.
 *
 * @author harald
 */
public class FormatHelper {


  /**
   * Locale-specific formatting.
   */
  private static class Formatting {

    /** localized integer format string. */
    private final String integerPattern;

    /** localized float/double format string. */
    private final String floatingNumberPattern;

    /** localized money format string. */
    private final String moneyPattern;

    /** localized format string for a timestamp. */
    private final String timestampPattern;

    /** localized format string for a date. */
    private final String datePattern;

    /** localized format string for a time. */
    private final String timePattern;

    /** localized format string for a short timestamp. */
    private final String shortTimestampPattern;

    /** localized format string for a short date. */
    private final String shortDatePattern;

    /** localized format string for a short time. */
    private final String shortTimePattern;

    /** localized {@link DecimalFormat} for an integer. */
    private final DecimalFormat integerFormat;

    /** localized {@link DecimalFormat} for a float or double. */
    private final DecimalFormat floatingNumberFormat;

    /** localized {@link DecimalFormat} for a money value. */
    private final DecimalFormat moneyFormat;

    /** localized {@link DateFormat} for a timestamp. */
    private final SimpleDateFormat timestampFormat;

    /** localized {@link DateFormat} for a date. */
    private final SimpleDateFormat dateFormat;

    /** localized {@link DateFormat} for a time. */
    private final SimpleDateFormat timeFormat;

    /** localized {@link DateFormat} for a short timestamp. */
    private final SimpleDateFormat shortTimestampFormat;

    /** localized {@link DateFormat} for a short date. */
    private final SimpleDateFormat shortDateFormat;

    /** localized {@link DateFormat} for a short time. */
    private final SimpleDateFormat shortTimeFormat;

    /** short letter for "debit" (soll). */
    private final char debitLetter;
    private final String debitString;    // used in betragSH

    /** short letter for "credit" (haben). */
    private final char creditLetter;
    private final String creditString;

    private Formatting(Locale locale) {
      // load format strings according to locale
      integerPattern = MiscCoreBundle.getString("integerPattern");
      floatingNumberPattern = MiscCoreBundle.getString("floatingNumberPattern");
      moneyPattern = MiscCoreBundle.getString("moneyPattern");
      timestampPattern = MiscCoreBundle.getString("timestampPattern");
      datePattern = MiscCoreBundle.getString("datePattern");
      timePattern = MiscCoreBundle.getString("timePattern");
      shortTimestampPattern = MiscCoreBundle.getString("shortTimestampPattern");
      shortDatePattern = MiscCoreBundle.getString("shortDatePattern");
      shortTimePattern = MiscCoreBundle.getString("shortTimePattern");
      debitLetter = MiscCoreBundle.getString("debitLetter").charAt(0);
      creditLetter = MiscCoreBundle.getString("creditLetter").charAt(0);
      debitString = " " + debitLetter + "    ";
      creditString = " " + creditLetter;
      // create formats
      integerFormat = new DecimalFormat(integerPattern);
      floatingNumberFormat = new DecimalFormat(floatingNumberPattern);
      moneyFormat = new DecimalFormat(moneyPattern);
      timestampFormat = new SimpleDateFormat(timestampPattern, locale);
      dateFormat = new SimpleDateFormat(datePattern, locale);
      timeFormat = new SimpleDateFormat(timePattern, locale);
      shortTimestampFormat = new SimpleDateFormat(shortTimestampPattern, locale);
      shortDateFormat = new SimpleDateFormat(shortDatePattern, locale);
      shortTimeFormat = new SimpleDateFormat(shortTimePattern, locale);
    }

    private String debitCreditToString(BMoney money, boolean debit) {
      if (money == null) {
        return "";
      }
      setScale(moneyFormat, money.scale());
      return debit ? (moneyFormat.format(money) + debitString) :
              ("    " + moneyFormat.format(money) + creditString);
    }
  }


  /** formattings by locale. */
  private static final Map<Locale,Formatting> formattingMap = new HashMap<>();


  /**
   * Gets the formatting for given locale.
   *
   * @param locale the locale
   * @return the formatting
   */
  private static Formatting getFormatting(Locale locale) {
    Formatting formatting = formattingMap.get(locale);
    if (formatting == null) {
      formatting = new Formatting(locale);
      formattingMap.put(locale, formatting);
    }
    return formatting;
  }


  /**
   * Gets the integer pattern.
   *
   * @return the pattern
   */
  public static synchronized String getIntegerPattern() {
    return getFormatting(LocaleProvider.getInstance().getLocale()).integerPattern;
  }

  /**
   * Gets the floating number pattern.
   *
   * @return the pattern
   */
  public static synchronized String getFloatingNumberPattern() {
    return getFormatting(LocaleProvider.getInstance().getLocale()).floatingNumberPattern;
  }

  /**
   * Gets the money pattern.
   *
   * @return the pattern
   */
  public static synchronized String getMoneyPattern() {
    return getFormatting(LocaleProvider.getInstance().getLocale()).moneyPattern;
  }

  /**
   * Gets the timestamp pattern.
   *
   * @return the pattern
   */
  public static synchronized String getTimestampPattern() {
    return getFormatting(LocaleProvider.getInstance().getLocale()).timestampPattern;
  }

  /**
   * Gets the short timestamp pattern.
   *
   * @return the pattern
   */
  public static synchronized String getShortTimestampPattern() {
    return getFormatting(LocaleProvider.getInstance().getLocale()).shortTimestampPattern;
  }

  /**
   * Gets the date pattern.
   *
   * @return the pattern
   */
  public static synchronized String getDatePattern() {
    return getFormatting(LocaleProvider.getInstance().getLocale()).datePattern;
  }

  /**
   * Gets the short date pattern.
   *
   * @return the pattern
   */
  public static synchronized String getShortDatePattern() {
    return getFormatting(LocaleProvider.getInstance().getLocale()).shortDatePattern;
  }

  /**
   * Gets the time pattern.
   *
   * @return the pattern
   */
  public static synchronized String getTimePattern() {
    return getFormatting(LocaleProvider.getInstance().getLocale()).timePattern;
  }

  /**
   * Gets the short time pattern.
   *
   * @return the pattern
   */
  public static synchronized String getShortTimePattern() {
    return getFormatting(LocaleProvider.getInstance().getLocale()).shortTimePattern;
  }


  /**
   * Parses an integer.
   *
   * @param str the string to parse
   * @return the number
   * @throws ParseException if parsing failed
   */
  public static synchronized Number parseInteger(String str) throws ParseException {
    return getFormatting(LocaleProvider.getInstance().getLocale()).integerFormat.parse(str);
  }

  /**
   * Parses a floating number.
   *
   * @param str the string to parse
   * @return the number
   * @throws ParseException if parsing failed
   */
  public static synchronized Number parseFloatingNumber(String str) throws ParseException {
    return getFormatting(LocaleProvider.getInstance().getLocale()).floatingNumberFormat.parse(str);
  }

  /**
   * Parses a money value.
   *
   * @param str the string to parse
   * @return the number
   * @throws ParseException if parsing failed
   */
  public static synchronized Number parseMoney(String str) throws ParseException {
    return getFormatting(LocaleProvider.getInstance().getLocale()).moneyFormat.parse(str);
  }

  /**
   * Parses a timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws ParseException if parsing failed
   */
  public static synchronized Date parseTimestamp(String str) throws ParseException {
    return getFormatting(LocaleProvider.getInstance().getLocale()).timestampFormat.parse(str);
  }

  /**
   * Parses a short timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws ParseException if parsing failed
   */
  public static synchronized Date parseShortTimestamp(String str) throws ParseException {
    return getFormatting(LocaleProvider.getInstance().getLocale()).shortTimestampFormat.parse(str);
  }

  /**
   * Parses a date.
   *
   * @param str the string to parse
   * @return the date
   * @throws ParseException if parsing failed
   */
  public static synchronized Date parseDate(String str) throws ParseException {
    return getFormatting(LocaleProvider.getInstance().getLocale()).dateFormat.parse(str);
  }

  /**
   * Parses a short date.
   *
   * @param str the string to parse
   * @return the date
   * @throws ParseException if parsing failed
   */
  public static synchronized Date parseShortDate(String str) throws ParseException {
    return getFormatting(LocaleProvider.getInstance().getLocale()).shortDateFormat.parse(str);
  }

  /**
   * Parses a time.
   *
   * @param str the string to parse
   * @return the time
   * @throws ParseException if parsing failed
   */
  public static synchronized Date parseTime(String str) throws ParseException {
    return getFormatting(LocaleProvider.getInstance().getLocale()).timeFormat.parse(str);
  }

  /**
   * Parses a short time.
   *
   * @param str the string to parse
   * @return the time
   * @throws ParseException if parsing failed
   */
  public static synchronized Date parseShortTime(String str) throws ParseException {
    return getFormatting(LocaleProvider.getInstance().getLocale()).shortTimeFormat.parse(str);
  }



  /**
   * Formats an integer.
   *
   * @param number the number to format
   * @return the formatted string
   */
  public static synchronized String formatInteger(Number number) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).integerFormat.format(number);
  }

  /**
   * Formats a floating number.
   *
   * @param number the number to format
   * @return the formatted string
   */
  public static synchronized String formatFloatingNumber(Number number) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).floatingNumberFormat.format(number);
  }

  /**
   * Formats a money value.
   *
   * @param number the number to format
   * @return the formatted string
   */
  public static synchronized String formatMoney(Number number) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).moneyFormat.format(number);
  }

  /**
   * Formats a timestamp.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static synchronized String formatTimestamp(Date timestamp) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).timestampFormat.format(timestamp);
  }

  /**
   * Formats a short timestamp.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static synchronized String formatShortTimestamp(Date timestamp) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).shortTimestampFormat.format(timestamp);
  }

  /**
   * Formats a date.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static synchronized String formatDate(Date date) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).dateFormat.format(date);
  }

  /**
   * Formats a short date.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static synchronized String formatShortDate(Date date) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).shortDateFormat.format(date);
  }

  /**
   * Formats a time.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static synchronized String formatTime(Date time) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).timeFormat.format(time);
  }

  /**
   * Formats a short time.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static synchronized String formatShortTime(Date time) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).shortTimeFormat.format(time);
  }


  /**
   * Determines the scale from a given numeric string.
   * <p>
   * Does not work with scientific number strings.
   *
   * @param value the numeric string
   * @return the scale
   */
  public static synchronized int determineScale(String value) {
    int scale = 0;
    if (value != null) {
      int len = value.length();
      char sep = getFormatting(LocaleProvider.getInstance().getLocale()).floatingNumberFormat.getDecimalFormatSymbols().getDecimalSeparator();
      int ndx = value.lastIndexOf(sep);
      if (ndx >= 0) {
        scale = len - ndx - 1;
      }
    }
    return scale;
  }


  /**
   * Changes the decimal format according to the given scale.<br>
   * Leaves the part before the comma unchanged!
   *
   * @param format the decimal format
   * @param scale the scale
   */
  static public void setScale(DecimalFormat format, int scale) {
    String fmt = format.toPattern();
    boolean groupingUsed = format.isGroupingUsed();
    int groupingSize = format.getGroupingSize();

    // cut after last dot, if any
    int dotNdx = fmt.lastIndexOf('.');
    if (dotNdx < 0) {
      dotNdx = fmt.length();
    }

    if (dotNdx > 0) {
      fmt = fmt.substring(0, dotNdx);
    }
    else  {
      // set default
      fmt = "#0";
    }

    if (scale > 0)  {
      fmt += '.';
      for (int i=0; i < scale; i++) {
        fmt += "0";
      }
    }

    // apply new format
    format.applyPattern(fmt);

    if (dotNdx <= 0 && groupingUsed)  {
      // default has been set: restore grouping pars
      format.setGroupingSize(groupingSize);
      format.setGroupingUsed(groupingUsed);
    }
  }


  /**
   * Translates a money value to a string with a suffix
   * indicating whether debit or credit. Commonly used in financial
   * accounting applications.
   *
   * @param money the amount of money
   * @param debit true if the amount is debit
   * @return the formatted value
   */
  public static synchronized String debitCreditToString(BMoney money, boolean debit) {
    return getFormatting(LocaleProvider.getInstance().getLocale()).debitCreditToString(money, debit);
  }


  /**
   * Checks whether dateformat contains formatting codes for time.
   *
   * @param format the format
   * @return true if contains time-formatting
   */
  public static boolean isFormattingTime(SimpleDateFormat format) {
    String fmt = format.toPattern();
    return fmt.indexOf('H') >= 0 ||
           fmt.indexOf('k') >= 0 ||
           fmt.indexOf('K') >= 0 ||
           fmt.indexOf('h') >= 0 ||
           fmt.indexOf('m') >= 0 ||
           fmt.indexOf('s') >= 0 ||
           fmt.indexOf('S') >= 0;
  }

  /**
   * Checks whether dateformat contains formatting codes for date.
   *
   * @param format the format
   * @return true if contains date-formatting
   */
  public static boolean isFormattingDate(SimpleDateFormat format) {
    String fmt = format.toPattern();
    return fmt.indexOf('y') >= 0 ||
           fmt.indexOf('M') >= 0 ||
           fmt.indexOf('w') >= 0 ||
           fmt.indexOf('W') >= 0 ||
           fmt.indexOf('D') >= 0 ||
           fmt.indexOf('d') >= 0 ||
           fmt.indexOf('F') >= 0 ||
           fmt.indexOf('E') >= 0;
  }


  /**
   * Translates a field of {@link java.util.GregorianCalendar} to a localized string.
   * <p>
   * Example: {@link java.util.GregorianCalendar#DAY_OF_MONTH} will be translated to "month".
   * <p>
   * Notice that only the following field types are supported:
   * <ul>
   * <li>{@link java.util.GregorianCalendar#YEAR}</li>
   * <li>{@link java.util.GregorianCalendar#MONTH}</li>
   * <li>{@link java.util.GregorianCalendar#WEEK_OF_YEAR}</li>
   * <li>{@link java.util.GregorianCalendar#DAY_OF_MONTH} or {@link java.util.GregorianCalendar#DATE}</li>
   * <li>{@link java.util.GregorianCalendar#HOUR} or {@link java.util.GregorianCalendar#HOUR_OF_DAY}</li>
   * <li>{@link java.util.GregorianCalendar#MINUTE}</li>
   * <li>{@link java.util.GregorianCalendar#SECOND}</li>
   * <li>{@link java.util.GregorianCalendar#MILLISECOND}</li>
   * </ul>
   *
   * @param field the field type
   * @param plural true if plural, else singular
   * @return the name, the empty string if unsupported field type
   */
  public static String calendarFieldToString(int field, boolean plural) {
    switch (field) {
      case YEAR:
        return plural ? MiscCoreBundle.getString("years") : MiscCoreBundle.getString("year");
      case MONTH:
        return plural ? MiscCoreBundle.getString("months") : MiscCoreBundle.getString("month");
      case WEEK_OF_YEAR:
        return plural ? MiscCoreBundle.getString("weeks") : MiscCoreBundle.getString("week");
      case DAY_OF_MONTH:
        return plural ? MiscCoreBundle.getString("days") : MiscCoreBundle.getString("day");
      case HOUR:
      case HOUR_OF_DAY:
        return plural ? MiscCoreBundle.getString("hours") : MiscCoreBundle.getString("hour");
      case MINUTE:
        return plural ? MiscCoreBundle.getString("minutes") : MiscCoreBundle.getString("minute");
      case SECOND:
        return plural ? MiscCoreBundle.getString("seconds") : MiscCoreBundle.getString("second");
      case MILLISECOND:
        return plural ? MiscCoreBundle.getString("milliseconds") : MiscCoreBundle.getString("millisecond");
    }
    return "";
  }


  private FormatHelper() {
  }

}

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.tentackle.common.BMoney;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.Timestamp;

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
   * Patterns to initialize formatting.
   */
  public static class Patterns {

    /** localized integer format string. */
    public String integerPattern;

    /** localized float/double format string. */
    public String floatingNumberPattern;

    /** localized money format string. */
    public String moneyPattern;

    /** localized format string for a timestamp. */
    public String timestampPattern;

    /** localized format string for a date. */
    public String datePattern;

    /** localized format string for a time. */
    public String timePattern;

    /** localized format string for a short timestamp. */
    public String shortTimestampPattern;

    /** localized format string for a short date. */
    public String shortDatePattern;

    /** localized format string for a short time. */
    public String shortTimePattern;

    /** short letter for "debit" (soll). */
    public char debitLetter;

    /** short letter for "credit" (haben). */
    public char creditLetter;

    /**
     * Creates empty patterns.
     */
    public Patterns() {
    }

    /**
     * Creates patterns for the given locale.
     *
     * @param locale the locale
     */
    public Patterns(Locale locale) {
      integerPattern = MiscCoreBundle.getString(locale, "integerPattern");
      floatingNumberPattern = MiscCoreBundle.getString(locale, "floatingNumberPattern");
      moneyPattern = MiscCoreBundle.getString(locale, "moneyPattern");
      integerPattern = MiscCoreBundle.getString(locale, "integerPattern");
      timestampPattern = MiscCoreBundle.getString(locale, "timestampPattern");
      datePattern = MiscCoreBundle.getString(locale, "datePattern");
      timePattern = MiscCoreBundle.getString(locale, "timePattern");
      shortTimestampPattern = MiscCoreBundle.getString(locale, "shortTimestampPattern");
      shortDatePattern = MiscCoreBundle.getString(locale, "shortDatePattern");
      shortTimePattern = MiscCoreBundle.getString(locale, "shortTimePattern");
      debitLetter = MiscCoreBundle.getString(locale, "debitLetter").charAt(0);
      creditLetter = MiscCoreBundle.getString(locale, "creditLetter").charAt(0);
    }

  }


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

    /** localized {@link java.text.DateFormat} for a timestamp. */
    private final SimpleDateFormat timestampFormat;

    /** localized {@link java.text.DateFormat} for a date. */
    private final SimpleDateFormat dateFormat;

    /** localized {@link java.text.DateFormat} for a time. */
    private final SimpleDateFormat timeFormat;

    /** localized {@link java.text.DateFormat} for a short timestamp. */
    private final SimpleDateFormat shortTimestampFormat;

    /** localized {@link java.text.DateFormat} for a short date. */
    private final SimpleDateFormat shortDateFormat;

    /** localized {@link java.text.DateFormat} for a short time. */
    private final SimpleDateFormat shortTimeFormat;


    /** localized {@link java.time.format.DateTimeFormatter} for a timestamp. */
    private final DateTimeFormatter localDateTimeFormat;

    /** localized {@link java.time.format.DateTimeFormatter} for a date. */
    private final DateTimeFormatter localDateFormat;

    /** localized {@link java.time.format.DateTimeFormatter} for a time. */
    private final DateTimeFormatter localTimeFormat;

    /** localized {@link java.time.format.DateTimeFormatter} for a short timestamp. */
    private final DateTimeFormatter shortLocalDateTimeFormat;

    /** localized {@link java.time.format.DateTimeFormatter} for a short date. */
    private final DateTimeFormatter shortLocalDateFormat;

    /** localized {@link java.time.format.DateTimeFormatter} for a short time. */
    private final DateTimeFormatter shortLocalTimeFormat;



    /** short letter for "debit" (soll). */
    private final char debitLetter;
    private final String debitString;    // used in betragSH

    /** short letter for "credit" (haben). */
    private final char creditLetter;
    private final String creditString;

    private Formatting(Locale locale, Patterns patterns) {
      // copy format strings from patterns
      integerPattern = patterns.integerPattern;
      floatingNumberPattern = patterns.floatingNumberPattern;
      moneyPattern = patterns.moneyPattern;
      timestampPattern = patterns.timestampPattern;
      datePattern = patterns.datePattern;
      timePattern = patterns.timePattern;
      shortTimestampPattern = patterns.shortTimestampPattern;
      shortDatePattern = patterns.shortDatePattern;
      shortTimePattern = patterns.shortTimePattern;
      debitLetter = patterns.debitLetter;
      creditLetter = patterns.creditLetter;
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
      // new java.time package
      localDateTimeFormat = DateTimeFormatter.ofPattern(timestampPattern, locale);
      localDateFormat = DateTimeFormatter.ofPattern(datePattern, locale);
      localTimeFormat = DateTimeFormatter.ofPattern(timePattern, locale);
      shortLocalDateTimeFormat = DateTimeFormatter.ofPattern(shortTimestampPattern, locale);
      shortLocalDateFormat = DateTimeFormatter.ofPattern(shortDatePattern, locale);
      shortLocalTimeFormat = DateTimeFormatter.ofPattern(shortTimePattern, locale);
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
  private static final Map<Locale,Formatting> FORMATTING_MAP = new HashMap<>();


  /**
   * Gets the formatting for the current locale.
   *
   * @return the formatting
   */
  private static Formatting getFormatting() {
    Locale locale = LocaleProvider.getInstance().getLocale();
    Formatting formatting = FORMATTING_MAP.get(locale);
    if (formatting == null) {
      Patterns patterns = new Patterns(locale);   // load from bundle
      formatting = new Formatting(locale, patterns);
      FORMATTING_MAP.put(locale, formatting);
    }
    return formatting;
  }


  /**
   * Applies a set of patterns to a locale.
   *
   * @param locale the locale
   * @param patterns the patterns
   */
  public static synchronized void applyPatterns(Locale locale, Patterns patterns) {
    FORMATTING_MAP.put(locale, new Formatting(locale, patterns));
  }

  /**
   * Applies a set of patterns to the current locale.
   *
   * @param patterns the patterns
   */
  public static void applyPatterns(Patterns patterns) {
    Locale locale = LocaleProvider.getInstance().getLocale();
    applyPatterns(locale, patterns);
  }


  /**
   * Gets the integer pattern.
   *
   * @return the pattern
   */
  public static synchronized String getIntegerPattern() {
    return getFormatting().integerPattern;
  }

  /**
   * Gets the floating number pattern.
   *
   * @return the pattern
   */
  public static synchronized String getFloatingNumberPattern() {
    return getFormatting().floatingNumberPattern;
  }

  /**
   * Gets the money pattern.
   *
   * @return the pattern
   */
  public static synchronized String getMoneyPattern() {
    return getFormatting().moneyPattern;
  }

  /**
   * Gets the timestamp pattern.
   *
   * @return the pattern
   */
  public static synchronized String getTimestampPattern() {
    return getFormatting().timestampPattern;
  }

  /**
   * Gets the short timestamp pattern.
   *
   * @return the pattern
   */
  public static synchronized String getShortTimestampPattern() {
    return getFormatting().shortTimestampPattern;
  }

  /**
   * Gets the date pattern.
   *
   * @return the pattern
   */
  public static synchronized String getDatePattern() {
    return getFormatting().datePattern;
  }

  /**
   * Gets the short date pattern.
   *
   * @return the pattern
   */
  public static synchronized String getShortDatePattern() {
    return getFormatting().shortDatePattern;
  }

  /**
   * Gets the time pattern.
   *
   * @return the pattern
   */
  public static synchronized String getTimePattern() {
    return getFormatting().timePattern;
  }

  /**
   * Gets the short time pattern.
   *
   * @return the pattern
   */
  public static synchronized String getShortTimePattern() {
    return getFormatting().shortTimePattern;
  }


  /**
   * Parses an integer.
   *
   * @param str the string to parse
   * @return the number
   * @throws ParseException if parsing failed
   */
  public static synchronized Number parseInteger(String str) throws ParseException {
    return getFormatting().integerFormat.parse(str);
  }

  /**
   * Parses a floating number.
   *
   * @param str the string to parse
   * @return the number
   * @throws ParseException if parsing failed
   */
  public static synchronized Number parseFloatingNumber(String str) throws ParseException {
    return getFormatting().floatingNumberFormat.parse(str);
  }

  /**
   * Parses a money value.
   *
   * @param str the string to parse
   * @return the number
   * @throws ParseException if parsing failed
   */
  public static synchronized Number parseMoney(String str) throws ParseException {
    return getFormatting().moneyFormat.parse(str);
  }

  /**
   * Parses a timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws ParseException if parsing failed
   */
  public static synchronized Timestamp parseTimestamp(String str) throws ParseException {
    return new Timestamp(getFormatting().timestampFormat.parse(str).getTime());
  }

  /**
   * Parses a short timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws ParseException if parsing failed
   */
  public static synchronized Timestamp parseShortTimestamp(String str) throws ParseException {
    return new Timestamp(getFormatting().shortTimestampFormat.parse(str).getTime());
  }

  /**
   * Parses a date.
   *
   * @param str the string to parse
   * @return the date
   * @throws ParseException if parsing failed
   */
  public static synchronized org.tentackle.common.Date parseDate(String str) throws ParseException {
    return new org.tentackle.common.Date(getFormatting().dateFormat.parse(str).getTime());
  }

  /**
   * Parses a short date.
   *
   * @param str the string to parse
   * @return the date
   * @throws ParseException if parsing failed
   */
  public static synchronized org.tentackle.common.Date parseShortDate(String str) throws ParseException {
    return new org.tentackle.common.Date(getFormatting().shortDateFormat.parse(str).getTime());
  }

  /**
   * Parses a time.
   *
   * @param str the string to parse
   * @return the time
   * @throws ParseException if parsing failed
   */
  public static synchronized org.tentackle.common.Time parseTime(String str) throws ParseException {
    return new org.tentackle.common.Time(getFormatting().timeFormat.parse(str).getTime());
  }

  /**
   * Parses a short time.
   *
   * @param str the string to parse
   * @return the time
   * @throws ParseException if parsing failed
   */
  public static synchronized org.tentackle.common.Time parseShortTime(String str) throws ParseException {
    return new org.tentackle.common.Time(getFormatting().shortTimeFormat.parse(str).getTime());
  }


  /**
   * Parses a timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws ParseException if parsing failed
   */
  public static LocalDateTime parseLocalDateTime(String str) throws ParseException {
    return LocalDateTime.parse(str, getFormatting().localDateTimeFormat);
  }

  /**
   * Parses a short timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDateTime parseShortLocalDateTime(String str) throws ParseException {
    return LocalDateTime.parse(str, getFormatting().shortLocalDateTimeFormat);
  }

  /**
   * Parses a date.
   *
   * @param str the string to parse
   * @return the date
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDate parseLocalDate(String str) throws ParseException {
    return LocalDate.parse(str, getFormatting().localDateFormat);
  }

  /**
   * Parses a short date.
   *
   * @param str the string to parse
   * @return the date
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDate parseShortLocalDate(String str) throws ParseException {
    return LocalDate.parse(str, getFormatting().shortLocalDateFormat);
  }

  /**
   * Parses a time.
   *
   * @param str the string to parse
   * @return the time
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalTime parseLocalTime(String str) throws ParseException {
    return LocalTime.parse(str, getFormatting().localTimeFormat);
  }

  /**
   * Parses a short time.
   *
   * @param str the string to parse
   * @return the time
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalTime parseShortLocalTime(String str) throws ParseException {
    return LocalTime.parse(str, getFormatting().shortLocalTimeFormat);
  }



  /**
   * Formats an integer.
   *
   * @param number the number to format
   * @return the formatted string
   */
  public static synchronized String formatInteger(Number number) {
    return getFormatting().integerFormat.format(number);
  }

  /**
   * Formats a floating number.
   *
   * @param number the number to format
   * @return the formatted string
   */
  public static synchronized String formatFloatingNumber(Number number) {
    return getFormatting().floatingNumberFormat.format(number);
  }

  /**
   * Formats a money value.
   *
   * @param number the number to format
   * @return the formatted string
   */
  public static synchronized String formatMoney(Number number) {
    return getFormatting().moneyFormat.format(number);
  }

  /**
   * Formats a timestamp.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static synchronized String formatTimestamp(Date timestamp) {
    return getFormatting().timestampFormat.format(timestamp);
  }

  /**
   * Formats a short timestamp.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static synchronized String formatShortTimestamp(Date timestamp) {
    return getFormatting().shortTimestampFormat.format(timestamp);
  }

  /**
   * Formats a date.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static synchronized String formatDate(Date date) {
    return getFormatting().dateFormat.format(date);
  }

  /**
   * Formats a short date.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static synchronized String formatShortDate(Date date) {
    return getFormatting().shortDateFormat.format(date);
  }

  /**
   * Formats a time.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static synchronized String formatTime(Date time) {
    return getFormatting().timeFormat.format(time);
  }

  /**
   * Formats a short time.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static synchronized String formatShortTime(Date time) {
    return getFormatting().shortTimeFormat.format(time);
  }


  /**
   * Formats a timestamp.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static String formatLocalDateTime(LocalDateTime timestamp) {
    return getFormatting().localDateTimeFormat.format(timestamp);
  }

  /**
   * Formats a short timestamp.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static String formatShortLocalDateTime(LocalDateTime timestamp) {
    return getFormatting().shortLocalDateTimeFormat.format(timestamp);
  }

  /**
   * Formats a date.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static String formatLocalDate(LocalDate date) {
    return getFormatting().localDateFormat.format(date);
  }

  /**
   * Formats a short date.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static String formatShortLocalDate(LocalDate date) {
    return getFormatting().shortLocalDateFormat.format(date);
  }

  /**
   * Formats a time.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static String formatLocalTime(LocalTime time) {
    return getFormatting().localTimeFormat.format(time);
  }

  /**
   * Formats a short time.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static String formatShortLocalTime(LocalTime time) {
    return getFormatting().shortLocalTimeFormat.format(time);
  }


  /**
   * Gets the formatter for a LocalDateTime.
   *
   * @return the immutable and threadsafe formatter
   */
  public static DateTimeFormatter getLocalDateTimeFormatter() {
    return getFormatting().localDateTimeFormat;
  }

  /**
   * Gets the formatter for a LocalDate.
   *
   * @return the immutable and threadsafe formatter
   */
  public static DateTimeFormatter getLocalDateFormatter() {
    return getFormatting().localDateFormat;
  }

  /**
   * Gets the formatter for a LocalTime.
   *
   * @return the immutable and threadsafe formatter
   */
  public static DateTimeFormatter getLocalTimeFormatter() {
    return getFormatting().localTimeFormat;
  }

  /**
   * Gets the formatter for a short LocalDateTime.
   *
   * @return the immutable and threadsafe formatter
   */
  public static DateTimeFormatter getShortLocalDateTimeFormatter() {
    return getFormatting().shortLocalDateTimeFormat;
  }

  /**
   * Gets the formatter for a short LocalDate.
   *
   * @return the immutable and threadsafe formatter
   */
  public static DateTimeFormatter getShortLocalDateFormatter() {
    return getFormatting().shortLocalDateFormat;
  }

  /**
   * Gets the formatter for a short LocalTime.
   *
   * @return the immutable and threadsafe formatter
   */
  public static DateTimeFormatter getShortLocalTimeFormatter() {
    return getFormatting().shortLocalTimeFormat;
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
      char sep = getFormatting().floatingNumberFormat.getDecimalFormatSymbols().getDecimalSeparator();
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
      StringBuilder buf = new StringBuilder(fmt).append('.');
      for (int i=0; i < scale; i++) {
        buf.append('0');
      }
      fmt = buf.toString();
    }

    // apply new format
    format.applyPattern(fmt);

    if (dotNdx <= 0 && groupingUsed)  {
      // default has been set: restore grouping pars
      format.setGroupingSize(groupingSize);
      format.setGroupingUsed(true);
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
    return getFormatting().debitCreditToString(money, debit);
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

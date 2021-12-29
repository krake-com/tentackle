/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.common.BMoney;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.Timestamp;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * Formatting helper methods.
 *
 * @author harald
 */
public class FormatHelper {

  /**
   * Patterns to initialize formatting.
   *
   * @param integerPattern the localized integer format string
   * @param floatingNumberPattern the localized float/double format string
   * @param moneyPattern the localized money format string
   * @param timestampPattern the localized format string for a timestamp
   * @param datePattern the localized format string for a date
   * @param timePattern the localized format string for a time
   * @param shortTimestampPattern the localized format string for a short timestamp
   * @param shortDatePattern the localized format string for a short date
   * @param shortTimePattern the localized format string for a short time
   * @param debitLetter the short letter for "debit" (Soll)
   * @param creditLetter the short letter for "credit" (Haben)
   */
  public record Patterns(String integerPattern,
                         String floatingNumberPattern,
                         String moneyPattern,
                         String timestampPattern,
                         String datePattern,
                         String timePattern,
                         String shortTimestampPattern,
                         String shortDatePattern,
                         String shortTimePattern,
                         char debitLetter,
                         char creditLetter) {

    /**
     * Creates patterns for the given locale.
     *
     * @param locale the locale
     */
    public Patterns(Locale locale) {
      this(MiscCoreBundle.getString(locale, "integerPattern"),
           MiscCoreBundle.getString(locale, "floatingNumberPattern"),
           MiscCoreBundle.getString(locale, "moneyPattern"),
           MiscCoreBundle.getString(locale, "timestampPattern"),
           MiscCoreBundle.getString(locale, "datePattern"),
           MiscCoreBundle.getString(locale, "timePattern"),
           MiscCoreBundle.getString(locale, "shortTimestampPattern"),
           MiscCoreBundle.getString(locale, "shortDatePattern"),
           MiscCoreBundle.getString(locale, "shortTimePattern"),
           MiscCoreBundle.getString(locale, "debitLetter").charAt(0),
           MiscCoreBundle.getString(locale, "creditLetter").charAt(0));
    }
  }


  /**
   * Locale-specific formatting.
   */
  private static class Formatting {

    private final Patterns patterns;

    private final char decimalSeparator;

    /** short letter for "debit" (soll). */
    private final String debitString;

    /** short letter for "credit" (haben). */
    private final String creditString;


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


    private Formatting(Locale locale, Patterns patterns) {
      this.patterns = patterns;
      debitString = " " + patterns.debitLetter + "    ";
      creditString = " " + patterns.creditLetter;
      // old java.util classes w/o threadsafe formats
      integerFormat = new DecimalFormat(patterns.integerPattern);
      floatingNumberFormat = new DecimalFormat(patterns.floatingNumberPattern);
      decimalSeparator = floatingNumberFormat.getDecimalFormatSymbols().getDecimalSeparator();
      moneyFormat = new DecimalFormat(patterns.moneyPattern);
      timestampFormat = new SimpleDateFormat(patterns.timestampPattern, locale);
      dateFormat = new SimpleDateFormat(patterns.datePattern, locale);
      timeFormat = new SimpleDateFormat(patterns.timePattern, locale);
      shortTimestampFormat = new SimpleDateFormat(patterns.shortTimestampPattern, locale);
      shortDateFormat = new SimpleDateFormat(patterns.shortDatePattern, locale);
      shortTimeFormat = new SimpleDateFormat(patterns.shortTimePattern, locale);
      // new java.time package (threadsafe formats)
      localDateTimeFormat = DateTimeFormatter.ofPattern(patterns.timestampPattern, locale);
      localDateFormat = DateTimeFormatter.ofPattern(patterns.datePattern, locale);
      localTimeFormat = DateTimeFormatter.ofPattern(patterns.timePattern, locale);
      shortLocalDateTimeFormat = DateTimeFormatter.ofPattern(patterns.shortTimestampPattern, locale);
      shortLocalDateFormat = DateTimeFormatter.ofPattern(patterns.shortDatePattern, locale);
      shortLocalTimeFormat = DateTimeFormatter.ofPattern(patterns.shortTimePattern, locale);
    }

    private String debitCreditToString(BMoney money, boolean debit) {
      if (money == null) {
        return "";
      }
      synchronized (moneyFormat) {
        setScale(moneyFormat, money.scale());
        return debit ? (moneyFormat.format(money) + debitString) :
               ("    " + moneyFormat.format(money) + creditString);
      }
    }

    private Number parseInteger(String str) throws ParseException {
      synchronized (integerFormat) {
        return integerFormat.parse(str);
      }
    }

    private String formatInteger(long number) {
      synchronized (integerFormat) {
        return integerFormat.format(number);
      }
    }

    private Number parseFloatingNumber(String str) throws ParseException {
      synchronized (floatingNumberFormat) {
        return floatingNumberFormat.parse(str);
      }
    }

    private String formatFloatingNumber(double number) {
      synchronized (floatingNumberFormat) {
        return floatingNumberFormat.format(number);
      }
    }

    // money makes no sense to parse or format since we need to know the scale and reflect that in the format itself

    private Date parseTimestamp(String str) throws ParseException {
      synchronized (timestampFormat) {
        return timestampFormat.parse(str);
      }
    }

    private String formatTimestamp(Date date) {
      synchronized (timestampFormat) {
        return timestampFormat.format(date);
      }
    }

    private Date parseShortTimestamp(String str) throws ParseException {
      synchronized (shortTimestampFormat) {
        return shortTimestampFormat.parse(str);
      }
    }

    private String formatShortTimestamp(Date date) {
      synchronized (shortTimestampFormat) {
        return shortTimestampFormat.format(date);
      }
    }

    private Date parseDate(String str) throws ParseException {
      synchronized (dateFormat) {
        return dateFormat.parse(str);
      }
    }

    private String formatDate(Date date) {
      synchronized (dateFormat) {
        return dateFormat.format(date);
      }
    }

    private Date parseShortDate(String str) throws ParseException {
      synchronized (shortDateFormat) {
        return shortDateFormat.parse(str);
      }
    }

    private String formatShortDate(Date date) {
      synchronized (shortDateFormat) {
        return shortDateFormat.format(date);
      }
    }

    private Date parseTime(String str) throws ParseException {
      synchronized (timeFormat) {
        return timeFormat.parse(str);
      }
    }

    private String formatTime(Date date) {
      synchronized (timeFormat) {
        return timeFormat.format(date);
      }
    }

    private Date parseShortTime(String str) throws ParseException {
      synchronized (shortTimeFormat) {
        return shortTimeFormat.parse(str);
      }
    }

    private String formatShortTime(Date date) {
      synchronized (shortTimeFormat) {
        return shortTimeFormat.format(date);
      }
    }
  }


  /** formattings by locale. */
  private static final Map<Locale, Formatting> FORMATTING_MAP = new ConcurrentHashMap<>();


  /**
   * Gets the formatting for the given locale.
   *
   * @param locale the locale
   * @return the formatting
   */
  private static Formatting getFormatting(Locale locale) {
    return FORMATTING_MAP.computeIfAbsent(locale, l -> new Formatting(l, new Patterns(locale)));
  }

  /**
   * Gets the formatting for the current locale.
   *
   * @return the formatting
   */
  private static Formatting getFormatting() {
    return getFormatting(LocaleProvider.getInstance().getLocale());
  }


  /**
   * Applies a set of patterns to a locale.
   *
   * @param locale the locale
   * @param patterns the patterns
   */
  public static void applyPatterns(Locale locale, Patterns patterns) {
    FORMATTING_MAP.put(locale, new Formatting(locale, patterns));
  }

  /**
   * Applies a set of patterns to the current locale.
   *
   * @param patterns the patterns
   */
  public static void applyPatterns(Patterns patterns) {
    applyPatterns(LocaleProvider.getInstance().getLocale(), patterns);
  }


  /**
   * Gets the integer pattern.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getIntegerPattern(Locale locale) {
    return getFormatting(locale).patterns.integerPattern;
  }

  /**
   * Gets the floating number pattern.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getFloatingNumberPattern(Locale locale) {
    return getFormatting(locale).patterns.floatingNumberPattern;
  }

  /**
   * Gets the money pattern.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getMoneyPattern(Locale locale) {
    return getFormatting(locale).patterns.moneyPattern;
  }

  /**
   * Gets the timestamp pattern.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getTimestampPattern(Locale locale) {
    return getFormatting(locale).patterns.timestampPattern;
  }

  /**
   * Gets the short timestamp pattern.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getShortTimestampPattern(Locale locale) {
    return getFormatting(locale).patterns.shortTimestampPattern;
  }

  /**
   * Gets the date pattern.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getDatePattern(Locale locale) {
    return getFormatting(locale).patterns.datePattern;
  }

  /**
   * Gets the short date pattern.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getShortDatePattern(Locale locale) {
    return getFormatting(locale).patterns.shortDatePattern;
  }

  /**
   * Gets the time pattern.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getTimePattern(Locale locale) {
    return getFormatting(locale).patterns.timePattern;
  }

  /**
   * Gets the short time pattern.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getShortTimePattern(Locale locale) {
    return getFormatting(locale).patterns.shortTimePattern;
  }



  /**
   * Gets the integer pattern.
   *
   * @return the pattern
   */
  public static String getIntegerPattern() {
    return getFormatting().patterns.integerPattern;
  }

  /**
   * Gets the floating number pattern.
   *
   * @return the pattern
   */
  public static String getFloatingNumberPattern() {
    return getFormatting().patterns.floatingNumberPattern;
  }

  /**
   * Gets the money pattern.
   *
   * @return the pattern
   */
  public static String getMoneyPattern() {
    return getFormatting().patterns.moneyPattern;
  }

  /**
   * Gets the timestamp pattern.
   *
   * @return the pattern
   */
  public static String getTimestampPattern() {
    return getFormatting().patterns.timestampPattern;
  }

  /**
   * Gets the short timestamp pattern.
   *
   * @return the pattern
   */
  public static String getShortTimestampPattern() {
    return getFormatting().patterns.shortTimestampPattern;
  }

  /**
   * Gets the date pattern.
   *
   * @return the pattern
   */
  public static String getDatePattern() {
    return getFormatting().patterns.datePattern;
  }

  /**
   * Gets the short date pattern.
   *
   * @return the pattern
   */
  public static String getShortDatePattern() {
    return getFormatting().patterns.shortDatePattern;
  }

  /**
   * Gets the time pattern.
   *
   * @return the pattern
   */
  public static String getTimePattern() {
    return getFormatting().patterns.timePattern;
  }

  /**
   * Gets the short time pattern.
   *
   * @return the pattern
   */
  public static String getShortTimePattern() {
    return getFormatting().patterns.shortTimePattern;
  }


  /**
   * Parses an integer.
   *
   * @param str the string to parse
   * @return the number
   * @throws ParseException if parsing failed
   */
  public static int parseInteger(String str) throws ParseException {
    return getFormatting().parseInteger(str).intValue();
  }

  /**
   * Parses a float.
   *
   * @param str the string to parse
   * @return the number
   * @throws ParseException if parsing failed
   */
  public static float parseFloat(String str) throws ParseException {
    return getFormatting().parseFloatingNumber(str).floatValue();
  }

  /**
   * Parses a double.
   *
   * @param str the string to parse
   * @return the number
   * @throws ParseException if parsing failed
   */
  public static double parseDouble(String str) throws ParseException {
    return getFormatting().parseFloatingNumber(str).doubleValue();
  }

  /**
   * Parses a timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws ParseException if parsing failed
   * @deprecated use {@link #parseTimestampFast(String)} instead
   */
  @Deprecated
  public static Timestamp parseTimestamp(String str) throws ParseException {
    return new Timestamp(getFormatting().parseTimestamp(str).getTime());
  }

  /**
   * Parses a timestamp.<br>
   * Faster than {@link #parseTimestamp(String)} using {@link DateTimeFormatter}.
   * <p>
   * Throws {@link java.time.format.DateTimeParseException} if parsing failed.
   *
   * @param str the string to parse
   * @return the timestamp
   */
  public static Timestamp parseTimestampFast(String str) {
    return new Timestamp(java.sql.Timestamp.valueOf(getFormatting().localDateTimeFormat.parse(str, LocalDateTime::from)).getTime());
  }

  /**
   * Parses a short timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws ParseException if parsing failed
   * @deprecated use {@link #parseShortTimestampFast(String)} instead
   */
  @Deprecated
  public static Timestamp parseShortTimestamp(String str) throws ParseException {
    return new Timestamp(getFormatting().parseShortTimestamp(str).getTime());
  }

  /**
   * Parses a short timestamp.<br>
   * Faster than {@link #parseShortTimestamp(String)} using {@link DateTimeFormatter}.
   * <p>
   * Throws {@link java.time.format.DateTimeParseException} if parsing failed.
   *
   * @param str the string to parse
   * @return the timestamp
   */
  public static Timestamp parseShortTimestampFast(String str) {
    return new Timestamp(java.sql.Timestamp.valueOf(getFormatting().shortLocalDateTimeFormat.parse(str, LocalDateTime::from)).getTime());
  }

  /**
   * Parses a date.
   *
   * @param str the string to parse
   * @return the date
   * @throws ParseException if parsing failed
   * @deprecated use {@link #parseDateFast(String)} instead
   */
  @Deprecated
  public static org.tentackle.common.Date parseDate(String str) throws ParseException {
    return new org.tentackle.common.Date(getFormatting().parseDate(str).getTime());
  }

  /**
   * Parses a date.<br>
   * Faster than {@link #parseDate(String)} using {@link DateTimeFormatter}.
   * <p>
   * Throws {@link java.time.format.DateTimeParseException} if parsing failed.
   *
   * @param str the string to parse
   * @return the date
   */
  public static org.tentackle.common.Date parseDateFast(String str) {
    return new org.tentackle.common.Date(java.sql.Date.valueOf(getFormatting().localDateFormat.parse(str, LocalDate::from)).getTime());
  }

  /**
   * Parses a short date.
   *
   * @param str the string to parse
   * @return the date
   * @throws ParseException if parsing failed
   * @deprecated use {@link #parseShortDateFast(String)} instead
   */
  @Deprecated
  public static org.tentackle.common.Date parseShortDate(String str) throws ParseException {
    return new org.tentackle.common.Date(getFormatting().parseShortDate(str).getTime());
  }

  /**
   * Parses a short date.<br>
   * Faster than {@link #parseShortDate(String)} using {@link DateTimeFormatter}.
   * <p>
   * Throws {@link java.time.format.DateTimeParseException} if parsing failed.
   *
   * @param str the string to parse
   * @return the date
   */
  public static org.tentackle.common.Date parseShortDateFast(String str) {
    return new org.tentackle.common.Date(java.sql.Date.valueOf(getFormatting().shortLocalDateFormat.parse(str, LocalDate::from)).getTime());
  }

  /**
   * Parses a time.
   *
   * @param str the string to parse
   * @return the time
   * @throws ParseException if parsing failed
   * @deprecated use {@link #parseTimeFast(String)} instead
   */
  @Deprecated
  public static org.tentackle.common.Time parseTime(String str) throws ParseException {
    return new org.tentackle.common.Time(getFormatting().parseTime(str).getTime());
  }

  /**
   * Parses a time.<br>
   * Faster than {@link #parseTime(String)} using {@link DateTimeFormatter}.
   * <p>
   * Throws {@link java.time.format.DateTimeParseException} if parsing failed.
   *
   * @param str the string to parse
   * @return the time
   */
  public static org.tentackle.common.Time parseTimeFast(String str) {
    return new org.tentackle.common.Time(java.sql.Time.valueOf(getFormatting().localTimeFormat.parse(str, LocalTime::from)).getTime());
  }

  /**
   * Parses a short time.
   *
   * @param str the string to parse
   * @return the time
   * @throws ParseException if parsing failed
   * @deprecated use {@link #parseShortTimeFast(String)} instead
   */
  @Deprecated
  public static org.tentackle.common.Time parseShortTime(String str) throws ParseException {
    return new org.tentackle.common.Time(getFormatting().parseShortTime(str).getTime());
  }

  /**
   * Parses a short time.<br>
   * Faster than {@link  #parseShortTime(String)} using {@link DateTimeFormatter}.
   * <p>
   * Throws {@link java.time.format.DateTimeParseException} if parsing failed.
   *
   * @param str the string to parse
   * @return the time
   */
  public static org.tentackle.common.Time parseShortTimeFast(String str) {
    return new org.tentackle.common.Time(java.sql.Time.valueOf(getFormatting().shortLocalTimeFormat.parse(str, LocalTime::from)).getTime());
  }


  /**
   * Parses a timestamp.
   * <p>
   * Throws java.time.format.DateTimeParseException if parsing failed
   *
   * @param str the string to parse
   * @return the timestamp
   */
  public static LocalDateTime parseLocalDateTime(String str) {
    return LocalDateTime.parse(str, getFormatting().localDateTimeFormat);
  }

  /**
   * Parses a short timestamp.
   * <p>
   * Throws java.time.format.DateTimeParseException if parsing failed
   *
   * @param str the string to parse
   * @return the timestamp
   */
  public static LocalDateTime parseShortLocalDateTime(String str) {
    return LocalDateTime.parse(str, getFormatting().shortLocalDateTimeFormat);
  }

  /**
   * Parses a date.
   * <p>
   * Throws java.time.format.DateTimeParseException if parsing failed
   *
   * @param str the string to parse
   * @return the date
   */
  public static LocalDate parseLocalDate(String str) {
    return LocalDate.parse(str, getFormatting().localDateFormat);
  }

  /**
   * Parses a short date.
   * <p>
   * Throws java.time.format.DateTimeParseException if parsing failed
   *
   * @param str the string to parse
   * @return the date
   */
  public static LocalDate parseShortLocalDate(String str) {
    return LocalDate.parse(str, getFormatting().shortLocalDateFormat);
  }

  /**
   * Parses a time.
   * <p>
   * Throws java.time.format.DateTimeParseException if parsing failed
   *
   * @param str the string to parse
   * @return the time
   */
  public static LocalTime parseLocalTime(String str) {
    return LocalTime.parse(str, getFormatting().localTimeFormat);
  }

  /**
   * Parses a short time.
   * <p>
   * Throws java.time.format.DateTimeParseException if parsing failed
   *
   * @param str the string to parse
   * @return the time
   */
  public static LocalTime parseShortLocalTime(String str) {
    return LocalTime.parse(str, getFormatting().shortLocalTimeFormat);
  }



  /**
   * Formats an integer.
   *
   * @param number the number to format
   * @return the formatted string
   */
  public static String formatInteger(Number number) {
    return getFormatting().formatInteger(number.longValue());
  }

  /**
   * Formats a floating number.
   *
   * @param number the number to format
   * @return the formatted string
   */
  public static String formatFloatingNumber(Number number) {
    return getFormatting().formatFloatingNumber(number.doubleValue());
  }

  /**
   * Formats a timestamp.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   * @deprecated use {@link #formatTimestampFast(Date)} instead
   */
  @Deprecated
  public static String formatTimestamp(Date timestamp) {
    return getFormatting().formatTimestamp(timestamp);
  }

  /**
   * Formats a timestamp.<br>
   * Faster than {@link #formatTimestamp(Date)} using {@link DateTimeFormatter}.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static String formatTimestampFast(Date timestamp) {
    return getFormatting().localDateTimeFormat.format(new java.sql.Timestamp(timestamp.getTime()).toLocalDateTime());
  }

  /**
   * Formats a short timestamp.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   * @deprecated use {@link #formatShortTimestampFast(Date)} instead
   */
  @Deprecated
  public static String formatShortTimestamp(Date timestamp) {
    return getFormatting().formatShortTimestamp(timestamp);
  }

  /**
   * Formats a short timestamp.<br>
   * Faster than {@link #formatShortTimestamp(Date)} using {@link DateTimeFormatter}.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static String formatShortTimestampFast(Date timestamp) {
    return getFormatting().shortLocalDateTimeFormat.format(new java.sql.Timestamp(timestamp.getTime()).toLocalDateTime());
  }

  /**
   * Formats a date.
   *
   * @param date the date to format
   * @return the formatted string
   * @deprecated use {@link #formatDateFast(Date)} instead
   */
  @Deprecated
  public static String formatDate(Date date) {
    return getFormatting().formatDate(date);
  }

  /**
   * Formats a date.<br>
   * Faster than {@link #formatDate(Date)} using {@link DateTimeFormatter}.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static String formatDateFast(Date date) {
    return getFormatting().localDateFormat.format(new java.sql.Date(date.getTime()).toLocalDate());
  }

  /**
   * Formats a short date.
   *
   * @param date the date to format
   * @return the formatted string
   * @deprecated use {@link #formatShortDateFast(Date)} instead
   */
  @Deprecated
  public static String formatShortDate(Date date) {
    return getFormatting().formatShortDate(date);
  }

  /**
   * Formats a short date.<br>
   * Faster than {@link #formatShortDate(Date)} using {@link DateTimeFormatter}.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static String formatShortDateFast(Date date) {
    return getFormatting().shortLocalDateFormat.format(new java.sql.Date(date.getTime()).toLocalDate());
  }

  /**
   * Formats a time.
   *
   * @param time the time to format
   * @return the formatted string
   * @deprecated use {@link #formatTimeFast(Date)} instead
   */
  @Deprecated
  public static String formatTime(Date time) {
    return getFormatting().formatTime(time);
  }

  /**
   * Formats a time.<br>
   * Faster than {@link #formatTime(Date)} using {@link DateTimeFormatter}.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static String formatTimeFast(Date time) {
    return getFormatting().localTimeFormat.format(new java.sql.Time(time.getTime()).toLocalTime());
  }

  /**
   * Formats a short time.
   *
   * @param time the time to format
   * @return the formatted string
   * @deprecated use {@link #formatShortTimeFast(Date)} instead
   */
  @Deprecated
  public static String formatShortTime(Date time) {
    return getFormatting().formatShortTime(time);
  }

  /**
   * Formats a short time.<br>
   * Faster than {@link #formatShortTime(Date)} using {@link DateTimeFormatter}.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static String formatShortTimeFast(Date time) {
    return getFormatting().shortLocalTimeFormat.format(new java.sql.Time(time.getTime()).toLocalTime());
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
  public static int determineScale(String value) {
    int scale = 0;
    if (value != null) {
      int len = value.length();
      char sep = getFormatting().decimalSeparator;
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
  public static void setScale(DecimalFormat format, int scale) {
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
      fmt += '.' + "0".repeat(scale);
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
  public static String debitCreditToString(BMoney money, boolean debit) {
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
   * Translates a field of {@link java.util.Calendar} to a localized string.
   * <p>
   * Example: {@link java.util.Calendar#DAY_OF_MONTH} will be translated to "month".
   * <p>
   * Notice that only the following field types are supported:
   * <ul>
   * <li>{@link java.util.Calendar#YEAR}</li>
   * <li>{@link java.util.Calendar#MONTH}</li>
   * <li>{@link java.util.Calendar#WEEK_OF_YEAR}</li>
   * <li>{@link java.util.Calendar#DAY_OF_MONTH} or {@link java.util.Calendar#DATE}</li>
   * <li>{@link java.util.Calendar#HOUR} or {@link java.util.Calendar#HOUR_OF_DAY}</li>
   * <li>{@link java.util.Calendar#MINUTE}</li>
   * <li>{@link java.util.Calendar#SECOND}</li>
   * <li>{@link java.util.Calendar#MILLISECOND}</li>
   * </ul>
   *
   * @param field the field type
   * @param plural true if plural, else singular
   * @return the name, the empty string if unsupported field type
   */
  public static String calendarFieldToString(int field, boolean plural) {
    return switch (field) {
      case YEAR -> plural ? MiscCoreBundle.getString("years") : MiscCoreBundle.getString("year");
      case MONTH -> plural ? MiscCoreBundle.getString("months") : MiscCoreBundle.getString("month");
      case WEEK_OF_YEAR -> plural ? MiscCoreBundle.getString("weeks") : MiscCoreBundle.getString("week");
      case DAY_OF_MONTH -> plural ? MiscCoreBundle.getString("days") : MiscCoreBundle.getString("day");
      case HOUR, HOUR_OF_DAY -> plural ? MiscCoreBundle.getString("hours") : MiscCoreBundle.getString("hour");
      case MINUTE -> plural ? MiscCoreBundle.getString("minutes") : MiscCoreBundle.getString("minute");
      case SECOND -> plural ? MiscCoreBundle.getString("seconds") : MiscCoreBundle.getString("second");
      case MILLISECOND -> plural ? MiscCoreBundle.getString("milliseconds") : MiscCoreBundle.getString("millisecond");
      default -> "";
    };
  }


  private FormatHelper() {
  }

}

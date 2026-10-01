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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
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
 * <p>
 * The localized patterns are taken from the {@link MiscCoreBundle} and can be replaced per locale via
 * {@link #applyPatterns(Locale, Patterns)}. Methods without a locale parameter refer to the current locale
 * of the {@link LocaleProvider}.
 * <p>
 * The {@code parseXXX} methods parse the exact format. For the smart parsers accepting shortcuts and incomplete
 * input, see {@link DateTimeUtilities}.
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
   * @param timestampPatternWithOffset the localized format string for a timestamp with zone offset
   * @param timestampPatternWithTimeZone the localized format string for a timestamp with timezone
   * @param datePattern the localized format string for a date
   * @param timePattern the localized format string for a time
   * @param timePatternWithOffset the localized format string for a time with zone offset
   * @param shortTimestampPattern the localized format string for a short timestamp
   * @param shortTimestampPatternWithOffset the localized format string for a short timestamp with zone offset
   * @param shortTimestampPatternWithTimeZone the localized format string for a short timestamp with timezone
   * @param shortDatePattern the localized format string for a short date
   * @param shortTimePattern the localized format string for a short time
   * @param shortTimePatternWithOffset the localized format string for a short time with zone offset
   * @param debitLetter the short letter for "debit" (Soll)
   * @param creditLetter the short letter for "credit" (Haben)
   */
  public record Patterns(String integerPattern,
                         String floatingNumberPattern,
                         String moneyPattern,
                         String timestampPattern,
                         String timestampPatternWithOffset,
                         String timestampPatternWithTimeZone,
                         String datePattern,
                         String timePattern,
                         String timePatternWithOffset,
                         String shortTimestampPattern,
                         String shortTimestampPatternWithOffset,
                         String shortTimestampPatternWithTimeZone,
                         String shortDatePattern,
                         String shortTimePattern,
                         String shortTimePatternWithOffset,
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
           MiscCoreBundle.getString(locale, "timestampPatternWithOffset"),
           MiscCoreBundle.getString(locale, "timestampPatternWithTimeZone"),
           MiscCoreBundle.getString(locale, "datePattern"),
           MiscCoreBundle.getString(locale, "timePattern"),
           MiscCoreBundle.getString(locale, "timePatternWithOffset"),
           MiscCoreBundle.getString(locale, "shortTimestampPattern"),
           MiscCoreBundle.getString(locale, "shortTimestampPatternWithOffset"),
           MiscCoreBundle.getString(locale, "shortTimestampPatternWithTimeZone"),
           MiscCoreBundle.getString(locale, "shortDatePattern"),
           MiscCoreBundle.getString(locale, "shortTimePattern"),
           MiscCoreBundle.getString(locale, "shortTimePatternWithOffset"),
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
    private final ThreadLocal<DecimalFormat> integerFormat;

    /** localized {@link DecimalFormat} for a float or double. */
    private final ThreadLocal<DecimalFormat> floatingNumberFormat;

    /** localized {@link DecimalFormat} for a money value. */
    private final ThreadLocal<DecimalFormat> moneyFormat;

    /** localized {@link java.time.format.DateTimeFormatter} for a timestamp. */
    private final DateTimeFormatter localDateTimeFormat;
    private final DateTimeFormatter localDateTimeFormatWithOffset;
    private final DateTimeFormatter localDateTimeFormatWithTimeZone;

    /** localized {@link java.time.format.DateTimeFormatter} for a date. */
    private final DateTimeFormatter localDateFormat;

    /** localized {@link java.time.format.DateTimeFormatter} for a time. */
    private final DateTimeFormatter localTimeFormat;
    private final DateTimeFormatter localTimeFormatWithOffset;

    /** localized {@link java.time.format.DateTimeFormatter} for a short timestamp. */
    private final DateTimeFormatter shortLocalDateTimeFormat;
    private final DateTimeFormatter shortLocalDateTimeFormatWithOffset;
    private final DateTimeFormatter shortLocalDateTimeFormatWithTimeZone;

    /** localized {@link java.time.format.DateTimeFormatter} for a short date. */
    private final DateTimeFormatter shortLocalDateFormat;

    /** localized {@link java.time.format.DateTimeFormatter} for a short time. */
    private final DateTimeFormatter shortLocalTimeFormat;
    private final DateTimeFormatter shortLocalTimeFormatWithOffset;


    private Formatting(Locale locale, Patterns patterns) {
      this.patterns = patterns;
      debitString = " " + patterns.debitLetter + "    ";
      creditString = " " + patterns.creditLetter;
      // number formats made thread-safe via thread-local (as there is no alternative in JDK yet)
      integerFormat = ThreadLocal.withInitial(() -> new DecimalFormat(patterns.integerPattern));
      floatingNumberFormat = ThreadLocal.withInitial(() -> new DecimalFormat(patterns.floatingNumberPattern));
      decimalSeparator = floatingNumberFormat.get().getDecimalFormatSymbols().getDecimalSeparator();
      moneyFormat = ThreadLocal.withInitial(() -> new DecimalFormat(patterns.moneyPattern));
      // new java.time package (thread-safe)
      localDateTimeFormat = DateTimeFormatter.ofPattern(patterns.timestampPattern, locale);
      localDateTimeFormatWithOffset = DateTimeFormatter.ofPattern(patterns.timestampPatternWithOffset, locale);
      localDateTimeFormatWithTimeZone = DateTimeFormatter.ofPattern(patterns.timestampPatternWithTimeZone, locale);
      localDateFormat = DateTimeFormatter.ofPattern(patterns.datePattern, locale);
      localTimeFormat = DateTimeFormatter.ofPattern(patterns.timePattern, locale);
      localTimeFormatWithOffset = DateTimeFormatter.ofPattern(patterns.timePatternWithOffset, locale);
      shortLocalDateTimeFormat = DateTimeFormatter.ofPattern(patterns.shortTimestampPattern, locale);
      shortLocalDateTimeFormatWithOffset = DateTimeFormatter.ofPattern(patterns.shortTimestampPatternWithOffset, locale);
      shortLocalDateTimeFormatWithTimeZone = DateTimeFormatter.ofPattern(patterns.shortTimestampPatternWithTimeZone, locale);
      shortLocalDateFormat = DateTimeFormatter.ofPattern(patterns.shortDatePattern, locale);
      shortLocalTimeFormat = DateTimeFormatter.ofPattern(patterns.shortTimePattern, locale);
      shortLocalTimeFormatWithOffset = DateTimeFormatter.ofPattern(patterns.shortTimePatternWithOffset, locale);
    }

    private String debitCreditToString(BMoney money, boolean debit) {
      if (money == null) {
        return "";
      }
      DecimalFormat mf = moneyFormat.get();
      setScale(mf, money.scale());
      return debit ? (mf.format(money) + debitString) :
             ("    " + mf.format(money) + creditString);
    }

    private Number parseInteger(String str) throws ParseException {
      return integerFormat.get().parse(str);
    }

    private String formatInteger(long number) {
      return integerFormat.get().format(number);
    }

    private Number parseFloatingNumber(String str) throws ParseException {
      return floatingNumberFormat.get().parse(str);
    }

    private String formatFloatingNumber(double number) {
      return floatingNumberFormat.get().format(number);
    }

    // money makes no sense to parse or format since we need to know the scale and reflect that in the format itself

  }


  /** Formattings by Locale. */
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
   * Applies a set of patterns to a locale.<br>
   * Replaces the patterns from the {@link MiscCoreBundle}.
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
   * Gets the timestamp pattern with zone offset.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getTimestampPatternWithOffset(Locale locale) {
    return getFormatting(locale).patterns.timestampPatternWithOffset;
  }

  /**
   * Gets the timestamp pattern with timezone.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getTimestampPatternWithTimeZone(Locale locale) {
    return getFormatting(locale).patterns.timestampPatternWithTimeZone;
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
   * Gets the short timestamp pattern with zone offset.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getShortTimestampPatternWithOffset(Locale locale) {
    return getFormatting(locale).patterns.shortTimestampPatternWithOffset;
  }

  /**
   * Gets the short timestamp pattern with timezone.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getShortTimestampPatternWithTimeZone(Locale locale) {
    return getFormatting(locale).patterns.shortTimestampPatternWithTimeZone;
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
   * Gets the time pattern with zone offset.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getTimePatternWithOffset(Locale locale) {
    return getFormatting(locale).patterns.timePatternWithOffset;
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
   * Gets the short time pattern with zone offset.
   *
   * @param locale the locale
   * @return the pattern
   */
  public static String getShortTimePatternWithOffset(Locale locale) {
    return getFormatting(locale).patterns.shortTimePatternWithOffset;
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
   * Gets the timestamp pattern with zone offset.
   *
   * @return the pattern
   */
  public static String getTimestampPatternWithOffset() {
    return getFormatting().patterns.timestampPatternWithOffset;
  }

  /**
   * Gets the timestamp pattern with timezone.
   *
   * @return the pattern
   */
  public static String getTimestampPatternWithTimeZone() {
    return getFormatting().patterns.timestampPatternWithTimeZone;
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
   * Gets the short timestamp pattern with offset.
   *
   * @return the pattern
   */
  public static String getShortTimestampPatternWithOffset() {
    return getFormatting().patterns.shortTimestampPatternWithOffset;
  }

  /**
   * Gets the short timestamp pattern with timezone.
   *
   * @return the pattern
   */
  public static String getShortTimestampPatternWithTimeZone() {
    return getFormatting().patterns.shortTimestampPatternWithTimeZone;
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
   * Gets the time pattern with zone offset.
   *
   * @return the pattern
   */
  public static String getTimePatternWithOffset() {
    return getFormatting().patterns.timePatternWithOffset;
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
   * Gets the short time pattern with zone offset.
   *
   * @return the pattern
   */
  public static String getShortTimePatternWithOffset() {
    return getFormatting().patterns.shortTimePatternWithOffset;
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
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static Timestamp parseTimestamp(String str) {
    return new Timestamp(java.sql.Timestamp.valueOf(getFormatting().localDateTimeFormat.parse(str, LocalDateTime::from)).getTime());
  }

  /**
   * Parses a short timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static Timestamp parseShortTimestamp(String str) {
    return new Timestamp(java.sql.Timestamp.valueOf(getFormatting().shortLocalDateTimeFormat.parse(str, LocalDateTime::from)).getTime());
  }

  /**
   * Parses a date.
   *
   * @param str the string to parse
   * @return the date
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static org.tentackle.common.Date parseDate(String str) {
    return new org.tentackle.common.Date(java.sql.Date.valueOf(getFormatting().localDateFormat.parse(str, LocalDate::from)).getTime());
  }

  /**
   * Parses a short date.
   *
   * @param str the string to parse
   * @return the date
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static org.tentackle.common.Date parseShortDate(String str) {
    return new org.tentackle.common.Date(java.sql.Date.valueOf(getFormatting().shortLocalDateFormat.parse(str, LocalDate::from)).getTime());
  }

  /**
   * Parses a time.
   *
   * @param str the string to parse
   * @return the time
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static org.tentackle.common.Time parseTime(String str) {
    return new org.tentackle.common.Time(java.sql.Time.valueOf(getFormatting().localTimeFormat.parse(str, LocalTime::from)).getTime());
  }

  /**
   * Parses a short time.
   *
   * @param str the string to parse
   * @return the time
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static org.tentackle.common.Time parseShortTime(String str) {
    return new org.tentackle.common.Time(java.sql.Time.valueOf(getFormatting().shortLocalTimeFormat.parse(str, LocalTime::from)).getTime());
  }

  /**
   * Parses a timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDateTime parseLocalDateTime(String str) {
    return LocalDateTime.parse(str, getFormatting().localDateTimeFormat);
  }

  /**
   * Parses a timestamp with zone offset.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static OffsetDateTime parseOffsetDateTime(String str) {
    return OffsetDateTime.parse(str, getFormatting().localDateTimeFormatWithOffset);
  }

  /**
   * Parses a timestamp with timezone.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static ZonedDateTime parseZonedDateTime(String str) {
    return ZonedDateTime.parse(str, getFormatting().localDateTimeFormatWithTimeZone);
  }

  /**
   * Parses a short timestamp.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDateTime parseShortLocalDateTime(String str) {
    return LocalDateTime.parse(str, getFormatting().shortLocalDateTimeFormat);
  }

  /**
   * Parses a short timestamp with zone offset.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static OffsetDateTime parseShortOffsetDateTime(String str) {
    return OffsetDateTime.parse(str, getFormatting().shortLocalDateTimeFormatWithOffset);
  }

  /**
   * Parses a short timestamp with timezone.
   *
   * @param str the string to parse
   * @return the timestamp
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static ZonedDateTime parseShortZonedDateTime(String str) {
    return ZonedDateTime.parse(str, getFormatting().shortLocalDateTimeFormatWithTimeZone);
  }

  /**
   * Parses a date.
   *
   * @param str the string to parse
   * @return the date
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDate parseLocalDate(String str) {
    return LocalDate.parse(str, getFormatting().localDateFormat);
  }

  /**
   * Parses a short date.
   *
   * @param str the string to parse
   * @return the date
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDate parseShortLocalDate(String str) {
    return LocalDate.parse(str, getFormatting().shortLocalDateFormat);
  }

  /**
   * Parses a time.
   *
   * @param str the string to parse
   * @return the time
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalTime parseLocalTime(String str) {
    return LocalTime.parse(str, getFormatting().localTimeFormat);
  }

  /**
   * Parses a time with zone offset.
   *
   * @param str the string to parse
   * @return the time
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static OffsetTime parseOffsetTime(String str) {
    return OffsetTime.parse(str, getFormatting().localTimeFormatWithOffset);
  }

  /**
   * Parses a short time.
   *
   * @param str the string to parse
   * @return the time
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalTime parseShortLocalTime(String str) {
    return LocalTime.parse(str, getFormatting().shortLocalTimeFormat);
  }

  /**
   * Parses short a time with zone offset.
   *
   * @param str the string to parse
   * @return the time
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static OffsetTime parseShortOffsetTime(String str) {
    return OffsetTime.parse(str, getFormatting().shortLocalTimeFormatWithOffset);
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
   */
  public static String formatTimestamp(Date timestamp) {
    return getFormatting().localDateTimeFormat.format(new java.sql.Timestamp(timestamp.getTime()).toLocalDateTime());
  }

  /**
   * Formats a short timestamp.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static String formatShortTimestamp(Date timestamp) {
    return getFormatting().shortLocalDateTimeFormat.format(new java.sql.Timestamp(timestamp.getTime()).toLocalDateTime());
  }

  /**
   * Formats a date.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static String formatDate(Date date) {
    return getFormatting().localDateFormat.format(new java.sql.Date(date.getTime()).toLocalDate());
  }

  /**
   * Formats a short date.
   *
   * @param date the date to format
   * @return the formatted string
   */
  public static String formatShortDate(Date date) {
    return getFormatting().shortLocalDateFormat.format(new java.sql.Date(date.getTime()).toLocalDate());
  }

  /**
   * Formats a time.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static String formatTime(Date time) {
    return getFormatting().localTimeFormat.format(new java.sql.Time(time.getTime()).toLocalTime());
  }

  /**
   * Formats a short time.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static String formatShortTime(Date time) {
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
   * Formats a timestamp with zone offset.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static String formatOffsetDateTime(OffsetDateTime timestamp) {
    return getFormatting().localDateTimeFormatWithOffset.format(timestamp);
  }

  /**
   * Formats a timestamp with timezone.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static String formatZonedDateTime(ZonedDateTime timestamp) {
    return getFormatting().localDateTimeFormatWithTimeZone.format(timestamp);
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
   * Formats a short timestamp with zone offset.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static String formatShortOffsetDateTime(OffsetDateTime timestamp) {
    return getFormatting().shortLocalDateTimeFormatWithOffset.format(timestamp);
  }

  /**
   * Formats a short timestamp with timezone.
   *
   * @param timestamp the timestamp to format
   * @return the formatted string
   */
  public static String formatShortZonedDateTime(ZonedDateTime timestamp) {
    return getFormatting().shortLocalDateTimeFormatWithTimeZone.format(timestamp);
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
   * Formats a time with zone offset.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static String formatOffsetTime(OffsetTime time) {
    return getFormatting().localTimeFormatWithOffset.format(time);
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
   * Formats a short time with zone offset.
   *
   * @param time the time to format
   * @return the formatted string
   */
  public static String formatShortOffsetTime(OffsetTime time) {
    return getFormatting().shortLocalTimeFormatWithOffset.format(time);
  }


  /**
   * Gets the formatter for a LocalDateTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getLocalDateTimeFormatter() {
    return getFormatting().localDateTimeFormat;
  }

  /**
   * Gets the formatter for an OffsetDateTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getOffsetDateTimeFormatter() {
    return getFormatting().localDateTimeFormatWithOffset;
  }

  /**
   * Gets the formatter for a ZonedDateTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getZonedDateTimeFormatter() {
    return getFormatting().localDateTimeFormatWithTimeZone;
  }

  /**
   * Gets the formatter for a LocalDate.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getLocalDateFormatter() {
    return getFormatting().localDateFormat;
  }

  /**
   * Gets the formatter for a LocalTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getLocalTimeFormatter() {
    return getFormatting().localTimeFormat;
  }

  /**
   * Gets the formatter for an OffsetTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getOffsetTimeFormatter() {
    return getFormatting().localTimeFormatWithOffset;
  }

  /**
   * Gets the formatter for a short LocalDateTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortLocalDateTimeFormatter() {
    return getFormatting().shortLocalDateTimeFormat;
  }

  /**
   * Gets the formatter for a short OffsetDateTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortOffsetDateTimeFormatter() {
    return getFormatting().shortLocalDateTimeFormatWithOffset;
  }

  /**
   * Gets the formatter for a short ZonedDateTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortZonedDateTimeFormatter() {
    return getFormatting().shortLocalDateTimeFormatWithTimeZone;
  }

  /**
   * Gets the formatter for a short LocalDate.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortLocalDateFormatter() {
    return getFormatting().shortLocalDateFormat;
  }

  /**
   * Gets the formatter for a short LocalTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortLocalTimeFormatter() {
    return getFormatting().shortLocalTimeFormat;
  }

  /**
   * Gets the formatter for a short OffsetTime.
   *
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortOffsetTimeFormatter() {
    return getFormatting().shortLocalTimeFormatWithOffset;
  }


  /**
   * Gets the formatter for a LocalDateTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getLocalDateTimeFormatter(Locale locale) {
    return getFormatting(locale).localDateTimeFormat;
  }

  /**
   * Gets the formatter for an OffsetDateTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getOffsetDateTimeFormatter(Locale locale) {
    return getFormatting(locale).localDateTimeFormatWithOffset;
  }

  /**
   * Gets the formatter for a ZonedDateTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getZonedDateTimeFormatter(Locale locale) {
    return getFormatting(locale).localDateTimeFormatWithTimeZone;
  }

  /**
   * Gets the formatter for a LocalDate.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getLocalDateFormatter(Locale locale) {
    return getFormatting(locale).localDateFormat;
  }

  /**
   * Gets the formatter for a LocalTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getLocalTimeFormatter(Locale locale) {
    return getFormatting(locale).localTimeFormat;
  }

  /**
   * Gets the formatter for an OffsetTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getOffsetTimeFormatter(Locale locale) {
    return getFormatting(locale).localTimeFormatWithOffset;
  }

  /**
   * Gets the formatter for a short LocalDateTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortLocalDateTimeFormatter(Locale locale) {
    return getFormatting(locale).shortLocalDateTimeFormat;
  }

  /**
   * Gets the formatter for a short OffsetDateTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortOffsetDateTimeFormatter(Locale locale) {
    return getFormatting(locale).shortLocalDateTimeFormatWithOffset;
  }

  /**
   * Gets the formatter for a short ZonedDateTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortZonedDateTimeFormatter(Locale locale) {
    return getFormatting(locale).shortLocalDateTimeFormatWithTimeZone;
  }

  /**
   * Gets the formatter for a short LocalDate.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortLocalDateFormatter(Locale locale) {
    return getFormatting(locale).shortLocalDateFormat;
  }

  /**
   * Gets the formatter for a short LocalTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortLocalTimeFormatter(Locale locale) {
    return getFormatting(locale).shortLocalTimeFormat;
  }

  /**
   * Gets the formatter for a short OffsetTime.
   *
   * @param locale the locale
   * @return the immutable and thread-safe formatter
   */
  public static DateTimeFormatter getShortOffsetTimeFormatter(Locale locale) {
    return getFormatting(locale).shortLocalTimeFormatWithOffset;
  }



  /**
   * Determines the scale from a given numeric string.<br>
   * The scale is the number of digits after the decimal separator of the current locale.
   * <p>
   * Does not work with scientific number strings.
   *
   * @param value the numeric string, may be null
   * @return the scale, 0 if value is null or has no decimal separator
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
   * Leaves the part before the decimal point unchanged!
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
      // default has been set: restore grouping parameters
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
   * @param debit true if the amount is debit, else credit
   * @return the formatted value, the empty string if money is null
   */
  public static String debitCreditToString(BMoney money, boolean debit) {
    return getFormatting().debitCreditToString(money, debit);
  }


  /**
   * Checks whether the format pattern contains formatting codes for time.
   *
   * @param pattern the format pattern
   * @return true if contains time-formatting
   */
  public static boolean isFormattingTime(String pattern) {
    return pattern.indexOf('H') >= 0 ||
           pattern.indexOf('k') >= 0 ||
           pattern.indexOf('K') >= 0 ||
           pattern.indexOf('h') >= 0 ||
           pattern.indexOf('m') >= 0 ||
           pattern.indexOf('s') >= 0 ||
           pattern.indexOf('a') >= 0 ||
           pattern.indexOf('S') >= 0;
  }

  /**
   * Checks whether the format pattern contains formatting codes for date.
   *
   * @param pattern the format pattern
   * @return true if contains date-formatting
   */
  public static boolean isFormattingDate(String pattern) {
    return pattern.indexOf('y') >= 0 ||
           pattern.indexOf('u') >= 0 ||
           pattern.indexOf('M') >= 0 ||
           pattern.indexOf('L') >= 0 ||
           pattern.indexOf('w') >= 0 ||
           pattern.indexOf('W') >= 0 ||
           pattern.indexOf('D') >= 0 ||
           pattern.indexOf('d') >= 0 ||
           pattern.indexOf('F') >= 0 ||
           pattern.indexOf('E') >= 0;
  }

  /**
   * Checks whether the format pattern contains formatting codes for the zone offset.
   *
   * @param pattern the format pattern
   * @return true if contains zone-offset formatting
   */
  public static boolean isFormattingOffset(String pattern) {
    return pattern.indexOf('x') >= 0 ||
           pattern.indexOf('X') >= 0 ||
           pattern.indexOf('O') >= 0 ||
           pattern.indexOf('Z') >= 0;
  }

  /**
   * Checks whether the format pattern contains formatting codes for the timezone.
   *
   * @param pattern the format pattern
   * @return true if contains timezone formatting
   */
  public static boolean isFormattingTimeZone(String pattern) {
    return pattern.indexOf('v') >= 0 ||
           pattern.indexOf('V') >= 0 ||
           pattern.indexOf('z') >= 0;
  }


  /**
   * Translates a field of {@link java.util.Calendar} to a localized string.
   * <p>
   * Example: {@link java.util.Calendar#DAY_OF_MONTH} will be translated to "day".
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

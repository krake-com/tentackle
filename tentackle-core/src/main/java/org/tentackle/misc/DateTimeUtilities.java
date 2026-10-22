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

package org.tentackle.misc;

import org.tentackle.common.LocaleProvider;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.misc.time.AddShortcut;
import org.tentackle.misc.time.DateFilter;
import org.tentackle.misc.time.MissingOffsetHandler;
import org.tentackle.misc.time.MissingTimeHandler;
import org.tentackle.misc.time.MissingZoneIdHandler;
import org.tentackle.misc.time.NowShortcut;
import org.tentackle.misc.time.NumberShortcut;
import org.tentackle.misc.time.OffsetFilter;
import org.tentackle.misc.time.OffsetShortcut;
import org.tentackle.misc.time.SmartDateTimeParser;
import org.tentackle.misc.time.ZoneFilter;
import org.tentackle.misc.time.ZoneShortcut;

import java.text.DecimalFormatSymbols;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;


interface DateTimeUtilitiesHolder {
  DateTimeUtilities INSTANCE = ServiceFactory.createService(DateTimeUtilities.class, DateTimeUtilities.class);
}


/**
 * Utility methods related to the {@code java.time} API.
 * <p>
 * Provides the smart parsers for the {@code java.time} types, which accept shortcuts and
 * incomplete input in addition to the regular format. See {@link SmartDateTimeParser}.
 * <p>
 * Replaceable via &#64;{@link Service}.
 */
@Service(DateTimeUtilities.class)   // defaults to self
public class DateTimeUtilities {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static DateTimeUtilities getInstance() {
    return DateTimeUtilitiesHolder.INSTANCE;
  }


  /**
   * Smart {@link LocalTime} parser.
   *
   * @param text the input string
   * @return the time, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalTime parseLocalTime(String text) {
    return getInstance().parse(LocalTime.class, LocalTime::from, LocalTime::truncatedTo, LocalTime::now,
                               FormatHelper.getLocalTimeFormatter(), FormatHelper.getTimePattern(),
                               text);
  }

  /**
   * Smart {@link LocalDate} parser.
   *
   * @param text the input string
   * @return the date, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDate parseLocalDate(String text) {
    return getInstance().parse(LocalDate.class, LocalDate::from, null, LocalDate::now,
                               FormatHelper.getLocalDateFormatter(), FormatHelper.getDatePattern(),
                               text);
  }

  /**
   * Smart {@link LocalDateTime} parser.
   *
   * @param text the input string
   * @return the timestamp, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDateTime parseLocalDateTime(String text) {
    return getInstance().parse(LocalDateTime.class, LocalDateTime::from, LocalDateTime::truncatedTo, LocalDateTime::now,
                               FormatHelper.getLocalDateTimeFormatter(), FormatHelper.getTimestampPattern(),
                               text);
  }

  /**
   * Smart {@link OffsetTime} parser.
   *
   * @param text the input string
   * @return the time with offset, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static OffsetTime parseOffsetTime(String text) {
    return getInstance().parse(OffsetTime.class, OffsetTime::from, OffsetTime::truncatedTo, OffsetTime::now,
                               FormatHelper.getOffsetTimeFormatter(), FormatHelper.getTimePatternWithOffset(),
                               text);
  }

  /**
   * Smart {@link OffsetDateTime} parser.
   *
   * @param text the input string
   * @return the timestamp with offset, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static OffsetDateTime parseOffsetDateTime(String text) {
    return getInstance().parse(OffsetDateTime.class, OffsetDateTime::from, OffsetDateTime::truncatedTo, OffsetDateTime::now,
                               FormatHelper.getOffsetDateTimeFormatter(), FormatHelper.getTimestampPatternWithOffset(),
                               text);
  }

  /**
   * Smart {@link ZonedDateTime} parser.
   *
   * @param text the input string
   * @return the timestamp with timezone, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static ZonedDateTime parseZonedDateTime(String text) {
    return getInstance().parse(ZonedDateTime.class, ZonedDateTime::from, ZonedDateTime::truncatedTo, ZonedDateTime::now,
                               FormatHelper.getZonedDateTimeFormatter(), FormatHelper.getTimestampPatternWithTimeZone(),
                               text);
  }


  /**
   * Smart short format {@link LocalTime} parser.
   *
   * @param text the input string
   * @return the time, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalTime parseShortLocalTime(String text) {
    return getInstance().parse(LocalTime.class, LocalTime::from, LocalTime::truncatedTo, LocalTime::now,
                               FormatHelper.getShortLocalTimeFormatter(), FormatHelper.getShortTimePattern(),
                               text);
  }

  /**
   * Smart short format {@link LocalDate} parser.
   *
   * @param text the input string
   * @return the date, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDate parseShortLocalDate(String text) {
    return getInstance().parse(LocalDate.class, LocalDate::from, null, LocalDate::now,
                               FormatHelper.getShortLocalDateFormatter(), FormatHelper.getShortDatePattern(),
                               text);
  }

  /**
   * Smart short format {@link LocalDateTime} parser.
   *
   * @param text the input string
   * @return the timestamp, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static LocalDateTime parseShortLocalDateTime(String text) {
    return getInstance().parse(LocalDateTime.class, LocalDateTime::from, LocalDateTime::truncatedTo, LocalDateTime::now,
                               FormatHelper.getShortLocalDateTimeFormatter(), FormatHelper.getShortTimestampPattern(),
                               text);
  }

  /**
   * Smart short format {@link OffsetTime} parser.
   *
   * @param text the input string
   * @return the time with offset, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static OffsetTime parseShortOffsetTime(String text) {
    return getInstance().parse(OffsetTime.class, OffsetTime::from, OffsetTime::truncatedTo, OffsetTime::now,
                               FormatHelper.getShortOffsetTimeFormatter(), FormatHelper.getShortTimePatternWithOffset(),
                               text);
  }

  /**
   * Smart short format {@link OffsetDateTime} parser.
   *
   * @param text the input string
   * @return the timestamp with offset, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static OffsetDateTime parseShortOffsetDateTime(String text) {
    return getInstance().parse(OffsetDateTime.class, OffsetDateTime::from, OffsetDateTime::truncatedTo, OffsetDateTime::now,
                               FormatHelper.getShortOffsetDateTimeFormatter(), FormatHelper.getShortTimestampPatternWithOffset(),
                               text);
  }

  /**
   * Smart short format {@link ZonedDateTime} parser.
   *
   * @param text the input string
   * @return the timestamp with timezone, null if text is null
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public static ZonedDateTime parseShortZonedDateTime(String text) {
    return getInstance().parse(ZonedDateTime.class, ZonedDateTime::from, ZonedDateTime::truncatedTo, ZonedDateTime::now,
                               FormatHelper.getShortZonedDateTimeFormatter(), FormatHelper.getShortTimestampPatternWithTimeZone(),
                               text);
  }



  // Temporal classes are all final -> class.hash/equals is ok
  private static final Map<Class<? extends Temporal>, Supplier<Temporal>> NOW_MAP = Map.of(
      OffsetTime.class, OffsetTime::now,
      OffsetDateTime.class, OffsetDateTime::now,
      LocalTime.class, LocalTime::now,
      LocalDate.class, LocalDate::now,
      LocalDateTime.class, LocalDateTime::now,
      ZonedDateTime.class, ZonedDateTime::now,
      Instant.class, Instant::now);


  private final List<SmartDateTimeParser.TextFilter> filters = List.of(new DateFilter(),
                                                                       new OffsetFilter(),
                                                                       new ZoneFilter());

  private final List<SmartDateTimeParser.Shortcut> shortcuts = List.of(new NowShortcut(),
                                                                       new AddShortcut(),
                                                                       new NumberShortcut(),
                                                                       new OffsetShortcut(),
                                                                       new ZoneShortcut());

  private final List<SmartDateTimeParser.ErrorHandler> handlers = List.of(new MissingTimeHandler(),
                                                                          new MissingOffsetHandler(),
                                                                          new MissingZoneIdHandler());


  /**
   * Creates the date- and time-utilities.
   */
  public DateTimeUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the list of default shortcuts.
   *
   * @return the list of shortcuts
   */
  public List<SmartDateTimeParser.Shortcut> getShortcuts() {
    return shortcuts;
  }

  /**
   * Gets the list of filters.
   *
   * @return the list of filters
   */
  public List<SmartDateTimeParser.TextFilter> getFilters() {
    return filters;
  }

  /**
   * Gets the list of error handlers.
   *
   * @return the handlers
   */
  public List<SmartDateTimeParser.ErrorHandler> getHandlers() {
    return handlers;
  }

  /**
   * Converts a string to a temporal value of the given type.<br>
   * Uses a {@link SmartDateTimeParser} with the filters, shortcuts and error handlers of this utility.
   *
   * @param clazz the temporal class
   * @param temporalQuery the formatter query
   * @param truncate the truncate method reference, null if the type does not support it
   * @param currentValueSupplier the supplier for the current/initial/default value, usually a reference to a method such as {@code ::now}
   * @param formatter the formatter
   * @param pattern the formatting pattern string
   * @param text the string to parse
   * @param <T> the type
   * @return the temporal value object
   * @throws java.time.format.DateTimeParseException if parsing failed
   */
  public <T extends Temporal> T parse(Class<T> clazz,
                                      TemporalQuery<T> temporalQuery, BiFunction<T, TemporalUnit, T> truncate,
                                      Supplier<T> currentValueSupplier,
                                      DateTimeFormatter formatter, String pattern,
                                      String text) {

    return new SmartDateTimeParser<>(clazz, temporalQuery, truncate, formatter, pattern,
                                     getFilters(), getShortcuts(), getHandlers()).parse(currentValueSupplier, text);
  }


  /**
   * Creates a zone offset from a string.<br>
   * Returns the current default offset if the string is null or empty.
   * A missing leading zero of the hours is added, for example {@code +2:30} becomes {@code +02:30}.
   *
   * @param text the string
   * @return the zone offset, never null
   * @throws DateTimeException if parsing failed
   */
  public ZoneOffset parseOffset(String text) {
    if (text != null && !text.isEmpty()) {
      // localized minus sign (swedish, for example)
      char minusSign = DecimalFormatSymbols.getInstance(LocaleProvider.getInstance().getLocale()).getMinusSign();
      // make some adjustments for lazy input
      char c = text.charAt(0);
      if (c == '-' || c == minusSign || c == '+') {
        int colonNdx = text.indexOf(':');
        if (colonNdx == 2) {
          // leading zero missing (+2:30)
          text = text.charAt(0) + "0" + text.substring(1);
        }
      }
      return ZoneOffset.of(text);
    }
    return OffsetTime.now().getOffset();
  }


  private List<String> zoneIds;

  /**
   * Gets the sorted list of zone ids.
   *
   * @return the zone id strings
   */
  public List<String> getZoneIds() {
    if (zoneIds == null) {
      List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
      Collections.sort(zones);
      zoneIds = zones;
    }
    return zoneIds;
  }


  /**
   * Creates a timezone from a string.<br>
   * Returns the default zone if the string is null or empty.
   * If the string is not a valid zone ID, the first zone ID (in sorted order) containing the string,
   * ignoring the case, is used.
   *
   * @param text the string
   * @return the timezone, never null
   * @throws DateTimeException if parsing failed
   */
  public ZoneId parseZoneId(String text) {
    if (text != null && !text.isEmpty()) {
      try {
        return ZoneId.of(text);
      }
      catch (DateTimeException ex) {
        // by partial name
        text = text.toUpperCase(Locale.ROOT);
        for (String zoneId : getZoneIds()) {
          if (zoneId.toUpperCase(Locale.ROOT).contains(text)) {
            return ZoneId.of(zoneId);
          }
        }
        throw ex;
      }
    }
    return ZonedDateTime.now().getZone();
  }


  /**
   * Gets the current time/date of the same type as the given instance.<br>
   * Provides an implementation of the missing method {@link Temporal}#now(),
   * if the concrete temporal type is unknown.
   * <p>
   * Example:
   * <pre>
   *   Temporal temporal = ....   // some Temporal of unknown type
   *   // the method temporal.now() does not exist, nor Temporal.nowOf(...) or alike
   *   Temporal now = DateTimeUtilities.getInstance().nowOf(temporal.getClass());
   * </pre>
   *
   * @param temporalClass the temporal class
   * @param <T> the type
   * @return the current time of the given type
   * @throws DateTimeException if the type is not supported
   */
  @SuppressWarnings("unchecked")
  public <T extends Temporal> T nowOf(Class<T> temporalClass) {
    Supplier<Temporal> supplier = NOW_MAP.get(Objects.requireNonNull(temporalClass, "temporal"));
    if (supplier == null) {
      throw new DateTimeException("unsupported Temporal " + temporalClass);
    }
    return (T) supplier.get();
  }

}

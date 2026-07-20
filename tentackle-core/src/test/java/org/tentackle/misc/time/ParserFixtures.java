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

import org.tentackle.misc.DateTimeUtilities;
import org.tentackle.misc.FormatHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;

/**
 * Builds {@link SmartDateTimeParser} instances for the {@code misc.time} unit tests.
 * <p>
 * The parsers are wired with the framework's default filters, shortcuts and error handlers
 * (the same ones {@link DateTimeUtilities} uses), so the individual components can be exercised
 * against a real parser without going through the full {@link DateTimeUtilities} entry points.
 * <p>
 * Not a test class itself (no {@code Test} suffix), so surefire ignores it.
 */
final class ParserFixtures {

  private ParserFixtures() {
  }

  private static <T extends Temporal> SmartDateTimeParser<T> build(
      Class<T> type,
      java.time.temporal.TemporalQuery<T> query,
      java.util.function.BiFunction<T, java.time.temporal.TemporalUnit, T> truncate,
      java.time.format.DateTimeFormatter formatter,
      String pattern) {
    DateTimeUtilities u = DateTimeUtilities.getInstance();
    return new SmartDateTimeParser<>(type, query, truncate, formatter, pattern,
                                     u.getFilters(), u.getShortcuts(), u.getHandlers());
  }

  static SmartDateTimeParser<LocalDate> localDate() {
    return build(LocalDate.class, LocalDate::from, null,
                 FormatHelper.getLocalDateFormatter(), FormatHelper.getDatePattern());
  }

  static SmartDateTimeParser<LocalTime> localTime() {
    return build(LocalTime.class, LocalTime::from, LocalTime::truncatedTo,
                 FormatHelper.getLocalTimeFormatter(), FormatHelper.getTimePattern());
  }

  static SmartDateTimeParser<LocalDateTime> localDateTime() {
    return build(LocalDateTime.class, LocalDateTime::from, LocalDateTime::truncatedTo,
                 FormatHelper.getLocalDateTimeFormatter(), FormatHelper.getTimestampPattern());
  }

  static SmartDateTimeParser<OffsetTime> offsetTime() {
    return build(OffsetTime.class, OffsetTime::from, OffsetTime::truncatedTo,
                 FormatHelper.getOffsetTimeFormatter(), FormatHelper.getTimePatternWithOffset());
  }

  static SmartDateTimeParser<OffsetDateTime> offsetDateTime() {
    return build(OffsetDateTime.class, OffsetDateTime::from, OffsetDateTime::truncatedTo,
                 FormatHelper.getOffsetDateTimeFormatter(), FormatHelper.getTimestampPatternWithOffset());
  }

  static SmartDateTimeParser<ZonedDateTime> zonedDateTime() {
    return build(ZonedDateTime.class, ZonedDateTime::from, ZonedDateTime::truncatedTo,
                 FormatHelper.getZonedDateTimeFormatter(), FormatHelper.getTimestampPatternWithTimeZone());
  }
}

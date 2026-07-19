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

import org.tentackle.misc.FormatHelper;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link SmartDateTimeParser} accessors, the parse control flow and edge cases.
 */
@SuppressWarnings("missing-explicit-ctor")
public class SmartDateTimeParserTest {

  @Test
  public void testDateOnlyFlags() {
    SmartDateTimeParser<LocalDate> parser = ParserFixtures.localDate();
    assertEquals(parser.getType(), LocalDate.class);
    assertTrue(parser.isWithDate());
    assertFalse(parser.isWithTime());
    assertFalse(parser.isWithOffset());
    assertFalse(parser.isWithTimeZone());
    assertEquals(parser.getPattern(), FormatHelper.getDatePattern());
  }

  @Test
  public void testTimeOnlyFlags() {
    SmartDateTimeParser<LocalTime> parser = ParserFixtures.localTime();
    assertFalse(parser.isWithDate());
    assertTrue(parser.isWithTime());
    assertFalse(parser.isWithOffset());
    assertFalse(parser.isWithTimeZone());
  }

  @Test
  public void testDateTimeFlags() {
    SmartDateTimeParser<LocalDateTime> parser = ParserFixtures.localDateTime();
    assertTrue(parser.isWithDate());
    assertTrue(parser.isWithTime());
  }

  @Test
  public void testOffsetFlag() {
    assertTrue(ParserFixtures.offsetTime().isWithOffset());
    assertTrue(ParserFixtures.offsetDateTime().isWithOffset());
  }

  @Test
  public void testTimeZoneFlag() {
    assertTrue(ParserFixtures.zonedDateTime().isWithTimeZone());
  }

  @Test
  public void testAccessorsExposeConfiguration() {
    SmartDateTimeParser<LocalDate> parser = ParserFixtures.localDate();
    assertSame(parser.getFilters(), parser.getFilters());
    assertFalse(parser.getShortcuts().isEmpty());
    assertFalse(parser.getFilters().isEmpty());
    assertFalse(parser.getErrorHandlers().isEmpty());
    assertEquals(parser.getFormatter(), FormatHelper.getLocalDateFormatter());
    assertNull(parser.getTruncate());   // LocalDate has no truncate function
  }

  @Test
  public void testNullListsBecomeEmpty() {
    SmartDateTimeParser<LocalDate> parser = new SmartDateTimeParser<>(
        LocalDate.class, LocalDate::from, null,
        FormatHelper.getLocalDateFormatter(), FormatHelper.getDatePattern(),
        null, null, null);
    assertEquals(parser.getFilters(), List.of());
    assertEquals(parser.getShortcuts(), List.of());
    assertEquals(parser.getErrorHandlers(), List.of());
  }

  @Test
  public void testConstructorNullChecks() {
    assertThrows(NullPointerException.class, () -> new SmartDateTimeParser<>(
        null, LocalDate::from, null, FormatHelper.getLocalDateFormatter(), FormatHelper.getDatePattern(), null, null, null));
    assertThrows(NullPointerException.class, () -> new SmartDateTimeParser<>(
        LocalDate.class, null, null, FormatHelper.getLocalDateFormatter(), FormatHelper.getDatePattern(), null, null, null));
    assertThrows(NullPointerException.class, () -> new SmartDateTimeParser<>(
        LocalDate.class, LocalDate::from, null, null, FormatHelper.getDatePattern(), null, null, null));
    assertThrows(NullPointerException.class, () -> new SmartDateTimeParser<>(
        LocalDate.class, LocalDate::from, null, FormatHelper.getLocalDateFormatter(), null, null, null, null));
  }

  @Test
  public void testParseNullTextReturnsNull() {
    // no text -> the holder never receives a value
    assertNull(ParserFixtures.localDate().parse(LocalDate::now, null));
  }

  @Test
  public void testParseBlankTextReachesFormatter() {
    // trimmed to empty: no shortcut matches, so the (empty) text is handed to the formatter, which fails
    assertThrows(java.time.format.DateTimeParseException.class,
                 () -> ParserFixtures.localDate().parse(LocalDate::now, "   "));
  }

  @Test
  public void testParseNullSupplierRejected() {
    assertThrows(NullPointerException.class, () -> ParserFixtures.localDate().parse(null, "10"));
  }

  @Test
  public void testFormat() {
    SmartDateTimeParser<LocalDate> parser = ParserFixtures.localDate();
    LocalDate date = LocalDate.of(2020, 5, 30);
    assertEquals(parser.format(date), FormatHelper.getLocalDateFormatter().format(date));
    assertNull(parser.format(null));
  }

  @Test
  public void testParseFullPatternRoundTrip() {
    SmartDateTimeParser<LocalDate> parser = ParserFixtures.localDate();
    LocalDate date = LocalDate.of(2020, 5, 30);
    LocalDate parsed = parser.parse(LocalDate::now, parser.format(date));
    assertEquals(parsed, date);
  }

  @Test
  public void testParseInvalidTextThrows() {
    // no shortcut matches and no handler fixes "hello" -> the parse exception propagates
    assertThrows(java.time.format.DateTimeParseException.class,
                 () -> ParserFixtures.localDate().parse(LocalDate::now, "hello"));
  }
}

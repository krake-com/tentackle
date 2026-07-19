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

import org.tentackle.misc.time.SmartDateTimeParser.TextRange;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests for the {@link SmartDateTimeParser.Shortcut} implementations in the {@code misc.time} package.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ShortcutsTest {

  // ---------------- NowShortcut ----------------

  @Test
  public void testNowShortcutTest() {
    NowShortcut shortcut = new NowShortcut();
    SmartDateTimeParser<LocalDate> parser = ParserFixtures.localDate();
    assertEquals(shortcut.test(parser, "."), new TextRange(0, 1));
    assertEquals(shortcut.test(parser, ","), new TextRange(0, 1));
    assertEquals(shortcut.test(parser, "/"), new TextRange(0, 1));
    // only the very first character is significant
    assertEquals(shortcut.test(parser, ".foo"), new TextRange(0, 1));
    assertNull(shortcut.test(parser, "x"));
    assertNull(shortcut.test(parser, ""));
  }

  @Test
  public void testNowShortcutParse() {
    NowShortcut shortcut = new NowShortcut();
    LocalDate result = shortcut.parse(ParserFixtures.localDate(), LocalDate.of(1970, 1, 1), ".");
    // "now" replaces the current value entirely with today
    assertEquals(result, LocalDate.now());
  }

  // ---------------- AddShortcut ----------------

  @Test
  public void testAddShortcutTest() {
    AddShortcut shortcut = new AddShortcut();
    SmartDateTimeParser<LocalDateTime> parser = ParserFixtures.localDateTime();
    assertEquals(shortcut.test(parser, "+10h"), new TextRange(0, 4));
    assertEquals(shortcut.test(parser, "-5"), new TextRange(0, 2));
    // no sign -> not an add shortcut
    assertNull(shortcut.test(parser, "10h"));
    assertNull(shortcut.test(parser, "abc"));
  }

  @Test
  public void testAddShortcutParseWithDateTime() {
    AddShortcut shortcut = new AddShortcut();
    SmartDateTimeParser<LocalDateTime> parser = ParserFixtures.localDateTime();
    LocalDateTime base = LocalDateTime.of(2020, 6, 15, 10, 30, 0);
    assertEquals(shortcut.parse(parser, base, "+10h"), base.plusHours(10));
    assertEquals(shortcut.parse(parser, base, "-5d"), base.minusDays(5));
    assertEquals(shortcut.parse(parser, base, "+1y"), base.plusYears(1));
    // 'M' means months when the type has a date
    assertEquals(shortcut.parse(parser, base, "+2M"), base.plusMonths(2));
    // 'm' means minutes when the type has a time
    assertEquals(shortcut.parse(parser, base, "+15m"), base.plusMinutes(15));
    assertEquals(shortcut.parse(parser, base, "+20s"), base.plusSeconds(20));
    // no unit defaults to days for a date type
    assertEquals(shortcut.parse(parser, base, "-10"), base.minusDays(10));
  }

  @Test
  public void testAddShortcutParseWithTimeOnly() {
    AddShortcut shortcut = new AddShortcut();
    SmartDateTimeParser<LocalTime> parser = ParserFixtures.localTime();
    LocalTime base = LocalTime.of(10, 30, 0);
    // no unit defaults to hours for a time-only type
    assertEquals(shortcut.parse(parser, base, "+5"), base.plusHours(5));
    // 'M' means minutes when there is no date
    assertEquals(shortcut.parse(parser, base, "+2M"), base.plusMinutes(2));
    assertEquals(shortcut.parse(parser, base, "+2m"), base.plusMinutes(2));
  }

  // ---------------- NumberShortcut ----------------

  @Test
  public void testNumberShortcutTest() {
    NumberShortcut shortcut = new NumberShortcut();
    SmartDateTimeParser<LocalDate> parser = ParserFixtures.localDate();
    assertEquals(shortcut.test(parser, "10"), new TextRange(0, 2));
    assertEquals(shortcut.test(parser, "10d"), new TextRange(0, 3));
    assertEquals(shortcut.test(parser, "031021"), new TextRange(0, 6));
    // a sign is handled by AddShortcut, not NumberShortcut
    assertNull(shortcut.test(parser, "+10"));
    assertNull(shortcut.test(parser, "10x"));
    assertNull(shortcut.test(parser, "abc"));
  }

  @Test
  public void testNumberShortcutParseTime() {
    NumberShortcut shortcut = new NumberShortcut();
    SmartDateTimeParser<LocalTime> parser = ParserFixtures.localTime();
    // HHmm
    assertEquals(shortcut.parse(parser, LocalTime.of(1, 2, 3), "1435"), LocalTime.of(14, 35, 0));
    // HHmmss
    assertEquals(shortcut.parse(parser, LocalTime.of(1, 2, 3), "143507"), LocalTime.of(14, 35, 7));
    // single hour, truncated
    assertEquals(shortcut.parse(parser, LocalTime.of(1, 2, 3), "5h"), LocalTime.of(5, 0, 0));
    // minutes keep the current hour
    assertEquals(shortcut.parse(parser, LocalTime.of(10, 0, 0), "30m"), LocalTime.of(10, 30, 0));
    // seconds keep hour and minute
    assertEquals(shortcut.parse(parser, LocalTime.of(10, 20, 0), "45s"), LocalTime.of(10, 20, 45));
  }

  @Test
  public void testNumberShortcutParseMonth() {
    NumberShortcut shortcut = new NumberShortcut();
    SmartDateTimeParser<LocalDate> parser = ParserFixtures.localDate();
    // month set, day truncated to 1, year kept
    assertEquals(shortcut.parse(parser, LocalDate.of(2020, 3, 15), "7M"), LocalDate.of(2020, 7, 1));
  }

  @Test
  public void testNumberShortcutWindowDefaults() {
    NumberShortcut shortcut = new NumberShortcut();
    assertNull(shortcut.getWindowProvider());
    // defaults when no provider
    assertEquals(shortcut.getYearWindow(), 50);
    assertEquals(shortcut.getMonthWindow(), -1);
    assertEquals(shortcut.getDayWindow(), -1);
  }

  @Test
  public void testNumberShortcutWindowProviderDelegation() {
    NumberShortcut.WindowProvider provider = new NumberShortcut.WindowProvider() {
      @Override public int getYearWindow() { return 5; }
      @Override public int getMonthWindow() { return 2; }
      @Override public int getDayWindow() { return 3; }
    };
    NumberShortcut shortcut = new NumberShortcut(provider);
    assertEquals(shortcut.getWindowProvider(), provider);
    assertEquals(shortcut.getYearWindow(), 5);
    assertEquals(shortcut.getMonthWindow(), 2);
    assertEquals(shortcut.getDayWindow(), 3);
  }

  // ---------------- OffsetShortcut ----------------

  @Test
  public void testOffsetShortcutTestOnlyWhenWithOffset() {
    OffsetShortcut shortcut = new OffsetShortcut();
    // a plain LocalTime parser has no offset -> shortcut never applies
    assertNull(shortcut.test(ParserFixtures.localTime(), "+2"));

    SmartDateTimeParser<OffsetTime> parser = ParserFixtures.offsetTime();
    assertEquals(shortcut.test(parser, "+2"), new TextRange(0, 2));
    assertEquals(shortcut.test(parser, "-2:30"), new TextRange(0, 5));
    assertEquals(shortcut.test(parser, "=+2:30"), new TextRange(0, 6));
    assertNull(shortcut.test(parser, "york"));
  }

  @Test
  public void testOffsetShortcutParseSameLocal() {
    OffsetShortcut shortcut = new OffsetShortcut();
    SmartDateTimeParser<OffsetTime> parser = ParserFixtures.offsetTime();
    OffsetTime base = OffsetTime.of(12, 0, 0, 0, ZoneOffset.ofHours(1));
    // without '=', the local time is kept and only the offset changes
    OffsetTime result = shortcut.parse(parser, base, "+2");
    assertEquals(result, base.withOffsetSameLocal(ZoneOffset.ofHours(2)));
    assertEquals(result.getHour(), 12);
  }

  @Test
  public void testOffsetShortcutParseSameInstant() {
    OffsetShortcut shortcut = new OffsetShortcut();
    SmartDateTimeParser<OffsetTime> parser = ParserFixtures.offsetTime();
    OffsetTime base = OffsetTime.of(12, 0, 0, 0, ZoneOffset.ofHours(1));
    // with '=', the instant is kept and the local time is recalculated
    OffsetTime result = shortcut.parse(parser, base, "=+2");
    assertEquals(result, base.withOffsetSameInstant(ZoneOffset.ofHours(2)));
    assertEquals(result.getHour(), 13);
  }

  // ---------------- ZoneShortcut ----------------

  @Test
  public void testZoneShortcutTestOnlyWhenWithTimeZone() {
    ZoneShortcut shortcut = new ZoneShortcut();
    // an OffsetDateTime parser has an offset but no zone -> shortcut never applies
    assertNull(shortcut.test(ParserFixtures.offsetDateTime(), "berlin"));

    SmartDateTimeParser<ZonedDateTime> parser = ParserFixtures.zonedDateTime();
    assertEquals(shortcut.test(parser, "berlin"), new TextRange(0, 6));
    assertEquals(shortcut.test(parser, "=paris"), new TextRange(0, 6));
    // whitespace is not a word char
    assertNull(shortcut.test(parser, "a b"));
  }

  @Test
  public void testZoneShortcutParseSameLocal() {
    ZoneShortcut shortcut = new ZoneShortcut();
    SmartDateTimeParser<ZonedDateTime> parser = ParserFixtures.zonedDateTime();
    ZonedDateTime base = ZonedDateTime.of(2020, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    ZonedDateTime result = shortcut.parse(parser, base, "Europe/Berlin");
    // same local time, different zone
    assertEquals(result.getZone().getId(), "Europe/Berlin");
    assertEquals(result.getHour(), 12);
  }

  @Test
  public void testZoneShortcutParseSameInstant() {
    ZoneShortcut shortcut = new ZoneShortcut();
    SmartDateTimeParser<ZonedDateTime> parser = ParserFixtures.zonedDateTime();
    ZonedDateTime base = ZonedDateTime.of(2020, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    ZonedDateTime result = shortcut.parse(parser, base, "=Europe/Berlin");
    // same instant -> local time shifts to the target zone (UTC+2 in summer)
    assertEquals(result.getZone().getId(), "Europe/Berlin");
    assertEquals(result.toInstant(), base.toInstant());
    assertEquals(result.getHour(), 14);
  }

  @Test
  public void testZoneShortcutParseIgnoresNonZonedValue() {
    ZoneShortcut shortcut = new ZoneShortcut();
    // parse leaves non-ZonedDateTime values untouched
    LocalDateTime base = LocalDateTime.of(2020, 6, 15, 12, 0);
    OffsetDateTime baseOffset = OffsetDateTime.of(base, ZoneOffset.UTC);
    assertEquals(shortcut.parse(ParserFixtures.offsetDateTime(), baseOffset, "berlin"), baseOffset);
  }
}

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

import static org.testng.Assert.assertEquals;

/**
 * Tests for the {@link SmartDateTimeParser.TextFilter} implementations in the {@code misc.time} package.
 * <p>
 * The tests run with the US locale (enforced by the surefire configuration).
 */
@SuppressWarnings("missing-explicit-ctor")
public class FiltersTest {

  // ---------------- DateFilter ----------------

  @Test
  public void testDateFilterPacksSeparatedDate() {
    DateFilter filter = new DateFilter();
    // separators removed, each part padded to an even number of digits
    assertEquals(filter.apply(ParserFixtures.localDate(), "5/30/21"), "053021");
    assertEquals(filter.apply(ParserFixtures.localDate(), "5/30"), "0530");
  }

  @Test
  public void testDateFilterLeavesCanonicalDateUntouched() {
    DateFilter filter = new DateFilter();
    // a fully formatted date has exactly the pattern length -> left unchanged
    String canonical = FormatHelper.getLocalDateFormatter().format(LocalDate.of(2021, 5, 30));
    assertEquals(filter.apply(ParserFixtures.localDate(), canonical), canonical);
  }

  @Test
  public void testDateFilterLeavesShortcutLikeInputUntouched() {
    DateFilter filter = new DateFilter();
    // a letter within the first word (e.g. an add/number shortcut) is left to the shortcuts
    assertEquals(filter.apply(ParserFixtures.localDate(), "10d"), "10d");
    // fewer than 3 characters is left alone
    assertEquals(filter.apply(ParserFixtures.localDate(), "12"), "12");
  }

  @Test
  public void testDateFilterIgnoredWithoutDate() {
    DateFilter filter = new DateFilter();
    // a time-only parser is not a date -> untouched
    assertEquals(filter.apply(ParserFixtures.localTime(), "5/30/21"), "5/30/21");
  }

  // ---------------- OffsetFilter ----------------

  @Test
  public void testOffsetFilterInsertsLeadingZeroAndMinutes() {
    OffsetFilter filter = new OffsetFilter();
    assertEquals(filter.apply(ParserFixtures.offsetTime(), "12:00:00 +2:30"), "12:00:00 +02:30");
    assertEquals(filter.apply(ParserFixtures.offsetTime(), "12:00:00 +2"), "12:00:00 +02:00");
  }

  @Test
  public void testOffsetFilterLeavesCompleteOffsetUntouched() {
    OffsetFilter filter = new OffsetFilter();
    assertEquals(filter.apply(ParserFixtures.offsetTime(), "12:00:00 +02:30"), "12:00:00 +02:30");
  }

  @Test
  public void testOffsetFilterIgnoredWithoutOffset() {
    OffsetFilter filter = new OffsetFilter();
    assertEquals(filter.apply(ParserFixtures.localTime(), "12:00:00 +2"), "12:00:00 +2");
  }

  // ---------------- ZoneFilter ----------------

  @Test
  public void testZoneFilterExpandsPartialZoneName() {
    ZoneFilter filter = new ZoneFilter();
    assertEquals(filter.apply(ParserFixtures.zonedDateTime(), "12:00 berlin"), "12:00 Europe/Berlin");
  }

  @Test
  public void testZoneFilterLeavesNonLetterTokenUntouched() {
    ZoneFilter filter = new ZoneFilter();
    // the trailing token must start with a letter to be treated as a zone
    assertEquals(filter.apply(ParserFixtures.zonedDateTime(), "12:00 123"), "12:00 123");
  }

  @Test
  public void testZoneFilterIgnoredWithoutTimeZone() {
    ZoneFilter filter = new ZoneFilter();
    assertEquals(filter.apply(ParserFixtures.offsetDateTime(), "12:00 berlin"), "12:00 berlin");
  }

  @Test
  public void testZoneFilterNeedsTwoTokens() {
    ZoneFilter filter = new ZoneFilter();
    // a single token does not match the "<something> <zone>" pattern
    assertEquals(filter.apply(ParserFixtures.zonedDateTime(), "berlin"), "berlin");
  }
}

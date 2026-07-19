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

import org.testng.annotations.Test;

import java.time.format.DateTimeParseException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for the {@link SmartDateTimeParser.ErrorHandler} implementations in the {@code misc.time} package.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ErrorHandlersTest {

  private static DateTimeParseException ex(String text, int errorIndex) {
    return new DateTimeParseException("test", text, errorIndex);
  }

  // ---------------- MissingTimeHandler ----------------

  @Test
  public void testMissingTimeAppendsMidnightWhenOnlyDate() {
    MissingTimeHandler handler = new MissingTimeHandler();
    String text = "05/30/2020";
    // date-only input for a date+time type -> append midnight (with seconds, as the pattern has them)
    assertEquals(handler.apply(ParserFixtures.localDateTime(), ex(text, text.length()), text),
                 "05/30/2020 00:00:00");
  }

  @Test
  public void testMissingTimeExpandsPartialTime() {
    MissingTimeHandler handler = new MissingTimeHandler();
    String text = "05/30/2020 915";
    assertEquals(handler.apply(ParserFixtures.localDateTime(), ex(text, text.length()), text),
                 "05/30/2020 09:15:00");
  }

  @Test
  public void testMissingTimeIgnoredWithoutTime() {
    MissingTimeHandler handler = new MissingTimeHandler();
    String text = "05/30/2020";
    // a date-only type has no time to add
    assertNull(handler.apply(ParserFixtures.localDate(), ex(text, text.length()), text));
  }

  @Test
  public void testMissingTimeReturnsNullWhenNothingToFix() {
    MissingTimeHandler handler = new MissingTimeHandler();
    // already a complete time -> the produced string equals the input -> null
    String text = "05/30/2020 09:15:00";
    assertNull(handler.apply(ParserFixtures.localDateTime(), ex(text, text.length()), text));
  }

  // ---------------- MissingOffsetHandler ----------------

  @Test
  public void testMissingOffsetAppendsDefaultOffset() {
    MissingOffsetHandler handler = new MissingOffsetHandler();
    String text = "12:00:00";
    String result = handler.apply(ParserFixtures.offsetTime(), ex(text, text.length()), text);
    // a default offset is appended after a space
    assertTrue(result.startsWith(text + " "), "unexpected result: " + result);
    assertTrue(result.length() > text.length() + 1);
  }

  @Test
  public void testMissingOffsetRequiresErrorAtEnd() {
    MissingOffsetHandler handler = new MissingOffsetHandler();
    String text = "12:00:00";
    // the error must be at the end of the text
    assertNull(handler.apply(ParserFixtures.offsetTime(), ex(text, 0), text));
  }

  @Test
  public void testMissingOffsetIgnoredWithoutOffset() {
    MissingOffsetHandler handler = new MissingOffsetHandler();
    String text = "12:00:00";
    assertNull(handler.apply(ParserFixtures.localTime(), ex(text, text.length()), text));
  }

  @Test
  public void testMissingOffsetWrongTokenCount() {
    MissingOffsetHandler handler = new MissingOffsetHandler();
    // an OffsetTime expects a single token; two tokens do not qualify
    String text = "12:00:00 extra";
    assertNull(handler.apply(ParserFixtures.offsetTime(), ex(text, text.length()), text));
  }

  // ---------------- MissingZoneIdHandler ----------------

  @Test
  public void testMissingZoneIdAppendsDefaultZone() {
    MissingZoneIdHandler handler = new MissingZoneIdHandler();
    String text = "05/30/2020 15:30:00";
    String result = handler.apply(ParserFixtures.zonedDateTime(), ex(text, text.length()), text);
    assertTrue(result.startsWith(text + " "), "unexpected result: " + result);
    assertTrue(result.length() > text.length() + 1);
  }

  @Test
  public void testMissingZoneIdRequiresErrorAtEnd() {
    MissingZoneIdHandler handler = new MissingZoneIdHandler();
    String text = "05/30/2020 15:30:00";
    assertNull(handler.apply(ParserFixtures.zonedDateTime(), ex(text, 0), text));
  }

  @Test
  public void testMissingZoneIdIgnoredWithoutTimeZone() {
    MissingZoneIdHandler handler = new MissingZoneIdHandler();
    String text = "05/30/2020 15:30:00";
    assertNull(handler.apply(ParserFixtures.offsetDateTime(), ex(text, text.length()), text));
  }

  @Test
  public void testMissingZoneIdWrongTokenCount() {
    MissingZoneIdHandler handler = new MissingZoneIdHandler();
    // a ZonedDateTime expects exactly two tokens (date + time)
    String text = "05/30/2020";
    assertNull(handler.apply(ParserFixtures.zonedDateTime(), ex(text, text.length()), text));
  }
}

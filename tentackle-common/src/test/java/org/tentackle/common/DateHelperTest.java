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
 */

package org.tentackle.common;

import org.testng.annotations.Test;

import java.util.Calendar;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertThrows;

/**
 * Test for DateHelper.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DateHelperTest {

  @Test
  public void testDeriveCentury() {
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(13, 2013, 50), 2013);
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(1, 2013, 50), 2001);
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(13, 1980, 50), 2013);
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(13, 2080, 50), 2113);
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(13, 2080, 30), 2013);
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(50, 2013, 50), 2050);
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(50, 2013, 30), 1950);
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(70, 2013, 50), 1970);
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(2010, 2013, 50), 2010);
    assertEquals(DateHelper.derive4DigitYearFrom2DigitYear(-10, 2013, 50), 1990);
  }

  @Test
  public void testDeriveYear() {
    assertEquals(DateHelper.deriveYearFromMonth(11, 10, 2020,3),2020);
    assertEquals(DateHelper.deriveYearFromMonth(11, 1, 2020,3),2019);
    assertEquals(DateHelper.deriveYearFromMonth(1, 11, 2020,3),2021);
    assertEquals(DateHelper.deriveYearFromMonth(1, 10, 2020,3),2020);
    // a window outside 0..11 always returns the current year
    assertEquals(DateHelper.deriveYearFromMonth(11, 1, 2020, 20), 2020);
  }

  @Test
  public void testDeriveMonthFromDay() {
    // within the window -> current month
    assertEquals(DateHelper.deriveMonthFromDay(20, 18, 5, 15), 5);
    // far ahead in the month -> previous month
    assertEquals(DateHelper.deriveMonthFromDay(30, 5, 5, 15), 4);
    // far behind in the month -> next month
    assertEquals(DateHelper.deriveMonthFromDay(2, 28, 5, 15), 6);
    // a window outside 0..30 always returns the current month
    assertEquals(DateHelper.deriveMonthFromDay(30, 5, 5, 40), 5);
  }

  @Test
  public void testNullSafeConversions() {
    assertNull(DateHelper.toDate((java.util.Date) null));
    assertNull(DateHelper.toDate((Calendar) null));
    assertNull(DateHelper.toTime((java.util.Date) null));
    assertNull(DateHelper.toTimestamp((java.util.Date) null));
    assertEquals(DateHelper.toMillis(null), 0L);

    java.util.Date util = new java.util.Date(123456789L);
    assertEquals(DateHelper.toMillis(util), 123456789L);
    assertEquals(DateHelper.toDate(util).getTime(), 123456789L);
    assertEquals(DateHelper.toTimestamp(util).getTime(), 123456789L);
  }

  @Test
  public void testSetMidnight() {
    Calendar cal = DateHelper.today();
    cal.set(Calendar.HOUR_OF_DAY, 13);
    cal.set(Calendar.MINUTE, 45);
    cal.set(Calendar.SECOND, 30);
    cal.set(Calendar.MILLISECOND, 500);
    DateHelper.setMidnight(cal);
    assertEquals(cal.get(Calendar.HOUR_OF_DAY), 0);
    assertEquals(cal.get(Calendar.MINUTE), 0);
    assertEquals(cal.get(Calendar.SECOND), 0);
    assertEquals(cal.get(Calendar.MILLISECOND), 0);
  }

  @Test
  public void testCurrentFields() {
    Calendar now = DateHelper.today();
    assertEquals(DateHelper.getCurrentYear(), now.get(Calendar.YEAR));
    // month and day are read via separate calendars, allow the value to be plausible
    assertTrue(DateHelper.getCurrentMonth() >= 0 && DateHelper.getCurrentMonth() <= 11);
    assertTrue(DateHelper.getCurrentDay() >= 1 && DateHelper.getCurrentDay() <= 31);
  }

  @Test
  public void testDeriveMonthFromDayAt31() {
    // day 31 must not wrap to 0
    assertEquals(DateHelper.deriveMonthFromDay(31, 31, 5, 15), 5);
    // 21 days ahead -> previous month
    assertEquals(DateHelper.deriveMonthFromDay(31, 10, 5, 15), 4);
    // out of range days return the current month
    assertEquals(DateHelper.deriveMonthFromDay(0, 10, 5, 15), 5);
    assertEquals(DateHelper.deriveMonthFromDay(32, 10, 5, 15), 5);
  }

  @Test
  public void testMinValuesAreFrozen() {
    assertTrue(DateHelper.MIN_DATE.isFrozen());
    assertTrue(DateHelper.MIN_TIMESTAMP.isFrozen());
    assertEquals(DateHelper.MIN_DATE.getTime(), 0L);
    assertEquals(DateHelper.MIN_TIMESTAMP.getTime(), 0L);
    assertThrows(TentackleRuntimeException.class, () -> DateHelper.MIN_DATE.setTime(1000L));
    assertThrows(TentackleRuntimeException.class, () -> DateHelper.MIN_TIMESTAMP.setTime(1000L));
  }

}

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

import static org.testng.Assert.assertEquals;

/**
 * Test for DateHelper.
 *
 * @author harald
 */
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
  }

}
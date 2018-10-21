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

package org.tentackle.misc;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test for DateHelper.
 *
 * @author harald
 */
public class DateHelperTest {

  @Test
  public void testconvert2DigitYearTo4DigitYear() {
    Assert.assertEquals(DateHelper.convert2DigitYearTo4DigitYear(13, 2013), 2013);
    Assert.assertEquals(DateHelper.convert2DigitYearTo4DigitYear(1, 2013), 2001);
    Assert.assertEquals(DateHelper.convert2DigitYearTo4DigitYear(13, 1980), 2013);
    Assert.assertEquals(DateHelper.convert2DigitYearTo4DigitYear(13, 2080), 2113);
    Assert.assertEquals(DateHelper.convert2DigitYearTo4DigitYear(50, 2013), 2050);
    Assert.assertEquals(DateHelper.convert2DigitYearTo4DigitYear(70, 2013), 1970);
    Assert.assertEquals(DateHelper.convert2DigitYearTo4DigitYear(2010, 2013), 2010);
    Assert.assertEquals(DateHelper.convert2DigitYearTo4DigitYear(-10, 2013), 1990);
  }

}
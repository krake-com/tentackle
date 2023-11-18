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

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests the SubString.
 */
@SuppressWarnings("missing-explicit-ctor")
public class SubStringTest {

  private static final String LOWER = "aaabbbcccdddcccddd";
  private static final String UPPER = "AAABBBCCCDDDCCCDDD";

  @Test
  public void startsWith() {
    Assert.assertTrue(new SubString(LOWER, 0, 6).startsWith(new SubString(LOWER, 0, 3)));
    Assert.assertFalse(new SubString(LOWER, 0, 6).startsWith(new SubString(UPPER, 0, 3)));
    Assert.assertTrue(new SubString(LOWER, 0, 6).startsWithIgnoreCase(new SubString(UPPER, 0, 3)));
  }

  @Test
  public void endsWith() {
    Assert.assertTrue(new SubString(LOWER).endsWith(new SubString(LOWER, 9)));
    Assert.assertFalse(new SubString(LOWER).endsWith(new SubString(UPPER, 9)));
    Assert.assertTrue(new SubString(LOWER).endsWithIgnoreCase(new SubString(UPPER, 9)));
  }

  @Test
  public void indexOf() {
    Assert.assertEquals(new SubString(LOWER, 3).indexOf(new SubString(LOWER, 9)), 6);
    Assert.assertEquals(new SubString(LOWER, 3).indexOf(new SubString(UPPER, 9)), -1);
    Assert.assertEquals(new SubString(LOWER).indexOfIgnoreCase(new SubString(UPPER, 9)), 9);
    Assert.assertEquals(new SubString(LOWER).indexOfIgnoreCase(new SubString(UPPER)), 0);
    Assert.assertEquals(new SubString(LOWER, 1).indexOfIgnoreCase(new SubString(UPPER)), -1);
  }

  @Test
  public void lastIndexOf() {
    Assert.assertEquals(new SubString(LOWER, 3).lastIndexOf(new SubString(LOWER, 12, 15)), 9);
    Assert.assertEquals(new SubString(LOWER, 3).lastIndexOf(new SubString(UPPER, 12, 15)), -1);
    Assert.assertEquals(new SubString(LOWER, 3).lastIndexOfIgnoreCase(new SubString(UPPER, 12, 15)), 9);
  }

  @Test
  public void equalsSegment() {
    Assert.assertTrue(new SubString(LOWER, 6, 9).equalsSegment(new SubString(LOWER, 12, 15)));
    Assert.assertFalse(new SubString(LOWER, 6, 9).equalsSegment(new SubString(UPPER, 12, 15)));
    Assert.assertTrue(new SubString(LOWER, 6, 9).equalsSegmentIgnoreCase(new SubString(UPPER, 12, 15)));
  }
}

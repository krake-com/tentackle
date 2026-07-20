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

package org.tentackle.common;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the null-safe {@link Compare} methods.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class CompareTest {

  @Test
  public void testCompareNullSmallest() {
    // both null -> equal
    assertEquals(Compare.compare(null, null), 0);
    // null is the smallest value
    assertTrue(Compare.compare(null, "a") < 0);
    assertTrue(Compare.compare("a", null) > 0);
    // ordinary comparison
    assertTrue(Compare.compare("a", "b") < 0);
    assertTrue(Compare.compare("b", "a") > 0);
    assertEquals(Compare.compare("a", "a"), 0);
    assertTrue(Compare.compare(1, 2) < 0);
  }

  @Test
  public void testCompareWithNullLargest() {
    // both null -> equal
    assertEquals(Compare.compareWithNullLargest(null, null), 0);
    // null is the largest value (opposite of compare())
    assertTrue(Compare.compareWithNullLargest(null, "a") > 0);
    assertTrue(Compare.compareWithNullLargest("a", null) < 0);
    // ordinary comparison stays the same
    assertTrue(Compare.compareWithNullLargest("a", "b") < 0);
    assertTrue(Compare.compareWithNullLargest("b", "a") > 0);
    assertEquals(Compare.compareWithNullLargest("a", "a"), 0);
  }

}

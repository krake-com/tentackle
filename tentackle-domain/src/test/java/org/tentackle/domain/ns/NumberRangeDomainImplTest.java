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

package org.tentackle.domain.ns;

import org.testng.annotations.Test;

import org.tentackle.ns.NumberSource.Range;
import org.tentackle.ns.NumberSourceEmptyException;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.Pdo;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link NumberRangeDomainImpl}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class NumberRangeDomainImplTest {

  /**
   * Creates a number range [begin,end[.
   *
   * @param begin the begin of the range
   * @param end the end of the range
   * @return the range's PDO
   */
  static NumberRange createRange(long begin, long end) {
    NumberRange range = Pdo.create(NumberRange.class);
    range.setBegin(begin);
    range.setEnd(end);
    return range;
  }


  @Test
  public void testFactoryWiresTheDomainDelegate() {
    assertEquals(createRange(0, 0).getDomainDelegate().getClass(), NumberRangeDomainImpl.class);
  }

  @Test
  public void testSize() {
    assertEquals(createRange(10, 20).size(), 10);
    assertEquals(createRange(10, 11).size(), 1);
    assertEquals(createRange(10, 10).size(), 0);
  }

  @Test
  public void testSizeOfInvertedRangeIsZero() {
    // must never become negative, no matter how the range got corrupted
    assertEquals(createRange(20, 10).size(), 0);
  }

  @Test
  public void testIsEmpty() {
    assertFalse(createRange(10, 20).isEmpty());
    assertTrue(createRange(10, 10).isEmpty());
    assertTrue(createRange(20, 10).isEmpty());
  }

  @Test
  public void testPopNumber() {
    NumberRange range = createRange(10, 13);
    assertEquals(range.popNumber(), 10);
    assertEquals(range.getBegin(), 11);
    assertEquals(range.size(), 2);
    assertEquals(range.popNumber(), 11);
    assertEquals(range.popNumber(), 12);
    assertTrue(range.isEmpty());
  }

  @Test
  public void testPopNumberFromEmptyRange() {
    NumberRange range = createRange(10, 10);
    NumberSourceEmptyException nex = expectThrows(NumberSourceEmptyException.class, range::popNumber);
    assertTrue(nex.getMessage().contains("is empty"), nex.getMessage());
  }

  @Test
  public void testPopNumbers() {
    NumberRange range = createRange(100, 110);
    assertEquals(range.popNumbers(4), new Range(100, 104));
    assertEquals(range.getBegin(), 104);
    assertEquals(range.size(), 6);
  }

  @Test
  public void testPopNumbersIsLimitedToTheRangeSize() {
    NumberRange range = createRange(100, 110);
    assertEquals(range.popNumbers(1000), new Range(100, 110));
    assertTrue(range.isEmpty());
  }

  @Test
  public void testPopNumbersOfWholeRange() {
    NumberRange range = createRange(100, 110);
    assertEquals(range.popNumbers(10), new Range(100, 110));
    assertTrue(range.isEmpty());
  }

  @Test
  public void testPopNumbersFromEmptyRange() {
    NumberRange range = createRange(10, 10);
    expectThrows(NumberSourceEmptyException.class, () -> range.popNumbers(1));
  }

  @Test
  public void testIntersects() {
    NumberRange range = createRange(10, 20);
    assertTrue(range.intersects(10, 20));     // identical
    assertTrue(range.intersects(0, 100));     // enclosing
    assertTrue(range.intersects(12, 15));     // enclosed
    assertTrue(range.intersects(5, 11));      // overlapping at the begin
    assertTrue(range.intersects(19, 25));     // overlapping at the end
  }

  @Test
  public void testDoesNotIntersect() {
    NumberRange range = createRange(10, 20);
    assertFalse(range.intersects(0, 10));     // ends where the range begins
    assertFalse(range.intersects(20, 30));    // begins where the range ends
    assertFalse(range.intersects(0, 5));
    assertFalse(range.intersects(25, 30));
  }

  @Test
  public void testToStringWithoutPool() {
    assertEquals(createRange(10, 20).toString(), "null[10,20]");
  }

  @Test
  public void testToStringWithPool() {
    NumberPool pool = Pdo.create(NumberPool.class);
    pool.setName("invoice");
    NumberRange range = createRange(1, 5);
    range.setNumberPool(pool);
    assertEquals(range.toString(), "invoice[1,5]");
  }

  @Test
  public void testSingularAndPlural() {
    NumberRange range = createRange(10, 20);
    assertEquals(range.getSingular(), "number range");
    assertEquals(range.getPlural(), "number ranges");
  }

}

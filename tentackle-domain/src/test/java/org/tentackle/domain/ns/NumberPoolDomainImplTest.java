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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.misc.TrackedList;
import org.tentackle.ns.NumberSourceEmptyException;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.Pdo;

import static org.tentackle.domain.ns.NumberRangeDomainImplTest.createRange;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link NumberPoolDomainImpl}.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class NumberPoolDomainImplTest {

  private NumberPool pool;
  private TrackedList<NumberRange> ranges;

  @BeforeMethod
  public void setUp() {
    pool = Pdo.create(NumberPool.class);
    pool.setName("invoice");
    ranges = pool.getNumberRangeList();
  }

  /**
   * Creates a number range [begin,end[ and appends it to the pool's range list.
   *
   * @param begin the begin of the range
   * @param end the end of the range
   * @return the range's PDO
   */
  private NumberRange addRange(long begin, long end) {
    NumberRange range = createRange(begin, end);
    range.setNumberPool(pool);
    ranges.add(range);
    return range;
  }


  @Test
  public void testFactoryWiresTheDomainDelegate() {
    assertEquals(pool.getDomainDelegate().getClass(), NumberPoolDomainImpl.class);
  }

  @Test
  public void testToString() {
    assertEquals(pool.toString(), "invoice");
  }

  @Test
  public void testToStringWithRealm() {
    pool.setRealm("acme");
    assertEquals(pool.toString(), "invoice:acme");
  }

  @Test
  public void testIsSlave() {
    pool.setLowWaterMark(100);
    pool.setRequestSize(1000);
    assertTrue(pool.isSlave());
  }

  @Test
  public void testIsNoSlaveWithoutLowWaterMark() {
    pool.setRequestSize(1000);
    assertFalse(pool.isSlave());
  }

  @Test
  public void testIsNoSlaveWithoutRequestSize() {
    pool.setLowWaterMark(100);
    assertFalse(pool.isSlave());
  }

  @Test
  public void testIsNoSlaveByDefault() {
    assertFalse(pool.isSlave());
  }

  @Test
  public void testGetCurrentRangeReturnsTheOnlyRange() {
    NumberRange range = addRange(10, 20);
    assertSame(pool.getCurrentRange(), range);
    assertEquals(ranges.size(), 1);
  }

  @Test
  public void testGetCurrentRangeReturnsTheLowestRange() {
    addRange(300, 400);
    NumberRange lowest = addRange(100, 200);
    addRange(500, 600);
    assertSame(pool.getCurrentRange(), lowest);
    assertEquals(ranges.size(), 3);
  }

  @Test
  public void testGetCurrentRangeRemovesEmptyRanges() {
    addRange(10, 10);
    NumberRange usable = addRange(100, 200);
    addRange(50, 50);
    assertSame(pool.getCurrentRange(), usable);
    assertEquals(ranges.size(), 1);
    assertSame(ranges.get(0), usable);
  }

  @Test
  public void testGetCurrentRangeOfEmptyPool() {
    NumberSourceEmptyException nex = expectThrows(NumberSourceEmptyException.class, pool::getCurrentRange);
    assertEquals(nex.getMessage(), "pool invoice is empty");
  }

  @Test
  public void testGetCurrentRangeDropsAllExhaustedRanges() {
    addRange(10, 10);
    addRange(20, 20);
    expectThrows(NumberSourceEmptyException.class, pool::getCurrentRange);
    assertTrue(ranges.isEmpty());
  }

  @Test
  public void testSingularAndPlural() {
    assertEquals(pool.getSingular(), "number pool");
    assertEquals(pool.getPlural(), "number pools");
  }

}

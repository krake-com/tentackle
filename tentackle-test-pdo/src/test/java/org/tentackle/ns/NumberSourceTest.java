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
 *
 */

package org.tentackle.ns;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.PdoMember;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.testng.AbstractPdoTest;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Number-source tests.
 *
 * @author harald
 */
public class NumberSourceTest extends AbstractPdoTest {

  private static final String POOL_NAME = "test";
  private static final String POOL_REALM = "00_01";

  @Test(groups = "local")
  public void printMembers() {
    Reporter.log("NumberSourceTest.printMembers <br>", true);
    for (PdoMember member: PdoUtilities.getInstance().getMembers(NumberPool.class)) {
      Reporter.log(member + "<br>");
    }
  }

  @Test(groups = "local")
  public void createAndRetrieveNumbers() {
    Reporter.log("NumberSourceTest.createAndRetrieveNumbers <br>", true);
    // create pool
    NumberPool numberPool = on(NumberPool.class);
    numberPool.setName(POOL_NAME);
    numberPool.setRealm(POOL_REALM);
    numberPool.setOnline(true);
    NumberRange numberRange = numberPool.on(NumberRange.class);
    numberRange.setBegin(2000);
    numberRange.setEnd(3000);
    numberPool.getNumberRangeList().add(numberRange);
    numberRange = numberRange.on();
    numberRange.setBegin(1);
    numberRange.setEnd(1000);   // 1 - 999
    numberPool.getNumberRangeList().add(numberRange);
    numberRange = numberRange.on();
    numberRange.setBegin(5000);
    numberRange.setEnd(6000);
    numberPool.getNumberRangeList().add(numberRange);
    numberPool.save();


    // retrieve numbers from range
    NumberSource ns = NumberSourceFactory.getInstance().getNumberSource(POOL_NAME, POOL_REALM);
    NumberSource.Range[] ranges = ns.getRanges();
    assertEquals(ranges.length, 3);
    NumberSource.Range range = ranges[0];
    assertEquals(range.begin(), 2000);
    assertEquals(range.end(), 3000);
    range = ranges[1];
    assertEquals(range.begin(), 1);
    assertEquals(range.end(), 1000);
    range = ranges[2];
    assertEquals(range.begin(), 5000);
    assertEquals(range.end(), 6000);

    long number = 0;
    for (int i=0; i < 500; i++) {
      number = ns.popNumber();
    }
    assertEquals(number, 500);
    ranges = ns.popNumbers(700);
    assertEquals(ranges.length, 2);
    range = ranges[0];
    assertEquals(range.begin(), 501);
    assertEquals(range.end(), 1000);
    range = ranges[1];
    assertEquals(range.begin(), 2000);
    assertEquals(range.end(), 2201);

    ranges = ns.getRanges();
    assertEquals(ranges.length, 2);
    range = ranges[0];
    assertEquals(range.begin(), 2201);
    assertEquals(range.end(), 3000);
    range = ranges[1];
    assertEquals(range.begin(), 5000);
    assertEquals(range.end(), 6000);

    ns.popNumbers(5500, 5600);
    ranges = ns.getRanges();
    assertEquals(ranges.length, 3);
    range = ranges[0];
    assertEquals(range.begin(), 2201);
    assertEquals(range.end(), 3000);
    range = ranges[1];
    assertEquals(range.begin(), 5000);
    assertEquals(range.end(), 5500);
    range = ranges[2];
    assertEquals(range.begin(), 5600);
    assertEquals(range.end(), 6000);

    ns.pushNumbers(2500, 5900);
    ranges = ns.popNumbers(1000, 7000);
    assertEquals(ranges.length, 1);
    range = ranges[0];
    assertEquals(range.begin(), 2201);
    assertEquals(range.end(), 6000);
    assertTrue(ns.isEmpty());
  }

  /**
   * Creates and saves a pool.
   *
   * @param name the pool name
   * @param online true if online
   * @param ranges the ranges as begin/end pairs
   * @return the saved pool
   */
  private NumberPool createPool(String name, boolean online, long... ranges) {
    NumberPool numberPool = on(NumberPool.class);
    numberPool.setName(name);
    numberPool.setRealm(POOL_REALM);
    numberPool.setOnline(online);
    for (int i = 0; i < ranges.length; i += 2) {
      NumberRange numberRange = numberPool.on(NumberRange.class);
      numberRange.setBegin(ranges[i]);
      numberRange.setEnd(ranges[i + 1]);
      numberPool.getNumberRangeList().add(numberRange);
    }
    return numberPool.persist();
  }

  @Test(groups = "local")
  public void offlinePoolIsNotOnline() {
    createPool("offline", false, 1, 10);
    NumberSource ns = NumberSourceFactory.getInstance().getNumberSource("offline", POOL_REALM);
    assertFalse(ns.isOnline());
    expectThrows(NumberSourceException.class, ns::popNumber);
  }

  @Test(groups = "local")
  public void rangesWithTheSameBeginAreMerged() {
    createPool("samebegin", true, 10, 20, 5, 8);
    NumberSource ns = NumberSourceFactory.getInstance().getNumberSource("samebegin", POOL_REALM);
    // extends [10,20) to [5,20), which now starts with the same number as [5,8)
    ns.pushNumbers(5, 15);
    NumberSource.Range[] ranges = ns.getRanges();
    assertEquals(ranges.length, 1);
    assertEquals(ranges[0].begin(), 5);
    assertEquals(ranges[0].end(), 20);
    assertEquals(ns.getCount(), 15);    // each number exactly once
  }

  @Test(groups = "local")
  public void adjacentRangesAreMerged() {
    createPool("adjacent", true, 1, 5, 6, 10);
    NumberSource ns = NumberSourceFactory.getInstance().getNumberSource("adjacent", POOL_REALM);
    ns.pushNumber(5);   // [1,6) now touches [6,10)
    NumberSource.Range[] ranges = ns.getRanges();
    assertEquals(ranges.length, 1);
    assertEquals(ranges[0].begin(), 1);
    assertEquals(ranges[0].end(), 10);
  }

}

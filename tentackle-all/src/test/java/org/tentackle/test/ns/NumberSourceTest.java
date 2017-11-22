/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.test.ns;

import org.tentackle.ns.NumberSource;
import org.tentackle.ns.NumberSourceFactory;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.test.pdo.AbstractPdoTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Number source tests.
 *
 * @author harald
 */
public class NumberSourceTest extends AbstractPdoTest {

  private static final String POOL_NAME = "test";
  private static final String POOL_REALM = "00_01";


  @Test
  public void createAndRetrieveNumbers() {

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
    assertEquals(range.getBegin(), 2000);
    assertEquals(range.getEnd(), 3000);
    range = ranges[1];
    assertEquals(range.getBegin(), 1);
    assertEquals(range.getEnd(), 1000);
    range = ranges[2];
    assertEquals(range.getBegin(), 5000);
    assertEquals(range.getEnd(), 6000);

    long number = 0;
    for (int i=0; i < 500; i++) {
      number = ns.popNumber();
    }
    assertEquals(number, 500);
    ranges = ns.popNumbers(700);
    assertEquals(ranges.length, 2);
    range = ranges[0];
    assertEquals(range.getBegin(), 501);
    assertEquals(range.getEnd(), 1000);
    range = ranges[1];
    assertEquals(range.getBegin(), 2000);
    assertEquals(range.getEnd(), 2201);

    ranges = ns.getRanges();
    assertEquals(ranges.length, 2);
    range = ranges[0];
    assertEquals(range.getBegin(), 2201);
    assertEquals(range.getEnd(), 3000);
    range = ranges[1];
    assertEquals(range.getBegin(), 5000);
    assertEquals(range.getEnd(), 6000);

    ns.popNumbers(5500, 5600);
    ranges = ns.getRanges();
    assertEquals(ranges.length, 3);
    range = ranges[0];
    assertEquals(range.getBegin(), 2201);
    assertEquals(range.getEnd(), 3000);
    range = ranges[1];
    assertEquals(range.getBegin(), 5000);
    assertEquals(range.getEnd(), 5500);
    range = ranges[2];
    assertEquals(range.getBegin(), 5600);
    assertEquals(range.getEnd(), 6000);

    ns.pushNumbers(2500, 5900);
    ranges = ns.popNumbers(1000, 7000);
    assertEquals(ranges.length, 1);
    range = ranges[0];
    assertEquals(range.getBegin(), 2201);
    assertEquals(range.getEnd(), 6000);
    assertTrue(ns.isEmpty());
  }

}

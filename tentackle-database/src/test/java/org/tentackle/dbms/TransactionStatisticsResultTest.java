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

package org.tentackle.dbms;

import org.testng.annotations.Test;

import org.tentackle.misc.TimeKeeper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link TransactionStatisticsResult}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TransactionStatisticsResultTest {

  @Test
  public void testInitialState() {
    TransactionStatisticsResult result = new TransactionStatisticsResult();
    assertEquals(result.getCount(), 0);
    assertFalse(result.isValid());
    assertEquals(result.getRollbackCount(), 0);
    assertEquals(result.getTotalStatements(), 0);
    assertEquals(result.getMinStatements(), Long.MAX_VALUE);
    assertEquals(result.getMaxStatements(), Long.MIN_VALUE);
    assertEquals(result.toString(), "<invalid>");
  }

  @Test
  public void testCountRollback() {
    TransactionStatisticsResult result = new TransactionStatisticsResult();
    result.countRollback();
    result.countRollback();
    assertEquals(result.getRollbackCount(), 2);
  }

  @Test
  public void testCountStatementsTracksMinMaxTotal() {
    TransactionStatisticsResult result = new TransactionStatisticsResult();
    result.countStatements(5);
    result.countStatements(3);
    result.countStatements(8);
    assertEquals(result.getMinStatements(), 3);
    assertEquals(result.getMaxStatements(), 8);
    assertEquals(result.getTotalStatements(), 16);
  }

  @Test
  public void testBecomesValidAfterCountingDuration() {
    TransactionStatisticsResult result = new TransactionStatisticsResult();
    result.count(new TimeKeeper(1_000_000));   // 1 ms
    assertTrue(result.isValid());
    assertEquals(result.getCount(), 1);
    assertNotEquals(result.toString(), "<invalid>");
  }

}

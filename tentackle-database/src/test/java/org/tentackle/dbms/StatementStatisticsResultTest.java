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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link StatementStatisticsResult}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class StatementStatisticsResultTest {

  @Test
  public void testInitialState() {
    StatementStatisticsResult result = new StatementStatisticsResult();
    assertFalse(result.isValid());
    assertFalse(result.isFetchResultValid());
    assertNull(result.getFetchResult());
    assertEquals(result.getMinRows(), Integer.MAX_VALUE);
    assertEquals(result.getMaxRows(), Integer.MIN_VALUE);
    assertEquals(result.getTotalRows(), 0);
    assertEquals(result.toString(), "<invalid>");
  }

  @Test
  public void testCountFetchTracksRowsAndActivatesFetchResult() {
    StatementStatisticsResult result = new StatementStatisticsResult();
    result.countFetch(new TimeKeeper(500_000), 10);
    result.countFetch(new TimeKeeper(200_000), 4);

    assertTrue(result.isFetchResultValid());
    assertNotNull(result.getFetchResult());
    assertEquals(result.getFetchResult().getCount(), 2);
    assertEquals(result.getMinRows(), 4);
    assertEquals(result.getMaxRows(), 10);
    assertEquals(result.getTotalRows(), 14);
  }

  @Test
  public void testToStringWithoutFetch() {
    StatementStatisticsResult result = new StatementStatisticsResult();
    result.count(new TimeKeeper(1_000_000));   // makes the statement result valid
    String s = result.toString();
    assertNotEquals(s, "<invalid>");
    assertTrue(s.endsWith("/ 1"), s);   // count of 1, no fetch section
  }

  @Test
  public void testToStringWithFetch() {
    StatementStatisticsResult result = new StatementStatisticsResult();
    result.count(new TimeKeeper(1_000_000));
    result.countFetch(new TimeKeeper(500_000), 7);
    String s = result.toString();
    assertTrue(s.contains(" 7 "), "fetch section must include the row count: " + s);
  }

}

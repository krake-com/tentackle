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

package org.tentackle.log;

import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.TimeKeeper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link StatisticsResult}.
 * <p>
 * All durations are created via {@link TimeKeeper#TimeKeeper(long)}, which yields a valid duration
 * of exactly the given number of nanoseconds. The tests run with {@code user.language=en}, hence the
 * {@code #0.000} decimal format of {@link TimeKeeper#millisToString()} is stable.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class StatisticsResultTest {

  private static final long MILLI = 1_000_000L;    // nanos per millisecond

  private static TimeKeeper millis(long millis) {
    return new TimeKeeper(millis * MILLI);
  }


  @Test
  public void testEmptyResult() {
    StatisticsResult result = new StatisticsResult();
    assertFalse(result.isValid());
    assertEquals(result.getCount(), 0);
    assertNull(result.getMinDuration());
    assertNull(result.getMaxDuration());
    assertNull(result.getTotalDuration());
    assertEquals(result.toString(), "<invalid>");
  }

  @Test
  public void testSingleInvocation() {
    StatisticsResult result = new StatisticsResult();
    result.count(millis(5));
    assertTrue(result.isValid());
    assertEquals(result.getCount(), 1);
    assertEquals(result.getMinDuration().millisToString(), "5.000");
    assertEquals(result.getMaxDuration().millisToString(), "5.000");
    assertEquals(result.getTotalDuration().millisToString(), "5.000");
    assertEquals(result.toString(), "5.000 5.000 5.000 ms / 1");
  }

  @Test
  public void testMinMaxAndTotal() {
    StatisticsResult result = new StatisticsResult();
    result.count(millis(5));
    result.count(millis(1));
    result.count(millis(9));
    assertEquals(result.getCount(), 3);
    assertEquals(result.getMinDuration().millisToString(), "1.000");
    assertEquals(result.getMaxDuration().millisToString(), "9.000");
    assertEquals(result.getTotalDuration().millisToString(), "15.000");
    assertEquals(result.toString(), "1.000 9.000 15.000 ms / 3");
  }

  @Test
  public void testCountedDurationIsNotMutated() {
    StatisticsResult result = new StatisticsResult();
    TimeKeeper duration = millis(5);
    result.count(duration);
    result.count(millis(1));
    result.count(millis(9));
    assertEquals(duration.nanos(), 5 * MILLI);    // the result works on clones
  }

  @Test
  public void testMinAndMaxAreIndependentOfInsertionOrder() {
    StatisticsResult ascending = new StatisticsResult();
    ascending.count(millis(1));
    ascending.count(millis(9));
    StatisticsResult descending = new StatisticsResult();
    descending.count(millis(9));
    descending.count(millis(1));
    assertEquals(ascending.toString(), descending.toString());
    assertEquals(ascending.toString(), "1.000 9.000 10.000 ms / 2");
  }

  @Test
  public void testMultiplierOfOneDelegates() {
    StatisticsResult result = new StatisticsResult();
    result.count(millis(4), 1);
    assertEquals(result.getCount(), 1);
    assertEquals(result.toString(), "4.000 4.000 4.000 ms / 1");
  }

  @Test
  public void testMultiplierBelowOneDelegates() {
    // a count of 0 or less is treated as a single invocation
    StatisticsResult result = new StatisticsResult();
    result.count(millis(4), 0);
    assertEquals(result.getCount(), 1);
    assertEquals(result.toString(), "4.000 4.000 4.000 ms / 1");
  }

  @Test
  public void testMultiplierSplitsTheDuration() {
    // 5 invocations taking 10ms altogether means 2ms per invocation
    StatisticsResult result = new StatisticsResult();
    result.count(millis(10), 5);
    assertEquals(result.getCount(), 5);
    assertEquals(result.getMinDuration().millisToString(), "2.000");
    assertEquals(result.getMaxDuration().millisToString(), "2.000");
    assertEquals(result.getTotalDuration().millisToString(), "10.000");
  }

  @Test
  public void testMultiplierKeepsMinAndMaxPerInvocation() {
    // min/max must be compared against the per-invocation duration, not against a scaled-down value
    StatisticsResult result = new StatisticsResult();
    result.count(millis(10), 5);     // 2ms each
    result.count(millis(30), 5);     // 6ms each
    assertEquals(result.getCount(), 10);
    assertEquals(result.getMinDuration().millisToString(), "2.000");
    assertEquals(result.getMaxDuration().millisToString(), "6.000");
    assertEquals(result.getTotalDuration().millisToString(), "40.000");
    assertEquals(result.toString(), "2.000 6.000 40.000 ms / 10");
  }

  @Test
  public void testMultiplierMixedWithSingleInvocations() {
    StatisticsResult result = new StatisticsResult();
    result.count(millis(8), 4);      // 2ms each
    result.count(millis(1));
    result.count(millis(7));
    assertEquals(result.getCount(), 6);
    assertEquals(result.getMinDuration().millisToString(), "1.000");
    assertEquals(result.getMaxDuration().millisToString(), "7.000");
    assertEquals(result.getTotalDuration().millisToString(), "16.000");
  }

  @Test
  public void testMultipliedDurationIsNotMutated() {
    StatisticsResult result = new StatisticsResult();
    TimeKeeper duration = millis(10);
    result.count(duration, 5);
    assertEquals(duration.nanos(), 10 * MILLI);
  }

  @Test
  public void testInvalidDurationRejected() {
    StatisticsResult result = new StatisticsResult();
    assertThrows(TentackleRuntimeException.class, () -> result.count(new TimeKeeper()));
    assertThrows(TentackleRuntimeException.class, () -> result.count(new TimeKeeper(), 5));
    assertFalse(result.isValid());
  }

}

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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.log.Logger.Level;
import org.tentackle.misc.TimeKeeper;
import org.tentackle.reflect.ReflectionHelper;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link MethodStatistics}.
 * <p>
 * {@link MethodStatistics} exposes its collected data through the log only, so the assertions are
 * made against the {@link java.util.logging.LogRecord}s of its own logger.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MethodStatisticsTest {

  private static final long MILLI = 1_000_000L;
  private static final String TAG = "  ";

  private LogCapture capture;

  private static TimeKeeper millis(long millis) {
    return new TimeKeeper(millis * MILLI);
  }

  private static Method length() throws NoSuchMethodException {
    return String.class.getMethod("length");
  }

  private static Method isEmpty() throws NoSuchMethodException {
    return String.class.getMethod("isEmpty");
  }

  @BeforeMethod
  private void setUp() {
    capture = LogCapture.attach(MethodStatistics.class, Level.FINE);
  }

  @AfterMethod
  private void tearDown() {
    if (capture != null) {
      capture.close();
    }
  }


  @Test
  public void testEmptyStatisticsLogNothing() {
    new MethodStatistics().logStatistics("Title", Level.FINE, TAG, true);
    assertTrue(capture.records().isEmpty());
  }

  @Test
  public void testSingleInvocation() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), millis(5));
    statistics.logStatistics("Title", Level.FINE, TAG, false);

    String message = capture.singleRecord().getMessage();
    assertTrue(message.startsWith("Title" + TAG), message);
    assertTrue(message.contains("\n" + TAG + "5.000 5.000 5.000 ms / 1 x " +
                                ReflectionHelper.methodToString(length())), message);
  }

  @Test
  public void testLevelIsHonored() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), millis(5));
    statistics.logStatistics("Title", Level.WARNING, TAG, false);
    assertEquals(capture.singleRecord().getLevel(), java.util.logging.Level.WARNING);
  }

  @Test
  public void testDefaultTitle() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), millis(5));
    statistics.logStatistics(null, Level.FINE, TAG, false);
    assertTrue(capture.singleRecord().getMessage().startsWith("Invocation Statistics" + TAG),
               capture.allMessages());
  }

  @Test
  public void testInvocationsAreAggregatedPerMethod() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), millis(5));
    statistics.countMethodInvocation(length(), millis(1));
    statistics.logStatistics("Title", Level.FINE, TAG, false);

    String message = capture.singleRecord().getMessage();
    assertTrue(message.contains("1.000 5.000 6.000 ms / 2 x " + ReflectionHelper.methodToString(length())), message);
    assertEquals(message.split(" x ", -1).length, 2);    // exactly one entry
  }

  @Test
  public void testDistinctMethodsAreCountedSeparately() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), millis(5));
    statistics.countMethodInvocation(isEmpty(), millis(2));
    statistics.logStatistics("Title", Level.FINE, TAG, false);

    String message = capture.singleRecord().getMessage();
    assertTrue(message.contains(ReflectionHelper.methodToString(length())), message);
    assertTrue(message.contains(ReflectionHelper.methodToString(isEmpty())), message);
    assertEquals(message.split(" x ", -1).length, 3);    // two entries
  }

  @Test
  public void testServicedClassIsAppended() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), StringBuilder.class, millis(5));
    statistics.logStatistics("Title", Level.FINE, TAG, false);
    assertTrue(capture.singleRecord().getMessage().endsWith(" for StringBuilder"), capture.allMessages());
  }

  @Test
  public void testDeclaringClassIsNotAppended() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), String.class, millis(5));
    statistics.logStatistics("Title", Level.FINE, TAG, false);
    assertFalse(capture.singleRecord().getMessage().contains(" for "), capture.allMessages());
  }

  @Test
  public void testServicedClassSplitsTheStatistics() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), StringBuilder.class, millis(5));
    statistics.countMethodInvocation(length(), StringBuffer.class, millis(2));
    statistics.logStatistics("Title", Level.FINE, TAG, false);

    String message = capture.singleRecord().getMessage();
    assertTrue(message.contains(" for StringBuilder"), message);
    assertTrue(message.contains(" for StringBuffer"), message);
    assertEquals(message.split(" x ", -1).length, 3);
  }

  @Test
  public void testClearMarksAndEmptiesTheStatistics() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), millis(5));
    statistics.logStatistics("Title", Level.FINE, TAG, true);
    assertTrue(capture.singleRecord().getMessage().endsWith("\n    (cleared)"), capture.allMessages());

    capture.clear();
    statistics.logStatistics("Title", Level.FINE, TAG, true);
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testWithoutClearTheStatisticsSurvive() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), millis(5));
    statistics.logStatistics("Title", Level.FINE, TAG, false);
    capture.clear();
    statistics.logStatistics("Title", Level.FINE, TAG, false);
    assertTrue(capture.singleRecord().getMessage().contains("/ 1"), capture.allMessages());
  }

  @Test
  public void testStatisticsAreClearedEvenWhenNotLogged() throws NoSuchMethodException {
    // the clear happens regardless of the logging level
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), millis(5));
    capture.setLevel(null);    // OFF
    statistics.logStatistics("Title", Level.FINE, TAG, true);
    assertTrue(capture.records().isEmpty());

    capture.setLevel(Level.FINE);
    statistics.logStatistics("Title", Level.FINE, TAG, false);
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testNothingIsLoggedBelowTheLevel() throws NoSuchMethodException {
    MethodStatistics statistics = new MethodStatistics();
    statistics.countMethodInvocation(length(), millis(5));
    capture.setLevel(Level.SEVERE);
    statistics.logStatistics("Title", Level.FINE, TAG, false);
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

}

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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link LoggingRunnable}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LoggingRunnableTest {

  private static final String NAME = "the-runnable";

  /**
   * A runnable with a stable {@code toString}, since the logged messages contain it.
   */
  private static class CountingRunnable implements Runnable {

    private final AtomicInteger runs = new AtomicInteger();
    private final RuntimeException failure;

    CountingRunnable() {
      this(null);
    }

    CountingRunnable(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public void run() {
      runs.incrementAndGet();
      if (failure != null) {
        throw failure;
      }
    }

    @Override
    public String toString() {
      return NAME;
    }
  }


  private DefaultLogger logger;
  private LogCapture capture;

  @BeforeMethod
  private void setUp() {
    logger = new DefaultLogger(LogCapture.uniqueLoggerName(LoggingRunnableTest.class));
    capture = LogCapture.attach(logger, Level.FINER);
  }

  @AfterMethod
  private void tearDown() {
    if (capture != null) {
      capture.close();
    }
  }


  @Test
  public void testRunnableIsExecuted() {
    CountingRunnable runnable = new CountingRunnable();
    new LoggingRunnable(logger, Level.FINE, runnable).run();
    assertEquals(runnable.runs.get(), 1);
  }

  @Test
  public void testStartAndDoneAreLogged() {
    new LoggingRunnable(logger, Level.FINE, new CountingRunnable()).run();
    List<String> messages = capture.messages();
    assertEquals(messages.size(), 2, capture.allMessages());
    assertEquals(messages.get(0), "start runnable " + NAME);
    assertTrue(messages.get(1).startsWith("done runnable " + NAME + " ("), messages.get(1));
    assertTrue(messages.get(1).endsWith("ms)"), messages.get(1));
  }

  @Test
  public void testLevelIsHonored() {
    new LoggingRunnable(logger, Level.WARNING, new CountingRunnable()).run();
    assertEquals(capture.records().size(), 2);
    for (var record : capture.records()) {
      assertEquals(record.getLevel(), java.util.logging.Level.WARNING);
    }
  }

  @Test
  public void testNothingIsLoggedBelowTheLevel() {
    capture.setLevel(Level.SEVERE);
    CountingRunnable runnable = new CountingRunnable();
    new LoggingRunnable(logger, Level.FINE, runnable).run();
    assertEquals(runnable.runs.get(), 1);                          // the runnable still runs
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testNothingIsLoggedWhenOff() {
    capture.setLevel(null);    // OFF
    CountingRunnable runnable = new CountingRunnable();
    new LoggingRunnable(logger, Level.SEVERE, runnable).run();
    assertEquals(runnable.runs.get(), 1);
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testExceptionIsPropagated() {
    IllegalStateException failure = new IllegalStateException("boom");
    CountingRunnable runnable = new CountingRunnable(failure);
    LoggingRunnable loggingRunnable = new LoggingRunnable(logger, Level.FINE, runnable);
    assertSame(expectThrows(IllegalStateException.class, loggingRunnable::run), failure);
    assertEquals(runnable.runs.get(), 1);
  }

  @Test
  public void testFailedRunnableIsNotReportedAsDone() {
    CountingRunnable runnable = new CountingRunnable(new IllegalStateException("boom"));
    LoggingRunnable loggingRunnable = new LoggingRunnable(logger, Level.FINE, runnable);
    assertThrows(IllegalStateException.class, loggingRunnable::run);
    assertEquals(capture.messages(), List.of("start runnable " + NAME));
  }

  @Test
  public void testRunnableIsReusable() {
    CountingRunnable runnable = new CountingRunnable();
    LoggingRunnable loggingRunnable = new LoggingRunnable(logger, Level.FINE, runnable);
    loggingRunnable.run();
    loggingRunnable.run();
    assertEquals(runnable.runs.get(), 2);
    assertEquals(capture.records().size(), 4);
  }

}

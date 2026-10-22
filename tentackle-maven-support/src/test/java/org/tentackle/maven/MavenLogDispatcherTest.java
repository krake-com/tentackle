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

package org.tentackle.maven;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests that java.util.logging is routed to the maven logger bound to the current thread.
 */
public class MavenLogDispatcherTest {

  private Handler[] savedHandlers;

  @BeforeClass
  public void saveRootHandlers() {
    savedHandlers = Logger.getLogger("").getHandlers();
  }

  @AfterClass(alwaysRun = true)
  public void restoreRootHandlers() {
    Logger rootLogger = Logger.getLogger("");
    for (Handler handler : rootLogger.getHandlers()) {
      rootLogger.removeHandler(handler);
    }
    for (Handler handler : savedHandlers) {
      rootLogger.addHandler(handler);
    }
  }


  @Test
  public void logsAreRoutedPerThread() throws InterruptedException {
    CapturingLogger log1 = new CapturingLogger();
    CapturingLogger log2 = new CapturingLogger();
    CountDownLatch installed = new CountDownLatch(2);
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

    Thread t1 = new Thread(() -> logConcurrently(log1, "one", installed, failures));
    Thread t2 = new Thread(() -> logConcurrently(log2, "two", installed, failures));
    t1.start();
    t2.start();
    t1.join();
    t2.join();

    assertTrue(failures.isEmpty(), failures.toString());
    assertEquals(log1.messages, List.of("one-0", "one-1", "one-2"));
    assertEquals(log2.messages, List.of("two-0", "two-1", "two-2"));

    long dispatchers = List.of(Logger.getLogger("").getHandlers()).stream()
                           .filter(MavenLogDispatcher.class::isInstance).count();
    assertEquals(dispatchers, 1L);
  }

  @Test
  public void nothingRoutedWhenUnbound() {
    CapturingLogger log = new CapturingLogger();
    MavenLogDispatcher.install(new MavenLogHandler(log, java.util.logging.Level.WARNING));
    MavenLogDispatcher.uninstall();
    Logger.getLogger(getClass().getName()).warning("lost");
    assertTrue(log.messages.isEmpty());
  }

  @Test
  public void spawnedThreadsInheritBinding() throws InterruptedException {
    CapturingLogger log = new CapturingLogger();
    MavenLogDispatcher.install(new MavenLogHandler(log, java.util.logging.Level.WARNING));
    try {
      Thread thread = new Thread(() -> Logger.getLogger(getClass().getName()).warning("child"));
      thread.start();
      thread.join();
    }
    finally {
      MavenLogDispatcher.uninstall();
    }
    assertEquals(log.messages, List.of("child"));
  }


  private void logConcurrently(CapturingLogger log, String prefix, CountDownLatch installed, List<Throwable> failures) {
    try {
      MavenLogDispatcher.install(new MavenLogHandler(log, java.util.logging.Level.WARNING));
      installed.countDown();
      installed.await();    // both threads are bound before anybody logs
      Logger logger = Logger.getLogger(MavenLogDispatcherTest.class.getName());
      for (int i = 0; i < 3; i++) {
        logger.warning(prefix + "-" + i);
      }
      logger.info("below min level");
    }
    catch (Throwable t) {
      failures.add(t);
    }
    finally {
      MavenLogDispatcher.uninstall();
    }
  }


  /**
   * SLF4J logger that captures the messages.
   */
  private static class CapturingLogger extends LegacyAbstractLogger {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @Override public boolean isTraceEnabled() { return true; }
    @Override public boolean isDebugEnabled() { return true; }
    @Override public boolean isInfoEnabled() { return true; }
    @Override public boolean isWarnEnabled() { return true; }
    @Override public boolean isErrorEnabled() { return true; }

    @Override
    protected String getFullyQualifiedCallerName() {
      return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern,
                                               Object[] arguments, Throwable throwable) {
      messages.add(messagePattern);
    }
  }
}

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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.LogRecord;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link DefaultLogger}.
 * <p>
 * Every test gets a logger of its own (see {@link LogCapture#uniqueLoggerName}), since loggers are
 * cached forever and configuring a shared one would leak into the other tests of this module.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultLoggerTest {

  private DefaultLogger logger;
  private LogCapture capture;

  @BeforeMethod
  private void setUp() {
    logger = new DefaultLogger(LogCapture.uniqueLoggerName(DefaultLoggerTest.class));
    capture = LogCapture.attach(logger, Level.FINER);
  }

  @AfterMethod
  private void tearDown() {
    if (capture != null) {
      capture.close();
    }
    DefaultMappedDiagnosticContext.getInstance().clear();
  }


  // ------------------------------------------------------------------ dispatchers

  private void logMessage(Level level, String message) {
    switch (level) {
      case FINER -> logger.finer(message);
      case FINE -> logger.fine(message);
      case INFO -> logger.info(message);
      case WARNING -> logger.warning(message);
      case SEVERE -> logger.severe(message);
    }
  }

  private void logParams(Level level, String message, Object... params) {
    switch (level) {
      case FINER -> logger.finer(message, params);
      case FINE -> logger.fine(message, params);
      case INFO -> logger.info(message, params);
      case WARNING -> logger.warning(message, params);
      case SEVERE -> logger.severe(message, params);
    }
  }

  private void logCause(Level level, String message, Throwable cause) {
    switch (level) {
      case FINER -> logger.finer(message, cause);
      case FINE -> logger.fine(message, cause);
      case INFO -> logger.info(message, cause);
      case WARNING -> logger.warning(message, cause);
      case SEVERE -> logger.severe(message, cause);
    }
  }

  private void logParamSuppliers(Level level, String message, Supplier<?>... paramSuppliers) {
    switch (level) {
      case FINER -> logger.finer(message, paramSuppliers);
      case FINE -> logger.fine(message, paramSuppliers);
      case INFO -> logger.info(message, paramSuppliers);
      case WARNING -> logger.warning(message, paramSuppliers);
      case SEVERE -> logger.severe(message, paramSuppliers);
    }
  }

  private void logSupplier(Level level, Supplier<String> messageSupplier) {
    switch (level) {
      case FINER -> logger.finer(messageSupplier);
      case FINE -> logger.fine(messageSupplier);
      case INFO -> logger.info(messageSupplier);
      case WARNING -> logger.warning(messageSupplier);
      case SEVERE -> logger.severe(messageSupplier);
    }
  }

  private void logCauseAndSupplier(Level level, Throwable cause, Supplier<String> messageSupplier) {
    switch (level) {
      case FINER -> logger.finer(cause, messageSupplier);
      case FINE -> logger.fine(cause, messageSupplier);
      case INFO -> logger.info(cause, messageSupplier);
      case WARNING -> logger.warning(cause, messageSupplier);
      case SEVERE -> logger.severe(cause, messageSupplier);
    }
  }

  private boolean isLevelLoggable(Level level) {
    return switch (level) {
      case FINER -> logger.isFinerLoggable();
      case FINE -> logger.isFineLoggable();
      case INFO -> logger.isInfoLoggable();
      case WARNING -> logger.isWarningLoggable();
      case SEVERE -> logger.isSevereLoggable();
    };
  }


  // ------------------------------------------------------------------ levels

  @Test
  public void testLevelTranslation() {
    for (Level level : Level.values()) {
      capture.clear();
      logMessage(level, "message");
      LogRecord record = capture.singleRecord();
      assertEquals(record.getLevel(), LogCapture.translate(level), "wrong level for " + level);
      assertEquals(record.getMessage(), "message");
    }
  }

  @Test
  public void testIsLoggable() {
    for (Level threshold : Level.values()) {
      capture.setLevel(threshold);
      for (Level level : Level.values()) {
        boolean expected = level.ordinal() >= threshold.ordinal();
        assertEquals(logger.isLoggable(level), expected, level + " with threshold " + threshold);
      }
    }
  }

  @Test
  public void testIsLoggableShortcuts() {
    for (Level threshold : Level.values()) {
      capture.setLevel(threshold);
      for (Level level : Level.values()) {
        assertEquals(isLevelLoggable(level), logger.isLoggable(level), level + " with threshold " + threshold);
      }
    }
  }

  @Test
  public void testNothingIsLoggableWhenOff() {
    capture.setLevel(null);    // OFF
    for (Level level : Level.values()) {
      assertFalse(logger.isLoggable(level), String.valueOf(level));
      assertFalse(isLevelLoggable(level), String.valueOf(level));
      logMessage(level, "message");
    }
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testMessagesBelowTheLevelAreDropped() {
    capture.setLevel(Level.WARNING);
    logger.finer("dropped");
    logger.fine("dropped");
    logger.info("dropped");
    logger.warning("kept");
    logger.severe("kept too");
    assertEquals(capture.messages(), java.util.List.of("kept", "kept too"));
  }


  // ------------------------------------------------------------------ overloads

  @Test
  public void testMessageOnly() {
    for (Level level : Level.values()) {
      capture.clear();
      logMessage(level, "plain message");
      assertEquals(capture.singleRecord().getMessage(), "plain message");
      assertNull(capture.singleRecord().getThrown());
    }
  }

  @Test
  public void testMessageWithParams() {
    for (Level level : Level.values()) {
      capture.clear();
      logParams(level, "a={0}, b={1}", "x", 42);
      assertEquals(capture.singleRecord().getMessage(), "a=x, b=42");
    }
  }

  @Test
  public void testMessageWithCause() {
    for (Level level : Level.values()) {
      capture.clear();
      Exception cause = new IllegalStateException("bad");
      logCause(level, "failed", cause);
      LogRecord record = capture.singleRecord();
      assertEquals(record.getMessage(), "failed");
      assertSame(record.getThrown(), cause);
    }
  }

  @Test
  public void testMessageWithParamSuppliers() {
    for (Level level : Level.values()) {
      capture.clear();
      logParamSuppliers(level, "a={0}, b={1}", () -> "x", () -> 42);
      assertEquals(capture.singleRecord().getMessage(), "a=x, b=42");
    }
  }

  @Test
  public void testNullParamSupplierYieldsNull() {
    logParamSuppliers(Level.INFO, "value={0}", (Supplier<?>) null);
    assertEquals(capture.singleRecord().getMessage(), "value=null");
  }

  @Test
  public void testMessageSupplier() {
    for (Level level : Level.values()) {
      capture.clear();
      logSupplier(level, () -> "supplied");
      LogRecord record = capture.singleRecord();
      assertEquals(record.getMessage(), "supplied");
      assertNull(record.getThrown());
    }
  }

  @Test
  public void testMessageSupplierWithCause() {
    for (Level level : Level.values()) {
      capture.clear();
      Exception cause = new IllegalStateException("bad");
      logCauseAndSupplier(level, cause, () -> "supplied");
      LogRecord record = capture.singleRecord();
      assertEquals(record.getMessage(), "supplied");
      assertSame(record.getThrown(), cause);
    }
  }

  @Test
  public void testGenericLogWithCause() {
    Exception cause = new IllegalStateException("bad");
    logger.log(Level.WARNING, "failed", cause);
    LogRecord record = capture.singleRecord();
    assertEquals(record.getLevel(), java.util.logging.Level.WARNING);
    assertEquals(record.getMessage(), "failed");
    assertSame(record.getThrown(), cause);
  }

  @Test
  public void testGenericLogWithNullCause() {
    logger.log(Level.INFO, "no cause", (Throwable) null);
    LogRecord record = capture.singleRecord();
    assertEquals(record.getMessage(), "no cause");
    assertNull(record.getThrown());
  }

  @Test
  public void testGenericLogWithParams() {
    logger.log(Level.INFO, "a={0}", "x");
    assertEquals(capture.singleRecord().getMessage(), "a=x");
  }

  @Test
  public void testGenericLogWithMessageSupplier() {
    Exception cause = new IllegalStateException("bad");
    logger.log(Level.FINE, cause, () -> "supplied");
    LogRecord record = capture.singleRecord();
    assertEquals(record.getLevel(), java.util.logging.Level.FINE);
    assertEquals(record.getMessage(), "supplied");
    assertSame(record.getThrown(), cause);
  }

  @Test
  public void testMessageWithoutParamsIsNotFormatted() {
    // MessageFormat is only applied if there are parameters
    logger.info("raw {0} text with a ' quote");
    assertEquals(capture.singleRecord().getMessage(), "raw {0} text with a ' quote");
  }

  @Test
  public void testEmptyParamsAreNotFormatted() {
    logger.info("raw {0} text", new Object[0]);
    assertEquals(capture.singleRecord().getMessage(), "raw {0} text");
  }


  // ------------------------------------------------------------------ laziness

  @Test
  public void testMessageSupplierIsLazy() {
    capture.setLevel(null);    // OFF
    AtomicInteger invocations = new AtomicInteger();
    logger.info(() -> {
      invocations.incrementAndGet();
      return "expensive";
    });
    assertEquals(invocations.get(), 0);
    assertTrue(capture.records().isEmpty());
  }

  @Test
  public void testParamSuppliersAreLazy() {
    capture.setLevel(null);    // OFF
    AtomicInteger invocations = new AtomicInteger();
    Supplier<?> supplier = () -> {
      invocations.incrementAndGet();
      return "expensive";
    };
    logger.info("value={0}", supplier);
    assertEquals(invocations.get(), 0);
    assertTrue(capture.records().isEmpty());
  }

  @Test
  public void testSuppliersAreInvokedOnceWhenLoggable() {
    AtomicInteger invocations = new AtomicInteger();
    logger.info(() -> {
      invocations.incrementAndGet();
      return "expensive";
    });
    assertEquals(invocations.get(), 1);
    assertEquals(capture.singleRecord().getMessage(), "expensive");
  }


  // ------------------------------------------------------------------ record details

  @Test
  public void testSourceIsTheCallingMethod() {
    // doLog drops all frames of the DefaultLogger itself
    logger.info("from the test");
    LogRecord record = capture.singleRecord();
    assertEquals(record.getSourceClassName(), DefaultLoggerTest.class.getName());
    assertEquals(record.getSourceMethodName(), "testSourceIsTheCallingMethod");
  }

  @Test
  public void testLoggerNameIsSet() {
    logger.info("named");
    assertEquals(capture.singleRecord().getLoggerName(), ((java.util.logging.Logger) logger.getLoggerImpl()).getName());
  }

  @Test
  public void testGetLoggerImpl() {
    String name = LogCapture.uniqueLoggerName(DefaultLoggerTest.class);
    DefaultLogger defaultLogger = new DefaultLogger(name);
    Object impl = defaultLogger.getLoggerImpl();
    assertTrue(impl instanceof java.util.logging.Logger);
    assertEquals(((java.util.logging.Logger) impl).getName(), name);
  }


  // ------------------------------------------------------------------ instances

  @Test
  public void testGetLoggerIsCached() {
    String name = LogCapture.uniqueLoggerName(DefaultLoggerTest.class);
    assertSame(DefaultLogger.getLogger(name), DefaultLogger.getLogger(name));
  }

  @Test
  public void testGetLoggerIsPerName() {
    DefaultLogger one = DefaultLogger.getLogger(LogCapture.uniqueLoggerName(DefaultLoggerTest.class));
    DefaultLogger other = DefaultLogger.getLogger(LogCapture.uniqueLoggerName(DefaultLoggerTest.class));
    assertNotSame(one, other);
    assertNotSame(one.getLoggerImpl(), other.getLoggerImpl());
  }

  @Test
  public void testConstructorIsNotCached() {
    String name = LogCapture.uniqueLoggerName(DefaultLoggerTest.class);
    DefaultLogger cached = DefaultLogger.getLogger(name);
    DefaultLogger created = new DefaultLogger(name);
    assertNotSame(created, cached);
    assertSame(created.getLoggerImpl(), cached.getLoggerImpl());    // but the backend is shared
  }

  @Test
  public void testMappedDiagnosticContextIsTheSingleton() {
    assertSame(logger.getMappedDiagnosticContext(), DefaultMappedDiagnosticContext.getInstance());
  }


  // ------------------------------------------------------------------ MDC

  @Test
  public void testMappedDiagnosticContextIsPrepended() {
    DefaultMappedDiagnosticContext.getInstance().put("user", "harald");
    logger.info("hello");
    assertEquals(capture.singleRecord().getMessage(), "[\"user\"=\"harald\"] hello");
  }

  @Test
  public void testMappedDiagnosticContextWithParams() {
    DefaultMappedDiagnosticContext.getInstance().put("user", "harald");
    logger.info("hello {0}", "world");
    assertEquals(capture.singleRecord().getMessage(), "[\"user\"=\"harald\"] hello world");
  }

  @Test
  public void testMappedDiagnosticContextWithSeveralEntries() {
    // the backing Hashtable has no defined iteration order, hence no assertion on the entry order
    DefaultMappedDiagnosticContext.getInstance().put("user", "harald");
    DefaultMappedDiagnosticContext.getInstance().put("host", "localhost");
    logger.info("hello");
    String message = capture.singleRecord().getMessage();
    assertTrue(message.startsWith("["), message);
    assertTrue(message.endsWith("] hello"), message);
    assertTrue(message.contains("\"user\"=\"harald\""), message);
    assertTrue(message.contains("\"host\"=\"localhost\""), message);
    assertTrue(message.contains(", "), message);
  }

  @Test
  public void testEmptyMappedDiagnosticContextIsNotPrepended() {
    DefaultMappedDiagnosticContext.getInstance().clear();
    logger.info("hello");
    assertEquals(capture.singleRecord().getMessage(), "hello");
  }

  @Test
  public void testRemovedMappedDiagnosticContextIsNotPrepended() {
    DefaultMappedDiagnosticContext.getInstance().put("user", "harald");
    DefaultMappedDiagnosticContext.getInstance().remove("user");
    logger.info("hello");
    assertEquals(capture.singleRecord().getMessage(), "hello");
  }


  // ------------------------------------------------------------------ stacktraces

  @Test
  public void testLogStacktraceDefaultsToSevere() {
    logger.logStacktrace(new IllegalStateException("boom"));
    assertFalse(capture.records().isEmpty());
    for (LogRecord record : capture.records()) {
      assertEquals(record.getLevel(), java.util.logging.Level.SEVERE);
    }
    String logged = capture.allMessages();
    assertTrue(logged.contains("java.lang.IllegalStateException: boom"), logged);
    assertTrue(logged.contains(DefaultLoggerTest.class.getName() + ".testLogStacktraceDefaultsToSevere"), logged);
  }

  @Test
  public void testLogStacktraceWithLevel() {
    logger.logStacktrace(Level.FINE, new IllegalStateException("boom"));
    assertFalse(capture.records().isEmpty());
    for (LogRecord record : capture.records()) {
      assertEquals(record.getLevel(), java.util.logging.Level.FINE);
    }
    assertTrue(capture.allMessages().contains("java.lang.IllegalStateException: boom"), capture.allMessages());
  }

  @Test
  public void testLogStacktraceIncludesTheCause() {
    Exception cause = new IllegalArgumentException("the root cause");
    logger.logStacktrace(new IllegalStateException("boom", cause));
    String logged = capture.allMessages();
    assertTrue(logged.contains("java.lang.IllegalStateException: boom"), logged);
    assertTrue(logged.contains("java.lang.IllegalArgumentException: the root cause"), logged);
  }

  @Test
  public void testLogStacktraceLogsNoEmptyRecords() {
    // the PrintStream flushes the underlying LoggerOutputStream more than once when being closed
    logger.logStacktrace(new IllegalStateException("boom"));
    for (LogRecord record : capture.records()) {
      assertFalse(record.getMessage().isEmpty(), "empty record among " + capture.records().size());
    }
  }

  @Test
  public void testLogStacktraceIsSuppressedWhenNotLoggable() {
    capture.setLevel(null);    // OFF
    logger.logStacktrace(new IllegalStateException("boom"));
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

}

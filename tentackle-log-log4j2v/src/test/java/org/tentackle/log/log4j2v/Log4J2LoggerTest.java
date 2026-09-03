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

package org.tentackle.log.log4j2v;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.log.Logger.Level;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link Log4J2Logger}.
 * <p>
 * Every test gets a logger of its own (see {@link Log4J2Capture#uniqueLoggerName}), since both
 * {@link Log4J2Logger}'s own logger cache and log4j-core's logger context cache persist for the
 * JVM's life, and configuring a shared one would leak into the other tests of this module.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class Log4J2LoggerTest {

  private String name;
  private Log4J2Logger logger;
  private Log4J2Capture capture;

  @BeforeMethod
  private void setUp() {
    name = Log4J2Capture.uniqueLoggerName(Log4J2LoggerTest.class);
    capture = Log4J2Capture.attach(name, Level.FINER);
    logger = new Log4J2Logger(name);
  }

  @AfterMethod
  private void tearDown() {
    if (capture != null) {
      capture.close();
    }
    Log4J2MappedDiagnosticContext.getInstance().clear();
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
      LogEvent event = capture.singleEvent();
      assertEquals(event.getLevel(), Log4J2Capture.translate(level), "wrong level for " + level);
      assertEquals(event.getMessage().getFormattedMessage(), "message");
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
    assertTrue(capture.events().isEmpty(), capture.allMessages());
  }

  @Test
  public void testMessagesBelowTheLevelAreDropped() {
    capture.setLevel(Level.WARNING);
    logger.finer("dropped");
    logger.fine("dropped");
    logger.info("dropped");
    logger.warning("kept");
    logger.severe("kept too");
    assertEquals(capture.messages(), List.of("kept", "kept too"));
  }


  // ------------------------------------------------------------------ overloads

  @Test
  public void testMessageOnly() {
    for (Level level : Level.values()) {
      capture.clear();
      logMessage(level, "plain message");
      assertEquals(capture.singleEvent().getMessage().getFormattedMessage(), "plain message");
      assertNull(capture.singleEvent().getThrown());
    }
  }

  @Test
  public void testMessageWithParams() {
    for (Level level : Level.values()) {
      capture.clear();
      logParams(level, "a={0}, b={1}", "x", 42);
      assertEquals(capture.singleEvent().getMessage().getFormattedMessage(), "a=x, b=42");
    }
  }

  @Test
  public void testMessageWithCause() {
    for (Level level : Level.values()) {
      capture.clear();
      Exception cause = new IllegalStateException("bad");
      logCause(level, "failed", cause);
      LogEvent event = capture.singleEvent();
      assertEquals(event.getMessage().getFormattedMessage(), "failed");
      assertSame(event.getThrown(), cause);
    }
  }

  @Test
  public void testMessageWithParamSuppliers() {
    for (Level level : Level.values()) {
      capture.clear();
      logParamSuppliers(level, "a={0}, b={1}", () -> "x", () -> 42);
      assertEquals(capture.singleEvent().getMessage().getFormattedMessage(), "a=x, b=42");
    }
  }

  @Test
  public void testNullParamSupplierYieldsNull() {
    logParamSuppliers(Level.INFO, "value={0}", (Supplier<?>) null);
    assertEquals(capture.singleEvent().getMessage().getFormattedMessage(), "value=null");
  }

  @Test
  public void testMessageSupplier() {
    for (Level level : Level.values()) {
      capture.clear();
      logSupplier(level, () -> "supplied");
      LogEvent event = capture.singleEvent();
      assertEquals(event.getMessage().getFormattedMessage(), "supplied");
      assertNull(event.getThrown());
    }
  }

  @Test
  public void testMessageSupplierWithCause() {
    for (Level level : Level.values()) {
      capture.clear();
      Exception cause = new IllegalStateException("bad");
      logCauseAndSupplier(level, cause, () -> "supplied");
      LogEvent event = capture.singleEvent();
      assertEquals(event.getMessage().getFormattedMessage(), "supplied");
      assertSame(event.getThrown(), cause);
    }
  }

  @Test
  public void testGenericLogWithCause() {
    Exception cause = new IllegalStateException("bad");
    logger.log(Level.WARNING, "failed", cause);
    LogEvent event = capture.singleEvent();
    assertEquals(event.getLevel(), Log4J2Capture.translate(Level.WARNING));
    assertEquals(event.getMessage().getFormattedMessage(), "failed");
    assertSame(event.getThrown(), cause);
  }

  @Test
  public void testGenericLogWithNullCause() {
    logger.log(Level.INFO, "no cause", (Throwable) null);
    LogEvent event = capture.singleEvent();
    assertEquals(event.getMessage().getFormattedMessage(), "no cause");
    assertNull(event.getThrown());
  }

  @Test
  public void testGenericLogWithParams() {
    logger.log(Level.INFO, "a={0}", "x");
    assertEquals(capture.singleEvent().getMessage().getFormattedMessage(), "a=x");
  }

  @Test
  public void testGenericLogWithMessageSupplier() {
    Exception cause = new IllegalStateException("bad");
    logger.log(Level.FINE, cause, () -> "supplied");
    LogEvent event = capture.singleEvent();
    assertEquals(event.getLevel(), Log4J2Capture.translate(Level.FINE));
    assertEquals(event.getMessage().getFormattedMessage(), "supplied");
    assertSame(event.getThrown(), cause);
  }

  @Test
  public void testMessageWithoutParamsIsNotFormatted() {
    // MessageFormat is only applied if there are parameters
    logger.info("raw {0} text with a ' quote");
    assertEquals(capture.singleEvent().getMessage().getFormattedMessage(), "raw {0} text with a ' quote");
  }

  @Test
  public void testEmptyParamsAreNotFormatted() {
    logger.info("raw {0} text", new Object[0]);
    assertEquals(capture.singleEvent().getMessage().getFormattedMessage(), "raw {0} text");
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
    assertTrue(capture.events().isEmpty());
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
    assertTrue(capture.events().isEmpty());
  }

  @Test
  public void testSuppliersAreInvokedOnceWhenLoggable() {
    AtomicInteger invocations = new AtomicInteger();
    logger.info(() -> {
      invocations.incrementAndGet();
      return "expensive";
    });
    assertEquals(invocations.get(), 1);
    assertEquals(capture.singleEvent().getMessage().getFormattedMessage(), "expensive");
  }


  // ------------------------------------------------------------------ event details

  @Test
  public void testSourceIsTheCallingMethod() {
    // Log4J2Logger passes its own FQCN to logIfEnabled so log4j2 skips its frames
    logger.info("from the test");
    LogEvent event = capture.singleEvent();
    assertEquals(event.getSource().getClassName(), Log4J2LoggerTest.class.getName());
    assertEquals(event.getSource().getMethodName(), "testSourceIsTheCallingMethod");
  }

  @Test
  public void testLoggerNameIsSet() {
    logger.info("named");
    assertEquals(capture.singleEvent().getLoggerName(), name);
  }

  @Test
  public void testGetLoggerImpl() {
    String freshName = Log4J2Capture.uniqueLoggerName(Log4J2LoggerTest.class);
    Log4J2Logger freshLogger = new Log4J2Logger(freshName);
    Object impl = freshLogger.getLoggerImpl();
    assertTrue(impl instanceof ExtendedLoggerWrapper);
    assertEquals(((ExtendedLoggerWrapper) impl).getName(), freshName);
  }


  // ------------------------------------------------------------------ instances

  @Test
  public void testGetLoggerIsCached() {
    String freshName = Log4J2Capture.uniqueLoggerName(Log4J2LoggerTest.class);
    assertSame(Log4J2Logger.getLogger(freshName), Log4J2Logger.getLogger(freshName));
  }

  @Test
  public void testGetLoggerIsPerName() {
    Log4J2Logger one = Log4J2Logger.getLogger(Log4J2Capture.uniqueLoggerName(Log4J2LoggerTest.class));
    Log4J2Logger other = Log4J2Logger.getLogger(Log4J2Capture.uniqueLoggerName(Log4J2LoggerTest.class));
    assertNotSame(one, other);
    assertNotSame(one.getLoggerImpl(), other.getLoggerImpl());
  }

  @Test
  public void testConstructorIsNotCached() {
    String freshName = Log4J2Capture.uniqueLoggerName(Log4J2LoggerTest.class);
    Log4J2Logger cached = Log4J2Logger.getLogger(freshName);
    Log4J2Logger created = new Log4J2Logger(freshName);
    assertNotSame(created, cached);
    // unlike DefaultLogger (see DefaultLoggerTest#testConstructorIsNotCached), the constructor here
    // always builds a NEW ExtendedLoggerWrapper around the shared log4j-core backend, so the wrapper
    // itself is never shared - do not "fix" this back to assertSame
    assertNotSame(created.getLoggerImpl(), cached.getLoggerImpl());
  }

  @Test
  public void testMappedDiagnosticContextIsTheSingleton() {
    assertSame(logger.getMappedDiagnosticContext(), Log4J2MappedDiagnosticContext.getInstance());
  }


  // ------------------------------------------------------------------ MDC

  @Test
  public void testMappedDiagnosticContextIsVisibleOnEvent() {
    // unlike DefaultLogger, Log4J2Logger does not prepend the MDC into the message text -
    // log4j2 exposes it as structured context data on the event instead
    Log4J2MappedDiagnosticContext.getInstance().put("user", "harald");
    logger.info("hello");
    LogEvent event = capture.singleEvent();
    assertEquals(event.getMessage().getFormattedMessage(), "hello");
    assertEquals(event.getContextData().getValue("user"), "harald");
  }

  @Test
  public void testMappedDiagnosticContextWithSeveralEntries() {
    Log4J2MappedDiagnosticContext.getInstance().put("user", "harald");
    Log4J2MappedDiagnosticContext.getInstance().put("host", "localhost");
    logger.info("hello");
    LogEvent event = capture.singleEvent();
    assertEquals(event.getContextData().getValue("user"), "harald");
    assertEquals(event.getContextData().getValue("host"), "localhost");
  }

  @Test
  public void testEmptyMappedDiagnosticContextHasNoContextData() {
    logger.info("hello");
    LogEvent event = capture.singleEvent();
    assertEquals(event.getMessage().getFormattedMessage(), "hello");
    assertTrue(event.getContextData().isEmpty());
  }

  @Test
  public void testRemovedMappedDiagnosticContextIsNotOnEvent() {
    Log4J2MappedDiagnosticContext.getInstance().put("user", "harald");
    Log4J2MappedDiagnosticContext.getInstance().remove("user");
    logger.info("hello");
    assertTrue(capture.singleEvent().getContextData().isEmpty());
  }


  // ------------------------------------------------------------------ stacktraces

  @Test
  public void testLogStacktraceDefaultsToSevere() {
    logger.logStacktrace(new IllegalStateException("boom"));
    List<LogEvent> events = capture.events();
    assertFalse(events.isEmpty());
    for (LogEvent event : events) {
      assertEquals(event.getLevel(), Log4J2Capture.translate(Level.SEVERE));
    }
    String logged = capture.allMessages();
    assertTrue(logged.contains("java.lang.IllegalStateException: boom"), logged);
    assertTrue(logged.contains(Log4J2LoggerTest.class.getName() + ".testLogStacktraceDefaultsToSevere"), logged);
  }

  @Test
  public void testLogStacktraceWithLevel() {
    logger.logStacktrace(Level.FINE, new IllegalStateException("boom"));
    List<LogEvent> events = capture.events();
    assertFalse(events.isEmpty());
    for (LogEvent event : events) {
      assertEquals(event.getLevel(), Log4J2Capture.translate(Level.FINE));
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
    for (LogEvent event : capture.events()) {
      assertFalse(event.getMessage().getFormattedMessage().isEmpty(), "empty event among " + capture.events().size());
    }
  }

  @Test
  public void testLogStacktraceIsSuppressedWhenNotLoggable() {
    capture.setLevel(null);    // OFF
    logger.logStacktrace(new IllegalStateException("boom"));
    assertTrue(capture.events().isEmpty(), capture.allMessages());
  }


  // ------------------------------------------------------------------ capture self-check

  @Test
  public void testCaptureCopiesEachEvent() {
    // guards against the ReusableLogEventFactory aliasing trap: a capture that stores the raw,
    // thread-reused LogEvent instead of a snapshot would silently corrupt earlier captures
    logger.info("first");
    logger.info("second");
    assertEquals(capture.messages(), List.of("first", "second"));
  }

}

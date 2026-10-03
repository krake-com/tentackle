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

package org.tentackle.log.slf4j;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;

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
 * Tests for {@link SLF4JLogger}.
 * <p>
 * Every test gets a logger of its own (see {@link SLF4JCapture#uniqueLoggerName}), since both
 * {@link SLF4JLogger}'s own logger cache and Logback's internal logger-context cache persist for the
 * JVM's life, and configuring a shared one would leak into the other tests of this module.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class SLF4JLoggerTest {

  private String name;
  private SLF4JLogger logger;
  private SLF4JCapture capture;

  @BeforeMethod
  private void setUp() {
    name = SLF4JCapture.uniqueLoggerName(SLF4JLoggerTest.class);
    capture = SLF4JCapture.attach(name, Level.FINER);
    logger = new SLF4JLogger(name);
  }

  @AfterMethod
  private void tearDown() {
    if (capture != null) {
      capture.close();
    }
    SLF4JMappedDiagnosticContext.getInstance().clear();
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

  private static Throwable thrown(ILoggingEvent event) {
    return event.getThrowableProxy() == null ? null : ((ThrowableProxy) event.getThrowableProxy()).getThrowable();
  }


  // ------------------------------------------------------------------ levels

  @Test
  public void testLevelTranslation() {
    for (Level level : Level.values()) {
      capture.clear();
      logMessage(level, "message");
      ILoggingEvent event = capture.singleEvent();
      assertEquals(event.getLevel(), SLF4JCapture.translate(level), "wrong level for " + level);
      assertEquals(event.getFormattedMessage(), "message");
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
      assertEquals(capture.singleEvent().getFormattedMessage(), "plain message");
      assertNull(thrown(capture.singleEvent()));
    }
  }

  @Test
  public void testMessageWithParams() {
    for (Level level : Level.values()) {
      capture.clear();
      logParams(level, "a={0}, b={1}", "x", 42);
      assertEquals(capture.singleEvent().getFormattedMessage(), "a=x, b=42");
    }
  }

  @Test
  public void testMessageWithCause() {
    for (Level level : Level.values()) {
      capture.clear();
      Exception cause = new IllegalStateException("bad");
      logCause(level, "failed", cause);
      ILoggingEvent event = capture.singleEvent();
      assertEquals(event.getFormattedMessage(), "failed");
      assertSame(thrown(event), cause);
    }
  }

  @Test
  public void testMessageWithParamSuppliers() {
    for (Level level : Level.values()) {
      capture.clear();
      logParamSuppliers(level, "a={0}, b={1}", () -> "x", () -> 42);
      assertEquals(capture.singleEvent().getFormattedMessage(), "a=x, b=42");
    }
  }

  @Test
  public void testNullParamSupplierYieldsNull() {
    logParamSuppliers(Level.INFO, "value={0}", (Supplier<?>) null);
    assertEquals(capture.singleEvent().getFormattedMessage(), "value=null");
  }

  @Test
  public void testMessageSupplier() {
    for (Level level : Level.values()) {
      capture.clear();
      logSupplier(level, () -> "supplied");
      ILoggingEvent event = capture.singleEvent();
      assertEquals(event.getFormattedMessage(), "supplied");
      assertNull(thrown(event));
    }
  }

  @Test
  public void testMessageSupplierWithCause() {
    for (Level level : Level.values()) {
      capture.clear();
      Exception cause = new IllegalStateException("bad");
      logCauseAndSupplier(level, cause, () -> "supplied");
      ILoggingEvent event = capture.singleEvent();
      assertEquals(event.getFormattedMessage(), "supplied");
      assertSame(thrown(event), cause);
    }
  }

  @Test
  public void testGenericLogWithCause() {
    Exception cause = new IllegalStateException("bad");
    logger.log(Level.WARNING, "failed", cause);
    ILoggingEvent event = capture.singleEvent();
    assertEquals(event.getLevel(), SLF4JCapture.translate(Level.WARNING));
    assertEquals(event.getFormattedMessage(), "failed");
    assertSame(thrown(event), cause);
  }

  @Test
  public void testGenericLogWithNullCause() {
    logger.log(Level.INFO, "no cause", (Throwable) null);
    ILoggingEvent event = capture.singleEvent();
    assertEquals(event.getFormattedMessage(), "no cause");
    assertNull(thrown(event));
  }

  @Test
  public void testGenericLogWithParams() {
    logger.log(Level.INFO, "a={0}", "x");
    assertEquals(capture.singleEvent().getFormattedMessage(), "a=x");
  }

  @Test
  public void testGenericLogWithMessageSupplier() {
    Exception cause = new IllegalStateException("bad");
    logger.log(Level.FINE, cause, () -> "supplied");
    ILoggingEvent event = capture.singleEvent();
    assertEquals(event.getLevel(), SLF4JCapture.translate(Level.FINE));
    assertEquals(event.getFormattedMessage(), "supplied");
    assertSame(thrown(event), cause);
  }

  @Test
  public void testMessageWithoutParamsIsNotFormatted() {
    // MessageFormat is only applied if there are parameters
    logger.info("raw {0} text with a ' quote");
    assertEquals(capture.singleEvent().getFormattedMessage(), "raw {0} text with a ' quote");
  }

  @Test
  public void testEmptyParamsAreNotFormatted() {
    logger.info("raw {0} text", new Object[0]);
    assertEquals(capture.singleEvent().getFormattedMessage(), "raw {0} text");
  }

  @Test
  public void testMessageWithParamsUnescapesQuotes() {
    logger.info("it''s {0}", "x");
    assertEquals(capture.singleEvent().getFormattedMessage(), "it's x");
  }

  @Test
  public void testEmptyParamSuppliersAreFormatted() {
    // unlike Object... params, the supplier variants always apply MessageFormat
    logger.info("it''s", new Supplier<?>[0]);
    assertEquals(capture.singleEvent().getFormattedMessage(), "it's");
  }

  @Test
  public void testSlf4jPlaceholdersAreNotInterpreted() {
    // the finished message is passed without arguments, so SLF4J leaves {} alone
    logger.info("raw {} text");
    assertEquals(capture.singleEvent().getFormattedMessage(), "raw {} text");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testSlf4jPlaceholdersWithParamsAreRejected() {
    // {} is not a valid MessageFormat element
    logger.info("raw {} text", "x");
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
    assertEquals(capture.singleEvent().getFormattedMessage(), "expensive");
  }


  // ------------------------------------------------------------------ event details

  @Test
  public void testSourceIsTheCallingMethod() {
    // SLF4JLogger passes its own FQCN to LocationAwareLogger.log so Logback skips its frames -
    // this only works because the test-scope binding (Logback) implements LocationAwareLogger;
    // a non-location-aware binding like slf4j-simple would silently report no caller data at all
    logger.info("from the test");
    ILoggingEvent event = capture.singleEvent();
    assertTrue(event.hasCallerData(), "no caller data captured");
    StackTraceElement caller = event.getCallerData()[0];
    assertEquals(caller.getClassName(), SLF4JLoggerTest.class.getName());
    assertEquals(caller.getMethodName(), "testSourceIsTheCallingMethod");
  }

  @Test
  public void testLoggerNameIsSet() {
    logger.info("named");
    assertEquals(capture.singleEvent().getLoggerName(), name);
  }

  @Test
  public void testGetLoggerImpl() {
    String freshName = SLF4JCapture.uniqueLoggerName(SLF4JLoggerTest.class);
    SLF4JLogger freshLogger = new SLF4JLogger(freshName);
    Object impl = freshLogger.getLoggerImpl();
    assertTrue(impl instanceof org.slf4j.Logger);
    assertEquals(((org.slf4j.Logger) impl).getName(), freshName);
  }


  // ------------------------------------------------------------------ instances

  @Test
  public void testGetLoggerIsCached() {
    String freshName = SLF4JCapture.uniqueLoggerName(SLF4JLoggerTest.class);
    assertSame(SLF4JLogger.getLogger(freshName), SLF4JLogger.getLogger(freshName));
  }

  @Test
  public void testGetLoggerIsPerName() {
    SLF4JLogger one = SLF4JLogger.getLogger(SLF4JCapture.uniqueLoggerName(SLF4JLoggerTest.class));
    SLF4JLogger other = SLF4JLogger.getLogger(SLF4JCapture.uniqueLoggerName(SLF4JLoggerTest.class));
    assertNotSame(one, other);
    assertNotSame(one.getLoggerImpl(), other.getLoggerImpl());
  }

  @Test
  public void testConstructorIsNotCached() {
    String freshName = SLF4JCapture.uniqueLoggerName(SLF4JLoggerTest.class);
    SLF4JLogger cached = SLF4JLogger.getLogger(freshName);
    SLF4JLogger created = new SLF4JLogger(freshName);
    assertNotSame(created, cached);
    // unlike Log4J2Logger (see Log4J2LoggerTest#testConstructorIsNotCached), the constructor here just
    // calls org.slf4j.LoggerFactory.getLogger(name) again, and SLF4J bindings (Logback included) cache
    // their own Logger instances per name internally - so the wrapped backend logger IS shared, even
    // though the SLF4JLogger wrapper around it is not; do not "fix" this back to assertNotSame
    assertSame(created.getLoggerImpl(), cached.getLoggerImpl());
  }

  @Test
  public void testMappedDiagnosticContextIsTheSingleton() {
    assertSame(logger.getMappedDiagnosticContext(), SLF4JMappedDiagnosticContext.getInstance());
  }


  // ------------------------------------------------------------------ MDC

  @Test
  public void testMappedDiagnosticContextIsVisibleOnEvent() {
    // unlike DefaultLogger, SLF4JLogger does not prepend the MDC into the message text - Logback
    // exposes it as a structured property map on the event instead
    SLF4JMappedDiagnosticContext.getInstance().put("user", "harald");
    logger.info("hello");
    ILoggingEvent event = capture.singleEvent();
    assertEquals(event.getFormattedMessage(), "hello");
    assertEquals(event.getMDCPropertyMap().get("user"), "harald");
  }

  @Test
  public void testMappedDiagnosticContextWithSeveralEntries() {
    SLF4JMappedDiagnosticContext.getInstance().put("user", "harald");
    SLF4JMappedDiagnosticContext.getInstance().put("host", "localhost");
    logger.info("hello");
    ILoggingEvent event = capture.singleEvent();
    assertEquals(event.getMDCPropertyMap().get("user"), "harald");
    assertEquals(event.getMDCPropertyMap().get("host"), "localhost");
  }

  @Test
  public void testEmptyMappedDiagnosticContextHasNoContextData() {
    logger.info("hello");
    ILoggingEvent event = capture.singleEvent();
    assertEquals(event.getFormattedMessage(), "hello");
    assertTrue(event.getMDCPropertyMap().isEmpty());
  }

  @Test
  public void testRemovedMappedDiagnosticContextIsNotOnEvent() {
    SLF4JMappedDiagnosticContext.getInstance().put("user", "harald");
    SLF4JMappedDiagnosticContext.getInstance().remove("user");
    logger.info("hello");
    assertTrue(capture.singleEvent().getMDCPropertyMap().isEmpty());
  }


  // ------------------------------------------------------------------ stacktraces

  @Test
  public void testLogStacktraceDefaultsToSevere() {
    logger.logStacktrace(new IllegalStateException("boom"));
    List<ILoggingEvent> events = capture.events();
    assertFalse(events.isEmpty());
    for (ILoggingEvent event : events) {
      assertEquals(event.getLevel(), SLF4JCapture.translate(Level.SEVERE));
    }
    String logged = capture.allMessages();
    assertTrue(logged.contains("java.lang.IllegalStateException: boom"), logged);
    assertTrue(logged.contains(SLF4JLoggerTest.class.getName() + ".testLogStacktraceDefaultsToSevere"), logged);
  }

  @Test
  public void testLogStacktraceWithLevel() {
    logger.logStacktrace(Level.FINE, new IllegalStateException("boom"));
    List<ILoggingEvent> events = capture.events();
    assertFalse(events.isEmpty());
    for (ILoggingEvent event : events) {
      assertEquals(event.getLevel(), SLF4JCapture.translate(Level.FINE));
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
    for (ILoggingEvent event : capture.events()) {
      assertFalse(event.getFormattedMessage().isEmpty(), "empty event among " + capture.events().size());
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
    // guards against a capture that would alias/reuse the same event instance across calls,
    // silently corrupting earlier captures (mirrors Log4J2LoggerTest's equivalent self-check,
    // even though Logback creates a fresh LoggingEvent per call and has no such aliasing trap)
    logger.info("first");
    logger.info("second");
    assertEquals(capture.messages(), List.of("first", "second"));
  }

}

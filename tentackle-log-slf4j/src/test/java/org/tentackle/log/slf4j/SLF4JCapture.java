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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures the {@link ILoggingEvent}s emitted by an SLF4J logger.
 * <p>
 * {@link SLF4JLogger} always dispatches through whichever {@link org.slf4j.spi.SLF4JServiceProvider}
 * SLF4J resolves at runtime, so this test capture wires directly against Logback (the module's own
 * test-scope binding, see {@code tentackle-log-slf4j/pom.xml}) rather than a backend-agnostic API -
 * there is no SLF4J-native equivalent of {@code Log4J2Capture}, since plain SLF4J has no capturing-
 * appender concept of its own.
 * <p>
 * A capture must be {@link #close()}d again (or used via try-with-resources), since it attaches an
 * appender to a real, shared {@link Logger} instance that would otherwise leak into other tests.
 * Each test should use a {@link #uniqueLoggerName(Class)} of its own: both {@link SLF4JLogger}'s own
 * static logger cache and Logback's internal per-context logger cache persist for the JVM's life.
 *
 * @author harald
 */
@SuppressWarnings("exports")
public class SLF4JCapture implements AutoCloseable {

  private static final AtomicLong UNIQUE_COUNTER = new AtomicLong();

  /**
   * Creates a logger name that is unique within this JVM.
   *
   * @param owner the class requesting the name
   * @return the unique logger name
   */
  public static String uniqueLoggerName(Class<?> owner) {
    return owner.getName() + ".logger" + UNIQUE_COUNTER.incrementAndGet();
  }

  /**
   * Translates a Tentackle level to its Logback counterpart.
   *
   * @param level the Tentackle level, null for {@link Level#OFF}
   * @return the Logback level
   */
  public static Level translate(org.tentackle.log.Logger.Level level) {
    if (level == null) {
      return Level.OFF;
    }
    return switch (level) {
      case FINER -> Level.TRACE;
      case FINE -> Level.DEBUG;
      case INFO -> Level.INFO;
      case WARNING -> Level.WARN;
      case SEVERE -> Level.ERROR;
    };
  }

  /**
   * Attaches a capture to the logger with the given name.
   *
   * @param name the logger name
   * @param level the level to set on the logger, null for {@link Level#OFF}
   * @return the capture
   */
  public static SLF4JCapture attach(String name, org.tentackle.log.Logger.Level level) {
    return new SLF4JCapture(name, translate(level));
  }


  private final Logger logger;
  private final CapturingAppender appender;
  private boolean closed;

  private SLF4JCapture(String name, Level level) {
    logger = (Logger) org.slf4j.LoggerFactory.getLogger(name);
    logger.setLevel(level);
    logger.setAdditive(false);   // keep captured output off the root/console appender
    appender = new CapturingAppender();
    appender.setContext(logger.getLoggerContext());
    appender.start();
    logger.addAppender(appender);
  }

  /**
   * Sets the level of the observed logger.
   *
   * @param level the Tentackle level, null for {@link Level#OFF}
   */
  public void setLevel(org.tentackle.log.Logger.Level level) {
    logger.setLevel(translate(level));
  }

  /**
   * Gets all events captured so far.
   *
   * @return the events, never null
   */
  public List<ILoggingEvent> events() {
    return new ArrayList<>(appender.list);
  }

  /**
   * Gets the single event captured so far.
   *
   * @return the event
   * @throws AssertionError if not exactly one event was captured
   */
  public ILoggingEvent singleEvent() {
    List<ILoggingEvent> events = events();
    if (events.size() != 1) {
      throw new AssertionError("expected exactly one log event, but got " + messages());
    }
    return events.get(0);
  }

  /**
   * Gets the formatted messages of all events captured so far.
   *
   * @return the messages, never null
   */
  public List<String> messages() {
    List<String> messages = new ArrayList<>();
    for (ILoggingEvent event : events()) {
      messages.add(event.getFormattedMessage());
    }
    return messages;
  }

  /**
   * Gets the messages of all events captured so far as a single string.
   *
   * @return the concatenated messages, never null
   */
  public String allMessages() {
    return String.join("\n", messages());
  }

  /**
   * Drops all events captured so far.
   */
  public void clear() {
    appender.list.clear();
  }

  /**
   * Detaches the capture from the logger it was attached to.
   */
  @Override
  public void close() {
    if (!closed) {
      closed = true;
      logger.detachAppender(appender);
      appender.stop();
    }
  }


  private static final class CapturingAppender extends ListAppender<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
      // Logback's LoggingEvent computes caller data lazily, from a freshly captured stacktrace,
      // the first time getCallerData() is invoked - forcing it here (still on the original calling
      // thread's stack, synchronously within the doLog() -> callAppenders() -> append() chain) is
      // required, or a later, out-of-band call from a test would capture the wrong stack entirely
      event.getCallerData();
      super.append(event);
    }
  }

}

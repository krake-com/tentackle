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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import org.tentackle.log.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures the {@link LogEvent}s emitted by a Log4J2 logger.
 * <p>
 * {@link Log4J2Logger} is backed by log4j-core, not {@code java.util.logging}, so tentackle-core's
 * {@code LogCapture} (which attaches a {@code java.util.logging.Handler}) does not apply here.
 * Instead, this registers a dedicated {@link LoggerConfig} plus a small capturing
 * {@link org.apache.logging.log4j.core.Appender} for a given logger name in the current
 * {@link LoggerContext}.
 * <p>
 * A capture must be {@link #close()}d again (or used via try-with-resources), since it installs a
 * logger config into the shared {@link LoggerContext} that would otherwise leak into other tests.
 * Each test should use a {@link #uniqueLoggerName(Class)} of its own: both {@link Log4J2Logger}'s
 * own static logger cache and log4j-core's internal per-context logger cache persist for the JVM's
 * life.
 *
 * @author harald
 */
@SuppressWarnings("exports")
public class Log4J2Capture implements AutoCloseable {

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
   * Translates a Tentackle level to its log4j2 counterpart.
   *
   * @param level the Tentackle level, null for {@link Level#OFF}
   * @return the log4j2 level
   */
  public static Level translate(Logger.Level level) {
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
  public static Log4J2Capture attach(String name, Logger.Level level) {
    return new Log4J2Capture(name, translate(level));
  }


  private final String name;
  private final LoggerContext context;
  private final Configuration configuration;
  private final LoggerConfig loggerConfig;
  private final CapturingAppender appender;
  private boolean closed;

  private Log4J2Capture(String name, Level level) {
    this.name = name;
    context = (LoggerContext) LogManager.getContext(false);
    configuration = context.getConfiguration();
    appender = new CapturingAppender(name + ".appender");
    appender.start();
    // built once: re-registering a fresh LoggerConfig for the same name on every level change does
    // NOT propagate to a Logger already obtained via LogManager.getLogger (verified empirically) -
    // the supported way to change the level afterward is to mutate this very instance, see setLevel
    loggerConfig = LoggerConfig.newBuilder()
        .setLoggerName(name)
        .setLevel(level)
        .setConfig(configuration)
        .setIncludeLocation("true")
        .setAdditivity(false)   // keep captured output off Root/Console
        .build();
    loggerConfig.addAppender(appender, null, null);
    configuration.addLogger(name, loggerConfig);
    context.updateLoggers();
  }

  /**
   * Sets the level of the observed logger.
   *
   * @param level the Tentackle level, null for {@link Level#OFF}
   */
  public void setLevel(Logger.Level level) {
    loggerConfig.setLevel(translate(level));
    context.updateLoggers();
  }

  /**
   * Gets all events captured so far.
   *
   * @return the events, never null
   */
  public List<LogEvent> events() {
    return new ArrayList<>(appender.events);
  }

  /**
   * Gets the single event captured so far.
   *
   * @return the event
   * @throws AssertionError if not exactly one event was captured
   */
  public LogEvent singleEvent() {
    List<LogEvent> events = events();
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
    for (LogEvent event : events()) {
      messages.add(event.getMessage().getFormattedMessage());
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
   * Gets all events whose formatted message contains the given text.
   *
   * @param text the text to look for
   * @return the matching events, never null
   */
  public List<LogEvent> eventsMatching(String text) {
    List<LogEvent> matching = new ArrayList<>();
    for (LogEvent event : events()) {
      String message = event.getMessage().getFormattedMessage();
      if (message != null && message.contains(text)) {
        matching.add(event);
      }
    }
    return matching;
  }

  /**
   * Drops all events captured so far.
   */
  public void clear() {
    appender.events.clear();
  }

  /**
   * Detaches the capture and removes the logger config it installed.
   */
  @Override
  public void close() {
    if (!closed) {
      closed = true;
      configuration.removeLogger(name);
      context.updateLoggers();
      appender.stop();
    }
  }


  private static final class CapturingAppender extends AbstractAppender {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    private CapturingAppender(String name) {
      super(name, null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      // the default ReusableLogEventFactory hands appenders a mutable, thread-reused event,
      // so it must be snapshotted before storing it
      events.add(event.toImmutable());
    }
  }

}

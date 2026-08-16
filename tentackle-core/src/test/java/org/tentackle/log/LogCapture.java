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

import org.testng.SkipException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Captures the {@link LogRecord}s emitted by a Tentackle {@link Logger}.
 * <p>
 * The whole {@code org.tentackle.log} package is backed by {@code java.util.logging}, so the records
 * are collected by a {@link Handler} attached to the {@code java.util.logging.Logger} returned by
 * {@link Logger#getLoggerImpl()}. The technique is the same as in {@code QuicLoggerTest} of
 * {@code tentackle-quic}.
 * <p>
 * A capture <em>must</em> be {@link #close()}d again, since it changes the level, the handlers and
 * the parent-handler flag of a logger that is shared with the rest of the test module. Use it either
 * with try-with-resources or from an {@code @AfterMethod}.
 *
 * @author harald
 */
public class LogCapture implements AutoCloseable {

  /** counter to build unique logger names. */
  private static final AtomicLong UNIQUE_COUNTER = new AtomicLong();

  /**
   * Creates a logger name that is unique within this JVM.
   * <p>
   * Loggers are cached forever (both by {@link DefaultLogger} and by the JDK's {@code LogManager}),
   * hence tests that configure a logger should use a name of their own instead of sharing one.
   *
   * @param owner the class requesting the name
   * @return the unique logger name
   */
  public static String uniqueLoggerName(Class<?> owner) {
    return owner.getName() + ".logger" + UNIQUE_COUNTER.incrementAndGet();
  }

  /**
   * Attaches a capture to the logger of the given class.
   *
   * @param clazz the class the logger belongs to
   * @param level the level to set on the logger
   * @return the capture
   */
  public static LogCapture attach(Class<?> clazz, Logger.Level level) {
    return attach(Logger.get(clazz), level);
  }

  /**
   * Attaches a capture to the logger with the given name.
   *
   * @param name the logger name
   * @param level the level to set on the logger
   * @return the capture
   */
  public static LogCapture attach(String name, Logger.Level level) {
    return attach(LoggerFactory.getLogger(name), level);
  }

  /**
   * Attaches a capture to the given logger.
   *
   * @param logger the logger to observe
   * @param level the level to set on the logger
   * @return the capture
   */
  public static LogCapture attach(Logger logger, Logger.Level level) {
    return new LogCapture(logger, translate(level));
  }

  /**
   * Translates a Tentackle level to its {@code java.util.logging} counterpart.
   *
   * @param level the Tentackle level, null for {@link java.util.logging.Level#OFF}
   * @return the java-util-logging level
   */
  public static java.util.logging.Level translate(Logger.Level level) {
    if (level == null) {
      return java.util.logging.Level.OFF;
    }
    return switch (level) {
      case FINER -> java.util.logging.Level.FINER;
      case FINE -> java.util.logging.Level.FINE;
      case INFO -> java.util.logging.Level.INFO;
      case WARNING -> java.util.logging.Level.WARNING;
      case SEVERE -> java.util.logging.Level.SEVERE;
    };
  }


  private final Logger logger;                          // strong reference, pins the JUL logger
  private final java.util.logging.Logger julLogger;
  private final Handler handler;
  private final java.util.logging.Level savedLevel;
  private final boolean savedUseParentHandlers;
  private final List<LogRecord> records;
  private boolean closed;


  private LogCapture(Logger logger, java.util.logging.Level level) {
    this.logger = logger;
    Object loggerImpl = logger.getLoggerImpl();
    if (!(loggerImpl instanceof java.util.logging.Logger jul)) {
      throw new SkipException("Tentackle is not backed by java.util.logging here, got " + loggerImpl);
    }
    julLogger = jul;
    records = new CopyOnWriteArrayList<>();
    savedLevel = julLogger.getLevel();
    savedUseParentHandlers = julLogger.getUseParentHandlers();
    julLogger.setLevel(level);
    julLogger.setUseParentHandlers(false);    // keep the test output clean
    handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        records.add(record);
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    };
    handler.setLevel(java.util.logging.Level.ALL);
    julLogger.addHandler(handler);
  }


  /**
   * Gets the observed logger.
   *
   * @return the logger
   */
  public Logger getLogger() {
    return logger;
  }

  /**
   * Sets the level of the observed logger.
   *
   * @param level the level, null for {@link java.util.logging.Level#OFF}
   */
  public void setLevel(Logger.Level level) {
    julLogger.setLevel(translate(level));
  }

  /**
   * Gets all records captured so far.
   *
   * @return the records, never null
   */
  public List<LogRecord> records() {
    return records;
  }

  /**
   * Gets the single record captured so far.
   *
   * @return the record
   * @throws AssertionError if not exactly one record was captured
   */
  public LogRecord singleRecord() {
    if (records.size() != 1) {
      throw new AssertionError("expected exactly one log record, but got " + messages());
    }
    return records.get(0);
  }

  /**
   * Gets the messages of all records captured so far.
   *
   * @return the messages, never null
   */
  public List<String> messages() {
    List<String> messages = new ArrayList<>();
    for (LogRecord record : records) {
      messages.add(record.getMessage());
    }
    return messages;
  }

  /**
   * Gets the messages of all records captured so far as a single string.
   * <p>
   * Handy whenever the number of records is an implementation detail, such as for stacktraces.
   *
   * @return the concatenated messages, never null
   */
  public String allMessages() {
    return String.join("\n", messages());
  }

  /**
   * Gets all records whose message contains the given text.
   *
   * @param text the text to look for
   * @return the matching records, never null
   */
  public List<LogRecord> recordsMatching(String text) {
    List<LogRecord> matching = new ArrayList<>();
    for (LogRecord record : records) {
      if (record.getMessage() != null && record.getMessage().contains(text)) {
        matching.add(record);
      }
    }
    return matching;
  }

  /**
   * Drops all records captured so far.
   */
  public void clear() {
    records.clear();
  }

  /**
   * Detaches the capture and restores the logger's configuration.
   */
  @Override
  public void close() {
    if (!closed) {
      closed = true;
      julLogger.removeHandler(handler);
      julLogger.setLevel(savedLevel);
      julLogger.setUseParentHandlers(savedUseParentHandlers);
    }
  }

}

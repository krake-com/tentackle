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

import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

import org.tentackle.common.Service;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerOutputStream;
import org.tentackle.log.MappedDiagnosticContext;

import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * Tentackle {@link Logger} backed by Log4J 2 ({@code org.apache.logging.log4j}).
 * <p>
 * Registered as {@code @Service(Logger.class)}, so it is picked up by the {@code DefaultLoggerFactory}
 * as soon as this module is on the class- or module path.<br>
 * Messages are formatted with {@link MessageFormat} (not Log4J's {@code {}} placeholders) and logged
 * on behalf of the caller, i.e. Log4J reports the caller's location, not this wrapper's.
 *
 * @author harald
 */
@Service(Logger.class)
public class Log4J2Logger implements Logger {

  // the wrapper's classname: Log4J skips it when determining the caller's location
  private static final String FQCN = Log4J2Logger.class.getName();

  // cached loggers, one per name (never evicted)
  private static final HashMap<String, Log4J2Logger> LOGGERS = new HashMap<>();

  /**
   * Gets the Log4J2Logger for the given name.<br>
   * If a logger with that name already exists, it will be reused.
   * <p>
   * This is the method {@code DefaultLoggerFactory} prefers over the constructor.
   * The cache is thread-safe and holds the loggers for the lifetime of the JVM.
   *
   * @param name the name of the logger
   * @return the logger
   */
  public static Log4J2Logger getLogger(String name) {
    synchronized(LOGGERS) {
      return LOGGERS.computeIfAbsent(name, Log4J2Logger::new);
    }
  }


  private final ExtendedLoggerWrapper logger;   // the wrapped Log4J logger


  /**
   * Creates a logger.<br>
   * Applications should use {@link #getLogger(String)} instead, which reuses existing instances.
   *
   * @param name the name of the logger
   */
  public Log4J2Logger(String name) {
    org.apache.logging.log4j.Logger lgr = org.apache.logging.log4j.LogManager.getLogger(name);
    logger = new ExtendedLoggerWrapper((ExtendedLogger) lgr, lgr.getName(), lgr.getMessageFactory());
  }


  /**
   * {@inheritDoc}
   * <p>
   * Returns the {@link ExtendedLoggerWrapper} around the Log4J logger, not the
   * {@code org.apache.logging.log4j.Logger} obtained from the {@code LogManager}.
   */
  @Override
  public Object getLoggerImpl() {
    return logger;
  }


  @Override
  public boolean isLoggable(Level level) {
    return logger.isEnabled(translateLevel(level));
  }


  @Override
  public void log(Level level, String message, Throwable cause) {
    doLog(level, message, cause);
  }

  @Override
  public void log(Level level, Throwable cause, Supplier<String> messageSupplier) {
    doLog(level, cause, null, messageSupplier);
  }

  @Override
  public void log(Level level, String message, Object... params) {
    doLog(level, message, null, params);
  }

  @Override
  public void finer(String message) {
    doLog(Level.FINER, message, null);
  }

  @Override
  public void fine(String message) {
    doLog(Level.FINE,  message, null);
  }

  @Override
  public void info(String message) {
    doLog(Level.INFO,  message, null);
  }

  @Override
  public void warning(String message) {
    doLog(Level.WARNING,  message, null);
  }

  @Override
  public void severe(String message) {
    doLog(Level.SEVERE,  message, null);
  }


  @Override
  public void finer(String message, Object... params) {
    doLog(Level.FINER, message, null, params);
  }

  @Override
  public void fine(String message, Object... params) {
    doLog(Level.FINE, message, null, params);
  }

  @Override
  public void info(String message, Object... params) {
    doLog(Level.INFO, message, null, params);
  }

  @Override
  public void warning(String message, Object... params) {
    doLog(Level.WARNING, message, null, params);
  }

  @Override
  public void severe(String message, Object... params) {
    doLog(Level.SEVERE, message, null, params);
  }


  @Override
  public void finer(String message, Supplier<?>... paramSuppliers) {
    doLog(Level.FINER, null, message, paramSuppliers);
  }

  @Override
  public void fine(String message, Supplier<?>... paramSuppliers) {
    doLog(Level.FINE, null, message, paramSuppliers);
  }

  @Override
  public void info(String message, Supplier<?>... paramSuppliers) {
    doLog(Level.INFO, null, message, paramSuppliers);
  }

  @Override
  public void warning(String message, Supplier<?>... paramSuppliers) {
    doLog(Level.WARNING, null, message, paramSuppliers);
  }

  @Override
  public void severe(String message, Supplier<?>... paramSuppliers) {
    doLog(Level.SEVERE, null, message, paramSuppliers);
  }


  @Override
  public void finer(String message, Throwable cause) {
    doLog(Level.FINER, message, cause);
  }

  @Override
  public void fine(String message, Throwable cause) {
    doLog(Level.FINE, message, cause);
  }

  @Override
  public void info(String message, Throwable cause) {
    doLog(Level.INFO, message, cause);
  }

  @Override
  public void warning(String message, Throwable cause) {
    doLog(Level.WARNING, message, cause);
  }

  @Override
  public void severe(String message, Throwable cause) {
    doLog(Level.SEVERE, message, cause);
  }


  @Override
  public void finer(Throwable cause, Supplier<String> messageSupplier) {
    doLog(Level.FINER, cause, null, messageSupplier);
  }

  @Override
  public void fine(Throwable cause, Supplier<String> messageSupplier) {
    doLog(Level.FINE, cause, null, messageSupplier);
  }

  @Override
  public void info(Throwable cause, Supplier<String> messageSupplier) {
    doLog(Level.INFO, cause, null, messageSupplier);
  }

  @Override
  public void warning(Throwable cause, Supplier<String> messageSupplier) {
    doLog(Level.WARNING, cause, null, messageSupplier);
  }

  @Override
  public void severe(Throwable cause, Supplier<String> messageSupplier) {
    doLog(Level.SEVERE, cause, null, messageSupplier);
  }


  @Override
  public void finer(Supplier<String> messageSupplier) {
    doLog(Level.FINER, null, null, messageSupplier);
  }

  @Override
  public void fine(Supplier<String> messageSupplier) {
    doLog(Level.FINE, null, null, messageSupplier);
  }

  @Override
  public void info(Supplier<String> messageSupplier) {
    doLog(Level.INFO, null, null, messageSupplier);
  }

  @Override
  public void warning(Supplier<String> messageSupplier) {
    doLog(Level.WARNING, null, null, messageSupplier);
  }

  @Override
  public void severe(Supplier<String> messageSupplier) {
    doLog(Level.SEVERE, null, null, messageSupplier);
  }


  @Override
  public boolean isFinerLoggable() {
    return isLoggable(Level.FINER);
  }

  @Override
  public boolean isFineLoggable() {
    return isLoggable(Level.FINE);
  }

  @Override
  public boolean isInfoLoggable() {
    return isLoggable(Level.INFO);
  }

  @Override
  public boolean isWarningLoggable() {
    return isLoggable(Level.WARNING);
  }

  @Override
  public boolean isSevereLoggable() {
    return isLoggable(Level.SEVERE);
  }

  /**
   * Logs the stacktrace of a throwable.<br>
   * The stacktrace is logged line by line via a {@link LoggerOutputStream}.
   *
   * @param level   the logging level
   * @param cause   the Throwable to log the stacktrace for
   */
  @Override
  public void logStacktrace(Level level, Throwable cause) {
    try (PrintStream ps = new PrintStream(new LoggerOutputStream(this, level))) {
      cause.printStackTrace(ps);
    }
  }

  /**
   * Logs the stacktrace of a throwable with a logging level of SEVERE.
   *
   * @param cause   the Throwable to log the stacktrace for
   */
  @Override
  public void logStacktrace(Throwable cause) {
    logStacktrace(Level.SEVERE, cause);
  }

  @Override
  public MappedDiagnosticContext getMappedDiagnosticContext() {
    return Log4J2MappedDiagnosticContext.getInstance();
  }


  /**
   * Logs a message with optional parameters.
   * <p>
   * If there are parameters, the message is formatted with {@link MessageFormat}.
   * Otherwise, it is logged verbatim, i.e. quotes are not unescaped and placeholders stay as they are.
   *
   * @param level the log level
   * @param message the message
   * @param cause the cause, null if none
   * @param params optional parameters
   */
  protected void doLog(Level level, String message, Throwable cause, Object... params) {
    // Log4J's {} placeholders differ from MessageFormat: format here and hand Log4J the finished message
    doLog(level, cause, null, () -> params != null && params.length > 0 ? MessageFormat.format(message, params) : message);
  }

  /**
   * Logs a message with lazily evaluated parameters.
   * <p>
   * The suppliers are evaluated only if the level is enabled. The message is always formatted
   * with {@link MessageFormat}, even if there are no suppliers.
   *
   * @param level the log level
   * @param cause the cause, null if none
   * @param message the message
   * @param paramSuppliers parameter suppliers, null elements yield null parameters
   */
  protected void doLog(Level level, Throwable cause, String message, Supplier<?>... paramSuppliers) {
    doLog(level, cause, null, () -> {
      Object[] params = new Object[paramSuppliers.length];
      for (int i=0; i < params.length; i++) {
        Supplier<?> supplier = paramSuppliers[i];
        params[i] = supplier == null ? null : supplier.get();
      }
      // Log4J's {} placeholders differ from MessageFormat: format here and hand Log4J the finished message
      return MessageFormat.format(message, params);
    });
  }

  /**
   * Logs a message or the result of a message supplier.<br>
   * All other logging methods end up here.
   * <p>
   * The message is created only if the level is enabled. The caller's location is determined
   * by Log4J relative to this class.
   *
   * @param level the log level
   * @param cause the cause, null if none
   * @param message the message, ignored if messageSupplier is not null
   * @param messageSupplier the message supplier, null to log message
   */
  protected void doLog(Level level, Throwable cause, String message, Supplier<String> messageSupplier) {
    logger.logIfEnabled(FQCN, translateLevel(level), null,
                        () -> new SimpleMessage(messageSupplier != null ? messageSupplier.get() : message),
                        cause);
  }


  /**
   * Translates the Tentackle logging level to the Log4J level.
   *
   * @param level the Tentackle level
   * @return the Log4J level ({@code ERROR} for {@code SEVERE})
   */
  private org.apache.logging.log4j.Level translateLevel(Level level) {
    return switch (level) {
      case FINER -> org.apache.logging.log4j.Level.TRACE;
      case FINE -> org.apache.logging.log4j.Level.DEBUG;
      case INFO -> org.apache.logging.log4j.Level.INFO;
      case WARNING -> org.apache.logging.log4j.Level.WARN;
      default -> org.apache.logging.log4j.Level.ERROR;
    };
  }

}

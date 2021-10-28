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
 * Pluggable logger using <code>org.apache.log4j</code>.
 * <p>
 * Logger wrapper for Log4J version 2.
 *
 * @author harald
 */
@Service(Logger.class)
public class Log4J2Logger implements Logger {

  // classname to exclude from stacktrace
  private static final String FQCN = Log4J2Logger.class.getName();

  // cached loggers
  private static final HashMap<String, Log4J2Logger> LOGGERS = new HashMap<>();

  /**
   * Gets the Log4J2Logger for given name.
   * If a logger with that name already exists, it will be re-used.
   *
   * @param name the name of the logger
   * @return the logger
   */
  public static Log4J2Logger getLogger(String name) {
    synchronized(LOGGERS) {
      return LOGGERS.computeIfAbsent(name, Log4J2Logger::new);
    }
  }


  private final ExtendedLoggerWrapper logger;   // the Log4J logger


  /**
   * Creates a logger.
   *
   * @param name the name of the logger
   */
  public Log4J2Logger(String name) {
    org.apache.logging.log4j.Logger lgr = org.apache.logging.log4j.LogManager.getLogger(name);
    logger = new ExtendedLoggerWrapper((ExtendedLogger) lgr, lgr.getName(), lgr.getMessageFactory());
  }


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
   * Logs the stacktrace of a throwable.
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
   * Translates the tentackle logging level to a LOG4J level.
   *
   * @param level the tt level
   * @return the LOG4J level
   */
  protected org.apache.logging.log4j.Level translateLevel(Level level) {
    return switch (level) {
      case FINER -> org.apache.logging.log4j.Level.TRACE;
      case FINE -> org.apache.logging.log4j.Level.DEBUG;
      case INFO -> org.apache.logging.log4j.Level.INFO;
      case WARNING -> org.apache.logging.log4j.Level.WARN;
      default -> org.apache.logging.log4j.Level.ERROR;
    };
  }


  /**
   * Logging workhorse.
   *
   * @param level the log level
   * @param message the message
   * @param cause the cause
   * @param params optional parameters
   */
  protected void doLog(Level level, String message, Throwable cause, Object... params) {
    // LOG4J parameter strings are incompatible with {@link MessageFormat}
    doLog(level, cause, null, () -> params != null && params.length > 0 ? MessageFormat.format(message, params) : message);
  }

  /**
   * Logging workhorse.
   *
   * @param level the log level
   * @param cause the cause
   * @param message the message
   * @param paramSuppliers parameter suppliers
   */
  protected void doLog(Level level, Throwable cause, String message, Supplier<?>... paramSuppliers) {
    doLog(level, cause, null, () -> {
      Object[] params = new Object[paramSuppliers.length];
      for (int i=0; i < params.length; i++) {
        Supplier<?> supplier = paramSuppliers[i];
        params[i] = supplier == null ? null : supplier.get();
      }
      // LOG4J parameter strings are incompatible with {@link MessageFormat}
      return MessageFormat.format(message, params);
    });
  }

  /**
   * Logging workhorse.
   *
   * @param level the log level
   * @param cause the cause, null if none
   * @param message the message, null if messageSupplier
   * @param messageSupplier the message supplier, null if message
   */
  protected void doLog(Level level, Throwable cause, String message, Supplier<String> messageSupplier) {
    logger.logIfEnabled(FQCN, translateLevel(level), null,
                        () -> new SimpleMessage(messageSupplier != null ? messageSupplier.get() : message),
                        cause);
  }

}

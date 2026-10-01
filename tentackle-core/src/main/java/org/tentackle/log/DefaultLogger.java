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

import org.tentackle.common.StringHelper;

import java.io.PrintStream;
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.LogRecord;

/**
 * Pluggable logger using the standard java logging via <code>java.util.logging</code>.
 * <p>
 * Deliberately not annotated as a service: the {@link DefaultLoggerFactory} falls back to this logger
 * if no other logging backend is configured.<br>
 * The optional {@link MappedDiagnosticContext} is prepended to each message.
 *
 * @author harald
 */
public class DefaultLogger implements Logger {

  // cached loggers
  private static final HashMap<String,DefaultLogger> LOGGERS = new HashMap<>();

  /**
   * Gets the DefaultLogger for given name.<br>
   * If a logger with that name already exists, it will be re-used.
   *
   * @param name the name of the logger
   * @return the logger
   */
  public static DefaultLogger getLogger(String name) {
    synchronized(LOGGERS) {
      return LOGGERS.computeIfAbsent(name, DefaultLogger::new);
    }
  }


  private final java.util.logging.Logger logger;    // Java SE standard logger


  /**
   * Creates a logger.
   *
   * @param name the name of the logger
   */
  public DefaultLogger(String name) {
    logger = java.util.logging.Logger.getLogger(name);
  }



  @Override
  public Object getLoggerImpl() {
    return logger;
  }


  private java.util.logging.Level translateLevel(Level level) {
    return switch (level) {
      case FINER -> java.util.logging.Level.FINER;
      case FINE -> java.util.logging.Level.FINE;
      case INFO -> java.util.logging.Level.INFO;
      case WARNING -> java.util.logging.Level.WARNING;
      default -> java.util.logging.Level.SEVERE;
    };
  }

  @Override
  public boolean isLoggable(Level level) {
    return logger.isLoggable(translateLevel(level));
  }



  @Override
  public void log(Level level, String message, Throwable cause) {
    doLog(level, message, cause);
  }

  @Override
  public void log(Level level, Throwable cause, Supplier<String> messageSupplier) {
    doLog(level, cause, messageSupplier);
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
    doLog(Level.FINE, message, null);
  }

  @Override
  public void info(String message) {
    doLog(Level.INFO, message, null);
  }

  @Override
  public void warning(String message) {
    doLog(Level.WARNING, message, null);
  }

  @Override
  public void severe(String message) {
    doLog(Level.SEVERE, message, null);
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
    doLog(Level.FINER, cause, messageSupplier);
  }

  @Override
  public void fine(Throwable cause, Supplier<String> messageSupplier) {
    doLog(Level.FINE, cause, messageSupplier);
  }

  @Override
  public void info(Throwable cause, Supplier<String> messageSupplier) {
    doLog(Level.INFO, cause, messageSupplier);
  }

  @Override
  public void warning(Throwable cause, Supplier<String> messageSupplier) {
    doLog(Level.WARNING, cause, messageSupplier);
  }

  @Override
  public void severe(Throwable cause, Supplier<String> messageSupplier) {
    doLog(Level.SEVERE, cause, messageSupplier);
  }


  @Override
  public void finer(Supplier<String> messageSupplier) {
    doLog(Level.FINER, null, messageSupplier);
  }

  @Override
  public void fine(Supplier<String> messageSupplier) {
    doLog(Level.FINE, null, messageSupplier);
  }

  @Override
  public void info(Supplier<String> messageSupplier) {
    doLog(Level.INFO, null, messageSupplier);
  }

  @Override
  public void warning(Supplier<String> messageSupplier) {
    doLog(Level.WARNING, null, messageSupplier);
  }

  @Override
  public void severe(Supplier<String> messageSupplier) {
    doLog(Level.SEVERE, null, messageSupplier);
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
  public DefaultMappedDiagnosticContext getMappedDiagnosticContext() {
    return DefaultMappedDiagnosticContext.getInstance();
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
    doLog(level, cause, () -> buildMessage(message, params));
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
    doLog(level, cause, () -> {
      Object[] params = new Object[paramSuppliers.length];
      for (int i=0; i < params.length; i++) {
        Supplier<?> supplier = paramSuppliers[i];
        params[i] = supplier == null ? null : supplier.get();
      }
      return buildMessage(message, params);
    });
  }

  /**
   * Logging workhorse.
   *
   * @param level the log level
   * @param cause the cause
   * @param messageSupplier the message supplier
   */
  protected void doLog(Level level, Throwable cause, Supplier<String> messageSupplier) {
    java.util.logging.Level juLevel = translateLevel(level);
    if (logger.isLoggable(juLevel)) {
      String loggerName = getClass().getName();
      Optional<StackFrame> sfOpt = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).
                                               walk(s -> s.dropWhile(f -> f.getClassName().equals(loggerName)).findFirst());
      if (sfOpt.isPresent()) {
        StackFrame sf = sfOpt.get();
        LogRecord lr = new LogRecord(juLevel, messageSupplier.get());
        lr.setSourceClassName(sf.getClassName());
        lr.setSourceMethodName(sf.getMethodName());
        lr.setThrown(cause);
        lr.setLoggerName(logger.getName());
        logger.log(lr);
      }
    }
  }

  /**
   * Builds the message text to log.<br>
   * Prepends the optional MDC and formats with MessageFormat if params given.
   *
   * @param message the original message text
   * @param params optional parameters
   * @return the message to log
   */
  protected String buildMessage(String message, Object... params) {
    Map<String, String> mdcMap = getMappedDiagnosticContext().getContext();
    if (!mdcMap.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      buf.append('[');
      boolean needComma = false;
      for (Entry<String, String> entry : mdcMap.entrySet()) {
        if (needComma) {
          buf.append(", ");
        }
        else {
          needComma = true;
        }
        buf.append(StringHelper.objectToLoggableString(entry.getKey()));
        buf.append('=');
        buf.append(StringHelper.objectToLoggableString(entry.getValue()));
      }
      buf.append("] ");
      buf.append(message);
      message = buf.toString();
    }
    return params != null && params.length > 0 ? MessageFormat.format(message, params) : message;
  }

}

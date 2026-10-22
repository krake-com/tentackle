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

import java.lang.StackWalker.Option;
import java.util.function.Supplier;

/**
 * A pluggable logger.
 * <p>
 * The concrete logging backend is determined by the {@link LoggerFactory}.
 * Messages with parameters are formatted via {@link java.text.MessageFormat}, i.e. the parameters
 * are referenced as <code>{0}</code>, <code>{1}</code>, etc. The parameter- and message-suppliers
 * are evaluated only if the level is loggable.
 *
 * @author harald
 */
public interface Logger {

  /**
   * Gets a logger for the given classname.<br>
   * If the logger doesn't exist so far for the given class, it will be created.
   * Otherwise, the existing logger instance is returned.
   *
   * @param clazz the clazz to get the logger for
   * @return the logger
   */
  static Logger get(Class<?> clazz) {
    return LoggerFactory.getInstance().getLogger(clazz.getName(), null);
  }

  /**
   * Gets a logger for the invoking class.<br>
   * Same as {@link #get(Class)} but uses the {@link StackWalker} to determine the caller class.
   *
   * @return the logger
   */
  static Logger get() {
    return get(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
  }


  /**
   * Logging levels.
   * <p>
   * Provided are:
   * <ul>
   *  <li>FINER</li>
   *  <li>FINE</li>
   *  <li>INFO</li>
   *  <li>WARNING</li>
   *  <li>SEVERE</li>
   * </ul>
   */
  enum Level {

    /**
     * FINER indicates a highly detailed tracing message.
     */
    FINER,

    /**
     * FINE is a message level providing tracing information.
     */
    FINE,

    /**
     * INFO is a message level for informational messages.
     */
    INFO,

    /**
     * WARNING is a message level indicating a potential problem.
     */
    WARNING,

    /**
     * SEVERE is a message level indicating a serious failure.
     */
    SEVERE

  }




  /**
   * Returns whether this logger will log the given level.
   *
   * @param level the logging level
   * @return true if the given logging level is currently being logged
   */
  boolean isLoggable(Level level);


  /**
   * Checks if logger logs level FINER.
   *
   * @return true if logger will log this level
   */
  boolean isFinerLoggable();

  /**
   * Checks if logger logs level FINE.
   *
   * @return true if logger will log this level
   */
  boolean isFineLoggable();

  /**
   * Checks if logger logs level INFO.
   *
   * @return true if logger will log this level
   */
  boolean isInfoLoggable();

  /**
   * Checks if logger logs level WARNING.
   *
   * @return true if logger will log this level
   */
  boolean isWarningLoggable();

  /**
   * Checks if logger logs level SEVERE.
   *
   * @return true if logger will log this level
   */
  boolean isSevereLoggable();


  /**
   * Logs a message.
   *
   * @param level   the logging level
   * @param message the message
   * @param cause   the throwable
   */
  void log(Level level, String message, Throwable cause);

  /**
   * Logs a message.
   *
   * @param level   the logging level
   * @param message the message
   * @param params  message parameters
   */
  void log(Level level, String message, Object... params);

  /**
   * Logs a message.
   *
   * @param level           the logging level
   * @param cause           the throwable, null for none
   * @param messageSupplier the message supplier
   */
  void log(Level level, Throwable cause, Supplier<String> messageSupplier);


  /**
   * Logs a message with level FINER.
   *
   * @param message the message
   */
  void finer(String message);

  /**
   * Logs a message with level FINE.
   *
   * @param message the message
   */
  void fine(String message);

  /**
   * Logs a message with level INFO.
   *
   * @param message the message
   */
  void info(String message);

  /**
   * Logs a message with level WARNING.
   *
   * @param message the message
   */
  void warning(String message);

  /**
   * Logs a message with level SEVERE.
   *
   * @param message the message
   */
  void severe(String message);


  /**
   * Logs a message with level FINER.
   *
   * @param message the message
   * @param params message parameters
   */
  void finer(String message, Object... params);

  /**
   * Logs a message with level FINE.
   *
   * @param message the message
   * @param params message parameters
   */
  void fine(String message, Object... params);

  /**
   * Logs a message with level INFO.
   *
   * @param message the message
   * @param params message parameters
   */
  void info(String message, Object... params);

  /**
   * Logs a message with level WARNING.
   *
   * @param message the message
   * @param params message parameters
   */
  void warning(String message, Object... params);

  /**
   * Logs a message with level SEVERE.
   *
   * @param message the message
   * @param params message parameters
   */
  void severe(String message, Object... params);


  /**
   * Logs a message with level FINER.
   *
   * @param message the message
   * @param paramSuppliers message parameter suppliers
   */
  void finer(String message, Supplier<?>... paramSuppliers);

  /**
   * Logs a message with level FINE.
   *
   * @param message the message
   * @param paramSuppliers message parameter suppliers
   */
  void fine(String message, Supplier<?>... paramSuppliers);

  /**
   * Logs a message with level INFO.
   *
   * @param message the message
   * @param paramSuppliers message parameter suppliers
   */
  void info(String message, Supplier<?>... paramSuppliers);

  /**
   * Logs a message with level WARNING.
   *
   * @param message the message
   * @param paramSuppliers message parameter suppliers
   */
  void warning(String message, Supplier<?>... paramSuppliers);

  /**
   * Logs a message with level SEVERE.
   *
   * @param message the message
   * @param paramSuppliers message parameter suppliers
   */
  void severe(String message, Supplier<?>... paramSuppliers);


  /**
   * Logs a message with level FINER.
   *
   * @param message the message
   * @param cause the throwable
   */
  void finer(String message, Throwable cause);

  /**
   * Logs a message with level FINE.
   *
   * @param message the message
   * @param cause the throwable
   */
  void fine(String message, Throwable cause);

  /**
   * Logs a message with level INFO.
   *
   * @param message the message
   * @param cause the throwable
   */
  void info(String message, Throwable cause);

  /**
   * Logs a message with level WARNING.
   *
   * @param message the message
   * @param cause the throwable
   */
  void warning(String message, Throwable cause);

  /**
   * Logs a message with level SEVERE.
   *
   * @param message the message
   * @param cause the throwable
   */
  void severe(String message, Throwable cause);


  /**
   * Logs a message with level FINER.
   *
   * @param messageSupplier the message supplier
   * @param cause the throwable
   */
  void finer(Throwable cause, Supplier<String> messageSupplier);

  /**
   * Logs a message with level FINE.
   *
   * @param messageSupplier the message supplier
   * @param cause the throwable
   */
  void fine(Throwable cause, Supplier<String> messageSupplier);

  /**
   * Logs a message with level INFO.
   *
   * @param messageSupplier the message supplier
   * @param cause the throwable
   */
  void info(Throwable cause, Supplier<String> messageSupplier);

  /**
   * Logs a message with level WARNING.
   *
   * @param messageSupplier the message supplier
   * @param cause the throwable
   */
  void warning(Throwable cause, Supplier<String> messageSupplier);

  /**
   * Logs a message with level SEVERE.
   *
   * @param messageSupplier the message supplier
   * @param cause the throwable
   */
  void severe(Throwable cause, Supplier<String> messageSupplier);


  /**
   * Logs a message with level FINER.
   *
   * @param messageSupplier the message supplier
   */
  void finer(Supplier<String> messageSupplier);

  /**
   * Logs a message with level FINE.
   *
   * @param messageSupplier the message supplier
   */
  void fine(Supplier<String> messageSupplier);

  /**
   * Logs a message with level INFO.
   *
   * @param messageSupplier the message supplier
   */
  void info(Supplier<String> messageSupplier);

  /**
   * Logs a message with level WARNING.
   *
   * @param messageSupplier the message supplier
   */
  void warning(Supplier<String> messageSupplier);

  /**
   * Logs a message with level SEVERE.
   *
   * @param messageSupplier the message supplier
   */
  void severe(Supplier<String> messageSupplier);


  /**
   * Logs the stacktrace of a throwable.
   *
   * @param level   the logging level
   * @param cause   the Throwable to log the stacktrace for
   */
  void logStacktrace(Level level, Throwable cause);

  /**
   * Logs the stacktrace of a throwable with a logging level of SEVERE.
   *
   * @param cause   the Throwable to log the stacktrace for
   */
  void logStacktrace(Throwable cause);


  /**
   * Gets the concrete logger implementation.<br>
   * Useful to access the logging-backend.
   *
   * @return the logger object
   */
  Object getLoggerImpl();


  /**
   * Gets the mapped diagnostic context (MDC).<br>
   * Usually a singleton (depending on the backend).
   *
   * @return the MDC
   */
  MappedDiagnosticContext getMappedDiagnosticContext();

}

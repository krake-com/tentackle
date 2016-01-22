/**
 * Tentackle - http://www.tentackle.org
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

/**
 * A pluggable logger.
 *
 * @author harald
 */
public interface Logger {

  /**
   * Logging levels.
   * <p>
   * Provided are:
   * <ul>
   *  <li>FINEST</li>
   *  <li>FINER</li>
   *  <li>FINE</li>
   *  <li>INFO</li>
   *  <li>WARNING</li>
   *  <li>SEVERE</li>
   * </ul>
   */
  public static enum Level {

    /**
     * FINEST indicates a highly detailed tracing message.
     */
    FINEST,

    /**
     * FINER indicates a fairly detailed tracing message.
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

  };




  /**
   * Checks if a message of the given level would actually be logged
   * by this logger.
   *
   * @param level the logging level
   * @return true if the given logging level is currently being logged
   */
  public boolean isLoggable(Level level);



  /**
   * Checks if logger logs level FINEST.
   *
   * @return true if logger will log this level
   */
  public boolean isFinestLoggable();

  /**
   * Checks if logger logs level FINER.
   *
   * @return true if logger will log this level
   */
  public boolean isFinerLoggable();

  /**
   * Checks if logger logs level FINE.
   *
   * @return true if logger will log this level
   */
  public boolean isFineLoggable();

  /**
   * Checks if logger logs level INFO.
   *
   * @return true if logger will log this level
   */
  public boolean isInfoLoggable();

  /**
   * Checks if logger logs level WARNING.
   *
   * @return true if logger will log this level
   */
  public boolean isWarningLoggable();

  /**
   * Checks if logger logs level SEVERE.
   *
   * @return true if logger will log this level
   */
  public boolean isSevereLoggable();




  /**
   * Logs a message.
   *
   * @param level   the logging level
   * @param message the message
   * @param cause   the Throwable associated with log message, null if none
   */
  public void log(Level level, String message, Throwable cause);


  /**
   * Logs a message with level FINEST.
   *
   * @param message the message
   * @param params optional parameters
   */
  public void finest(String message, Object... params);

  /**
   * Logs a message with level FINER.
   *
   * @param message the message
   * @param params optional parameters
   */
  public void finer(String message, Object... params);

  /**
   * Logs a message with level FINE.
   *
   * @param message the message
   * @param params optional parameters
   */
  public void fine(String message, Object... params);

  /**
   * Logs a message with level INFO.
   *
   * @param message the message
   * @param params optional parameters
   */
  public void info(String message, Object... params);

  /**
   * Logs a message with level WARNING.
   *
   * @param message the message
   * @param params optional parameters
   */
  public void warning(String message, Object... params);

  /**
   * Logs a message with level SEVERE.
   *
   * @param message the message
   * @param params optional parameters
   */
  public void severe(String message, Object... params);


  /**
   * Logs a message with level FINEST.
   *
   * @param message the message
   * @param cause the throwable
   */
  public void finest(String message, Throwable cause);

  /**
   * Logs a message with level FINER.
   *
   * @param message the message
   * @param cause the throwable
   */
  public void finer(String message, Throwable cause);

  /**
   * Logs a message with level FINE.
   *
   * @param message the message
   * @param cause the throwable
   */
  public void fine(String message, Throwable cause);

  /**
   * Logs a message with level INFO.
   *
   * @param message the message
   * @param cause the throwable
   */
  public void info(String message, Throwable cause);

  /**
   * Logs a message with level WARNING.
   *
   * @param message the message
   * @param cause the throwable
   */
  public void warning(String message, Throwable cause);

  /**
   * Logs a message with level SEVERE.
   *
   * @param message the message
   * @param cause the throwable
   */
  public void severe(String message, Throwable cause);




  /**
   * Logs the stacktrace of a throwable.
   *
   * @param level   the logging level
   * @param cause   the Throwable to log the stacktrace for
   */
  public void logStacktrace(Level level, Throwable cause);

  /**
   * Logs the stacktrace of a throwable with a logging level of SEVERE.
   *
   * @param cause   the Throwable to log the stacktrace for
   */
  public void logStacktrace(Throwable cause);


  /**
   * Gets the concrete logger implementation.<br>
   * Useful to access the logging-backend.
   *
   * @return the logger object
   */
  public Object getLoggerImpl();


  /**
   * Gets the MDC.<br>
   * Usually a singleton (depending on the backend).
   *
   * @return the MDC
   */
  public MappedDiagnosticContext getMappedDiagnosticContext();

}

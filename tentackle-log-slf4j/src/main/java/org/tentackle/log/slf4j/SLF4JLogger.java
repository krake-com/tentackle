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


package org.tentackle.log.slf4j;

import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.HashMap;
import org.tentackle.common.Service;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerOutputStream;
import org.tentackle.log.MappedDiagnosticContext;

/**
 * Pluggable logger using <tt>org.slf4j</tt>.
 *
 * @author harald
 */
@Service(Logger.class)
public class SLF4JLogger implements Logger {

  // classname to exclude from stacktrace
  private static final String EXCLUDE_CLASSNAME = SLF4JLogger.class.getName();

  // cached loggers
  private static final HashMap<String,SLF4JLogger> LOGGERS = new HashMap<>();

  /**
   * Gets the Log4JLogger for given name.
   * If a logger with that name already exists, it will be re-used.
   *
   * @param name the name of the logger
   * @return the logger
   */
  public static SLF4JLogger getLogger(String name) {
    synchronized(LOGGERS) {
      SLF4JLogger logger = LOGGERS.computeIfAbsent(name, SLF4JLogger::new);
      return logger;
    }
  }


  private final org.slf4j.Logger logger;    // the SLF4J logger
  private final boolean locationAware;      // true if instanceof LocationAwareLogger


  /**
   * Creates a logger.
   *
   * @param name the name of the logger
   */
  public SLF4JLogger(String name) {
    logger = org.slf4j.LoggerFactory.getLogger(name);
    locationAware = logger instanceof org.slf4j.spi.LocationAwareLogger;
  }


  @Override
  public Object getLoggerImpl() {
    return logger;
  }



  @Override
  public boolean isLoggable(Level level) {
    switch (level) {
      case FINER:   return isFinerLoggable();
      case FINE:    return isFineLoggable();
      case INFO:    return isInfoLoggable();
      case WARNING: return isWarningLoggable();
      default:      return isSevereLoggable();
    }
  }




  @Override
  public void log(Level level, String message, Throwable cause) {
    doLog(level, message, cause);
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
  public boolean isFinerLoggable() {
    return logger.isTraceEnabled();
  }

  @Override
  public boolean isFineLoggable() {
    return logger.isDebugEnabled();
  }

  @Override
  public boolean isInfoLoggable() {
    return logger.isInfoEnabled();
  }

  @Override
  public boolean isWarningLoggable() {
    return logger.isWarnEnabled();
  }

  @Override
  public boolean isSevereLoggable() {
    return logger.isErrorEnabled();
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
    return SLF4JMappedDiagnosticContext.getInstance();
  }



  /**
   * Translate the tentackle logging level to the SLF4J level.
   *
   * @param level the tt level
   * @return one of org.slf4j.spi.LocationAwareLogger.XXX_INT
   */
  protected int translateLevel(Level level) {
    switch (level) {
      case FINER:   return org.slf4j.spi.LocationAwareLogger.TRACE_INT;
      case FINE:    return org.slf4j.spi.LocationAwareLogger.DEBUG_INT;
      case INFO:    return org.slf4j.spi.LocationAwareLogger.INFO_INT;
      case WARNING: return org.slf4j.spi.LocationAwareLogger.WARN_INT;
      default:      return org.slf4j.spi.LocationAwareLogger.ERROR_INT;
    }
  }


  /**
   * Returns whether this is a location aware logger.
   *
   * @return true if location aware
   */
  protected boolean isLocationAware() {
    return locationAware;
  }


  /**
   * Logging workhorse.
   *
   * @param level the log level
   * @param message the message
   * @param cause the cause
   * @param params optional parameters (ignored if cause != null and !locationAware)
   */
  protected void doLog(Level level, String message, Throwable cause, Object... params) {
    if (params != null && params.length > 0) {
      // SLF4J is incompatible with MessageFormat, so we must translate before
      if (!isLoggable(level)) { // check before formatting
        return;
      }
      message = MessageFormat.format(message, params);
    }
    if (isLocationAware()) {
      ((org.slf4j.spi.LocationAwareLogger) logger).
              log(null, EXCLUDE_CLASSNAME, translateLevel(level), message, null, cause);
    }
    else  {
      // probably the SimpleLogger...
      switch (level) {
        case FINER:
          logger.trace(message, cause);
          break;
        case FINE:
          logger.debug(message, cause);
          break;
        case INFO:
          logger.info(message, cause);
          break;
        case WARNING:
          logger.warn(message, cause);
          break;
        default:
          logger.error(message, cause);
          break;
      }
    }
  }


}

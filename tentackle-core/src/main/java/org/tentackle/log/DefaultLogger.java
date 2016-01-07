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

// $Id$

package org.tentackle.log;

import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.tentackle.misc.StringHelper;
import org.tentackle.reflect.ReflectionHelper;

/**
 * Pluggable logger using the standard java logging using <tt>java.util.logging</tt>.
 * <p>
 * No Service annotation! See UtilLogger.
 *
 * @author harald
 */
public class DefaultLogger implements Logger {

  // classnames to exclude from stacktrace
  private static final String[] EXCLUDE_CLASSNAMES = new String [] {
    DefaultLogger.class.getName()
  };


  // cached loggers
  private static final HashMap<String,DefaultLogger> loggers = new HashMap<>();

  /**
   * Gets the DefaultLogger for given name.
   * If a logger with that name already exists, it will be re-used.
   *
   * @param name the name of the logger
   * @return the logger
   */
  public static DefaultLogger getLogger(String name) {
    synchronized(loggers) {
      DefaultLogger logger = loggers.get(name);
      if (logger == null) {
        // create a new one
        logger = new DefaultLogger(name);
        loggers.put(name, logger);
      }
      return logger;
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
    switch (level) {
      case FINEST:  return java.util.logging.Level.FINEST;
      case FINER:   return java.util.logging.Level.FINER;
      case FINE:    return java.util.logging.Level.FINE;
      case INFO:    return java.util.logging.Level.INFO;
      case WARNING: return java.util.logging.Level.WARNING;
      default:      return java.util.logging.Level.SEVERE;
    }
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
  public void finest(String message, Throwable cause) {
    doLog(Level.FINEST, message, cause);
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
  public void finest(String message, Object... params) {
    doLog(Level.FINEST, message, null, params);
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
  public boolean isFinestLoggable() {
    return isLoggable(Level.FINEST);
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
   * <p>
   * If the {@link MappedDiagnosticContext} contains entries
   * those entries will be prepended in brackets as key=value pairs.
   *
   * @param level the log level
   * @param message the message
   * @param cause the cause
   * @param params optional parameters (ignored if cause != null)
   */
  protected void doLog(Level level, String message, Throwable cause, final Object... params) {

    // we check the loggability before (and thus twice, but it's still a lot faster
    // because ReflectionHelper.getInvocation is slow due to getStackTrace())
    java.util.logging.Level juLevel = translateLevel(level);
    if (logger.isLoggable(juLevel)) {

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

      // this is really slow...
      StackTraceElement se = ReflectionHelper.getInvocation(EXCLUDE_CLASSNAMES);

      if (params != null && params.length > 0) {
        final String msg = message;
        logger.logp(juLevel, se.getClassName(), se.getMethodName(), cause,
                () -> {
                  return MessageFormat.format(msg, params);
                });
      }
      else  {
        logger.logp(juLevel, se.getClassName(), se.getMethodName(), message, cause);
      }
    }
  }

}

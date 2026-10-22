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

import org.tentackle.log.Logger.Level;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Output stream for a logger.<br>
 * Nice to catch {@code e.printStackTrace(Stream)}.
 * The buffered bytes are logged on {@link #flush()} and {@link #close()}.
 *
 * @author harald
 */
public class LoggerOutputStream extends ByteArrayOutputStream {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = Logger.get(LoggerOutputStream.class);

  private final Logger logger;    // the logger
  private final Level  level;     // one of LogLevel


  /**
   * Creates a logger output stream.
   *
   * @param logger the logger
   * @param level one of {@link Level#SEVERE}, etc...
   * @param size the buffer size
   */
  public LoggerOutputStream(Logger logger, Level level, int size) {
    super(size);
    this.logger = logger;
    this.level  = level;
  }

  /**
   * Creates a logger output stream with a default buffer size.
   *
   * @param logger the logger
   * @param level one of {@link Level#SEVERE}, etc...
   */
  public LoggerOutputStream(Logger logger, Level level) {
    this(logger, level, 512);
  }

  /**
   * Creates a logger output stream with a default buffer size
   * and {@link Level#SEVERE}.
   *
   * @param logger the logger
   */
  public LoggerOutputStream(Logger logger)  {
    this(logger, Level.SEVERE);
  }

  /**
   * Logs the buffered bytes and clears the buffer.
   * <p>
   * Nothing is logged if the buffer is empty. This matters because a {@link PrintStream} wrapping
   * this stream flushes it more than once when being closed.
   */
  @Override
  public void flush() {
    if (size() > 0) {
      logger.log(level, toString());    // print Bytearray output stream
      reset();
    }
  }

  @Override
  public void close() {
    flush();
  }


  /**
   * Logs an exception stacktrace to the logger.
   *
   * @param e the exception
   * @param logger the logger
   * @param level the logging level
   * @param size the buffer size
   */
  public static void logException(Exception e, Logger logger, Level level, int size)  {
    // print exception and stacktrace
    PrintStream ps = new PrintStream(new LoggerOutputStream(logger, level, size));
    ps.println(e);
    e.printStackTrace(ps);
    ps.close();
  }

  /**
   * Logs an exception stacktrace to the logger with a default buffer size
   * and {@link Level#SEVERE}.
   *
   * @param e the exception
   * @param logger the logger
   */
  public static void logException(Exception e, Logger logger)  {
    logException(e, logger, Level.SEVERE, 512);
  }

  /**
   * Logs an exception stacktrace with a default buffer size
   * and {@link Level#SEVERE} to the logger of this class.
   *
   * @param e the exception
   */
  public static void logException(Exception e)  {
    logException(e, LOGGER, Level.SEVERE, 512);
  }

}

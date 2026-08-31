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

package org.tentackle.maven;

import org.apache.maven.plugin.logging.Log;

import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Log handler that redirects java.util logging to the maven logger.<br>
 * By default, all tentackle logging goes to the java util logger and the logs below WARN level
 * are not of big interest during the build.
 */
public class MavenLogHandler extends Handler {

  private final Log log;
  private final Level minLevel;

  /**
   * Creates a maven log handler.
   *
   * @param log the maven logger
   * @param minLevel the minimum logging level
   */
  public MavenLogHandler(Log log, Level minLevel) {
    this.log = Objects.requireNonNull(log);
    this.minLevel = Objects.requireNonNull(minLevel);
  }

  @Override
  public void publish(LogRecord logRecord) {
    if (logRecord != null && logRecord.getLevel().intValue() >= minLevel.intValue()) {
      if (logRecord.getLevel() == Level.SEVERE) {
        log.error(logRecord.getMessage(), logRecord.getThrown());
      }
      else if (logRecord.getLevel() == Level.WARNING) {
        log.warn(logRecord.getMessage(), logRecord.getThrown());
      }
      else if (logRecord.getLevel() == Level.INFO) {
        log.info(logRecord.getMessage(), logRecord.getThrown());
      }
      else {
        log.debug(logRecord.getMessage(), logRecord.getThrown());
      }
    }
  }

  @Override
  public void flush() {
    // nothing to do
  }

  @Override
  public void close() {
    // nothing to do
  }
}

/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.log;

import org.tentackle.log.Logger.Level;

/**
 * An interface telling whether an object is loggable or not.
 * <p>
 * Used by the {@code RemoteDbDelegateInvocationHandler} of tentackle-database, for example.<br>
 * If used with exceptions, it usually tells whether the stacktrace should be logged or not.
 *
 * @author harald
 */
public interface Loggable {

  /**
   * Gets the log-level this object should be logged with.
   *
   * @return the log level, null if no logging at all
   */
  default Level getLogLevel() {
    return Level.INFO;
  }

  /**
   * Returns whether to log the stacktrace.
   *
   * @return true if with stacktrace, false if message only
   */
  default boolean withStacktrace() {
    return true;
  }

}

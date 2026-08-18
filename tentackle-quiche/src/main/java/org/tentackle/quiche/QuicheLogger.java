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

package org.tentackle.quiche;

import org.tentackle.log.Logger;
import org.tentackle.quiche.ffm.Quiche;

/**
 * Bridges quiche's own logging onto Tentackle's logging API.
 * <p>
 * Quiche logs through a single callback that is installed process-wide and, once installed, cannot be
 * removed or replaced &mdash; the native library accepts only the first call. The callback is therefore
 * installed lazily by {@link #install()}, and everything it produces is logged at {@code FINE}: these are
 * the Rust library's internal trace messages, far too detailed for anything else.
 * <p>
 * Installing is worth it only when {@code FINE} is actually enabled, because quiche formats every message
 * before handing it over regardless of what this side does with it. {@link #install()} therefore checks
 * first, which means the messages are unavailable if logging is turned up only after the first connection
 * has been made.
 */
public final class QuicheLogger {

  private static final Logger LOGGER = Logger.get(QuicheLogger.class);

  private static boolean installed;   // guarded by the class monitor


  /**
   * Installs the log callback in the native library, unless that already happened.
   * <p>
   * Does nothing if {@code FINE} is not loggable for this class, see the class comment.
   */
  public static synchronized void install() {
    if (!installed && LOGGER.isFineLoggable()) {
      installed = true;
      if (!Quiche.enableDebugLogging(QuicheLogger::log)) {
        LOGGER.fine("quiche debug logging is already enabled by someone else");
      }
    }
  }

  /**
   * Tells whether the log callback has been installed.
   *
   * @return true if installed
   */
  public static synchronized boolean isInstalled() {
    return installed;
  }

  /**
   * Logs a single line coming from the native library.
   * <p>
   * Invoked on whatever thread happens to be inside quiche, so it must not throw: an exception crossing
   * back into native code would take the JVM down.
   *
   * @param line the log line
   */
  private static void log(String line) {
    try {
      LOGGER.fine(line);
    }
    catch (RuntimeException e) {    // NOSONAR: nothing may escape into the native caller
      // there is nowhere left to report this to
    }
  }

  private QuicheLogger() {
  }

}

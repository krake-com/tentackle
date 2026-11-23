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

package org.tentackle.trip;

import org.tentackle.log.Logger;

import java.util.function.LongSupplier;

/**
 * Throttles the log messages about peers that are not authenticated.
 * <p>
 * Every rejected connection is logged: an invalid magic, a broken handshake, a policy violation, an evicted
 * or refused connection. Anybody who can reach the port can open connections as fast as the network allows,
 * so these messages could fill the disk. Within each {@link #setInterval(long) interval}, at most
 * {@link #setMaxMessages(int) maxMessages} are logged. The first message of the next interval is preceded
 * by a summary of how many were suppressed.
 * <p>
 * The limit is global, not per host: an attacker with many addresses would otherwise multiply it.
 * <pre>
 *   if (RejectionLog.isLoggable()) {
 *     LOGGER.warning("connection {0} sent an invalid magic -> closed", clientInfo);
 *   }
 * </pre>
 */
public final class RejectionLog {

  private static final Logger LOGGER = Logger.get(RejectionLog.class);

  /** Default maximum number of messages per interval. */
  public static final int DEFAULT_MAX_MESSAGES = 100;

  /** Default interval in milliseconds. */
  public static final long DEFAULT_INTERVAL = 60000;

  private static int maxMessages = DEFAULT_MAX_MESSAGES;
  private static long interval = DEFAULT_INTERVAL;
  private static LongSupplier clock = System::currentTimeMillis;

  private static long intervalStart;      // start of the current interval, 0 if none yet
  private static int logged;              // messages logged in the current interval
  private static long suppressed;         // messages suppressed in the current interval


  /**
   * Returns whether the next message about an unauthenticated peer may be logged.<br>
   * Counts the message as logged if so, as suppressed otherwise.
   *
   * @return true if the message may be logged
   */
  public static boolean isLoggable() {
    long suppressedBefore = 0;
    long intervalMillis;
    boolean loggable;
    synchronized (RejectionLog.class) {
      intervalMillis = interval;
      int max = maxMessages;
      if (max <= 0 || intervalMillis <= 0) {
        return true;
      }
      long now = clock.getAsLong();
      if (intervalStart == 0 || now - intervalStart >= intervalMillis) {
        suppressedBefore = suppressed;
        intervalStart = now;
        logged = 0;
        suppressed = 0;
      }
      loggable = logged < max;
      if (loggable) {
        logged++;
      }
      else {
        suppressed++;
      }
    }
    if (suppressedBefore > 0) {
      LOGGER.warning("{0} more messages about unauthenticated peers suppressed in {1} ms", suppressedBefore, intervalMillis);
    }
    return loggable;
  }

  /**
   * Sets the maximum number of messages logged per interval.
   *
   * @param maxMessages the maximum number, 0 to log all
   */
  public static synchronized void setMaxMessages(int maxMessages) {
    RejectionLog.maxMessages = maxMessages;
  }

  /**
   * Gets the maximum number of messages logged per interval.
   *
   * @return the maximum number, 0 if all are logged
   */
  public static synchronized int getMaxMessages() {
    return maxMessages;
  }

  /**
   * Sets the interval.
   *
   * @param interval the interval in milliseconds, 0 to log all
   */
  public static synchronized void setInterval(long interval) {
    RejectionLog.interval = interval;
  }

  /**
   * Gets the interval.
   *
   * @return the interval in milliseconds
   */
  public static synchronized long getInterval() {
    return interval;
  }

  /**
   * Sets the clock and starts over.<br>
   * For tests only.
   *
   * @param clock the clock in milliseconds, null for the system clock
   */
  static synchronized void setClock(LongSupplier clock) {
    RejectionLog.clock = clock == null ? System::currentTimeMillis : clock;
    intervalStart = 0;
    logged = 0;
    suppressed = 0;
  }


  private RejectionLog() {}

}

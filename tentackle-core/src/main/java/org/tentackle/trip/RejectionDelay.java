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

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * The random delay until a peer that is not authenticated is rejected.
 * <p>
 * A server rejects an unauthenticated peer when it sends an invalid TRIP magic, looks up an
 * unknown remote object name, or fails to log in. Each rejection takes effect after a random time
 * within the same interval, measured from the start of the rejected request. The response time therefore
 * tells an attacker neither what was wrong (no TRIP at all, a wrong service name, an unknown user,
 * a wrong password, an incompatible version) nor how long the server took to find out, as long as
 * that is shorter than the minimum delay. It also throttles brute-force attacks.
 * <p>
 * All rejections share one interval on purpose: different intervals would be a side channel
 * of their own.
 * <p>
 * The server waits out every rejection in the virtual thread of the connection, never in a platform thread
 * of the call pool. A remote call that must be answered late, such as a failed login, invokes {@link #defer(long)}:
 * the call returns at once, and the connection handler sends its response after the delay.
 */
public final class RejectionDelay {

  /**
   * The pending deferral of the remote call the current thread is processing.
   */
  private static final ThreadLocal<Deferral> DEFERRAL = new ThreadLocal<>();


  /**
   * The deferred response of a remote call.
   * <p>
   * Used by the call handler of the server: it {@link #begin() begins} a deferral before it invokes
   * the remote method, and {@link #end() ends} it afterward. If the method {@link #defer(long) deferred}
   * its response, the connection handler {@link #await() waits} in its virtual thread before it sends the response.
   */
  public static final class Deferral {

    private boolean requested;
    private long startedNanos;

    private Deferral() {}

    /**
     * Begins a deferral for the remote call processed by the current thread.
     *
     * @return the deferral
     */
    public static Deferral begin() {
      Deferral deferral = new Deferral();
      DEFERRAL.set(deferral);
      return deferral;
    }

    /**
     * Ends the deferral of the current thread.
     */
    public void end() {
      if (DEFERRAL.get() == this) {
        DEFERRAL.remove();
      }
    }

    /**
     * Returns whether the response was deferred.
     *
     * @return true if deferred
     */
    public boolean isRequested() {
      return requested;
    }

    /**
     * Waits until the random delay since the start of the rejected request has elapsed.
     */
    public void await() {
      if (requested) {
        RejectionDelay.await(startedNanos);
      }
    }

    private void request(long startedNanos) {
      if (!requested || startedNanos - this.startedNanos < 0) {   // the earliest start wins
        this.startedNanos = startedNanos;
      }
      requested = true;
    }
  }

  /** Default minimum delay in milliseconds. */
  public static final long DEFAULT_MIN_DELAY = 1000;

  /** Default maximum delay in milliseconds. */
  public static final long DEFAULT_MAX_DELAY = 5000;

  /** Random source for the delay. */
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Minimum delay in milliseconds. */
  private static volatile long minDelay = DEFAULT_MIN_DELAY;

  /** Maximum delay in milliseconds, 0 if not delayed. */
  private static volatile long maxDelay = DEFAULT_MAX_DELAY;


  /**
   * Sets the interval of the rejection delay.
   *
   * @param minDelay the minimum delay in milliseconds
   * @param maxDelay the maximum delay in milliseconds, 0 to reject immediately
   * @throws IllegalArgumentException if a delay is negative or {@code minDelay > maxDelay > 0}
   */
  public static void setInterval(long minDelay, long maxDelay) {
    if (minDelay < 0 || maxDelay < 0) {
      throw new IllegalArgumentException("rejection delays must not be negative");
    }
    if (maxDelay > 0 && minDelay > maxDelay) {
      throw new IllegalArgumentException("minimum rejection delay " + minDelay + " exceeds the maximum " + maxDelay);
    }
    // a concurrent rejection may see a mix of old and new values: await tolerates that
    RejectionDelay.minDelay = maxDelay > 0 ? minDelay : 0;
    RejectionDelay.maxDelay = maxDelay;
  }

  /**
   * Gets the minimum delay.
   *
   * @return the minimum delay in milliseconds
   */
  public static long getMinDelay() {
    return minDelay;
  }

  /**
   * Gets the maximum delay.
   *
   * @return the maximum delay in milliseconds, 0 if rejected immediately
   */
  public static long getMaxDelay() {
    return maxDelay;
  }

  /**
   * Waits until a random time within the interval has elapsed since the rejected request started.<br>
   * Returns immediately if that time has already elapsed. Keeps the interrupt status if interrupted.
   *
   * @param startedNanos the {@link System#nanoTime()} when the rejected request started
   */
  public static void await(long startedNanos) {
    long min = minDelay;
    long max = maxDelay;
    if (max > 0 && min <= max) {
      long target = min + RANDOM.nextLong(max - min + 1);
      long remaining = target - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
      if (remaining > 0) {
        try {
          Thread.sleep(remaining);
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  /**
   * Delays the response to the remote call the current thread is processing.
   * <p>
   * Within a call handler of the server, the call returns at once and the response is sent from the
   * virtual thread of the connection after the delay. Otherwise, waits like {@link #await(long)}.
   *
   * @param startedNanos the {@link System#nanoTime()} when the rejected request started
   */
  public static void defer(long startedNanos) {
    Deferral deferral = DEFERRAL.get();
    if (deferral != null) {
      deferral.request(startedNanos);
    }
    else {
      await(startedNanos);
    }
  }


  private RejectionDelay() {}

}

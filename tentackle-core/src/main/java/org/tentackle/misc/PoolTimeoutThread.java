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

package org.tentackle.misc;

import org.tentackle.common.Constants;
import org.tentackle.daemon.Scavenger;
import org.tentackle.log.Logger;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Timeout thread to monitor {@link Pool}s.<br>
 * There is only one such thread for all pools.
 * <p>
 * Closes pooled instances when unused for too long or should be closed after
 * a certain amount of usage time.<br>
 * The goal is to release resources and reduce memory consumption in long-running servers.
 */
public class PoolTimeoutThread extends Thread implements Scavenger {

  private static final Logger LOGGER = Logger.get(PoolTimeoutThread.class);
  private static final long SLEEP_INTERVAL = Constants.SECOND_MS * 10;   // sleep interval for timeout thread

  /**
   * The monitored pools.
   * <p>
   * Deliberately not keyed by the pool name: pools are not required to have unique names
   * and a colliding registration would silently drop a pool from the monitoring.
   */
  private static final Collection<WeakReference<Pool<?>>> pools = new ConcurrentLinkedQueue<>();

  /** number of created threads so far. */
  private static final AtomicInteger instanceCount = new AtomicInteger();

  /** the currently running thread, null if none running. */
  private static PoolTimeoutThread timeoutThread;


  /**
   * Registers a new pool for monitoring.<br>
   * Starts the timeout thread, if not running.<br>
   * There is no "unregister".
   * Pools are automatically unregistered when garbage collected or shut down.
   * The thread terminates when there are no more pools to monitor.
   *
   * @param pool the pool
   */
  public static void register(Pool<?> pool) {
    /*
     * Registering and terminating must be mutually exclusive, see run().
     * Otherwise, a thread that has just decided to terminate is still alive here,
     * no replacement is started and the pool would never be monitored at all.
     */
    synchronized (PoolTimeoutThread.class) {
      pools.add(new WeakReference<>(pool));
      if (timeoutThread == null || !timeoutThread.isAlive()) {
        timeoutThread = new PoolTimeoutThread();
        timeoutThread.start();
      }
    }
    LOGGER.info("pool {0} registered", pool);
  }


  private PoolTimeoutThread() {
    super("Pool Timeout Thread(" + instanceCount.incrementAndGet() + ")");
    setDaemon(true);
  }

  @Override
  public void run() {
    LOGGER.info(this + " started");
    for (;;) {
      try {
        try {
          sleep(SLEEP_INTERVAL); // wait for a few seconds
        }
        catch (InterruptedException ex) {
          // is a daemon thread: an interrupt must not terminate it.
          // Don't restore the interrupt flag either: the next sleep() would throw
          // immediately and the loop would spin without ever sleeping again.
          LOGGER.warning("interrupted -> keep running", ex);
          // check the termination condition below
        }

        synchronized (PoolTimeoutThread.class) {
          if (pools.isEmpty()) {
            // hand the duty to start a new thread back to register(), which holds the same lock
            timeoutThread = null;
            break;    // no more pools -> terminate
          }
        }

        for (Iterator<WeakReference<Pool<?>>> iter = pools.iterator(); iter.hasNext(); ) {
          Pool<?> pool = iter.next().get();
          if (pool == null || pool.isShutdown()) {
            iter.remove();
          }
          else {
            /*
             * Don't hold the pool's monitor here: monitor() may shut the pool down, which waits
             * for its resources to be closed. Holding it would also invert the locking order
             * against register(), which is invoked while holding the pool's monitor.
             */
            pool.monitor();   // bring down timed out unused instances
          }
        }
      }
      catch(RuntimeException ex){
        LOGGER.severe("monitoring pools failed", ex);
      }
    }
    LOGGER.info(this + " terminated");
  }

}

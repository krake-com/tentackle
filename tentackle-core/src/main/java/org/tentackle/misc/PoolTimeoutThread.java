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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

  /** pool name -> pool ref. */
  private static final Map<String, WeakReference<Pool<?>>> poolMap = new ConcurrentHashMap<>();

  /** number of created threads so far. */
  private static final AtomicInteger instanceCount = new AtomicInteger();

  /** the currently running or dead thread, null if no thread started yet. */
  private static PoolTimeoutThread timeoutThread;


  /**
   * Registers a new pool for monitoring.<br>
   * There is no "unregister".
   * Pools are automatically unregistered when GC'd or shut down.
   *
   * @param pool the pool
   */
  public static void register(Pool<?> pool) {
    poolMap.put(pool.getName(), new WeakReference<>(pool));
    LOGGER.info("session pool {0} registered", pool);
    checkAlive();
  }

  private static synchronized void checkAlive() {
    if (timeoutThread == null || !timeoutThread.isAlive()) {
      timeoutThread = new PoolTimeoutThread();
      timeoutThread.start();
    }
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

        if (poolMap.isEmpty()) {
          break;    // no more pools -> terminate
        }

        for (Iterator<WeakReference<Pool<?>>> iter = poolMap.values().iterator(); iter.hasNext(); ) {
          Pool<?> pool = iter.next().get();
          if (pool == null) {
            iter.remove();
          }
          else {
            synchronized (pool) {
              if (pool.isShutdown()) {
                iter.remove();
              }
              else {
                // bring down timed out unused instances
                pool.monitor();
              }
            }
          }
        }
      }
      catch(RuntimeException ex){
        LOGGER.severe("cleaning up unused session instance(s) failed", ex);
      }
    }
    LOGGER.info(this + " terminated");
  }

}

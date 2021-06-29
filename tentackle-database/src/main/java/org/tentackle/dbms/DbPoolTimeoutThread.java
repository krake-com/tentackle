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

package org.tentackle.dbms;

import org.tentackle.common.Constants;
import org.tentackle.common.Timestamp;
import org.tentackle.daemon.Scavenger;
import org.tentackle.log.Logger;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Timeout thread to supervise {@link DbPool}s.
 * There is only one such thread for all pools.
 * <p>
 * Closes pooled db instances when not used for maxMinutes time.
 * This is to release resources, if any, and improves memory consumption
 * for long running servers.
 */
public class DbPoolTimeoutThread extends Thread implements Scavenger {

  private static final Logger LOGGER = Logger.get(DbPoolTimeoutThread.class);
  private static final long SLEEP_INTERVAL = Constants.SECOND_MS * 10;   // sleep interval for timeout thread (10secs)

  private static final Map<String, WeakReference<DbPool>> poolMap = new ConcurrentHashMap<>();   // poolname -> pool ref
  private static final AtomicInteger instanceCount = new AtomicInteger();   // number of created threads so far
  private static DbPoolTimeoutThread timeoutThread;                         // the currently running or dead thread, null if no thread started yet

  /**
   * Registers a new pool for supervision.<br>
   * There is no unregister. Pools are automatically unregistered when GC'd or shut down.
   *
   * @param pool the pool
   */
  public static void register(DbPool pool) {
    poolMap.put(pool.getName(), new WeakReference<>(pool));
    LOGGER.info("session pool {0} registered", pool);
    checkAlive();
  }

  private static synchronized void checkAlive() {
    if (timeoutThread == null || !timeoutThread.isAlive()) {
      timeoutThread = new DbPoolTimeoutThread();
      timeoutThread.start();
    }
  }


  private DbPoolTimeoutThread() {
    super("Sessionpool Timeout Thread(" + instanceCount.incrementAndGet() + ")");
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
          // check termination condition below (pool == null)
        }

        if (poolMap.isEmpty()) {
          break;    // no more pools -> terminate
        }

        for (Iterator<WeakReference<DbPool>> iter = poolMap.values().iterator(); iter.hasNext(); ) {
          DbPool pool = iter.next().get();
          if (pool == null) {
            iter.remove();
          }
          else {
            long curtime = System.currentTimeMillis();

            synchronized (pool) {
              if (pool.isShutdown()) {
                iter.remove();
              }
              else {
                // bring down timed out unused Db instances
                int i = 0;    // start with oldest unused
                while (i < pool.getUnusedCount()) {
                  int index = pool.getUnusedSessionIndex(i);
                  PooledDb pooledDb = pool.getPooledSessions()[index];  // pooledDb.db != null because unused

                  boolean idleTimedOut = false;
                  long maxIdleMinutes = pool.getMaxIdleMinutes();
                  if (maxIdleMinutes > 0) {
                    long idleMinutes = pooledDb.idleMinutes(curtime);
                    idleTimedOut = idleMinutes > maxIdleMinutes;
                    if (idleTimedOut) {
                      LOGGER.info("{0} idle for {1} (max={2}) -> closed", pooledDb, idleMinutes, maxIdleMinutes);
                    }
                  }

                  boolean usageTimedOut = false;
                  long maxUsageMinutes = pool.getMaxUsageMinutes();
                  if (maxUsageMinutes > 0) {
                    long usedMinutes = pooledDb.usedMinutes(curtime);
                    usageTimedOut = usedMinutes > maxUsageMinutes;
                    if (usageTimedOut) {
                      LOGGER.info("{0} used for {1} (max={2}) -> closed", pooledDb, usedMinutes, maxUsageMinutes);
                    }
                  }

                  // if used at all and unused interval elapsed
                  if (idleTimedOut || usageTimedOut) {
                    pool.removeDbInstance(pooledDb.getSession(), index);
                    i--; // start over at same slot
                  }
                  else if (pooledDb.getSession().isRemote()) {
                    try {
                      // remote connections must be kept alive in order not to be closed by remote server!
                      pooledDb.getSession().setAlive(true);
                    }
                    catch (RuntimeException ex) {
                      LOGGER.severe("remote keep alive failed", ex);
                      pool.removeDbInstance(pooledDb.getSession(), index);
                      i--; // start over at same slot
                    }
                  }
                  i++;
                }

                // remove unreferenced sessions
                i = 0;
                for (PooledDb pooledDb : pool.getPooledSessions()) {
                  if (pooledDb != null && pooledDb.isUnreferenced()) {
                    LOGGER.warning("unreferenced " + pooledDb +
                                   " last used by " + pooledDb.getUsingThreadStr() +
                                   " in MDC{" + pooledDb.getMdcStr() +
                                   "} since " + new Timestamp(pooledDb.getUnusedSince()) +
                                   " -> removed from pool!");
                    pool.removeDbIndex(i);
                    // note: the finalizer will close the Db physically
                  }
                  i++;
                }

                // check if we need to bring up some sessions for minSize
                int size = pool.getSize();
                if (pool.getMinSize() > 0 && size < pool.getMinSize()) {
                  pool.createDbInstances(pool.getMinSize() - size);
                }
                else if (size == 0 && pool.getMinSize() < 0) {
                  // minsize < 0 and all sessions closed -> close pool and remove it
                  pool.shutdown();
                  iter.remove();
                }
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

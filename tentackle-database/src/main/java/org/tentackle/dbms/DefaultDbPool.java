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
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionClosedException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;

import java.lang.ref.WeakReference;


/**
 * An implementation of a database pool.<br>
 * It allows min/max sizes, fixed increments and timeouts for unused instances.
 * <p>
 * The pool can be used with any ConnectionManager.
 * If used with the {@link DefaultConnectionManager}, each {@link Db} instance corresponds to a
 * physical JDBC-connection. If used with an {@link MpxConnectionManager}, the {@link Db} instances
 * map to virtual connections that will be attached temporarily during db-operations.
 * This is the preferred configuration in server applications with a lot of clients.
 * In order to clear the PdoCache on db-close, you have to override the closeDb method.
 */
public class DefaultDbPool implements SessionPool {

  private static final Logger LOGGER = Logger.get(DefaultDbPool.class);

  private static final long SLEEP_INTERVAL = Constants.SECOND_MS * 10;   // sleep interval for timeout thread (10secs)


  private final String name;                  // the pool's name
  private final ConnectionManager conMgr;     // the connection manager
  private final SessionInfo sessionInfo;      // the server's session info
  private final int iniSize;                  // initial size of the pool (0 if initialization completed)
  private int incSize;                        // increment size
  private int minSize;                        // min pool size
  private int maxSize;                        // max pool size
  private long maxIdleMinutes;                // idle timeout in [minutes]
  private long maxUsageMinutes;               // usage timeout in [minutes]

  private boolean initialized;                // true if pool is initialized
  private PooledDb[] pooledSessions;          // the pooled sessions
  private int[] freeList;                     // free slots in 'pool'
  private int freeCount;                      // number of entries in freeList
  private int[] unusedList;                   // unused Db instances in the pool
  private int unusedCount;                    // number of unused Db instances

  private Thread timeoutThread;               // watching for timed out Db instances to close and for min pool size
  private volatile boolean shutdownRequested; // true if shutdown procedure initiated


  /**
   * Creates a pool.
   *
   * @param name the name of the pool
   * @param conMgr the connection manager to use for new Db instances
   * @param sessionInfo the server's session info
   * @param iniSize the initial poolsize
   * @param incSize the number of Db instances to enlarge the pool if all in use
   * @param minSize the minimum number of Db instances to keep in pool
   * @param maxSize the maximum number of Db instances, 0 = unlimited
   * @param maxIdleMinutes the idle timeout in minutes to close unused Db instances, 0 = unlimited
   * @param maxUsageMinutes the max. used time in minutes, 0 = unlimited
   */
  public DefaultDbPool (String name, ConnectionManager conMgr, SessionInfo sessionInfo,
                        int iniSize, int incSize, int minSize, int maxSize, long maxIdleMinutes, long maxUsageMinutes) {

    if (maxSize > 0 && (maxSize < iniSize || maxSize < minSize) ||
        minSize < 1 ||
        incSize < 1 ||
        iniSize < 1) {
      throw new IllegalArgumentException("illegal size parameters");
    }

    if (name == null) {
      throw new NullPointerException("name must not be null");
    }

    this.name = name;
    this.conMgr = conMgr;
    this.sessionInfo = sessionInfo;
    this.iniSize = iniSize;
    this.incSize = incSize;
    this.minSize = minSize;
    this.maxSize = maxSize;
    this.maxIdleMinutes = maxIdleMinutes;
    this.maxUsageMinutes = maxUsageMinutes;

    // setup the pool
    pooledSessions = new PooledDb[iniSize];
    freeList = new int[iniSize];
    unusedList = new int[iniSize];
    for (int i=0; i < iniSize; i++) {
      pooledSessions[i] = null;
      freeList[freeCount++] = i;
      unusedList[i] = -1;
    }

    // the db instances are created the first time a getSession() is requested
  }


  /**
   * Creates a pool useful for most servers.<br>
   * Using the default connection manager.
   * Starts with 8 Db instances, increments by 2, minSize 4, maxSize from connection manager.
   * Idle timeout 1 hour, usage timeout 1 day.
   *
   * @param conMgr the connection manager
   * @param ui the userinfo for the created Db
   */
  public DefaultDbPool (ConnectionManager conMgr, SessionInfo ui) {
    this("default-pool", conMgr, ui, 8, 2, 4, conMgr.getMaxSessions(), 60L, 24*60L);
  }


  @Override
  public String toString() {
    return name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  /**
   * Gets the connection manager.
   *
   * @return the connection manager
   */
  public ConnectionManager getConnectionManager() {
    return conMgr;
  }

  /**
   * Gets the initial size.
   *
   * @return the initial size
   */
  public int getIniSize() {
    return iniSize;
  }

  /**
   * Gets the minimum increment to enlarge the pool.
   *
   * @return the increment size
   */
  public synchronized int getIncSize() {
    return incSize;
  }

  /**
   * Sets the minimum increment to enlarge the pool.
   *
   * @param incSize the minimum increment (at least 1)
   */
  public synchronized void setIncSize(int incSize) {
    if (incSize < 1) {
      throw new IllegalArgumentException("increment size must be at least 1");
    }
    this.incSize = incSize;
  }

  /**
   * Gets the minimum size.
   *
   * @return the minimum size
   */
  public synchronized int getMinSize() {
    return minSize;
  }

  /**
   * Sets the minimum size.
   *
   * @param minSize the minimum size (at least 1)
   */
  public synchronized void setMinSize(int minSize) {
    if (minSize < 1) {
      throw new IllegalArgumentException("minimum size must be at least 1");
    }
    this.minSize = minSize;
  }

  @Override
  public synchronized int getMaxSize() {
    return maxSize;
  }

  /**
   * Sets the maximum size.
   *
   * @param maxSize the maximum size (at least minSize)
   */
  public synchronized void setMaxSize(int maxSize) {
    if (maxSize < minSize) {
      throw new IllegalArgumentException("maximum size must not be lower than minsize=" + minSize);
    }
    this.maxSize = maxSize;
  }

  /**
   * Gets the idle timeout in minutes.
   *
   * @return the idle timeout
   */
  public synchronized long getMaxIdleMinutes() {
    return maxIdleMinutes;
  }

  /**
   * Sets the idle minutes.<br>
   * Sessions are closed if unused for given minutes.
   *
   * @param maxIdleMinutes the idle timeout, 0 if unlimited
   */
  public synchronized void setMaxIdleMinutes(long maxIdleMinutes) {
    this.maxIdleMinutes = maxIdleMinutes;
  }

  /**
   * Gets the usage timeout in minutes.
   *
   * @return the usage timeout
   */
  public synchronized long getMaxUsageMinutes() {
    return maxUsageMinutes;
  }

  /**
   * Sets the maximum usage minutes.<br>
   * Sessions are closed if unused and first used foe given timeout.
   *
   * @param maxUsageMinutes the usage timeout, 0 if unlimited
   */
  public synchronized void setMaxUsageMinutes(long maxUsageMinutes) {
    this.maxUsageMinutes = maxUsageMinutes;
  }


  @Override
  public void shutdown() {
    shutdownRequested = true;

    if (timeoutThread != null && timeoutThread.isAlive()) {
      timeoutThread.interrupt();
      try {
        timeoutThread.join();
      }
      catch (InterruptedException ex) {
        LOGGER.warning("shutdown " + timeoutThread + " for " + this + " failed", ex);
        // continue and close all Db instances
      }
    }

    synchronized(this) {
      for (PooledDb pdb: pooledSessions) {
        if (pdb != null) {
          pdb.close();
        }
      }
      pooledSessions = null;
      freeList = null;
      unusedList = null;
      timeoutThread = null;
    }

  }

  @Override
  public synchronized boolean isShutdown() {
    return pooledSessions == null;
  }

  @Override
  public synchronized int getSize() {
    return pooledSessions == null ? 0 : pooledSessions.length - freeCount;
  }

  @Override
  public synchronized Db getSession() {
    assertNotShutdown();
    if (!initialized) {
      // pool is empty at begin: create instances
      createDbInstances(iniSize);
      // start timeout thread
      timeoutThread = new TimeoutThread(this);
      timeoutThread.start();
      initialized = true;    // first time initialization completed
    }
    if (unusedCount == 0) {
      createDbInstances(incSize); // enlarge the pool (will throw Exception if pool is exhausted)
    }
    int poolId = unusedList[--unusedCount];
    PooledDb pooledDb = pooledSessions[poolId];
    Db db = pooledDb == null ? null : pooledDb.getSession();
    if (!db.isOpen()) {
      throw new SessionClosedException(db, this + ": Db has been closed unexpectedly");
    }
    pooledDb.use(Thread.currentThread());
    db.setPoolId(poolId + 1);   // starting at 1
    LOGGER.fine("{0}: Db {1} assigned to pool id {2}", this, db, poolId);
    return db;
  }

  @Override
  public synchronized void putSession(Session session) {
    assertNotShutdown();
    final Db db = (Db) session;
    if (db.getPool() != this) {
      throw new PersistenceException(db, "Db is not managed by pool " + this);
    }
    int poolId = db.getPoolId();
    // 0 = already returned to pool, -1 removed from pool, else not returned yet
    if (poolId < -1 || poolId > pooledSessions.length) {
      throw new PersistenceException(db, this + ": Db has invalid poolid " + poolId);
    }
    if (db.isOpen()) {
      boolean txRunning = db.isTxRunning();
      if (txRunning) {
        // rollback first and return to pool, log the exception later (see below)
        db.rollbackImmediately(null);
      }
      if (poolId > 0) { // if not returned to pool yet
        // check if there are no pending transactions
        ManagedConnection con = db.getConnection();
        if (con != null) {
          con.closePreparedStatements(true); // cleanup all pending statements
          removeDbInstance(db, poolId - 1);  // remove from pool
        }
        else {
          LOGGER.fine("{0}: Db {1} returned to pool, id {2}", this, db, poolId);
          poolId--;
          pooledSessions[poolId].unUse(db);
          unusedList[unusedCount++] = poolId;
        }
        db.setPoolId(0);            // returned to pool
        db.setSessionGroupId(0);    // clear group
        db.setOwnerThread(null);    // unlink any owner thread
      }
      // else: not an error to return a db more than once
      if (txRunning) {
        throw new PersistenceException(db, "Db was still running a transaction -- rolled back!");
      }
    }
    else {
      if (poolId > 0) { // if not returned to pool yet
        removeDbInstance(db, poolId - 1);           // remove from pool
        LOGGER.warning(this + ": returned Db " + db + " was closed and removed from pool");
      }
      else {
        LOGGER.warning(this + ": returned Db " + db + " was closed and already returned to pool");
      }
    }
  }


  /**
   * Creates a new pooled db.
   *
   * @param slotNumber the slot number, starting at 0
   * @return the pooled db
   */
  protected PooledDb createPooledDb(int slotNumber) {
    return new PooledDb(this, slotNumber);
  }


  /**
   * Creates Db instances.<br>
   * The number of created instances is at least 1.
   *
   * @param num the number of instances to add to the pool
   * @throws PersistenceException if pool exhausted and max poolsize reached
   */
  private void createDbInstances(int num) {
    if (num > freeCount) {
      // enlarge arrays
      int nSize = pooledSessions.length + num - freeCount;
      if (maxSize > 0 && nSize > maxSize) {
        nSize = maxSize;
      }
      if (nSize <= pooledSessions.length) {
        throw new PersistenceException("cannot create more Db instances, max. poolsize " + maxSize + " reached");
      }
      PooledDb[] nPool  = new PooledDb[nSize];
      int[] nFreeList   = new int[nSize];
      int[] nUnusedList = new int[nSize];
      System.arraycopy(pooledSessions, 0, nPool, 0, pooledSessions.length);
      System.arraycopy(freeList, 0, nFreeList, 0, pooledSessions.length);
      System.arraycopy(unusedList, 0, nUnusedList, 0, pooledSessions.length);
      for (int i=pooledSessions.length; i < nSize; i++) {
        nPool[i] = null;
        nFreeList[freeCount++] = i;
        nUnusedList[i] = -1;
      }
      pooledSessions = nPool;
      freeList = nFreeList;
      unusedList = nUnusedList;
    }

    // freeCount is at least 1: get from freelist
    while (num > 0 && freeCount > 0) {
      int slotNumber = freeList[freeCount - 1];  // no --freeCount because new PooledDb() may throw exceptions
      pooledSessions[slotNumber] = createPooledDb(slotNumber);
      freeCount--;
      unusedList[unusedCount++] = slotNumber;
      num--;
    }

    // now we have at least 1 unused Db instances
  }

  /**
   * Closes a Db instance and removes it from the pool.
   *
   * @param dbToRemove the db instance to remove
   * @param index the pool index
   */
  private void removeDbInstance(Db dbToRemove, int index) {
    Db db = pooledSessions[index].getReferencedDb();    // pooledDb.db may be null because currently lended
    if (db != dbToRemove) {
      throw new PersistenceException(dbToRemove + " to remove does not match " + db + " in pool " + this + " at index " + index);
    }

    if (db != null) {
      try {
        if (db.isOpen()) {
          db.close();       // this will also check for pending attach/tx and rollback if necessary
        }
        db.setPoolId(-1);   // mark it as removed from pool -> cannot be re-opened again
      }
      catch (RuntimeException re) {
        LOGGER.severe("closing pooled db failed", re);
      }
    }

    removeDbIndex(index);
  }

  /**
   * Removes the index from the pool.
   *
   * @param index the pool index
   */
  private void removeDbIndex(int index) {
    pooledSessions[index] = null;
    freeList[freeCount++] = index;  // add to freelist

    // check if index was in the unused list. If so, remove it
    for (int i=0; i < unusedCount; i++) {
      if (unusedList[i] == index) {
        // found:
        System.arraycopy(unusedList, i + 1, unusedList, i, unusedCount - (i + 1));
        unusedCount--;
        break;
      }
    }
  }

  /**
   * Asserts that this pool wasn't shutdown.
   */
  private void assertNotShutdown() {
    if (pooledSessions == null) {
      throw new PersistenceException(this + " already shutdown");
    }
  }


  /**
   * Timeout thread to close pooled db instances when not used for maxMinutes time.
   * This is to release resources, if any, and improves memory consumption
   * for long running servers. Furthermore, if references to a closed db
   * are still in use, an exception is thrown when used again.
   * <p>
   * Notice: the thread will be terminated if pool is shutdown or unreferenced
   */
  private static class TimeoutThread extends Thread implements Scavenger {

    private final WeakReference<DefaultDbPool> poolRef;

    private TimeoutThread(DefaultDbPool pool) {
      super("Db-Pool '" + pool.getName() + "' Timeout Thread");
      poolRef = new WeakReference<>(pool);
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
            // just continue
          }

          DefaultDbPool pool = poolRef.get();
          if (pool == null || pool.shutdownRequested) {
            break;
          }

          long curtime = System.currentTimeMillis();

          synchronized (pool) {

            // bring down timed out unused Db instances
            int i = 0;    // start with oldest unused
            while (i < pool.unusedCount) {
              int index = pool.unusedList[i];
              PooledDb pooledDb = pool.pooledSessions[index];  // pooledDb.db != null because unused

              long idleMinutes = pooledDb.idleMinutes(curtime);
              boolean idleTimedOut = idleMinutes > pool.getMaxIdleMinutes();
              if (idleTimedOut) {
                LOGGER.info("{0} idle for {1} (max={2}) -> closed", pooledDb, idleMinutes, pool.getMaxIdleMinutes());
              }

              long usedMinutes = pooledDb.usedMinutes(curtime);
              boolean usageTimedOut = usedMinutes > pool.getMaxUsageMinutes();
              if (usageTimedOut) {
                LOGGER.info("{0} used for {1} (max={2}) -> closed", pooledDb, usedMinutes, pool.getMaxUsageMinutes());
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

            // remove unreferenced Db instances
            i = 0;
            for (PooledDb pooledDb : pool.pooledSessions) {
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

            // check if we need to bring up some Db for minSize
            int size = pool.getSize();
            if (size < pool.getMinSize()) {
              pool.createDbInstances(pool.getMinSize() - size);
            }
          }
        }
        catch (RuntimeException ex) {
          LOGGER.severe("cleaning up unused Db instance(s) failed", ex);
        }
      }
      LOGGER.info(this + " terminated");
    }
  }

}

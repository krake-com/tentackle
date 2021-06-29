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

import org.tentackle.log.Logger;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionClosedException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;

import java.util.Objects;

/**
 * An implementation of a session pool.<br>
 * It allows min/max sizes, fixed increments and timeouts for unused instances.
 * <p>
 * The pool can be used with any ConnectionManager.
 * If used with the {@link DefaultConnectionManager}, each {@link Db} instance corresponds to a
 * physical JDBC-connection. If used with an {@link MpxConnectionManager}, the {@link Db} instances
 * map to virtual connections that will be attached temporarily during db-operations.
 * This is the preferred configuration in server applications with a lot of clients.
 * In order to clear the PdoCache on db-close, you have to override the closeDb method.
 */
public class DbPool implements SessionPool {

  private static final Logger LOGGER = Logger.get(DbPool.class);

  private final String name;                  // the pool's name
  private final ConnectionManager conMgr;     // the connection manager (if local)
  private final SessionInfo sessionInfo;      // the session info
  private final int sessionGroupId;           // optional session group id for new sessions
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


  /**
   * Creates a pool.
   *
   * @param name the name of the pool
   * @param conMgr the connection manager to use for new local sessions, null if remote
   * @param sessionInfo the session info
   * @param sessionGroupId the session group, 0 if none
   * @param iniSize the initial poolsize
   * @param incSize the number of Db instances to enlarge the pool if all in use
   * @param minSize the minimum number of Db instances to keep in pool, &lt; 0 if auto shutdown pool if all sessions timed out
   * @param maxSize the maximum number of Db instances, 0 = unlimited
   * @param maxIdleMinutes the idle timeout in minutes to close unused Db instances, 0 = unlimited
   * @param maxUsageMinutes the max. used time in minutes, 0 = unlimited
   */
  public DbPool(String name, ConnectionManager conMgr, SessionInfo sessionInfo, int sessionGroupId,
                int iniSize, int incSize, int minSize, int maxSize, long maxIdleMinutes, long maxUsageMinutes) {

    if (maxSize > 0 && (maxSize < iniSize || maxSize < minSize) ||
        incSize < 1 ||
        iniSize < 1) {
      throw new IllegalArgumentException("illegal size parameters");
    }

    this.name = Objects.requireNonNull(name, "name");
    this.conMgr = conMgr;
    this.sessionInfo = Objects.requireNonNull(sessionInfo, "sessionInfo");
    this.sessionGroupId = sessionGroupId;
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
  public DbPool(ConnectionManager conMgr, SessionInfo ui) {
    this("default-pool", conMgr, ui, 0, 8, 2, 4, conMgr.getMaxSessions(), 60L, 24*60L);
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

  @Override
  public int getSessionGroupId() {
    return sessionGroupId;
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
   * @param minSize the minimum size (&lt; 0 if shutdown pool when all sessions timed out)
   */
  public synchronized void setMinSize(int minSize) {
    this.minSize = minSize;
  }

  @Override
  public synchronized int getMaxSize() {
    return maxSize;
  }

  /**
   * Sets the maximum size.
   *
   * @param maxSize the maximum size (at least minSize, 0 if unlimited)
   */
  public synchronized void setMaxSize(int maxSize) {
    if (maxSize > 0 && maxSize < minSize) {
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
  public synchronized void shutdown() {
    assertNotShutdown();
    LOGGER.info("shutting down {0}", this);
    for (PooledDb pdb: pooledSessions) {
      if (pdb != null) {
        pdb.close();
      }
    }
    pooledSessions = null;
    freeList = null;
    unusedList = null;
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
      initialized = true;
      DbPoolTimeoutThread.register(this);   // start monitoring by timeout thread
    }
    if (unusedCount == 0) {
      createDbInstances(incSize); // enlarge the pool (will throw Exception if pool is exhausted)
    }
    int poolId = unusedList[--unusedCount];
    PooledDb pooledDb = pooledSessions[poolId];
    if (pooledDb == null) {
      throw new SessionClosedException("pool " + this + ": session cleared unexpectedly");
    }
    Db db = pooledDb.getSession();
    if (db == null) {
      throw new SessionClosedException("pool " + this + ": session still in use unexpectedly");
    }
    if (!db.isOpen()) {
      throw new SessionClosedException(db, "pool " + this + ": session has been closed unexpectedly");
    }
    pooledDb.use(Thread.currentThread());
    db.setPoolId(poolId + 1);   // starting at 1
    LOGGER.fine("{0}: session {1} assigned to pool id {2}", this, db, poolId);
    return db;
  }

  @Override
  public synchronized void putSession(Session session) {
    assertNotShutdown();
    final Db db = (Db) session;
    if (db.getPool() != this) {
      throw new PersistenceException(db, "session is not managed by pool " + this);
    }
    int poolId = db.getPoolId();
    // 0 = already returned to pool, -1 removed from pool, else not returned yet
    if (poolId < -1 || poolId > pooledSessions.length) {
      throw new PersistenceException(db, "pool " + this + ": session has invalid poolid " + poolId);
    }
    if (db.isOpen()) {
      boolean txRunning = db.isTxRunning();
      String exMsg = null;
      if (txRunning) {
        exMsg = "session " + db + " was still running a transaction -> ";
        if (db.isRemote()) {
          // rollbackImmediately not allowed for remote sessions.
          // just close it, the rollback will be performed at the remote side.
          exMsg = "remote " + exMsg + "closed and removed from pool";
          removeDbInstance(db, poolId - 1);
          poolId = 0;

        }
        else {
          // rollback first and return to pool, log the exception later (see below)
          exMsg += "rolled back";
          db.rollbackImmediately(null);
        }
      }
      if (poolId > 0) { // if not returned to pool yet
        // check if there are no pending statements
        ManagedConnection con = db.getConnection();
        if (con != null) {
          con.closePreparedStatements(true); // cleanup all pending statements
          removeDbInstance(db, poolId - 1);  // remove from pool
        }
        else {
          LOGGER.fine("{0}: session {1} returned to pool, id {2}", this, db, poolId);
          poolId--;
          pooledSessions[poolId].unUse(db);
          unusedList[unusedCount++] = poolId;
        }
        db.setPoolId(0);            // returned to pool
        db.setOwnerThread(null);    // unlink any owner thread
      } // else: not an error to return a db more than once

      if (exMsg != null) {
        throw new PersistenceException(db, exMsg);
      }
    }
    else {
      if (poolId > 0) { // if not returned to pool yet
        removeDbInstance(db, poolId - 1);           // remove from pool
        LOGGER.warning("pool " + this + ": returned session " + db + " was closed and removed from pool");
      }
      else {
        LOGGER.warning("pool " + this + ": returned session " + db + " was closed and already returned to pool");
      }
    }
  }

  /**
   * Gets the number of unused sessions.
   *
   * @return the unused count
   */
  protected int getUnusedCount() {
    return unusedCount;
  }

  /**
   * Transforms an index of the unused sessions to a session index.
   *
   * @param unusedIndex the unused index
   * @return the session index
   */
  protected int getUnusedSessionIndex(int unusedIndex) {
    return unusedList[unusedIndex];
  }

  /**
   * Gets all pooled sessions.
   *
   * @return the pooled sessions
   */
  protected PooledDb[] getPooledSessions() {
    return pooledSessions;
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
   * @return number of instances actually created
   * @throws PersistenceException if pool exhausted and max poolsize reached
   */
  protected int createDbInstances(int num) {
    if (num > freeCount) {
      // enlarge arrays
      int nSize = pooledSessions.length + num - freeCount;
      if (maxSize > 0 && nSize > maxSize) {
        nSize = maxSize;
      }
      if (nSize <= pooledSessions.length) {
        PersistenceException pex = new PersistenceException("cannot create more session instances, max. poolsize " + maxSize + " reached");
        pex.setTemporary(true);
        throw pex;
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
    int created = 0;
    while (num > 0 && freeCount > 0) {
      int slotNumber = freeList[freeCount - 1];  // no --freeCount because new PooledDb() may throw exceptions
      pooledSessions[slotNumber] = createPooledDb(slotNumber);
      created++;
      freeCount--;
      unusedList[unusedCount++] = slotNumber;
      num--;
    }

    // now we have at least 1 unused Db instances
    return created;
  }

  /**
   * Closes a Db instance and removes it from the pool.
   *
   * @param dbToRemove the db instance to remove
   * @param index the pool index
   */
  protected void removeDbInstance(Db dbToRemove, int index) {
    Db db = pooledSessions[index].getReferencedDb();    // pooledDb.db may be null because currently lended
    if (db != null) {
      if (db != dbToRemove && dbToRemove != null) {
        throw new PersistenceException(dbToRemove + " to remove does not match " + db + " in pool '" + this + "' at index " + index);
      }
      try {
        if (db.isOpen()) {
          db.close();       // this will also check for pending attach/tx and rollback if necessary
        }
        db.setPoolId(-1);   // mark it as removed from pool -> cannot be re-opened again
      }
      catch (RuntimeException re) {
        LOGGER.severe("closing pooled session failed", re);
      }
    }

    removeDbIndex(index);
  }

  /**
   * Removes the index from the pool.
   *
   * @param index the pool index
   */
  protected void removeDbIndex(int index) {
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
    if (isShutdown()) {
      throw new PersistenceException("pool '" + this + "' already shutdown");
    }
  }

}

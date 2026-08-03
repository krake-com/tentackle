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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.misc.AbstractPool;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;

import java.util.Objects;

/**
 * Implementation of a session pool.
 * <p>
 * The pool can be used with any ConnectionManager.
 * If used with the {@link DefaultConnectionManager}, each {@link Db} instance corresponds to a
 * physical JDBC connection. If used with an {@link MpxConnectionManager}, the {@link Db} instances
 * map to virtual connections that will be attached temporarily during database operations.
 * This is the preferred configuration in server applications with a lot of clients.
 */
public class DbPool extends AbstractPool<Session> implements SessionPool {

  private static final Logger LOGGER = Logger.get(DbPool.class);

  private final ConnectionManager conMgr;     // the connection manager (if local)
  private final SessionInfo sessionInfo;      // the session info
  private final int sessionGroupId;           // optional session group id for new sessions


  /**
   * Creates a pool.
   *
   * @param name the name of the pool
   * @param conMgr the connection manager to use for new local sessions, null if remote
   * @param sessionInfo the session info
   * @param sessionGroupId the session group ID, 0 if none
   * @param initialSize the initial pool size
   * @param incrementSize the number of Db instances to enlarge the pool if all in use
   * @param minimumSize the minimum number of Db instances to keep in the pool, &lt; 0 if auto shutdown pool if all sessions timed out
   * @param maximumSize the maximum number of Db instances, 0 = unlimited
   * @param idleMinutes the idle timeout in minutes to close unused Db instances, 0 = unlimited
   * @param usageMinutes the max. used time in minutes, 0 = unlimited
   */
  public DbPool(String name, ConnectionManager conMgr, SessionInfo sessionInfo, int sessionGroupId,
                int initialSize, int incrementSize, int minimumSize, int maximumSize, long idleMinutes, long usageMinutes) {

    super(name, initialSize, incrementSize, minimumSize, maximumSize, idleMinutes, usageMinutes);

    this.conMgr = conMgr;
    this.sessionInfo = Objects.requireNonNull(sessionInfo, "sessionInfo");
    this.sessionGroupId = sessionGroupId;
  }

  /**
   * Creates a pool useful for most servers.<br>
   * Using the default connection manager.
   * Starts with 8 Db instances, increments by 2, minSize 4, maxSize from connection manager.
   * Idle timeout 1 hour, usage timeout 1 day.
   *
   * @param conMgr the connection manager
   * @param sessionInfo the session info for the created Db
   */
  public DbPool(ConnectionManager conMgr, SessionInfo sessionInfo) {
    this("default-pool", conMgr, sessionInfo, 0, 8, 2, 4, conMgr.getMaxSessions(), 60L, 24*60L);
  }


  /**
   * Gets the connection manager.
   *
   * @return the connection manager
   */
  public ConnectionManager getConnectionManager() {
    return conMgr;
  }

  @Override
  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  @Override
  public int getSessionGroupId() {
    return sessionGroupId;
  }

  @Override
  protected void unusePoolable(Session session) {
    Db db = (Db) session;
    int poolId = db.getPoolId();    // 0 if already returned, -1 if removed, else the slot index + 1
    boolean txRunning = db.isTxRunning();
    String exMsg = null;
    if (txRunning) {
      exMsg = "session " + db + " was still running a transaction ->";
      if (db.isRemote()) {
        // rollbackImmediately is not allowed for remote sessions.
        // just close it, the rollback will be performed at the remote side.
        if (poolId > 0) {   // only if not returned to the pool yet, else the slot index is unknown
          removePoolable(db, poolId - 1);
          poolId = -1;
          exMsg += " remote session closed and removed from pool";
        }
        else {
          exMsg += " remote session already returned to the pool";
        }
      }
      else {
        // rollback first and return to pool, log the exception later (see below)
        try {
          db.rollbackImmediately(null);
          exMsg += " rolled back";
        }
        catch (RuntimeException rx) {
          exMsg += " rollback failed (" + rx.getMessage() + ")";
        }
      }
    }

    if (poolId > 0) { // if not returned to pool yet
      // check if there are no pending statements
      ManagedConnection con = db.getConnection();
      if (con != null) {    // still attached?
        // attached is an error -> remove it for sure
        if (!con.isDead()) {
          try {
            con.closePreparedStatements(true); // clean up all pending statements
          }
          catch (RuntimeException rx) {
            LOGGER.warning("cleaning up pending statements failed", rx);
          }
        }
        removePoolable(db, poolId - 1);    // remove from the pool for sure
        poolId = -1;
        if (exMsg == null) {
          exMsg = "session " + db + " still attached";
        }
        exMsg += " -> removed from pool";
      }
    } // else: not an error to return a db more than once

    db.setOwnerThread(null);    // unlink any owner thread
    db.clearProperties();

    if (poolId > 0) {
      super.unusePoolable(db);
    }

    if (exMsg != null) {
      throw new PersistenceException(db, exMsg);
    }
  }


  /**
   * Creates the session info for a new pooled Db.
   *
   * @param slotNumber the slot number
   * @return the session info
   */
  protected SessionInfo createSessionInfo(int slotNumber) {
    SessionInfo sessionInfo = getSessionInfo().clone();
    sessionInfo.setSessionName(getName() + "#" + slotNumber);
    return sessionInfo;
  }

  /**
   * Creates a new session.
   *
   * @param slotNumber the slot number
   * @return the open session
   */
  protected Db createSession(int slotNumber) {
    LOGGER.fine("open pooled Db for {0}, connection manager {1}, slot {2}",
                getSessionInfo(), getConnectionManager(), slotNumber);
    Db session = (Db) SessionFactory.getInstance().create(this, createSessionInfo(slotNumber));
    if (getSessionGroupId() != 0) {
      session.groupWith(getSessionGroupId());
    }
    return session;
  }

  @Override
  protected DbPoolSlot createSlot(int slotNumber) {
    return new DbPoolSlot(this, slotNumber, createSession(slotNumber));
  }

  @Override
  protected TentackleRuntimeException createException(String msg) {
    return new PersistenceException(msg);
  }

  @Override
  protected TentackleRuntimeException createException(Session session, String msg) {
    return new PersistenceException(session, msg);
  }

}

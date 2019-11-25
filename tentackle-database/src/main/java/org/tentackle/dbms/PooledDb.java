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
import org.tentackle.common.StringHelper;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionInfo;

import java.lang.ref.WeakReference;

/**
 * Session managed by the session pool.
 */
public class PooledDb {

  private static final Logger LOGGER = Logger.get(PooledDb.class);

  private final DefaultDbPool pool;         // the session pool
  private final int slotNumber;             // the slot number
  private Db db;                            // the managed db, null if currently lended
  private final String dbStr;               // the db string (not the reference!)
  private final WeakReference<Db> refDb;    // weak reference to detect unreferenced lended Db instances
  private long usedSince;                   // last lend time, 0 if in pool
  private String usingThreadStr;            // the lending thread string (not the reference!), null if in pool
  private String mdcStr;                    // mapped diagnostic context string
  private long unusedSince;                 // last return time, 0 if lended
  private long firstUse;                    // epochal time of first use


  /**
   * Creates a pooled session.
   *
   * @param pool the session pool
   */
  public PooledDb(DefaultDbPool pool, int slotNumber) {
    this.pool = pool;
    this.slotNumber = slotNumber;

    db = createSession(slotNumber);
    db.setPool(pool);
    dbStr = db.toString();
    unusedSince = System.currentTimeMillis();
    refDb = new WeakReference<>(db);
  }

  /**
   * Gets the session pool.
   *
   * @return the pool
   */
  public DefaultDbPool getPool() {
    return pool;
  }

  /**
   * Gets the slot number.
   *
   * @return the slot number
   */
  public int getSlotNumber() {
    return slotNumber;
  }

  /**
   * Returns the managed session.
   *
   * @return the managed session, null if currently lended
   */
  public Db getSession() {
    return db;
  }

  /**
   * Gets the referenced session.<br>
   * This is the value of a {@link WeakReference}.
   *
   * @return the session, null if no more referenced.
   */
  public Db getReferencedDb() {
    return refDb.get();
  }

  /**
   * Get epochal time of first use.
   *
   * @return time in ms
   */
  public long getFirstUse() {
    return firstUse;
  }

  /**
   * Gets the last return time, 0 if lended.
   *
   * @return time in ms
   */
  public long getUnusedSince() {
    return unusedSince;
  }

  /**
   * Gets the lending thread string.<br>
   * Avoids the references to the thread.
   *
   * @return the thread name, null if in pool
   */
  public String getUsingThreadStr() {
    return usingThreadStr;
  }

  /**
   * Gets the mapped diagnostic context string.
   *
   * @return the MDC
   */
  public String getMdcStr() {
    return mdcStr;
  }

  @Override
  public String toString() {
    return pool + "#" + slotNumber + "|" + dbStr;
  }

  /**
   * Closes a pooled db.
   */
  public void close() {
    Db dbToClose = refDb.get();    // db may be null because currently lended
    if (dbToClose != null) {       // refDb.db may be null because already garbage collected
      try {
        dbToClose.close();
      }
      catch (RuntimeException rex) {
        LOGGER.warning("closing pooled Db " + dbToClose + " failed", rex);
      }
      finally {
        db = null;
      }
    }
  }

  /**
   * Marks a pooled db used.
   *
   * @param usingThread the using thread
   */
  public void use(Thread usingThread) {
    if (db == null) {
      throw new PersistenceException("unexpected loss of reference to " + dbStr +
                                         " (last returned by " + usingThreadStr + " since " + new Timestamp(unusedSince) + ")");
    }
    usedSince = System.currentTimeMillis();
    if (firstUse == 0) {
      firstUse = usedSince;
    }
    unusedSince = 0;
    usingThreadStr = usingThread.toString();
    mdcStr = LOGGER.getMappedDiagnosticContext().toString();
    // keep only the weak reference
    db = null;
  }

  /**
   * Marks a pooled db unused.
   */
  public void unUse(Db db) {
    // switch back to hard reference
    this.db = refDb.get();
    if (this.db == null) {
      throw new PersistenceException("unexpected loss of reference to " + dbStr +
                                         " (last use by " + usingThreadStr + " since " + new Timestamp(usedSince) + ")");
    }
    if (this.db != db) {
      this.db = null;
      throw new PersistenceException("attempt to unuse " + db + " in wrong slot " + dbStr +
                                         " (last use by " + usingThreadStr + " since " + new Timestamp(usedSince) + ")");
    }
    unusedSince = System.currentTimeMillis();
    usedSince = 0;
    usingThreadStr = null;
    mdcStr = null;
  }

  /**
   * Checks for forgotten puts.
   *
   * @return true if db has been lended but never returned and is not referenced anymore
   */
  public boolean isUnreferenced() {
    return refDb.get() == null;
  }

  /**
   * Returns the number of minutes the session has been unused.
   *
   * @param currentTimeMillis the current time to refer to in epochal milliseconds
   * @return the idle minutes, 0 if in use or never used at all
   */
  public long idleMinutes(long currentTimeMillis) {
    return firstUse == 0 || unusedSince == 0 ? 0 : (currentTimeMillis - unusedSince) / Constants.MINUTE_MS;
  }

  /**
   * Returns the number of minutes the session has been used at all.
   *
   * @param currentTimeMillis the current time to refer to in epochal milliseconds
   * @return the usage minutes, 0 if never used at all
   */
  public long usedMinutes(long currentTimeMillis) {
    return firstUse == 0 ? 0 : (currentTimeMillis - firstUse) / Constants.MINUTE_MS;
  }


  /**
   * Creates the session info for a new db.
   *
   * @param slotNumber the slot number
   * @return the session info
   */
  protected SessionInfo createSessionInfo(int slotNumber) {
    SessionInfo sessionInfo = pool.getSessionInfo().clone();
    StringBuilder buf = new StringBuilder();
    if (!StringHelper.isAllWhitespace(sessionInfo.getApplicationName())) {
      buf.append(sessionInfo.getApplicationName());
    }
    else {
      buf.append("<unnamed>");
    }
    buf.append('|').append(pool.getName()).append('#').append(slotNumber);
    sessionInfo.setApplicationName(buf.toString());
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
        pool.getSessionInfo(), pool.getConnectionManager(), slotNumber);
    Db session = new Db(pool.getConnectionManager(), createSessionInfo(slotNumber));
    if (pool.getSessionGroupId() > 0) {
      session.groupWith(pool.getSessionGroupId());
    }
    return session;
  }

}

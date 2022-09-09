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
import org.tentackle.session.SessionUtilities;

import java.util.Map;
import java.util.WeakHashMap;



/**
 * Counter to track modifications for a class/table.<br>
 * The ModificationTracker maintains a list of ModificationTally-objects, one for each tracked name.
 *
 * @author harald
 */
public class ModificationTally {

  private static final Logger LOGGER = Logger.get(ModificationTally.class);


  private final DbModificationTracker tracker;        // the mod tracker
  private final DbModification dbModification;        // the persisted modification object
  private DbObjectClassVariables<?> clazzVar;         // the classvariables if tablename belongs to a PDO, null if just a name
  private Boolean isTableSerialProvided;              // true if table serial is managed by table, null if not known yet
  private String tableSerialTableName;                // if tableSerialValid holds the tableserial's tablename
  private long lastSerial;                            // last (known) serial for the table
  private long pendingCount;                          // number of pending counts
  private final Map<Session, Object> sessionsInTx;    // sessions running a transaction during countPending()


  /**
   * Creates a modification counter for a given tracked name.
   *
   * @param tracker the modification tracker
   * @param trackedName the tracked name
   */
  public ModificationTally(DbModificationTracker tracker, String trackedName) {
    this.tracker = tracker;
    sessionsInTx = new WeakHashMap<>();     // keys are weak! Db does not override equals!
    dbModification = new DbModification(tracker.isLocalClientMode() ? null : tracker.getSession(), trackedName);
    clazzVar = DbObjectClassVariables.getVariables(trackedName);
    if (clazzVar == null) {
      // try to load the class by its name (maybe not referenced so far)
      String className = SessionUtilities.getInstance().getClassName(trackedName);
      if (className != null) {
        try {
          // this will also register the classvariables, if any
          DbUtilities.getInstance().createObject(Class.forName(className));
          // try again
          clazzVar = DbObjectClassVariables.getVariables(trackedName);
        }
        catch (ClassNotFoundException | RuntimeException ex) {
          LOGGER.fine("not a PDO class", ex);
        }
      }
    }
    if (clazzVar == null) {
      LOGGER.info("counter created for {0} not assigned to any class variable", trackedName);
    }
    else  {
      LOGGER.info("counter created for {0} assigned to class variable {1}", trackedName, clazzVar);
    }
  }


  /**
   * Gets the modification PO.
   *
   * @return the modification PO
   */
  public DbModification getDbModification() {
    return dbModification;
  }

  /**
   * Gets the ID of the counter.
   *
   * @return the unique id
   */
  public long getId() {
    return dbModification.getId();
  }

  /**
   * Adds a pending count.
   *
   * @param session the session persisting the modification, null if thread-local session
   */
  public synchronized void countPending(Session session) {
    if (session == null) {
      session = Session.getCurrentSession();
      if (session == null) {
        throw new PersistenceException("no thread-local session to count modification for " + dbModification);
      }
    }
    if (session.isRemote()) {
      throw new PersistenceException(session, "modification counting not allowed for remote sessions");
    }
    if (session.isTxRunning() && !tracker.isLocalClientMode()) {
      sessionsInTx.putIfAbsent(session, null);
    }
    ++pendingCount;
  }


  /**
   * Gets the pending count.
   *
   * @return the pending count, 0 if none
   */
  public synchronized long getPendingCount() {
    return pendingCount;
  }



  /**
   * Sets the last serial.
   *
   * @param lastSerial the serial
   */
  public synchronized void setLastSerial(long lastSerial) {
    this.lastSerial = lastSerial;
  }

  /**
   * Gets the latest serial updated by this counter.<br>
   *
   * @return the latest serial from last update plus pending count
   */
  public synchronized long getLatestSerial() {
    return lastSerial + pendingCount;
  }



  /**
   * Performs the physical pending count if pending count &gt; 0.
   */
  public synchronized void performPendingCount() {
    if (pendingCount > 0) {
      // don't flush to database because listeners could be triggered that may not see the uncommitted data
      // committed
      sessionsInTx.keySet().removeIf(session -> !session.isTxRunning());
      if (sessionsInTx.isEmpty()) {
        LOGGER.fine("increment serial for {0} by {1}", dbModification, pendingCount);
        if (dbModification.countModification(pendingCount) != 2)  {
          // probably the record doesn't exist? create it!
          dbModification.addToModificationTable(clazzVar != null, getTableSerialTableName());
          // try again
          if (dbModification.countModification(pendingCount) != 2)  {
            throw new PersistenceException(dbModification, "updating modification count failed");
          }
        }
        lastSerial += pendingCount;
        pendingCount = 0;
      }
    }
  }


  /**
   * Adds this counter to the modification table.
   */
  public void addToModificationTable()  {
    dbModification.addToModificationTable(clazzVar != null, getTableSerialTableName());
    setLastSerial(getModificationCount());
  }

  /**
   * Get the current modification count by tablename.
   *
   * @return the modification count
   */
  public long getModificationCount() {
    return dbModification.refresh();
  }


  /**
   * Determines whether table serial is valid for this pdo class.
   *
   * @return the tablename holding the tableserial, null if no tableserial
   */
  private synchronized String getTableSerialTableName() {
    if (isTableSerialProvided == null) {   // not known yet
      isTableSerialProvided = false;
      if (clazzVar != null) {
        tableSerialTableName = DbUtilities.getInstance().determineTableSerialTableName(clazzVar);
        if (tableSerialTableName != null) {
          isTableSerialProvided = true;
        }
      }
    }
    return tableSerialTableName;
  }

}

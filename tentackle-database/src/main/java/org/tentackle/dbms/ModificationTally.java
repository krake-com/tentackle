/**
 * Tentackle - http://www.tentackle.org
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionUtilities;



/**
 * Counter to track modifications for a class/table.<br>
 * The ModificationTracker maintains a list of ModificationTally-objects, one for each tracked name.
 *
 * @author harald
 */
public class ModificationTally {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ModificationTally.class);


  private final DbModification dbModification;        // the persisted modification object
  private DbObjectClassVariables<?> clazzVar;         // the classvariables if tablename belongs to a PDO, null if just a name
  private Boolean isTableSerialProvided;              // true if table serial is managed by table, null if not known yet
  private String tableSerialTableName;                // if tableSerialValid holds the tableserial's tablename
  private long lastSerial;                            // last (known) serial for the table
  private long pendingCount;                          // number of pending counts
  private final Set<Session> sessionsInTx;            // sessions running a transaction during countPending()


  /**
   * Creates a modification counter for a given tracked name.
   *
   * @param db the db connection
   * @param trackedName the tracked name
   */
  public ModificationTally(Db db, String trackedName) {
    sessionsInTx = new HashSet<>();
    dbModification = new DbModification(db, trackedName);
    clazzVar = DbObjectClassVariables.getVariables(trackedName);
    if (clazzVar == null) {
      // try to load the class by its name (may be not referenced so far)
      String className = SessionUtilities.getInstance().getClassName(trackedName);
      if (className != null) {
        try {
          // this will also register the classvariables, if any
          DbUtilities.getInstance().createObject(Class.forName(className));
          // try again
          clazzVar = DbObjectClassVariables.getVariables(trackedName);
        }
        catch (Exception ex) {
          // may be not a PDO class...
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
   * @return the new pending count
   */
  public synchronized long countPending() {
    Session session = Session.getCurrentSession();
    if (session != null && session.isTxRunning()) {
      sessionsInTx.add(session);
    }
    return ++pendingCount;
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
      for (Iterator<Session> iter = sessionsInTx.iterator(); iter.hasNext(); ) {
        Session session = iter.next();
        if (!session.isTxRunning()) {
          iter.remove();    // committed
        }
      }
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

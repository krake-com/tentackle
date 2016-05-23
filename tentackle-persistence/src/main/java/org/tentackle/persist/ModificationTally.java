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


package org.tentackle.persist;

import java.util.concurrent.atomic.AtomicLong;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistenceException;



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
  private final AtomicLong lastSerial;                // last (known) serial for the table
  private final AtomicLong pendingCount;              // number of pending counts


  /**
   * Creates a modification counter for a given tracked name.
   *
   * @param db the db connection
   * @param trackedName the tracked name
   */
  public ModificationTally(Db db, String trackedName) {
    lastSerial = new AtomicLong();
    pendingCount = new AtomicLong();
    dbModification = new DbModification(db, trackedName);
    clazzVar = DbObjectClassVariables.getVariables(trackedName);
    if (clazzVar == null) {
      // try to load the class by its name (may be not referenced so far)
      String className = PdoUtilities.getInstance().getPdoClassName(trackedName);
      if (className != null) {
        try {
          Pdo.create(className);   // this will also register the classvariables, if any
          // try again
          clazzVar = DbObjectClassVariables.getVariables(trackedName);
        }
        catch (PdoRuntimeException ex) {
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
  public long countPending() {
    return pendingCount.incrementAndGet();
  }


  /**
   * Gets the pending count.
   *
   * @return the pending count, 0 if none
   */
  public long getPendingCount() {
    return pendingCount.get();
  }



  /**
   * Sets the last serial.
   *
   * @param lastSerial the serial
   */
  public void setLastSerial(long lastSerial) {
    this.lastSerial.set(lastSerial);
  }

  /**
   * Gets the latest serial updated by this counter.<br>
   *
   * @return the latest serial from last update plus pending count
   */
  public long getLatestSerial() {
    return lastSerial.get() + pendingCount.get();
  }



  /**
   * Performs the physical pending count if pending count &gt; 0.
   */
  public void performPendingCount() {
    long count = pendingCount.getAndSet(0);
    if (count > 0) {
      lastSerial.addAndGet(count);
      LOGGER.fine("increment serial for {0} by {1}", dbModification, count);
      if (dbModification.countModification(count) != 2)  {
        // probably the record doesn't exist? create it!
        dbModification.addToModificationTable(clazzVar != null, getTableSerialTableName());
        // try again
        if (dbModification.countModification(count) != 2)  {
          throw new PersistenceException(dbModification, "updating modification count failed");
        }
      }
    }
  }


  /**
   * Adds this counter to the modification table.
   */
  public void addToModificationTable()  {
    dbModification.addToModificationTable(clazzVar != null, getTableSerialTableName());
    lastSerial.set(getModificationCount());
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
      if (clazzVar != null) {
        try {
          AbstractDbObject<?> po = AbstractDbObject.newInstance(clazzVar.clazz);
          isTableSerialProvided = po.isTableSerialProvided();
          if (isTableSerialProvided) {
            tableSerialTableName = po.getTableName();
            if (clazzVar instanceof PersistentObjectClassVariables) {
              // check if the topmost superclass provides its own table: take that
              PersistentObjectClassVariables<?,?> cv = (PersistentObjectClassVariables) clazzVar;
              while (cv != null) {
                if (cv.tableName != null) {
                  tableSerialTableName = cv.tableName;
                }
                cv = cv.superClassVariables;
              }
            }
          }
        }
        catch (Exception ex) {
          throw new IllegalStateException("can't evaluate isTableSerialProvided() for " + clazzVar, ex);
        }
      }
      else  {
        isTableSerialProvided = false;
      }
    }
    return tableSerialTableName;
  }

}

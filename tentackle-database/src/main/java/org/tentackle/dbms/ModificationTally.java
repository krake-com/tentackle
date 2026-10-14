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
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionUtilities;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Counter to track modifications for a class/table.<br>
 * The ModificationTracker maintains a list of ModificationTally-objects, one for each tracked name.
 * <p>
 * ModificationTallies are only used in local sessions, never remote.
 *
 * @author harald
 */
public class ModificationTally {

  private static final Logger LOGGER = Logger.get(ModificationTally.class);

  /**
   * Transaction property to rollback pending counts.<br>
   * Set to {@link Boolean#TRUE} when the session performed a countPending within a transaction, and
   * that pending count must be adjusted when the transaction is rolled back.
   */
  public static final Object SESSION_COUNT_PENDING = ModificationTally.class;


  /**
   * Simple counter for modifications within a transaction of a session.<br>
   * Allows correction of the pending count and deletions when a transaction is rolled back.
   */
  private class SessionCount {

    private long txNumber;                  // latest transaction
    private Set<Long> tableSerials;         // tableserials for inserts and updates and deletions

    private SessionCount(long txNumber) {
      this.txNumber = txNumber;
    }

    private void alignTxNumber(long txNumber) {
      if (this.txNumber != txNumber) {
        // another transaction: the last one was committed, obviously
        if (tableSerials != null) {
          tableSerials.clear();
        }
        this.txNumber = txNumber;
      }
    }

    private void addModification(long txNumber, long tableSerial) {
      alignTxNumber(txNumber);
      if (tableSerials == null) {
        tableSerials = new HashSet<>();
      }
      tableSerials.add(tableSerial);
    }

    private int getModificationCount(long txNumber) {
      alignTxNumber(txNumber);
      return tableSerials != null ? tableSerials.size() : 0;
    }

    private void rollback() {
      if (tableSerials != null && isTableSerialProvided()) {
        // overwrite deletion gaps, if any
        tableSerials.forEach(tableSerial -> TableSerialHistory.getInstance().memorize(trackedName, 0, tableSerial));
      }
    }
  }

  private final DbModificationTracker tracker;            // the mod tracker
  private final String trackedName;                       // the symbolic tracked name
  private final DbModification dbModification;            // the persisted modification object
  private final Map<Session, SessionCount> sessionsInTx;  // sessions running a transaction during countPending()
  private final Object flushLock;                         // serializes the use of the tracker's session, null if thread-local
  private DbObjectClassVariables<?> clazzVar;             // the class variables if tablename belongs to a PDO, null for a generic name
  private String tableSerialTableName;                    // the table name, if the tracked name is a table with a tableserial column
  private long lastSerial;                                // last (known) serial for the table
  private long pendingCount;                              // number of pending counts
  private int flushesRunning;                             // number of counts being flushed to the database
  private boolean retired;                                // true if replaced by another tally, no more counts accepted

  /**
   * Creates a modification counter for a given tracked name.
   *
   * @param tracker the modification tracker
   * @param trackedName the tracked name
   */
  public ModificationTally(DbModificationTracker tracker, String trackedName) {
    this.tracker = tracker;
    this.trackedName = trackedName;
    sessionsInTx = new WeakHashMap<>();     // keys are weak! Db does not override equals!
    // In regular mode the tally will only be used by the modification tracker's thread and its session.
    // In local client mode, however, the user's thread directly performs the counting. Therefore,
    // the session must be thread-local in this case.
    boolean localClientMode = tracker.isLocalClientMode();
    dbModification = new DbModification(localClientMode ? null : tracker.getSession(), trackedName);
    // the tracker's session must not be used by two threads at a time (see performPendingCount),
    // neither by the flushes of different tallies nor by the tracker's configuration tasks
    flushLock = localClientMode ? null : tracker.getSessionLock();
    dbModification.refresh();               // this will fail if the session is remote!
    clazzVar = DbObjectClassVariables.getVariables(trackedName);
    if (clazzVar == null) {
      // try to load the class by its name (maybe not referenced so far)
      String className = SessionUtilities.getInstance().getClassName(trackedName);
      if (className != null) {
        try {
          // this will also register the class variables, if any
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

    if (clazzVar != null) {
      tableSerialTableName = DbUtilities.getInstance().determineTableSerialTableName(clazzVar);
    }
  }

  /**
   * Gets the tracked name.
   *
   * @return the symbolic name of the tracked entity
   */
  public String getTrackedName() {
    return trackedName;
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
   * Adds a pending modification count.
   *
   * @param session the session persisting the modification, null if thread-local session
   * @return the serial assigned to the modification, unique for this counter,
   *         0 if retired and the modification must be counted by its successor
   */
  public synchronized long countPending(Session session) {
    if (retired) {
      return 0;
    }
    // this method is only invoked for local sessions, never remote
    long serial = lastSerial + ++pendingCount;
    if (!tracker.isLocalClientMode()) {
      session = getEffectiveSession(session);
      if (session.isTxRunning()) {
        long txNumber = session.getTxNumber();
        sessionsInTx.computeIfAbsent(session, _ -> new SessionCount(txNumber))
                    .addModification(txNumber, serial);
        ((Db) session).setTransactionProperty(SESSION_COUNT_PENDING, Boolean.TRUE);
      }
      if (isTableSerialProvided()) {
        // add to possible gaps
        TableSerialHistory.getInstance().memorize(trackedName, 0, serial);
      }
    }
    return serial;
  }

  /**
   * Retires this tally.<br>
   * Used by the tracker when replacing its tallies, while other threads may still hold a reference to this one.
   * From now on, no more counts are accepted, but the pending counts are still performed.
   */
  public synchronized void retire() {
    retired = true;
  }

  /**
   * Returns whether this tally is retired and has no pending counts left.
   *
   * @return true if the tally can be dropped
   */
  public synchronized boolean isRetiredAndDone() {
    return retired && pendingCount == 0 && flushesRunning == 0;   // a failed flush gives its counts back
  }

  /**
   * Adds a deletion.<br>
   * A deletion is represented by an {@link IdSerialTuple} while the serial
   * is the tableserial indicating the modification count of the whole table.<br>
   * If the table provides a tableserial column, deletions are memorized in the {@link TableSerialHistory},
   * since they cannot be derived from the table itself.
   *
   * @param session the session associated with the deletion; it determines the transaction context
   * @param tuple the ID and negative(!) tableserial that represents the deletion to be added
   */
  public synchronized void addDeletion(Session session, IdSerialTuple tuple) {
    if (tuple.getSerial() >= 0 || tuple.getId() <= 0) {
      throw new PersistenceException(session, "invalid id/tableserial for deletion: " + tuple);
    }
    if (!tracker.isLocalClientMode()) {
      session = getEffectiveSession(session);
      if (session.isTxRunning()) {
        ((Db) session).setTransactionProperty(SESSION_COUNT_PENDING, Boolean.TRUE);
      }
      if (isTableSerialProvided()) {
        TableSerialHistory.getInstance().memorize(trackedName, tuple);
      }
    }
  }

  /**
   * Rolls back the pending count and deletions for a given session.<br>
   * Avoids invalidation of the associated caches when a transaction
   * is rolled back that caused all the modifications.
   *
   * @param session the session
   * @param txNumber the transaction number
   */
  public synchronized void rollbackPending(Session session, long txNumber) {
    if (!tracker.isLocalClientMode()) {
      SessionCount sessionCount = sessionsInTx.remove(getEffectiveSession(session));
      if (sessionCount != null) {
        int modificationCount = sessionCount.getModificationCount(txNumber);  // clears also deletions and tableserials if txNumber changed
        if (modificationCount == pendingCount) {
          // All modifications were made by the current transaction of this session, which is being rolled back now.
          // We can safely forget everything, since we are sure that no other transaction of this or any other session
          // consumed a tableserial.
          pendingCount = 0;
        }
        else if (modificationCount > 0) {
          // Other transactions already consumed tableserials, and we cannot know whether our numbers are ahead
          // or above the others, or even mixed -> turn the consumed tableserials of this transaction into gaps.
          sessionCount.rollback();
        }
      }
      // sessionCount -> GC
    }
  }

  /**
   * Gets the pending count.
   *
   * @return the pending count, 0 for none
   */
  public synchronized long getPendingCount() {
    return pendingCount;
  }

  /**
   * Sets the last serial.<br>
   * The serial never decreases: in local client mode, {@link #performPendingCount()} advances it before
   * the flush is committed, so the committed serial read by the tracker meanwhile may still be lower.
   * Setting it back would hand out the same table serial twice.
   *
   * @param lastSerial the serial
   */
  public synchronized void setLastSerial(long lastSerial) {
    if (lastSerial > this.lastSerial) {
      this.lastSerial = lastSerial;
    }
  }

  /**
   * Gets the latest serial updated by this counter.
   *
   * @return the latest serial from the last update plus pending count
   */
  public synchronized long getLatestSerial() {
    return lastSerial + pendingCount;
  }

  /**
   * Performs the physical pending count if pending count &gt; 0.<br>
   * This is only done if no transactions related to the data monitored by this ModificationTally
   * are currently running. This also implies that all modifications have been committed to the database.
   * <p>
   * The database update is performed without holding the monitor of this tally.
   * In local client mode, the update runs within the transaction of the counting session and locks
   * the modification rows until the end of that transaction. A thread blocked by such a lock while holding
   * the monitor would deadlock with the lock owner counting its next modification.
   * The counts taken over are moved to the last serial, so that the serials assigned in the meantime
   * by {@link #countPending(Session)} continue seamlessly. If the update fails, they are given back.
   */
  public void performPendingCount() {
    long count;
    synchronized (this) {
      if (pendingCount <= 0) {
        return;
      }
      // remove session counters for committed transactions,
      // even if the session is running another transaction meanwhile, which did not count (yet)
      sessionsInTx.entrySet().removeIf(entry -> {
        Session session = entry.getKey();
        return !session.isTxRunning() || session.getTxNumber() != entry.getValue().txNumber;
      });
      if (!sessionsInTx.isEmpty()) {
        return;   // transactions still running: flush later
      }
      count = pendingCount;
      lastSerial += count;
      pendingCount = 0;
      flushesRunning++;
    }

    boolean flushed = false;
    try {
      // No more transactions running: flush to the database.
      // This ensures that triggered listeners will see the committed data!
      if (flushLock == null) {
        flush(count);   // thread-local session: the modification rows serialize the transactions
      }
      else {
        synchronized (flushLock) {
          flush(count);   // the tracker's session runs in autocommit mode: no row locks held after the update
        }
      }
      flushed = true;
    }
    finally {
      synchronized (this) {
        flushesRunning--;
        if (!flushed) {
          // retried by the next invocation
          lastSerial -= count;
          pendingCount += count;
        }
      }
    }
  }

  /**
   * Updates the modification count in the database.<br>
   * Must not be invoked with the monitor of this tally held.
   *
   * @param count the number of modifications to add
   */
  private void flush(long count) {
    LOGGER.fine("increment serial for {0} by {1}", dbModification, count);
    if (dbModification.countModification(count) != 2)  {
      // probably the record doesn't exist? create it!
      dbModification.addToModificationTable(clazzVar != null, tableSerialTableName);
      // try again
      if (dbModification.countModification(count) != 2)  {
        throw new PersistenceException(dbModification, "updating modification count failed");
      }
    }
  }

  /**
   * Adds this counter to the modification table.
   */
  public void addToModificationTable()  {
    dbModification.addToModificationTable(clazzVar != null, tableSerialTableName);
    setLastSerial(getModificationCount());
  }

  /**
   * Gets the current modification count by tablename.
   *
   * @return the modification count
   */
  public long getModificationCount() {
    return dbModification.refresh();
  }


  /**
   * Maps to the effective session if the session is null (thread-local).
   *
   * @param session the session
   * @return the real session, always not null
   */
  private Session getEffectiveSession(Session session) {
    if (session == null) {
      session = Session.getCurrentSession();
      if (session == null) {
        throw new PersistenceException("no thread-local session to count modification for " + dbModification);
      }
    }
    if (session.isRemote()) {
      throw new PersistenceException(session, "modification counting not allowed for remote sessions");
    }
    return session;
  }

  private boolean isTableSerialProvided() {
    return tableSerialTableName != null;
  }

}

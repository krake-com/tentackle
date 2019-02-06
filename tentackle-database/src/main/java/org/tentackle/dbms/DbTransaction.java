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

import org.tentackle.session.PersistenceException;
import org.tentackle.session.SavepointHandle;

import java.sql.Savepoint;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holder for transaction local data.
 *
 * @author harald
 */
public class DbTransaction {

  // the jvm-wide transaction counter
  private static final AtomicLong TX_COUNT = new AtomicLong();

  private static final String DEFAULT_TXNAME = "<unnamed>";

  private Db db;                        // the db connection
  private final long creationTime;      // epochal time in ms when started/created
  private final long txNumber;          // the unique transaction number
  private final String txName;          // optional transaction name
  private final long txVoucher;         // the random voucher for commit or rollback
  private int handleCount;              // the last handle number
  private int txLevel;                  // number of nested begin() (only for local Db), Integer.MIN_VALUE if invalid
  private int updateCount;              // number of object modified by executeUpdate since the last begin()
  private AbstractDbObject<?> txObject; // optional top-level object initiating the transaction

  private Map<DbTransactionHandle,PersistenceVisitor> visitors;            // the visitors
  private Map<DbTransactionHandle,CommitTxRunnable> commitTxRunnables;     // the commit runnables
  private Map<DbTransactionHandle,RollbackTxRunnable> rollbackTxRunnables; // the commit runnables
  private Map<SavepointHandle,Savepoint> savepoints;                       // the savepoints


  /**
   * Creates a transaction.
   *
   * @param db the db
   * @param txName the transaction name
   * @param fromRemote true if initiated from remote client
   */
  public DbTransaction(Db db, String txName, boolean fromRemote) {

    this.db = db;
    this.txName = txName == null ? DEFAULT_TXNAME : txName;

    txNumber = TX_COUNT.incrementAndGet();
    txLevel = 1;      // nesting level 1 = first begin
    creationTime = System.currentTimeMillis();

    // create new commit/rollback voucher
    long magic;
    do {
      magic = UUID.randomUUID().getLeastSignificantBits();
    }
    while (magic == 0);

    // remote are negative, local positive
    txVoucher = (fromRemote && magic > 0 || !fromRemote && magic < 0) ? -magic : magic;
  }



  /**
   * Sets the db.
   * <p>
   * Used after transferring from server to client.
   *
   * @param db the new db
   */
  public void setSession(Db db) {
    this.db = db;
  }


  /**
   * Gets the db.
   *
   * @return the db
   */
  public Db getSession() {
    return db;
  }


  /**
   * Gets the epochal creation time in ms.
   *
   * @return the milliseconds since 1970-01-01
   */
  public long getCreationTime() {
    return creationTime;
  }


  /**
   * Gets the number of objects modified since the last begin().
   * The method is provided to check whether objects have been
   * modified at all.
   *
   * @return the number of modified objects
   */
  public int getUpdateCount() {
    return updateCount;
  }


  /**
   * Add to updateCount.
   *
   * @param count the number of updates to add
   */
  public void addToUpdateCount(int count) {
    updateCount += count;
  }


  /**
   * Gets the transaction name.
   *
   * @return the name
   */
  public String getTxName() {
    return txName;
  }


  /**
   * Gets the transaction number.
   *
   * @return the tx number
   */
  public long getTxNumber() {
    return txNumber;
  }


  /**
   * Gets the nesting level.
   *
   * @return the tx level
   */
  public int getTxLevel() {
    return txLevel;
  }


  /**
   * Marks the txLevel invalid.
   * <p>
   * Will suppress any checks and warnings.
   */
  public void invalidateTxLevel() {
    txLevel = Integer.MIN_VALUE;
  }


  /**
   * Returns whether the txLevel is valid.
   *
   * @return true if valid
   */
  public boolean isTxLevelValid() {
    return txLevel != Integer.MIN_VALUE;
  }


  /**
   * Increments the transaction level.
   *
   * @return the new tx level
   */
  public int incrementTxLevel() {
    if (isTxLevelValid()) {
      txLevel++;
    }
    return txLevel;
  }


  /**
   * Decrements the transaction level.
   *
   * @return the new tx level
   */
  public int decrementTxLevel() {
    if (isTxLevelValid()) {
      --txLevel;
      if (txLevel < 1) {
        throw new PersistenceException(db, "unbalanced tx level");
      }
    }
    return txLevel;
  }


  /**
   * Gets the transaction voucher.
   *
   * @return the voucher
   */
  public long getTxVoucher() {
    return txVoucher;
  }


  /**
   * Sets the optional transaction object.<br>
   * By default, whenever a transaction is initiated by a persistence operation of
   * a PDO, that object becomes the "parent" of the transaction.<br>
   * The {@code txObject} is mainly used for logging and enhanced auditing (partial history) during transactions.
   * The {@code txObject} is cleared at the end of the transaction.
   *
   * @param txObject the transaction object, null to clear
   */
  public void setTxObject(AbstractDbObject<?> txObject) {
    this.txObject = txObject;
  }


  /**
   * Gets the optional transaction object.
   *
   * @return the transaction object, null if none
   */
  public AbstractDbObject<?> getTxObject() {
    return txObject;
  }


  /**
   * Adds a savepoint to the transaction.
   *
   * @param handle the handle
   * @param savepoint the savepoint
   */
  public void addSavepoint(SavepointHandle handle, Savepoint savepoint) {
    if (savepoints == null) {
      savepoints = new HashMap<>();
    }
    savepoints.put(handle, savepoint);
  }

  /**
   * Gets a savepoint by handle.
   *
   * @param handle the handle
   * @return savepoint the savepoint
   */
  public Savepoint getSavepoint(SavepointHandle handle) {
    return savepoints == null ? null : savepoints.get(handle);
  }

  /**
   * Removes a savepoint.
   *
   * @param handle the handle
   * @return the removed savepoint, null if no such handle
   */
  public Savepoint removeSavepoint(SavepointHandle handle) {
    return savepoints == null ? null : savepoints.remove(handle);
  }

  /**
   * Returns whether this transaction has active savepoints.
   *
   * @return true if savepoints are active
   */
  public boolean isWithSavepoints() {
    return savepoints != null && !savepoints.isEmpty();
  }


  @Override
  public String toString() {
    return "transaction-" + txNumber + (isTxLevelValid() ? ("." + txLevel) : "") + "(" + txName + ") on " + db;
  }


  /**
   * Registers a {@link PersistenceVisitor} to be invoked just before
   * performing a persistence operation.<br>
   *
   * @param visitor the visitor to register
   * @return the handle for the visitor
   */
  public DbTransactionHandle registerPersistenceVisitor(PersistenceVisitor visitor) {
    if (visitors == null)  {
      visitors = new HashMap<>();
    }
    DbTransactionHandle handle = new DbTransactionHandle(txNumber, ++handleCount);
    visitors.put(handle, visitor);
    return handle;
  }


  /**
   * Unegisters a {@link PersistenceVisitor}.
   *
   * @param handle the visitor's handle to unregister
   * @return the removed visitor, null if not registered
   */
  public PersistenceVisitor unregisterPersistenceVisitor(DbTransactionHandle handle) {
    return visitors == null ? null : visitors.remove(handle);
  }


  /**
   * Gets the currently registered persistence visitors.
   *
   * @return the visitors, null or empty if none
   */
  public Collection<PersistenceVisitor> getPersistenceVisitors() {
    return visitors.values();
  }


  /**
   * Registers a {@link CommitTxRunnable} to be invoked just before
   * committing a transaction.
   *
   * @param commitRunnable the runnable to register
   * @return the handle for the runnable
   */
  public DbTransactionHandle registerCommitTxRunnable(CommitTxRunnable commitRunnable) {
    if (commitTxRunnables == null)  {
      commitTxRunnables = new HashMap<>();
    }
    DbTransactionHandle handle = new DbTransactionHandle(txNumber, ++handleCount);
    commitTxRunnables.put(handle, commitRunnable);
    return handle;
  }

  /**
   * Unregisters a {@link CommitTxRunnable}.
   *
   * @param handle the runnable's handle to unregister
   * @return true if removed, else not registered
   */
  public CommitTxRunnable unregisterCommitTxRunnable(DbTransactionHandle handle) {
    return commitTxRunnables == null ? null : commitTxRunnables.remove(handle);
  }


  /**
   * Gets the currently registered commit runnables.
   *
   * @return the runnables, null or empty if none
   */
  public Collection<CommitTxRunnable> getCommitTxRunnables() {
    return commitTxRunnables.values();
  }


  /**
   * Registers a {@link RollbackTxRunnable} to be invoked just before
   * rolling back a transaction.
   *
   * @param rollbackRunnable the runnable to register
   * @return the handle for the runnable
   */
  public DbTransactionHandle registerRollbackTxRunnable(RollbackTxRunnable rollbackRunnable) {
    if (rollbackTxRunnables == null)  {
      rollbackTxRunnables = new HashMap<>();
    }
    DbTransactionHandle handle = new DbTransactionHandle(txNumber, ++handleCount);
    rollbackTxRunnables.put(handle, rollbackRunnable);
    return handle;
  }

  /**
   * Unregisters a {@link RollbackTxRunnable}.
   *
   * @param handle the runnable's handle to unregister
   * @return true if removed, else not registered
   */
  public RollbackTxRunnable unregisterRollbackTxRunnable(DbTransactionHandle handle) {
    return rollbackTxRunnables == null ? null : rollbackTxRunnables.remove(handle);
  }


  /**
   * Gets the currently registered rollback runnables.
   *
   * @return the runnables, null or empty if none
   */
  public Collection<RollbackTxRunnable> getRollbackTxRunnables() {
    return rollbackTxRunnables.values();
  }


  /**
   * Executes all commit runnables and removes them.
   */
  public void invokeCommitTxRunnables() {
    if (commitTxRunnables != null) {
      /**
       * execute pending runnables.
       * Notice: the runnables should throw DbRuntimeException on failure!
       */
      for (CommitTxRunnable r: commitTxRunnables.values())  {
        r.commit(db);
      }
      commitTxRunnables = null;
    }
  }


  /**
   * Executes all rollback runnables and removes them.
   */
  public void invokeRollbackTxRunnables() {
    if (rollbackTxRunnables != null) {
      /**
       * execute pending runnables.
       * Notice: the runnables should throw DbRuntimeException on failure!
       */
      for (RollbackTxRunnable r: rollbackTxRunnables.values())  {
        r.rollback(db);
      }
      rollbackTxRunnables = null;
    }
  }



  /**
   * Checks whether a persistence operation is allowed.<br>
   * This is determined by consulting the {@link PersistenceVisitor}s.<br>
   *
   * @param object the persistence object
   * @param modType the modification type
   * @return true if allowed
   * @see #registerPersistenceVisitor(org.tentackle.dbms.PersistenceVisitor) 
   */
  public boolean isPersistenceOperationAllowed(AbstractDbObject<?> object, char modType) {
    if (visitors != null) {
      for (PersistenceVisitor visitor: visitors.values()) {
        object.acceptPersistenceVisitor(visitor, modType);
        if (!visitor.isPersistenceOperationAllowed(object, modType)) {
          return false;
        }
      }
    }
    return true;
  }

}

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

import org.tentackle.session.PersistenceException;
import org.tentackle.session.SavepointHandle;

import java.sql.Savepoint;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holder for transaction local data.
 *
 * @author harald
 */
public class DbTransaction {

  /**
   * Maximum recursion depth of tx runnables.<br>
   * To detect recursion loops when tx runnables create other runnables.
   */
  public static int maxTxRunnableDepth = 100;

  // the jvm-wide transaction counter
  private static final AtomicLong TX_COUNT = new AtomicLong();

  private static final String DEFAULT_TXNAME = "<unnamed>";

  private final Db db;                  // the session
  private final DbBatch batch;          // statement batch, null if no batching (default)
  private final long creationTime;      // epochal time in ms when started/created
  private final long txNumber;          // the unique transaction number
  private final String txName;          // optional transaction name
  private final long txVoucher;         // the random voucher for commit or rollback
  private int handleCount;              // the last handle number
  private int txLevel;                  // number of nested begin() (only for local Db), Integer.MIN_VALUE if invalid
  private Deque<String> nestedTxNames;  // transaction names for nested transactions

  private Map<DbTransactionHandle,PersistenceVisitor> visitors;            // the visitors
  private Map<DbTransactionHandle,CommitTxRunnable> commitTxRunnables;     // the commit runnables
  private Map<DbTransactionHandle,RollbackTxRunnable> rollbackTxRunnables; // the rollback runnables
  private Map<SavepointHandle,Savepoint> savepoints;                       // the savepoints


  /**
   * Creates a transaction.
   *
   * @param db the session
   * @param txName the transaction name, null if &lt;unnamed&gt;
   * @param fromRemote true if initiated from remote client
   */
  public DbTransaction(Db db, String txName, boolean fromRemote) {

    this.db = db;
    this.txName = txName == null ? DEFAULT_TXNAME : txName;

    batch = createBatch();    // null if no batching
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
   * Gets the session.
   *
   * @return the session
   */
  public Db getSession() {
    return db;
  }


  /**
   * Gets the statements batch.
   *
   * @return the batch, null if no auto batching
   */
  public DbBatch getBatch() {
    return batch;
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
    nestedTxNames = null;
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
   * @param txName the optional transaction name
   * @return the new tx level
   */
  public int incrementTxLevel(String txName) {
    if (isTxLevelValid()) {
      if (txName == null) {
        txName = DEFAULT_TXNAME;
      }
      if (nestedTxNames == null) {
        nestedTxNames = new ArrayDeque<>();
      }
      nestedTxNames.push(txName);
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
      if (txLevel <= 1) {
        throw new PersistenceException(db, "unbalanced tx level");
      }
      --txLevel;
      if (nestedTxNames != null) {
        nestedTxNames.pop();
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
   * Asserts the validity of the given voucher.<br>
   * If the voucher does not match, a {@link PersistenceException} is thrown.
   *
   * @param txVoucher the voucher to test
   */
  public void assertValidTxVoucher(long txVoucher) {
    if (this.txVoucher != txVoucher) {
      throw new PersistenceException(db, "invalid voucher " + txVoucher);
    }
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
    StringBuilder buf = new StringBuilder();
    buf.append("TX").append(txNumber);
    if (isTxLevelValid()) {
      buf.append('.').append(txLevel);
    }
    buf.append('-').append(txName);
    if (nestedTxNames != null) {
      for (Iterator<String> iterator = nestedTxNames.descendingIterator(); iterator.hasNext(); ) {
        buf.append('>').append(iterator.next());
      }
    }
    return buf.toString();
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
   * Unregisters a {@link PersistenceVisitor}.
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
   * @return the runnable if removed, null if not registered
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
   * @return the runnable that was removed, null if not registered
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
   * Performs the commit.<br>
   * Finishes any pending batched statements.<br>
   * Executes all commit runnables and removes them.<br>
   */
  public void commit() {
    if (batch != null) {
      batch.execute(true);
    }
    if (commitTxRunnables != null) {
      /*
       * execute pending commit runnables.
       * Notice: the runnables should throw PersistenceException on failure!
       * Runnables may create other runnables up to maxTxRunnableDepth recursion depth.
       */
      int depth = 0;
      while (!commitTxRunnables.isEmpty()) {
        for (Map.Entry<DbTransactionHandle, CommitTxRunnable> entry : Map.copyOf(commitTxRunnables).entrySet()) {
          entry.getValue().commit(db);
          commitTxRunnables.remove(entry.getKey());
        }
        if (++depth > maxTxRunnableDepth) {
          throw new PersistenceException(getSession(), "commit runnables nesting level too deep");
        }
      }
      commitTxRunnables = null;
    }
  }


  /**
   * Performs a rollback.<br>
   * Executes all rollback runnables and removes them.
   */
  public void rollback() {
    if (rollbackTxRunnables != null) {
      /*
       * execute pending rollback runnables.
       * Notice: the runnables should throw PersistenceException on failure!
       * Runnables may create other runnables up to maxTxRunnableDepth recursion depth.
       */
      int depth = 0;
      while (!rollbackTxRunnables.isEmpty()) {
        for (Map.Entry<DbTransactionHandle, RollbackTxRunnable> entry : Map.copyOf(rollbackTxRunnables).entrySet()) {
          entry.getValue().rollback(db);
          rollbackTxRunnables.remove(entry.getKey());
        }
        if (++depth > maxTxRunnableDepth) {
          throw new PersistenceException(getSession(), "rollback runnables nesting level too deep");
        }
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
  public boolean isPersistenceOperationAllowed(AbstractDbObject<?> object, ModificationType modType) {
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


  /**
   * Creates the statement batch for this transaction.
   *
   * @return the batch, null if no batching
   */
  protected DbBatch createBatch() {
    int batchSize = db.getBatchSize();
    return batchSize > 1 ? new DbBatch(this, batchSize) : null;
  }

}

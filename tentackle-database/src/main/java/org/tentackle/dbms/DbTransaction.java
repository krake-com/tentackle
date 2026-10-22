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

import org.tentackle.misc.TimeKeeper;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SavepointHandle;

import java.io.Serial;
import java.sql.Savepoint;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local transaction status.
 *
 * @author harald
 */
public class DbTransaction extends AbstractTransaction {

  // will not be serialized but makes compiler happy
  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * The completion state.
   */
  private enum Completion {
    /** neither completed nor timed out. */
    RUNNING,
    /** completed by the session (committed or rolled back). */
    COMPLETED,
    /** timed out by the transaction supervisor. */
    TIMED_OUT
  }

  private transient final Db session;             // the session
  private transient final DbBatch batch;          // statement batch, null if no batching (default)
  private transient final long creationTime;      // epochal time in ms when started/created
  private transient final TimeKeeper timeKeeper;  // to measure the exact duration
  private transient int handleCount;              // the last handle number
  private transient int savepointRollbackCount;   // number of rollbacks to savepoints
  private transient final AtomicBoolean alive;    // the alive-flag to detect idle transactions
  private transient boolean global;               // true if part of a global transaction
  private transient boolean globalOwner;          // true if this transaction started the global transaction
  private transient final AtomicReference<Completion> completion;   // completed by the session or timed out by the supervisor

  private transient Map<DbTransactionHandle, PersistenceVisitor> visitors;  // the visitors
  private transient Map<SavepointHandle, Savepoint> savepoints;             // the savepoints


  /**
   * Creates a transaction.
   *
   * @param session the session
   * @param txVoucher the transaction voucher
   * @param txName the transaction name, null for &lt;unnamed&gt;
   * @param txNumber the transaction number
   */
  public DbTransaction(Db session, long txVoucher, String txName, long txNumber) {
    super(txVoucher, txName, txNumber, 1);   // nesting level 1 = first begin
    this.session = session;
    super.setSession(session);
    batch = createBatch();    // null if no batching
    creationTime = System.currentTimeMillis();
    timeKeeper = new TimeKeeper();
    completion = new AtomicReference<>(Completion.RUNNING);
    alive = new AtomicBoolean();
  }


  /**
   * Gets the session.
   *
   * @return the session
   */
  public Db getSession() {
    return session;
  }

  @Override
  public void setSession(Db session) {
    if (session != this.session) {
      throw new PersistenceException(this.session, "session of local transaction is final");
    }
  }

  /**
   * Gets the statements-batch.
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
   * Gets the timekeeper for measurement of the duration.
   *
   * @return the timekeeper
   */
  public TimeKeeper getTimeKeeper() {
    return timeKeeper;
  }

  /**
   * Asserts the validity of the given voucher.<br>
   * If the voucher does not match, a {@link PersistenceException} is thrown.
   *
   * @param txVoucher the voucher to test
   */
  public void assertValidTxVoucher(long txVoucher) {
    if (getTxVoucher() != txVoucher) {
      // don't expose the expected voucher!
      throw new PersistenceException(session, "invalid voucher " + txVoucher);
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
   * @return the savepoint, null if no such handle
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

  /**
   * Counts a rollback to a savepoint.
   */
  public void countSavepointRollback() {
    savepointRollbackCount++;
  }

  /**
   * Gets the number of rollbacks to savepoints.<br>
   * Allows detecting whether a savepoint has been rolled back since a given point in time.
   *
   * @return the number of savepoint rollbacks within this transaction
   */
  public int getSavepointRollbackCount() {
    return savepointRollbackCount;
  }

  /**
   * Registers a {@link PersistenceVisitor} to be invoked just before
   * performing a persistence operation.
   *
   * @param visitor the visitor to register
   * @return the handle for the visitor
   */
  public DbTransactionHandle registerPersistenceVisitor(PersistenceVisitor visitor) {
    if (visitors == null)  {
      visitors = new HashMap<>();
    }
    DbTransactionHandle handle = new DbTransactionHandle(getTxNumber(), ++handleCount);
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
    return visitors == null ? null : visitors.values();
  }

  /**
   * Performs the commit.<br>
   * Executes any pending batched statements and finishes the transaction.
   * <p>
   * Notice that the physical commit is performed by the session.
   *
   * @see DbTransactionFactory#finish(DbTransaction, boolean)
   */
  public void commit() {
    executeBatch();
    if (!complete()) {
      throw new PersistenceException(session, this + " timed out and has been rolled back by the transaction supervisor");
    }
    finishCommit();
  }

  /**
   * Marks this transaction as completed by its session.<br>
   * From now on, the transaction supervisor will not time it out anymore.
   *
   * @return true if completed, false if already timed out by the transaction supervisor
   */
  public boolean complete() {
    return completion.compareAndSet(Completion.RUNNING, Completion.COMPLETED) || completion.get() == Completion.COMPLETED;
  }

  /**
   * Marks this transaction as timed out by the transaction supervisor.<br>
   * From now on, the transaction cannot be committed anymore.
   *
   * @return true if timed out, false if already completed by its session
   */
  public boolean timeOut() {
    return completion.compareAndSet(Completion.RUNNING, Completion.TIMED_OUT);
  }

  /**
   * Returns whether this transaction has been timed out by the transaction supervisor.
   *
   * @return true if timed out
   */
  public boolean isTimedOut() {
    return completion.get() == Completion.TIMED_OUT;
  }

  /**
   * Executes any pending batched statements.
   */
  public void executeBatch() {
    if (batch != null) {
      batch.execute(true);
    }
  }

  /**
   * Finishes a committed transaction.<br>
   * Same as {@link #commit()} without executing the batched statements.
   * Used for global transactions, which flush the batch before the two-phase commit.
   */
  public void finishCommit() {
    DbTransactionFactory.getInstance().finish(this, false);
  }

  /**
   * Finishes a rolled back transaction.<br>
   * Pending batched statements are discarded.
   * <p>
   * Notice that the physical rollback is performed by the session.
   *
   * @see DbTransactionFactory#finish(DbTransaction, boolean)
   */
  public void rollback() {
    DbTransactionFactory.getInstance().finish(this, true);
  }

  /**
   * Checks whether a persistence operation is allowed.<br>
   * This is determined by consulting the {@link PersistenceVisitor}s.
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
   * Sets the alive-flag.
   *
   * @param alive true if alive, false if reset after polling
   */
  public void setAlive(boolean alive) {
    this.alive.set(alive);
  }

  /**
   * Gets the alive-flag.
   *
   * @return true if alive, false if reset after polling and not marked alive since then
   */
  public boolean isAlive() {
    return alive.get();
  }

  /**
   * Gets and resets the alive-flag atomically.<br>
   * Used by the transaction supervisor, so that the transaction cannot be marked alive
   * in between and the mark gets lost.
   *
   * @return true if the transaction was alive since the last poll
   */
  public boolean pollAlive() {
    return alive.getAndSet(false);
  }


  /**
   * Marks this transaction as part of a global transaction.
   *
   * @param globalOwner true if this transaction started the global transaction, false if joined
   */
  public void setGlobal(boolean globalOwner) {
    this.global = true;
    this.globalOwner = globalOwner;
  }

  /**
   * Returns whether this transaction is part of a global transaction.
   *
   * @return true if global, false if local
   * @see GlobalTransactionSupport
   */
  public boolean isGlobal() {
    return global;
  }

  /**
   * Returns whether this transaction started the global transaction.<br>
   * Only the owner commits or rolls back the global transaction.
   *
   * @return true if owner, false if joined or local
   */
  public boolean isGlobalOwner() {
    return globalOwner;
  }

  @Override
  public String toString() {
    return global ? (globalOwner ? "XA:" : "XA+") + super.toString() : super.toString();
  }


  /**
   * Creates the statement batch for this transaction.
   *
   * @return the batch, null if no batching
   */
  protected DbBatch createBatch() {
    int batchSize = session.getBatchSize();
    return batchSize > 1 ? new DbBatch(this, batchSize) : null;
  }

}

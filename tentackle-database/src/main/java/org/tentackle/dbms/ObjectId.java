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
import org.tentackle.sql.Backend;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.tentackle.common.Constants.CN_ID;

/**
 * An {@link IdSource} to create unique object IDs.
 * <p>
 * Only used if the backend does not support sequences, or if configured explicitly via {@code table:<name>}.<br>
 * The counter is kept in a table with a single row and column {@code id}.
 * <p>
 * The first ID requested within a transaction increments the counter in the database, which locks the
 * row until the end of the transaction. Further IDs of the same transaction are counted in memory only, and
 * the final value is written once at the end of the transaction (via a pre-commit).
 * IDs requested from within a pre-commit are written immediately, since the final update
 * may already have been executed by then.
 * As a consequence, the database serializes the transactions of all sessions allocating IDs from the same table.
 * <p>
 * The counting state belongs to the transaction (it is kept as a transaction property of the session),
 * so a single instance can safely be shared by all sessions.
 *
 * @author harald
 * @see ObjectSequenceId
 */
public class ObjectId extends AbstractIdSource {

  private static final StatementId SELECT_ID = new StatementId();
  private static final StatementId INCREMENT_ID = new StatementId();
  private static final StatementId UPDATE_ID = new StatementId();


  /**
   * The counting state of a transaction.
   */
  private static class TxCounter {
    private long lastId;                  // the last ID returned within the transaction
    private int savepointRollbackCount;   // savepoint rollbacks of the transaction when the row was locked
    private boolean flushRegistered;      // true if the final update is registered as a pre-commit

    private TxCounter(long lastId, int savepointRollbackCount) {
      this.lastId = lastId;
      this.savepointRollbackCount = savepointRollbackCount;
    }

    /**
     * Returns whether a savepoint has been rolled back since the row was locked.<br>
     * The rollback may have undone the increment and released the row lock,
     * so other sessions may have allocated IDs in the meantime.
     */
    private boolean isStale(Db db) {
      return savepointRollbackCount != db.getSavepointRollbackCount();
    }
  }

  /**
   * Sequences mapped by unique name.
   */
  private static final Map<String, ObjectId> SEQUENCES = new ConcurrentHashMap<>();

  /**
   * Gets a sequence id source.<br>
   * ID sources are unique by name.
   *
   * @param name the unique sequence name
   * @return the ID source
   */
  public static ObjectId get(String name) {
    return SEQUENCES.computeIfAbsent(name, ObjectId::new);
  }


  /**
   * Creates an object id.
   *
   * @param name the source and table name
   */
  public ObjectId(String name) {
    super(name);
  }


  @Override
  public boolean isLockFree() {
    // becomes part of transaction and therefore is not lock-free
    return false;
  }

  @Override
  public long nextId(Db db) {

    assertDbNotRemote(db);

    final long txVoucher = db.begin("nextId");   // begin tx if not already done

    try {
      TxCounter counter = (TxCounter) db.getTransactionProperty(this);    // the properties are cleared by each new tx

      if (counter == null) {
        // first ID within this transaction: increment by 1 (this locks the row until the end of the transaction!)
        counter = new TxCounter(incrementCounter(db), db.getSavepointRollbackCount());
        db.setTransactionProperty(this, counter);
      }
      else {
        // same tx: count in memory only
        counter.lastId++;
        if (counter.isStale(db)) {
          synchronizeCounter(db, counter);
        }
        if (db.isPreCommitsRunning()) {
          // invoked from within a pre-commit: the final update may already have been executed
          // (it is registered at a deeper tx level and thus runs before the pre-commits of the outer levels)
          // or cannot be registered anymore -> write the counter now, the row is still locked by this tx
          updateCounter(db, counter.lastId);
        }
        else if (!counter.flushRegistered) {
          // write the final value at the end of the transaction
          TxCounter txCounter = counter;
          db.preCommit(_ -> {
            if (txCounter.isStale(db)) {
              synchronizeCounter(db, txCounter);   // don't set back the counter
            }
            updateCounter(db, txCounter.lastId);
          });
          counter.flushRegistered = true;
        }
      }

      db.commit(txVoucher);
      return counter.lastId;
    }
    catch (PersistenceException px) {
      db.rollback(txVoucher);
      throw px;
    }
    catch (RuntimeException rx) {
      db.rollback(txVoucher);
      throw new PersistenceException(db, rx);
    }
  }


  /**
   * Creates the statement key.<br>
   * The key includes the table name, since id sources on different tables must not share their statements.
   *
   * @param statementId the statement id
   * @return the statement key
   */
  protected StatementKey createStatementKey(StatementId statementId) {
    return new StatementKey(statementId, getClass(), getName());
  }


  /**
   * Increments the counter in the database.<br>
   * This locks the row until the end of the transaction.
   *
   * @param db the session
   * @return the new counter value
   */
  private long incrementCounter(Db db) {
    PreparedStatementWrapper incrementStatement = db.getPreparedStatement(createStatementKey(INCREMENT_ID), false,
      _ -> Backend.SQL_UPDATE + getName() + Backend.SQL_SET + CN_ID + Backend.SQL_EQUAL + CN_ID + "+1"
    );
    assertOneRowAffected(db, incrementStatement.executeUpdate());

    // read back new value
    PreparedStatementWrapper selectStatement = db.getPreparedStatement(createStatementKey(SELECT_ID), false,
      _ -> Backend.SQL_SELECT + CN_ID + Backend.SQL_FROM + getName()
    );
    try (ResultSetWrapper rs = selectStatement.executeQuery()) {
      if (rs.next()) {
        return rs.getLong(1);
      }
      throw new PersistenceException(db, "table " + getName() + " is empty");
    }
  }

  /**
   * Synchronizes the counter of the transaction with the database after a rollback to a savepoint.<br>
   * Locks the row again and continues after the IDs other sessions may have allocated in the meantime.
   * The IDs already returned are kept, since they may have been returned before the savepoint was set.
   * The caller must write the counter to the database.
   *
   * @param db the session
   * @param counter the transaction's counter
   */
  private void synchronizeCounter(Db db, TxCounter counter) {
    counter.lastId = Math.max(counter.lastId, incrementCounter(db));
    counter.savepointRollbackCount = db.getSavepointRollbackCount();
  }

  /**
   * Writes the counter to the database.
   *
   * @param db the session
   * @param lastId the last ID returned within the transaction
   */
  private void updateCounter(Db db, long lastId) {
    PreparedStatementWrapper updateStatement = db.getPreparedStatement(createStatementKey(UPDATE_ID), false,
      _ -> Backend.SQL_UPDATE + getName() + Backend.SQL_SET + CN_ID + Backend.SQL_EQUAL_PAR
    );
    updateStatement.setLong(1, lastId);
    assertOneRowAffected(db, updateStatement.executeUpdate());
  }

}

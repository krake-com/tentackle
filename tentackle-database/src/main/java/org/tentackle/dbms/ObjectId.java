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
 * As a consequence, the transactions of all sessions allocating IDs from the same table are serialized
 * by the database.
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
    private long lastId;              // the last ID returned within the transaction
    private boolean flushRegistered;  // true if the final update is registered as a pre-commit

    private TxCounter(long lastId) {
      this.lastId = lastId;
    }
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
            counter = new TxCounter(rs.getLong(1));
          }
          else {
            throw new PersistenceException(db, "table " + getName() + " is empty");
          }
        }
        db.setTransactionProperty(this, counter);
      }
      else {
        // same tx: count in memory only
        counter.lastId++;
        if (!counter.flushRegistered) {
          // write the final value at the end of the transaction
          TxCounter txCounter = counter;
          db.preCommit(_ -> {
            PreparedStatementWrapper updateStatement = db.getPreparedStatement(createStatementKey(UPDATE_ID), false,
              _ -> Backend.SQL_UPDATE + getName() + Backend.SQL_SET + CN_ID + Backend.SQL_EQUAL_PAR
            );
            updateStatement.setLong(1, txCounter.lastId);
            assertOneRowAffected(db, updateStatement.executeUpdate());
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

}

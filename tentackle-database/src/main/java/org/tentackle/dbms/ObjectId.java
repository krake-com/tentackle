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
 * Only used if the backend does not support sequences.<br>
 * Provides some degree of optimization to update the table only once at the end of the transaction.
 *
 * @author harald
 * @see ObjectSequenceId
 */
public class ObjectId extends AbstractIdSource {

  private static final StatementId SELECT_ID = new StatementId();
  private static final StatementId INCREMENT_ID = new StatementId();
  private static final StatementId UPDATE_ID = new StatementId();


  private long lastId;                // last ID
  private long lastTxCount;           // for optimization within larger transactions
  private boolean registerTxPending;  // true if we need to register the final update as a pre-commit

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
  public synchronized long nextId(Db db) {

    assertDbNotRemote(db);

    final long txVoucher = db.begin("nextId");   // begin tx if not already done

    try {

      if (db.getTxNumber() > lastTxCount) {

        // new transaction (either from begin() above or previously from application)
        lastTxCount = db.getTxNumber();   // remember current tx
        registerTxPending = true;         // next invocation will register final update

        // increment by 1 (this starts isolation within the transaction!)
        PreparedStatementWrapper incrementStatement = db.getPreparedStatement(new StatementKey(INCREMENT_ID, getClass()), false,
          _ -> Backend.SQL_UPDATE + getName() + Backend.SQL_SET + CN_ID + Backend.SQL_EQUAL + CN_ID + "+1"
        );
        PreparedStatementWrapper selectStatement = db.getPreparedStatement(new StatementKey(SELECT_ID, getClass()), false,
          _ -> Backend.SQL_SELECT + CN_ID + Backend.SQL_FROM + getName()
        );

        /*
         * Note: this will get the write-lock on object-id. Further nextId() will only increment the id without any
         * database operation until the transaction gets closed and the final update is done.
         */
        assertOneRowAffected(db, incrementStatement.executeUpdate());

        // read back new value
        try (ResultSetWrapper rs = selectStatement.executeQuery()) {
          if (rs.next()) {
            lastId = rs.getLong(1);
            db.commit(txVoucher);
          }
          else {
            throw new PersistenceException(db, "table " + getName() + " is empty");
          }
        }
      }
      else {
        // same tx: optimize and count only
        // begin() didn't return a new voucher
        if (txVoucher != 0) {
          throw new PersistenceException(db, "transaction counter corrupted");
        }
        lastId++;
        if (registerTxPending) {
          // register the final update for the end of transaction
          db.preCommit(_ -> {
            PreparedStatementWrapper updateStatement = db.getPreparedStatement(new StatementKey(UPDATE_ID, getClass()), false,
                                                                               _ -> Backend.SQL_UPDATE + getName() + Backend.SQL_SET + CN_ID + Backend.SQL_EQUAL_PAR
            );
            updateStatement.setLong(1, lastId);   // set the current value of ID
            assertOneRowAffected(db, updateStatement.executeUpdate());
          });
          registerTxPending = false;
        }

        db.commit(txVoucher);
      }
    }
    catch (PersistenceException px) {
      db.rollback(txVoucher);
      throw px;
    }
    catch (RuntimeException rx) {
      db.rollback(txVoucher);
      throw new PersistenceException(db, rx);
    }

    return lastId;
  }

}

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

import org.testng.annotations.Test;

import org.tentackle.session.PersistenceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link Db#rollbackImmediately(Throwable)} and the persistence visitors of a transaction.
 */
public class RollbackImmediatelyTest extends AbstractDbTest {

  private static final AtomicLong TX_NUMBER = new AtomicLong(Long.MAX_VALUE / 2);

  public RollbackImmediatelyTest() {
    super(TransactionType.NONE);   // the transactions are managed by the test methods
  }


  /**
   * A session whose transactions fail to roll back.
   */
  private static class FailingRollbackDb extends Db {

    private FailingRollbackDb(Db db) {
      super(db.getConnectionManager(), db.getSessionInfo().clone());
    }

    @Override
    protected DbTransaction createTransaction(String txName, boolean fromRemote) {
      return new DbTransaction(this, 4711L, txName, TX_NUMBER.incrementAndGet()) {
        @Override
        public void rollback() {
          throw new PersistenceException(getSession(), "rollback failed deliberately");
        }
      };
    }
  }


  @Test
  public void testPersistenceVisitorsWithoutRegistration() {
    Db db = getSession();
    long voucher = db.begin("noVisitors");
    try {
      assertNull(db.getPersistenceVisitors(), "no visitors registered");
    }
    finally {
      db.rollback(voucher);
    }
  }


  @Test
  public void testLowLevelRollbackIfConnectionStillInTransaction() throws SQLException {
    Db db = new FailingRollbackDb(getSession());
    try {
      db.begin("failingRollback");
      ManagedConnection mc = db.getConnection();
      Connection con = mc.getConnection();
      assertFalse(con.getAutoCommit(), "physical connection must be in a transaction");

      assertThrows(PersistenceException.class, () -> db.rollbackImmediately(null));

      assertTrue(mc.isDead(), "connection must be marked dead");
      assertTrue(con.getAutoCommit(), "connection must have been rolled back at the lowest level");
    }
    finally {
      try {
        db.close();
      }
      catch (RuntimeException ex) {
        // the session is in a broken state on purpose
      }
    }
  }

}

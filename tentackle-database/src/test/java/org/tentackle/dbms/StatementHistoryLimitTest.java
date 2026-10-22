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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.tentackle.session.PersistenceException;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Tests {@link Db#setStatementHistoryLimit(int)}.
 */
@SuppressWarnings("exports")
public class StatementHistoryLimitTest extends AbstractDbTest {

  private static final StatementId SELECT_ID = new StatementId();


  public StatementHistoryLimitTest() {
    super(TransactionType.NONE);    // the transactions are managed by the test methods
  }

  @AfterMethod
  public void rollbackPendingTransaction() {
    Db db = getSession();
    while (db.isTxRunning()) {
      db.rollbackImmediately(null);
    }
  }


  /**
   * Executes a select with the given number as its parameter.
   *
   * @param db the session
   * @param n the parameter
   */
  private void select(Db db, int n) {
    PreparedStatementWrapper stmt = db.getPreparedStatement(new StatementKey(SELECT_ID, getClass()), false,
                                                            _ -> "SELECT CAST(? AS INTEGER)");
    stmt.setInt(1, n);
    try (ResultSetWrapper rs = stmt.executeQuery()) {
      rs.next();
    }
  }


  @Test
  public void testNoTransaction() {
    assertThrows(PersistenceException.class, () -> getSession().setStatementHistoryLimit(1));
  }

  @Test
  public void testUnlimited() {
    Db db = getSession();
    long voucher = db.begin("unlimited");
    assertEquals(db.getStatementHistoryLimit(), -1);
    for (int i = 0; i < 10; i++) {
      select(db, i);
    }
    assertEquals(db.getConnection().getStatementHistory().size(), 10);
    assertEquals(db.getConnection().getStatementCount(), 10);
    db.rollback(voucher);
  }

  @Test
  public void testLimited() {
    Db db = getSession();
    long voucher = db.begin("limited");
    db.setStatementHistoryLimit(3);
    for (int i = 0; i < 10; i++) {
      select(db, i);
    }
    List<StatementHistory> history = db.getConnection().getStatementHistory();
    assertEquals(history.size(), 3);
    assertEquals(history.get(0).getParameters().get(1), 7);    // the most recent statements are kept
    assertEquals(history.get(2).getParameters().get(1), 9);
    assertEquals(db.getConnection().getStatementCount(), 10);
    db.commit(voucher);
    assertEquals(db.getStatementHistoryLimit(), -1, "limit must be reset after commit");

    voucher = db.begin("next");
    for (int i = 0; i < 5; i++) {
      select(db, i);
    }
    assertEquals(db.getConnection().getStatementHistory().size(), 5, "next transaction must not be limited");
    db.rollback(voucher);
  }

  @Test
  public void testNoHistory() {
    Db db = getSession();
    long voucher = db.begin("none");
    db.setStatementHistoryLimit(0);
    for (int i = 0; i < 10; i++) {
      select(db, i);
    }
    assertEquals(db.getConnection().getStatementHistory().size(), 0);
    assertEquals(db.getConnection().getStatementCount(), 10);
    db.rollback(voucher);
  }

  @Test
  public void testMaxStatementsStillCountsAll() {
    Db db = getSession();
    int oldMax = ManagedConnection.getMaxStatementsPerTransaction();
    ManagedConnection.setMaxStatementsPerTransaction(5);
    try {
      long voucher = db.begin("capped");
      db.setStatementHistoryLimit(2);
      for (int i = 0; i < 5; i++) {
        select(db, i);
      }
      assertThrows(PersistenceException.class, () -> select(db, 5));
      db.rollback(voucher);
    }
    finally {
      ManagedConnection.setMaxStatementsPerTransaction(oldMax);
    }
  }

}

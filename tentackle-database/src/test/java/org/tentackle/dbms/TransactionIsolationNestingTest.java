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
import org.tentackle.session.TransactionIsolation;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Tests the transaction isolation of nested {@link Db#begin(String, TransactionIsolation,
 * org.tentackle.session.TransactionWritability)} invocations.<br>
 * The isolation of the running transaction is taken from the connection without asking the driver.
 *
 * @author harald
 */
@SuppressWarnings("exports")
public class TransactionIsolationNestingTest extends AbstractDbTest {

  public TransactionIsolationNestingTest() {
    super(TransactionType.NONE);    // the transactions are managed by the test methods
  }

  @AfterMethod
  public void rollbackPendingTransaction() {
    Db db = getSession();
    while (db.isTxRunning()) {
      db.rollbackImmediately(null);
    }
  }


  @Test
  public void testNestedBeginWithSameIsolationIsAllowed() {
    Db db = getSession();

    long voucher = db.begin("serializable", TransactionIsolation.SERIALIZABLE, null);
    assertEquals(db.connection().getCurrentTransactionIsolation(), TransactionIsolation.SERIALIZABLE.getLevel());
    assertEquals(db.connection().getCurrentTransactionIsolation(), db.connection().getTransactionIsolation(),
                 "cached isolation must match the driver's");
    assertEquals(db.begin("nested", TransactionIsolation.SERIALIZABLE, null), 0,
                 "a nested begin must not create another transaction");
    assertEquals(db.getTxLevel(), 2);
    db.rollback(voucher);
  }


  @Test
  public void testNestedBeginWithOtherIsolationIsRejected() {
    Db db = getSession();

    long voucher = db.begin("serializable", TransactionIsolation.SERIALIZABLE, null);
    assertThrows(PersistenceException.class, () -> db.begin("nested", TransactionIsolation.READ_COMMITTED, null));
    db.rollback(voucher);
  }

}

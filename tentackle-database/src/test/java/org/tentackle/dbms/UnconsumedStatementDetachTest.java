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

import java.math.BigInteger;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests that closing a one-shot statement without executing it releases the session's attachment.
 */
@SuppressWarnings("exports")
public class UnconsumedStatementDetachTest extends AbstractDbTest {

  public UnconsumedStatementDetachTest() {
    super(TransactionType.NONE);    // the attachment is checked outside transactions
  }

  @AfterMethod
  public void rollbackPendingTransaction() {
    Db db = getSession();
    while (db.isTxRunning()) {
      db.rollbackImmediately(null);
    }
  }


  @Test
  public void testUnconsumedStatement() {
    Db db = getSession();
    assertNull(db.getConnection(), "session attached before test");
    StatementWrapper st = db.createStatement();
    assertTrue(st.isMarkedReady());
    st.close();
    assertNull(db.getConnection(), "session still attached");
  }

  @Test
  public void testUnconsumedPreparedStatement() {
    Db db = getSession();
    assertNull(db.getConnection(), "session attached before test");
    try (PreparedStatementWrapper st = db.createPreparedStatement(_ -> "SELECT 1")) {
      assertTrue(st.isMarkedReady());
    }
    assertNull(db.getConnection(), "session still attached");
  }

  @Test
  public void testFailingParameters() {
    Db db = getSession();
    assertNull(db.getConnection(), "session attached before test");
    Query query = new Query();
    query.add("SELECT 1 WHERE 1 = ?", BigInteger.ONE);   // no datatype for BigInteger
    expectThrows(PersistenceException.class, () -> query.getRowCount(db));
    assertNull(db.getConnection(), "session still attached");
  }

  @Test
  public void testUnconsumedWithinTransaction() {
    Db db = getSession();
    long voucher = db.begin("unconsumed");
    try (PreparedStatementWrapper st = db.createPreparedStatement(_ -> "SELECT 1")) {
      assertTrue(st.isMarkedReady());
    }
    // still attached by the transaction
    assertTrue(db.isTxRunning());
    assertFalse(db.getConnection() == null, "transaction lost its connection");
    db.rollback(voucher);
    assertNull(db.getConnection(), "session still attached");
  }

}

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

package org.tentackle.jta;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import org.testng.annotations.Test;

import org.tentackle.dbms.GlobalTransactionMode;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link GlobalTransactionMode#JOIN}: the global transactions are demarcated by the application
 * via the transaction manager, as a container would do.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class JoinModeTest extends AbstractJtaTest {

  @Override
  protected GlobalTransactionMode getMode() {
    return GlobalTransactionMode.JOIN;
  }


  @Test
  public void testExternalCommit() throws Exception {
    List<String> events = new ArrayList<>();
    tm.begin();
    long voucher = db1.begin("first");
    assertEquals(voucher, 0L, "joined sessions never get a voucher");
    db1.postCommit(_ -> events.add("post1"));
    insertRow(db1, 1);
    db1.commit(voucher);
    assertTrue(db1.isTxRunning(), "the session remains in the global transaction until it completes");

    // a second unit of work in the same global transaction
    voucher = db1.begin("again");
    insertRow(db1, 2);
    db1.commit(voucher);

    voucher = db2.begin("second");
    insertRow(db2, 3);
    db2.commit(voucher);
    assertTrue(events.isEmpty());

    tm.commit();
    assertFalse(db1.isTxRunning());
    assertFalse(db2.isTxRunning());
    assertEquals(events, List.of("post1"));
    assertEquals(countRows(db1), 2);
    assertEquals(countRows(db2), 1);
  }

  @Test
  public void testExternalRollback() throws Exception {
    tm.begin();
    long voucher = db1.begin("first");
    insertRow(db1, 1);
    db1.commit(voucher);
    voucher = db2.begin("second");
    insertRow(db2, 2);
    db2.commit(voucher);

    tm.rollback();
    assertFalse(db1.isTxRunning());
    assertFalse(db2.isTxRunning());
    assertEquals(countRows(db1), 0);
    assertEquals(countRows(db2), 0);
  }

  @Test
  public void testRollbackMarksRollbackOnly() throws Exception {
    tm.begin();
    long voucher = db1.begin("failing");
    long nested = db1.begin("nested");
    insertRow(db1, 1);
    db1.rollback(nested);
    assertEquals(tm.getStatus(), Status.STATUS_ACTIVE, "a nested rollback leaves the decision to the outer level");
    db1.rollback(voucher);
    assertEquals(tm.getStatus(), Status.STATUS_MARKED_ROLLBACK, "the failed unit of work dooms the global transaction");
    tm.rollback();
    assertFalse(db1.isTxRunning());
    assertEquals(countRows(db1), 0);
  }

  @Test
  public void testLocalTransactionWithoutGlobalTransaction() throws SystemException {
    long voucher = db1.begin("local");
    assertNotEquals(voucher, 0L, "no global transaction: a local transaction is started");
    assertEquals(tm.getStatus(), Status.STATUS_NO_TRANSACTION);
    insertRow(db1, 1);
    assertTrue(db1.commit(voucher));
    assertEquals(countRows(db1), 1);
  }

}

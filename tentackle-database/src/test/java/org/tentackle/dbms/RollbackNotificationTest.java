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

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests that a rollback is notified before the session's transaction is cleared.
 * <p>
 * Otherwise, the modification tracker could take the session as no longer running the transaction
 * and its pending modification counts as committed, before they are rolled back.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class RollbackNotificationTest extends AbstractDbTest {

  public RollbackNotificationTest() {
    super(TransactionType.NONE);   // the transactions are managed by the test methods
  }

  @AfterMethod
  public void rollbackPendingTransaction() {
    Db db = getSession();
    while (db.isTxRunning()) {
      db.rollbackImmediately(null);
    }
  }


  /**
   * Creates a serializer notified about the rollback via {@link DbUtilities#notifyRollback(Db, long)}.
   *
   * @param rollback invoked on rollback
   * @return the serializer
   */
  private static ModificationLogSerializer createSerializer(Runnable rollback) {
    return (ModificationLogSerializer) Proxy.newProxyInstance(
        ModificationLogSerializer.class.getClassLoader(), new Class<?>[] { ModificationLogSerializer.class },
        (_, method, _) -> {
          if (method.getName().equals("rollback")) {
            rollback.run();
          }
          return null;
        });
  }


  @Test
  public void testNotifiedWhileTransactionRunning() {
    Db db = getSession();
    AtomicBoolean notified = new AtomicBoolean();
    AtomicBoolean txRunning = new AtomicBoolean();
    AtomicLong txNumber = new AtomicLong();

    long txVoucher = db.begin("rollbackNotification");
    long expectedTxNumber = db.getTxNumber();
    db.setTransactionProperty(ModificationLogSerializer.class, createSerializer(() -> {
      notified.set(true);
      txRunning.set(db.isTxRunning());
      txNumber.set(db.getTxNumber());
    }));

    assertTrue(db.rollback(txVoucher));
    assertTrue(notified.get(), "rollback not notified");
    assertTrue(txRunning.get(), "rollback notified after the transaction was cleared");
    assertEquals(txNumber.get(), expectedTxNumber);
    assertFalse(db.isTxRunning());
  }

  @Test
  public void testFailedNotificationClearsTransaction() {
    Db db = getSession();
    long txVoucher = db.begin("failedRollbackNotification");
    db.setTransactionProperty(ModificationLogSerializer.class, createSerializer(() -> {
      throw new IllegalStateException("notification failed");
    }));

    assertThrows(IllegalStateException.class, () -> db.rollback(txVoucher));
    assertFalse(db.isTxRunning(), "transaction not cleared after a failed notification");

    // the session is still usable
    long nextVoucher = db.begin("nextTransaction");
    assertTrue(db.commit(nextVoucher));
  }

}

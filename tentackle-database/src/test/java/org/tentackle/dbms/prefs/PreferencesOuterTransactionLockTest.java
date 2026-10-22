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

package org.tentackle.dbms.prefs;

import org.testng.annotations.Test;

import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.dbms.AbstractDbTest;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.StatementWrapper;
import org.tentackle.prefs.PersistedPreferences;

import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests that a flush joining an application's transaction does not block the preferences of other threads.
 * <p>
 * The flushed rows stay locked in the database until the application's transaction ends.
 * If another thread flushed the same rows meanwhile, it must not wait for the row locks while holding
 * the monitor of the {@link DbPreferencesFactory}. Otherwise, the application's thread could not even read
 * a preference before committing, and both threads would wait for each other until the database's lock timeout.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class PreferencesOuterTransactionLockTest extends AbstractDbTest {

  private static final String NODE_NAME = "outertxlocktest";
  private static final String KEY = "key";
  private static final String LOCKED_KEY = "lockedkey";
  private static final long MAX_GET_MILLIS = 500;
  private static final long AWAIT_MILLIS = 10000;

  public PreferencesOuterTransactionLockTest() {
    super(TransactionType.NONE);    // the test runs its own transaction
  }


  @Test    // no timeOut: would run in another thread without the thread-local session
  public void testFlushWhileOuterTransactionHoldsRowLocks() throws Exception {
    Db db = getSession();
    DbPreferences prefs = (DbPreferences) PersistedPreferences.systemRoot().node(NODE_NAME);
    try {
      prefs.put(KEY, "0");
      prefs.put(LOCKED_KEY, "0");
      prefs.flush();
      long lockedKeyId = prefs.getPersistentKey(LOCKED_KEY).getId();

      long txVoucher = db.begin("outer transaction");
      Thread otherThread;
      AtomicReference<Throwable> otherFailure = new AtomicReference<>();
      long getMillis;
      try {
        prefs.put(KEY, "1");
        prefs.flush();    // joins the transaction

        // the application modified another key's row within the same transaction, which stays locked until the commit.
        // Unlike the flushed row, its serial is still the committed one, so another flush must wait for the row lock.
        try (StatementWrapper st = db.createStatement()) {
          st.executeUpdate("UPDATE " + DbPreferencesKey.CLASSVARIABLES.tableName +
                           " SET " + AbstractDbObject.CN_SERIAL + "=" + AbstractDbObject.CN_SERIAL +
                           " WHERE " + AbstractDbObject.CN_ID + "=" + lockedKeyId);
        }

        // another thread without a thread-local session flushes the locked key
        otherThread = new Thread(() -> {
          try {
            prefs.put(LOCKED_KEY, "2");
            prefs.flush();
          }
          catch (Throwable t) {
            otherFailure.set(t);
          }
        }, "other preferences thread");
        otherThread.start();
        awaitWaiting(otherThread, otherFailure);

        // this thread must still be able to read the preferences
        long start = System.currentTimeMillis();
        prefs.get(KEY, null);
        getMillis = System.currentTimeMillis() - start;

        db.commit(txVoucher);
      }
      catch (RuntimeException rx) {
        db.rollback(txVoucher);
        throw rx;
      }

      otherThread.join(AWAIT_MILLIS);
      assertTrue(getMillis < MAX_GET_MILLIS, "reading the preferences was blocked for " + getMillis + "ms");
      assertNull(otherFailure.get(), "flush of the other thread failed");
      assertEquals(prefs.get(KEY, null), "1");
      // LOCKED_KEY is not checked: the repository update triggered by the commit competes with the other thread's
      // flush and may reload the row's committed value before the flush, which discards the unflushed value.
    }
    finally {
      prefs.removeNode();
      prefs.flush();
    }
  }


  /**
   * Waits until the thread waits for the row locks or the preferences.
   */
  private void awaitWaiting(Thread thread, AtomicReference<Throwable> failure) throws InterruptedException {
    long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      Thread.State state = thread.getState();
      if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
        Thread.sleep(100);    // give it the chance to settle
        return;
      }
      if (state == Thread.State.TERMINATED) {
        throw new AssertionError("the other thread did not wait for the transaction", failure.get());
      }
      Thread.sleep(20);
    }
    throw new AssertionError("the other thread is not waiting");
  }

}

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the pending counts of a {@link ModificationTally} are flushed as soon as the counting
 * transactions are committed, even if their sessions are running other transactions by then.
 *
 * @author harald
 */
public class ModificationTallyTest extends AbstractDbTest {

  private static final String TRACKED_NAME = "mtallytest";
  private static final String LOCAL_TRACKED_NAME = "mtallylocaltest";
  private static final String SERIAL_TRACKED_NAME = "mtallyserialtest";
  private static final long LOCK_TIMEOUT_MS = 10000;   // way longer than the max. time to count

  public ModificationTallyTest() {
    super(TransactionType.NONE);    // the transactions are managed by the test method
  }


  @Test
  public void testFlushWhileSessionRunsAnotherTransaction() {
    Db db = getSession();

    // an own tracker, not started: the pending counts are flushed by the test
    DbModificationTracker tracker = new DbModificationTracker("test tally tracker");
    Db trackerSession = db.clone(null);
    tracker.setSession(trackerSession);
    try {
      ModificationTally tally = new ModificationTally(tracker, TRACKED_NAME);
      tally.performPendingCount();
      long serial = tally.getModificationCount();

      // count within a transaction: not flushed before the commit
      long txVoucher = db.begin("count");
      tally.countPending(db);
      tally.performPendingCount();
      assertEquals(tally.getPendingCount(), 1, "flushed before the counting transaction was committed");
      db.commit(txVoucher);

      // the session is busy with another transaction, not counting: the committed count must be flushed
      txVoucher = db.begin("other");
      try {
        tally.performPendingCount();
        assertEquals(tally.getPendingCount(), 0, "count of the committed transaction not flushed");
        assertEquals(tally.getModificationCount(), serial + 1);

        // counting in the new transaction defers the flush again until it is committed
        tally.countPending(db);
        tally.performPendingCount();
        assertEquals(tally.getPendingCount(), 1, "flushed before the counting transaction was committed");
      }
      finally {
        db.commit(txVoucher);
      }
      tally.performPendingCount();
      assertEquals(tally.getPendingCount(), 0);
      assertEquals(tally.getModificationCount(), serial + 2);
    }
    finally {
      trackerSession.close();
    }
  }


  /**
   * In local client mode, the modification count is flushed within the transaction of the counting session,
   * which locks the modification rows until the end of that transaction.
   * A session blocked by such a lock must not keep the lock owner from counting its next modification.
   */
  @Test
  public void testNoDeadlockInLocalClientMode() throws Exception {
    Db dbA = getSession();
    dbA.makeCurrent();

    DbModificationTracker tracker = new DbModificationTracker("test local tally tracker");
    Db trackerSession = dbA.clone(null);
    Db dbB = dbA.clone(null);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      tracker.setSession(trackerSession);   // initializes the modification table, if necessary
      tracker.setLocalClientMode(true);     // count with the thread-local sessions
      setLockTimeout(dbA);
      setLockTimeout(dbB);

      ModificationTally tally = new ModificationTally(tracker, LOCAL_TRACKED_NAME);
      tally.countPending(null);
      tally.performPendingCount();          // creates the modification row, if missing
      long serial = tally.getModificationCount();

      CountDownLatch flushing = new CountDownLatch(1);
      Future<?> futureB;
      long txVoucher = dbA.begin("A");
      try {
        // A locks the modification rows until committed
        tally.countPending(null);
        tally.performPendingCount();

        // B waits for A's lock while flushing its count
        futureB = executor.submit(() -> {
          dbB.makeCurrent();
          return dbB.transaction("B", () -> {
            tally.countPending(null);
            flushing.countDown();
            tally.performPendingCount();
            return null;
          });
        });
        assertTrue(flushing.await(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS), "B did not start counting");
        Thread.sleep(300);    // let B run into A's lock

        // A counts again: must not wait for B, which waits for A
        long start = System.currentTimeMillis();
        tally.countPending(null);
        tally.performPendingCount();
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < LOCK_TIMEOUT_MS / 2, "A blocked by B for " + elapsed + " ms");
        assertFalse(futureB.isDone(), "B did not wait for A's lock");
        dbA.commit(txVoucher);
      }
      catch (RuntimeException | Error ex) {
        dbA.rollback(txVoucher);
        throw ex;
      }

      futureB.get(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);   // B completes once A has committed
      assertEquals(tally.getPendingCount(), 0);
      assertEquals(tally.getModificationCount(), serial + 3);
    }
    finally {
      executor.shutdownNow();
      dbB.close();
      trackerSession.close();
    }
  }

  /**
   * In local client mode, the last serial is advanced before the flush is committed.
   * The tracker's poll meanwhile reads the lower committed serial and must not set the last serial back,
   * otherwise the next count gets a table serial already handed out.
   */
  @Test
  public void testLastSerialNotSetBackByUncommittedFlush() {
    Db db = getSession();
    db.makeCurrent();

    DbModificationTracker tracker = new DbModificationTracker("test serial tally tracker");
    Db trackerSession = db.clone(null);
    try {
      tracker.setSession(trackerSession);
      tracker.setLocalClientMode(true);     // count with the thread-local session

      ModificationTally tally = new ModificationTally(tracker, SERIAL_TRACKED_NAME);
      tally.countPending(null);
      tally.performPendingCount();          // creates the modification row, if missing
      long committedSerial = tally.getModificationCount();

      long txVoucher = db.begin("count");
      try {
        long firstSerial = tally.countPending(null);
        tally.performPendingCount();        // flushed, but not committed yet
        tally.setLastSerial(committedSerial);   // what poll() reads from the database meanwhile
        long secondSerial = tally.countPending(null);
        assertNotEquals(secondSerial, firstSerial, "table serial handed out twice");
        tally.performPendingCount();
        db.commit(txVoucher);
      }
      catch (RuntimeException | Error ex) {
        db.rollback(txVoucher);
        throw ex;
      }
      assertEquals(tally.getModificationCount(), committedSerial + 2);
    }
    finally {
      trackerSession.close();
    }
  }

  private void setLockTimeout(Db db) {
    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate("SET LOCK_TIMEOUT " + LOCK_TIMEOUT_MS);   // H2: wait for row locks
    }
  }

}

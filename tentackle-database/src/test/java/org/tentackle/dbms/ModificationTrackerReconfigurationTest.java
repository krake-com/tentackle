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

import org.tentackle.misc.IdSerialTuple;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationListenerAdapter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link DbModificationTracker} while it is reconfigured or started concurrently.
 *
 * @author harald
 */
public class ModificationTrackerReconfigurationTest extends AbstractDbTest {

  private static final String RETIRE_NAME = "mtretiretest";
  private static final String CONFIG_NAME = "mtconfigtest";
  private static final String INVALIDATE_NAME = "mtinvalidatetest";
  private static final String INVALIDATE_FILLER_NAME = "mtinvalidatefiller";
  private static final long AWAIT_MILLIS = 10000;

  public ModificationTrackerReconfigurationTest() {
    super(TransactionType.NONE);    // the transactions are managed by the test methods
  }


  @Test(timeOut = 60000)
  public void testPendingCountSurvivesModeSwitch() throws Exception {
    Db db = getSession();
    // an own tracker, so that the other tests are not affected
    DbModificationTracker tracker = new DbModificationTracker("retire test tracker");
    tracker.setSession(db.clone(null));
    tracker.setSleepInterval(100);
    tracker.start();
    try {
      long serialBefore = new DbModification(db, RETIRE_NAME).refresh();

      long txVoucher = db.begin("pendingCount");
      tracker.countModification(db, RETIRE_NAME);   // pending until the transaction is committed
      tracker.setLocalClientMode(true);             // replaces the counters
      db.commit(txVoucher);

      // the next poll performs the pending count
      long serial = serialBefore;
      for (long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
           serial == serialBefore && System.currentTimeMillis() < deadline; ) {
        Thread.sleep(50);
        serial = new DbModification(db, RETIRE_NAME).refresh();
      }
      assertTrue(serial > serialBefore, "the pending count of the committed transaction got lost");
    }
    finally {
      tracker.terminate();
    }
  }


  @Test(timeOut = 60000)
  public void testConfigurationSerializedWhileTrackerStarts() throws Exception {
    Db db = getSession();
    CountDownLatch blocked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean blockNext = new AtomicBoolean();
    DbModificationTracker tracker = new DbModificationTracker("configuration test tracker") {
      @Override
      protected IdSerialTuple selectIdSerialForName(String tableName) {
        if (CONFIG_NAME.equals(tableName) && blockNext.compareAndSet(true, false)) {
          // some lengthy configuration
          blocked.countDown();
          try {
            release.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
          }
          catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
        return super.selectIdSerialForName(tableName);
      }
    };
    tracker.setSession(db.clone(null));
    tracker.setSleepInterval(30000);    // no more polls after the first one during the test
    tracker.getMasterSerial();          // configures the master entry before the tracker runs
    try {
      AtomicReference<Throwable> failure = new AtomicReference<>();
      AtomicReference<IdSerialTuple> first = new AtomicReference<>();
      AtomicReference<IdSerialTuple> second = new AtomicReference<>();

      // configured by the requesting thread itself, since the tracker isn't running yet
      blockNext.set(true);
      Thread firstThread = start("first", failure, () -> first.set(tracker.getIdSerialForName(CONFIG_NAME)));
      assertTrue(blocked.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "the configuration did not start");

      // the tracker is started meanwhile, and the same name is requested again
      Thread starter = start("starter", failure, tracker::start);
      Thread secondThread = start("second", failure, () -> second.set(tracker.getIdSerialForName(CONFIG_NAME)));
      secondThread.join(1000);
      boolean configuredConcurrently = !secondThread.isAlive();

      release.countDown();
      firstThread.join(AWAIT_MILLIS);
      starter.join(AWAIT_MILLIS);
      secondThread.join(AWAIT_MILLIS);

      // the first thread must not use the session taken over by the tracker thread
      assertNull(failure.get(), "configuration failed: " + failure.get());
      assertFalse(configuredConcurrently, "the name was configured again while being configured by another thread");
      assertTrue(tracker.isAlive(), "the tracker was not started");
      assertNotNull(first.get());
      assertNotNull(second.get());
      assertEquals(second.get().getId(), first.get().getId());
    }
    finally {
      release.countDown();
      tracker.terminate();
    }
  }


  @Test(timeOut = 60000)
  public void testListenerFiresAfterInvalidate() throws Exception {
    Db db = getSession();
    // a row unknown to the tracker, created before the tracked name:
    // the tracked name gets another id once the table is re-initialized
    DbModification filler = new DbModification(db, INVALIDATE_FILLER_NAME);
    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate("DELETE FROM " + filler.getTableName() + " WHERE " +
                       DbModification.CN_TRACKEDNAME + "='" + INVALIDATE_NAME + "'");
    }
    filler.refresh();
    if (filler.getId() == 0) {
      filler.addToModificationTable(false, null);
    }

    DbModificationTracker tracker = new DbModificationTracker("invalidate test tracker");
    tracker.setSession(db.clone(null));
    tracker.setSleepInterval(100);
    CountDownLatch changed = new CountDownLatch(1);
    tracker.addModificationListener(new ModificationListenerAdapter(INVALIDATE_NAME) {
      @Override
      public void dataChanged(ModificationEvent event) {
        changed.countDown();
      }
    });
    long oldId = tracker.getIdSerialForName(INVALIDATE_NAME).getId();
    tracker.start();
    try {
      tracker.invalidate();
      db.transaction("count", () -> {
        tracker.countModification(db, INVALIDATE_NAME);
        return null;
      });
      assertTrue(changed.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "listener not fired after invalidate");

      DbModification row = new DbModification(db, INVALIDATE_NAME);
      row.refresh();
      assertNotEquals(row.getId(), oldId, "id not changed by the re-initialization");
      assertEquals(tracker.getIdSerialForName(INVALIDATE_NAME).getId(), row.getId(), "stale id after invalidate");
    }
    finally {
      tracker.terminate();
    }
  }


  private static Thread start(String name, AtomicReference<Throwable> failure, Runnable action) {
    Thread thread = new Thread(() -> {
      try {
        action.run();
      }
      catch (Throwable t) {
        failure.compareAndSet(null, t);
      }
    }, name);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

}

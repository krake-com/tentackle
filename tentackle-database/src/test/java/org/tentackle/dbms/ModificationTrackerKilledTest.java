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
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the cleanup of a killed tracker does not use the session of the stuck tracker thread.
 * <p>
 * If the tracker thread does not terminate within the dead interval, {@link DbModificationTracker#terminate()}
 * marks it killed and runs the cleanup in the terminating thread. The tracker thread may still use its session
 * meanwhile, which is not thread-safe. The pending counts must be flushed and the session closed by the tracker
 * thread itself when it returns, while the terminating thread completes the remaining cleanup without failing.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ModificationTrackerKilledTest extends AbstractDbTest {

  private static final String TRACKED_NAME = "mtkilledtest";
  private static final long AWAIT_MILLIS = 10000;

  public ModificationTrackerKilledTest() {
    super(TransactionType.NONE);    // the modifications must be committed to be seen in the table
  }


  /**
   * A tracker getting stuck in its poll on request.
   */
  private static class StuckTracker extends DbModificationTracker {

    private volatile CountDownLatch stuck;          // counted down when stuck
    private final CountDownLatch release = new CountDownLatch(1);

    private StuckTracker() {
      super("stuck test tracker");
    }

    private void getStuck() {
      stuck = new CountDownLatch(1);
    }

    @Override
    protected Long getModifiedMasterSerial() {
      CountDownLatch latch = stuck;
      if (latch != null) {
        latch.countDown();
        // the session is in use, as with a hanging database call ignoring interrupts
        boolean interrupted = false;
        for (;;) {
          try {
            release.await();
            break;
          }
          catch (InterruptedException ix) {
            interrupted = true;   // by terminate()
          }
        }
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
      return super.getModifiedMasterSerial();
    }
  }


  @Test(timeOut = 60000)
  public void testCleanupLeavesSessionToStuckThread() throws Exception {
    Db db = getSession();
    StuckTracker tracker = new StuckTracker();
    Db trackerDb = db.clone(null);
    tracker.setSession(trackerDb);
    tracker.setSleepInterval(100);
    tracker.setDeadInterval(500);
    assertFalse(tracker.isLocalClientMode());
    tracker.countModification(db, TRACKED_NAME);    // configure the counter before the tracker thread runs
    tracker.start();
    try {
      tracker.getStuck();
      assertTrue(tracker.stuck.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "tracker not stuck");

      long serial = new DbModification(db).selectIdSerial(TRACKED_NAME).getSerial();
      tracker.countModification(db, TRACKED_NAME);    // pending, since the tracker thread is alive

      tracker.terminate();
      assertTrue(tracker.isKilled(), "stuck tracker not killed");
      assertTrue(tracker.isAlive(), "stuck tracker not stuck anymore");
      assertTrue(trackerDb.isOpen(), "session closed while the tracker thread uses it");
      assertEquals(new DbModification(db).selectIdSerial(TRACKED_NAME).getSerial(), serial,
                   "pending count flushed while the tracker thread uses the session");

      tracker.release.countDown();
      tracker.join(AWAIT_MILLIS);
      assertFalse(tracker.isAlive(), "killed tracker did not terminate");
      assertFalse(trackerDb.isOpen(), "session not closed by the killed tracker");
      assertEquals(new DbModification(db).selectIdSerial(TRACKED_NAME).getSerial(), serial + 1,
                   "pending count lost");
    }
    finally {
      tracker.release.countDown();
    }
  }

}

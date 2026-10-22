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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the session of a {@link DbModificationTracker} is not used by two threads at a time
 * while the tracker thread is not running.
 * <p>
 * The flushes of the pending counts, the configuration tasks and the invalidation are then executed by
 * the invoking threads. They all use the tracker's session, which is not thread-safe, and must be
 * serialized by the same lock.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ModificationTrackerSessionLockTest extends AbstractDbTest {

  private static final String TRACKED_NAME = "mtlocktest";
  private static final long AWAIT_MILLIS = 10000;

  private DbModificationTracker tracker;

  public ModificationTrackerSessionLockTest() {
    super(TransactionType.NONE);    // the transactions are managed by the test methods
  }

  @BeforeMethod
  public void createTracker() {
    // an own tracker, not started: the invoking threads use its session
    tracker = new DbModificationTracker("test modification tracker");
    tracker.setSession(getSession().clone(null));
    assertFalse(tracker.isLocalClientMode());
    tracker.countModification(getSession(), TRACKED_NAME);    // configure the counter beforehand
  }

  @AfterMethod(alwaysRun = true)
  public void terminateTracker() {
    tracker.terminate();
  }


  /**
   * Runs an action in another thread while the session lock is held, and asserts that it waits for the lock.
   *
   * @param name the thread name
   * @param action the action using the tracker's session
   */
  private void assertWaitsForSessionLock(String name, Runnable action) throws InterruptedException {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread = new Thread(() -> {
      try {
        action.run();
      }
      catch (Throwable t) {
        failure.set(t);
      }
    }, name);
    thread.setDaemon(true);

    synchronized (tracker.getSessionLock()) {
      thread.start();
      long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
      while (thread.getState() != Thread.State.BLOCKED) {
        assertTrue(thread.isAlive(), name + " used the tracker's session without waiting for the session lock");
        assertTrue(System.currentTimeMillis() < deadline, name + " did not start");
        Thread.sleep(10);
      }
    }

    thread.join(AWAIT_MILLIS);
    assertFalse(thread.isAlive(), name + " did not complete");
    assertNull(failure.get(), name + " failed: " + failure.get());
  }


  @Test(timeOut = 60000)
  public void testFlushWaitsForSessionLock() throws Exception {
    assertWaitsForSessionLock("count modification", () -> tracker.countModification(getSession(), TRACKED_NAME));
  }

  @Test(timeOut = 60000)
  public void testInvalidateWaitsForSessionLock() throws Exception {
    assertWaitsForSessionLock("invalidate", tracker::invalidate);
  }

  @Test(timeOut = 60000)
  public void testLocalClientModeWaitsForSessionLock() throws Exception {
    assertWaitsForSessionLock("local client mode", () -> tracker.setLocalClientMode(true));
    assertTrue(tracker.isLocalClientMode());
  }

}

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

import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationListener;
import org.tentackle.session.ModificationListenerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests that modification listeners can be registered while the tracker is polling.
 * <p>
 * The tracker thread iterates the listeners in poll(), so registering a listener from
 * an application thread must not disturb it. An exception thrown in poll() terminates
 * the tracker, which would silently stop all cache invalidation.
 *
 * @author harald
 */
public class ModificationTrackerConcurrencyTest extends AbstractDbTest {

  private static final String TRACKED_NAME = "mttest";
  private static final int LISTENER_COUNT = 3000;   // the more listeners, the longer poll() iterates them
  private static final long RUN_MILLIS = 5000;

  public ModificationTrackerConcurrencyTest() {
    super(TransactionType.NONE);    // the transactions are managed by the test method
  }


  @Test
  public void testListenerRegistrationWhilePolling() throws Exception {
    Db db = getSession();

    // an own tracker, so that a failure cannot affect the tracker of the other tests
    DbModificationTracker tracker = new DbModificationTracker("test modification tracker");
    tracker.setSession(db.clone(null));
    tracker.setSleepInterval(50);   // poll as often as possible
    tracker.start();

    try {
      // pre-register a lot of listeners to widen the window poll() spends iterating them
      List<ModificationListener> listeners = new ArrayList<>();
      for (int i = 0; i < LISTENER_COUNT; i++) {
        ModificationListener listener = createListener();
        tracker.addModificationListener(listener);
        listeners.add(listener);
      }

      AtomicBoolean stop = new AtomicBoolean();
      AtomicReference<Throwable> registrarFailure = new AtomicReference<>();

      // keep on registering and unregistering listeners while the tracker polls
      Thread registrar = new Thread(() -> {
        try {
          while (!stop.get()) {
            ModificationListener listener = createListener();
            tracker.addModificationListener(listener);
            tracker.removeModificationListener(listener);
          }
        }
        catch (Throwable t) {
          registrarFailure.set(t);
        }
      }, "listener registrar");
      registrar.start();

      // count modifications so that the serials change and poll() iterates the listeners
      long endTime = System.currentTimeMillis() + RUN_MILLIS;
      int rounds = 0;
      RuntimeException countFailure = null;
      while (System.currentTimeMillis() < endTime && registrarFailure.get() == null && tracker.isAlive()) {
        try {
          db.transaction("countModification", () -> {
            tracker.countModification(db, TRACKED_NAME);
            return null;
          });
        }
        catch (RuntimeException rex) {
          // a dead tracker closes its session, so this is most probably just a consequence
          countFailure = rex;
          break;
        }
        rounds++;
        Thread.sleep(5);
      }

      stop.set(true);
      registrar.join(10000);

      // check the tracker first: its termination cause is the root cause of any failure below
      assertNull(tracker.getTerminationCause(), "the tracker died after " + rounds + " modifications: " +
                                                tracker.getTerminationCause());
      assertTrue(tracker.isAlive(), "the tracker is not running anymore after " + rounds + " modifications");
      assertNull(registrarFailure.get(), "registering listeners failed: " + registrarFailure.get());
      assertNull(countFailure, "counting modifications failed: " + countFailure);

      // all pre-registered listeners must still be removable
      for (ModificationListener listener : listeners) {
        tracker.removeModificationListener(listener);
      }
    }
    finally {
      tracker.terminate();
    }
  }


  private ModificationListener createListener() {
    return new ModificationListenerAdapter(TRACKED_NAME) {
      @Override
      public void dataChanged(ModificationEvent event) {
        // nothing to do, we're only interested in the tracker surviving
      }
    };
  }

}

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
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
  private static final String OTHER_NAME = "mttest2";
  private static final String SERIAL_NAME = "mttest3";
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


  /**
   * Registering listeners for the same, not yet configured name concurrently must not
   * configure the name twice.
   * <p>
   * The loser's table entry is replaced in the tracker's maps, but is still referenced by
   * the listener registered for it. Since poll() only updates the entry found in the maps,
   * the listener's entry would keep its initial serial and fire on every poll from then on.
   */
  @Test
  public void testConcurrentConfigurationOfSameName() throws Exception {
    Db db = getSession();

    DbModificationTracker tracker = new DbModificationTracker("test configuration tracker");
    tracker.setSession(db.clone(null));
    tracker.setSleepInterval(50);
    tracker.start();

    try {
      // register listeners for the same still unconfigured name at the very same time
      int threadCount = 4;
      List<CountingListener> listeners = new ArrayList<>();
      List<Thread> threads = new ArrayList<>();
      CountDownLatch startSignal = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();

      for (int i = 0; i < threadCount; i++) {
        CountingListener listener = new CountingListener();
        listeners.add(listener);
        Thread thread = new Thread(() -> {
          try {
            startSignal.await();
            tracker.addModificationListener(listener);
          }
          catch (Throwable t) {
            failure.set(t);
          }
        }, "registrar " + i);
        threads.add(thread);
        thread.start();
      }

      startSignal.countDown();
      for (Thread thread : threads) {
        thread.join(30000);
      }
      assertNull(failure.get(), "registering the listeners failed: " + failure.get());

      // modify the tracked name once: all listeners must notice
      countModification(db, tracker, TRACKED_NAME);
      awaitPolls(db, tracker, 3);
      for (CountingListener listener : listeners) {
        assertTrue(listener.count.get() > 0, "the listener was not notified at all");
      }

      // now modify another name only: the listeners of TRACKED_NAME must not fire anymore
      List<Integer> countsBefore = new ArrayList<>();
      listeners.forEach(listener -> countsBefore.add(listener.count.get()));

      awaitPolls(db, tracker, 5);

      for (int i = 0; i < listeners.size(); i++) {
        assertEquals(listeners.get(i).count.get(), (int) countsBefore.get(i),
                     "listener " + i + " was notified again although '" + TRACKED_NAME +
                     "' was not modified - it refers to a table entry configured twice");
      }
    }
    finally {
      tracker.terminate();
    }
  }


  /**
   * Concurrent modifications of the same name must get distinct serials.
   * <p>
   * The serial is the tableserial of the modified object. Duplicates collapse in the
   * {@link TableSerialHistory} and the {@link TableSerialExpirationBacklog}, which are keyed by tableserial,
   * so that caches would miss the expiration of one of the objects.
   */
  @Test
  public void testConcurrentCountsGetDistinctSerials() throws Exception {
    Db db = getSession();

    DbModificationTracker tracker = new DbModificationTracker("test serial tracker");
    tracker.setSession(db.clone(null));
    tracker.setSleepInterval(10);   // flush the pending counts concurrently as well
    tracker.start();

    int threadCount = 4;
    int countsPerThread = 500;
    List<Db> sessions = new ArrayList<>();
    try {
      tracker.countModification(db, SERIAL_NAME);   // configure the counter before the race starts

      Set<Long> serials = ConcurrentHashMap.newKeySet();
      Set<Long> duplicates = ConcurrentHashMap.newKeySet();
      List<Thread> threads = new ArrayList<>();
      CountDownLatch startSignal = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();

      for (int i = 0; i < threadCount; i++) {
        Db session = db.clone(null);    // sessions are not thread-safe: one per thread
        sessions.add(session);
        Thread thread = new Thread(() -> {
          try {
            startSignal.await();
            for (int n = 0; n < countsPerThread; n++) {
              long serial = session.transaction("countModification",
                                                () -> tracker.countModification(session, SERIAL_NAME));
              if (!serials.add(serial)) {
                duplicates.add(serial);
              }
            }
          }
          catch (Throwable t) {
            failure.set(t);
          }
        }, "counter " + i);
        threads.add(thread);
        thread.start();
      }

      startSignal.countDown();
      for (Thread thread : threads) {
        thread.join(60000);
      }

      assertNull(failure.get(), "counting modifications failed: " + failure.get());
      assertTrue(duplicates.isEmpty(), duplicates.size() + " serials were assigned more than once: " + duplicates);
      assertEquals(serials.size(), threadCount * countsPerThread);
    }
    finally {
      tracker.terminate();
      sessions.forEach(Db::close);
    }
  }


  /**
   * Modifies another name repeatedly, so that the master serial changes and the tracker polls.
   *
   * @param db the session
   * @param tracker the tracker
   * @param count the number of modifications
   */
  private void awaitPolls(Db db, DbModificationTracker tracker, int count) throws InterruptedException {
    for (int i = 0; i < count; i++) {
      countModification(db, tracker, OTHER_NAME);
      Thread.sleep(150);    // at least 2 polling intervals
    }
  }

  private void countModification(Db db, DbModificationTracker tracker, String name) {
    db.transaction("countModification", () -> {
      tracker.countModification(db, name);
      return null;
    });
  }


  private ModificationListener createListener() {
    return new ModificationListenerAdapter(TRACKED_NAME) {
      @Override
      public void dataChanged(ModificationEvent event) {
        // nothing to do, we're only interested in the tracker surviving
      }
    };
  }


  /**
   * A listener counting its invocations.
   */
  private static class CountingListener extends ModificationListenerAdapter {

    private final AtomicInteger count = new AtomicInteger();

    private CountingListener() {
      super(TRACKED_NAME);
    }

    @Override
    public void dataChanged(ModificationEvent event) {
      count.incrementAndGet();
    }
  }

}

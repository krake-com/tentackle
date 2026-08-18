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

package org.tentackle.daemon;

import org.testng.Reporter;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the {@link DaemonSupervisor} life cycle and its {@link DaemonSupervisor.TerminationInfo}.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class DaemonSupervisorTest {

  /** small check interval so the supervisor reacts quickly. */
  private static final long CHECK_INTERVAL = 100;

  /** generous upper bound for all polling loops. */
  private static final long AWAIT_TIMEOUT = 10000;    // 10s for slow machines (windows jenkins slave in KVM)


  /**
   * A supervisable daemon whose life cycle can be steered from the test.
   */
  private static class TestDaemon extends Thread implements Supervisable {

    private volatile boolean killed;
    private volatile boolean dead;
    private volatile boolean oneShot;         // if true, run() returns immediately
    private volatile boolean keepRunning = true;
    private volatile long startedAt;
    private volatile long terminatedAt;
    private volatile Object terminationCause;

    TestDaemon(String name, boolean oneShot) {
      super(name);
      this.oneShot = oneShot;
      setDaemon(true);
    }

    @Override
    public void run() {
      startedAt = System.currentTimeMillis();
      try {
        while (!oneShot && keepRunning && !killed && !isInterrupted()) {
          Thread.sleep(5);
        }
      }
      catch (InterruptedException ex) {
        // requested to stop
      }
      finally {
        terminatedAt = System.currentTimeMillis();
      }
    }

    @Override
    public void kill() {
      killed = true;
      keepRunning = false;
      interrupt();
    }

    @Override
    public boolean isKilled() {
      return killed;
    }

    @Override
    public boolean isDead() {
      return dead;
    }

    @Override
    public void setDead(boolean dead) {
      this.dead = dead;
    }

    @Override
    public long startedAt() {
      return startedAt;
    }

    @Override
    public long terminatedAt() {
      return terminatedAt;
    }

    @Override
    public Object getTerminationCause() {
      return terminationCause;
    }
  }


  /**
   * A supervisor creating {@link TestDaemon}s on demand.
   */
  private static class TestSupervisor extends DaemonSupervisor {

    private final List<TestDaemon> created = Collections.synchronizedList(new ArrayList<>());
    private final List<Thread> plainCreated = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger cleanupCount = new AtomicInteger();
    private volatile boolean createReturnsNull;
    private volatile boolean oneShotDaemons;
    private volatile boolean plainDaemons;        // create ordinary threads, not Supervisables
    private volatile boolean terminateOnCreate;   // request termination from within createDaemon
    private volatile Object terminationCause;

    TestSupervisor(String name, long checkInterval, int maxStartCount, int daemonNum) {
      super(name, checkInterval, maxStartCount, daemonNum);
    }

    @Override
    public Thread createDaemon(int index) {
      if (terminateOnCreate) {
        requestTermination();   // invoked while run() holds the read lock
        return null;
      }
      if (createReturnsNull) {
        return null;
      }
      if (plainDaemons) {
        Thread thread = new Thread(() -> {
          try {
            while (!Thread.currentThread().isInterrupted()) {
              Thread.sleep(5);
            }
          }
          catch (InterruptedException ex) {
            // requested to stop
          }
        }, getName() + "-plain-" + index + "#" + plainCreated.size());
        thread.setDaemon(true);
        plainCreated.add(thread);
        return thread;
      }
      TestDaemon daemon = new TestDaemon(getName() + "-daemon-" + index + "#" + created.size(), oneShotDaemons);
      daemon.terminationCause = terminationCause;
      created.add(daemon);
      return daemon;
    }

    @Override
    public void cleanupDaemon(Thread daemon) {
      cleanupCount.incrementAndGet();
    }
  }


  /**
   * Polls until the condition becomes true or the timeout elapses.
   *
   * @param condition the condition to wait for
   * @return true if the condition became true within the timeout
   */
  private static boolean await(BooleanSupplier condition) {
    long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      try {
        Thread.sleep(5);
      }
      catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return condition.getAsBoolean();
  }


  // --- parameter validation (no supervision thread needed) -------------------

  @Test
  public void testInvalidCheckInterval() {
    try {
      new TestSupervisor("bad", 0, 0, 1);
      fail("expected IllegalArgumentException");
    }
    catch (IllegalArgumentException ex) {
      Reporter.log("expected: " + ex + "<br/>");
    }
  }

  @Test
  public void testInvalidMaxStartCount() {
    try {
      new TestSupervisor("bad", CHECK_INTERVAL, -1, 1);
      fail("expected IllegalArgumentException");
    }
    catch (IllegalArgumentException ex) {
      Reporter.log("expected: " + ex + "<br/>");
    }
  }

  @Test
  public void testInvalidDaemonNum() {
    try {
      new TestSupervisor("bad", CHECK_INTERVAL, 0, -1);
      fail("expected IllegalArgumentException");
    }
    catch (IllegalArgumentException ex) {
      Reporter.log("expected: " + ex + "<br/>");
    }
  }

  @Test
  public void testGettersAndSetters() {
    TestSupervisor supervisor = new TestSupervisor("props", CHECK_INTERVAL, 5, 2);
    assertEquals(supervisor.getCheckInterval(), CHECK_INTERVAL);
    assertEquals(supervisor.getMaxStartCount(), 5);
    assertEquals(supervisor.getDaemonNum(), 2);

    supervisor.setCheckInterval(123);
    assertEquals(supervisor.getCheckInterval(), 123);
    supervisor.setMaxStartCount(0);
    assertEquals(supervisor.getMaxStartCount(), 0);

    try {
      supervisor.setCheckInterval(0);
      fail("expected IllegalArgumentException");
    }
    catch (IllegalArgumentException ex) {
      Reporter.log("expected: " + ex + "<br/>");
    }
    try {
      supervisor.setMaxStartCount(-1);
      fail("expected IllegalArgumentException");
    }
    catch (IllegalArgumentException ex) {
      Reporter.log("expected: " + ex + "<br/>");
    }
  }

  @Test
  public void testSetDaemonNumBeforeStart() {
    TestSupervisor supervisor = new TestSupervisor("resize", CHECK_INTERVAL, 0, 1);
    assertEquals(supervisor.getDaemonNum(), 1);

    supervisor.setDaemonNum(3);   // grow
    assertEquals(supervisor.getDaemonNum(), 3);

    supervisor.setDaemonNum(0);   // shrink (no supervisables started yet)
    assertEquals(supervisor.getDaemonNum(), 0);

    try {
      supervisor.setDaemonNum(-1);
      fail("expected IllegalArgumentException");
    }
    catch (IllegalArgumentException ex) {
      Reporter.log("expected: " + ex + "<br/>");
    }
  }

  @Test
  public void testOutOfRangeIndexIsTolerated() {
    TestSupervisor supervisor = new TestSupervisor("range", CHECK_INTERVAL, 0, 1);
    // negative and too-large indices resolve to no daemon
    assertFalse(supervisor.isDaemonEnabled(-1));
    assertFalse(supervisor.isDaemonEnabled(99));
    assertNull(supervisor.getStartedDaemon(-1));
    assertNull(supervisor.getStartedDaemon(99));
    assertNull(supervisor.getRunningDaemon(99));
    assertNull(supervisor.getTerminationInfo(99));
    // enabled by default, and the following must not throw
    assertTrue(supervisor.isDaemonEnabled(0));
    supervisor.clearStartCount(99);
    supervisor.setDaemonEnabled(99, false);
  }

  @Test
  public void testTimeGettersWithoutStartedDaemons() {
    TestSupervisor supervisor = new TestSupervisor("times", CHECK_INTERVAL, 0, 2);
    assertEquals(supervisor.getStartedDaemons().length, 0);
    assertEquals(supervisor.getRunningDaemons().length, 0);
    assertEquals(supervisor.getEarliestStartTime(), 0);
    assertEquals(supervisor.getLatestStartTime(), 0);
    assertEquals(supervisor.getEarliestTerminationTime(), 0);
    assertEquals(supervisor.getLatestTerminationTime(), 0);
  }


  // --- supervision loop (running thread) -------------------------------------

  @Test
  public void testStartsAndTerminatesDaemons() {
    TestSupervisor supervisor = new TestSupervisor("run", CHECK_INTERVAL, 0, 2);
    try {
      supervisor.start();
      // wait until both daemons are alive AND have recorded their start time
      // (a thread is alive the moment start() returns, but startedAt is only set
      //  in the first line of run(), which runs asynchronously)
      assertTrue(await(() -> supervisor.getRunningDaemons().length == 2
                             && supervisor.getEarliestStartTime() > 0), "daemons should start");

      assertEquals(supervisor.getStartedDaemons().length, 2);
      assertNotNull(supervisor.getStartedDaemon(0));
      assertNotNull(supervisor.getRunningDaemon(0));
      assertTrue(supervisor.getEarliestStartTime() > 0);
      assertTrue(supervisor.getLatestStartTime() >= supervisor.getEarliestStartTime());
    }
    finally {
      supervisor.terminate();
    }
    // on shutdown all supervisables are killed
    assertFalse(supervisor.isAlive());
    assertTrue(await(() -> supervisor.getRunningDaemons().length == 0), "daemons should be killed on shutdown");
  }

  @Test
  public void testCreateDaemonReturningNullDefersStart() {
    TestSupervisor supervisor = new TestSupervisor("null", CHECK_INTERVAL, 0, 1);
    supervisor.createReturnsNull = true;
    try {
      supervisor.start();
      // give the supervisor a few rounds; nothing must start
      assertFalse(await(() -> supervisor.getStartedDaemon(0) != null),
                  "no daemon must be started while createDaemon returns null");

      supervisor.createReturnsNull = false;
      assertTrue(await(() -> supervisor.getRunningDaemon(0) != null),
                 "daemon must start once createDaemon delivers one");
    }
    finally {
      supervisor.terminate();
    }
  }

  @Test
  public void testTerminatedDaemonGetsRestarted() {
    TestSupervisor supervisor = new TestSupervisor("restart", CHECK_INTERVAL, 0, 1);
    try {
      supervisor.start();
      assertTrue(await(() -> supervisor.getRunningDaemon(0) != null), "daemon should start");

      Thread first = supervisor.getStartedDaemon(0);
      // let the daemon terminate by itself
      ((TestDaemon) first).keepRunning = false;

      assertTrue(await(() -> {
        Thread current = supervisor.getStartedDaemon(0);
        return current != null && current != first && current.isAlive();
      }), "a fresh daemon should replace the terminated one");

      assertTrue(supervisor.cleanupCount.get() > 0, "cleanupDaemon should have been called");
      assertTrue(supervisor.created.size() >= 2);
    }
    finally {
      supervisor.terminate();
    }
  }

  @Test
  public void testDeadDaemonIsKilledAndReplaced() {
    TestSupervisor supervisor = new TestSupervisor("dead", CHECK_INTERVAL, 0, 1);
    try {
      supervisor.start();
      assertTrue(await(() -> supervisor.getRunningDaemon(0) != null), "daemon should start");

      TestDaemon first = (TestDaemon) supervisor.getStartedDaemon(0);
      first.setDead(true);    // supervisor must detect this and kill it

      assertTrue(await(() -> supervisor.getStartedDaemon(0) != null
                             && supervisor.getStartedDaemon(0) != first), "dead daemon should be replaced");
      assertTrue(first.isKilled(), "dead daemon should have been killed");
    }
    finally {
      supervisor.terminate();
    }
  }

  @Test
  public void testMaxStartCountLimitsRestarts() {
    TestSupervisor supervisor = new TestSupervisor("maxstart", CHECK_INTERVAL, 3, 1);
    supervisor.oneShotDaemons = true;   // every daemon terminates immediately -> forces restarts
    try {
      supervisor.start();
      // exactly maxStartCount daemons must be created, then restarting stops
      assertTrue(await(() -> supervisor.created.size() >= 3), "should start up to maxStartCount daemons");
      // must not exceed the limit
      assertFalse(await(() -> supervisor.created.size() > 3), "must not restart beyond maxStartCount");
      assertEquals(supervisor.created.size(), 3);
      assertNull(supervisor.getRunningDaemon(0), "no daemon running after limit reached");

      // clearing the start counter allows restarts again
      supervisor.clearStartCount(0);
      assertTrue(await(() -> supervisor.created.size() > 3), "restarts should resume after clearStartCount");
    }
    finally {
      supervisor.terminate();
    }
  }

  @Test
  public void testDisableKillsAndEnableRestarts() {
    TestSupervisor supervisor = new TestSupervisor("enable", CHECK_INTERVAL, 0, 1);
    try {
      supervisor.start();
      assertTrue(await(() -> supervisor.getRunningDaemon(0) != null), "daemon should start");

      supervisor.setDaemonEnabled(0, false);
      assertFalse(supervisor.isDaemonEnabled(0));
      assertTrue(await(() -> supervisor.getRunningDaemon(0) == null), "disabled daemon should be killed");

      supervisor.setDaemonEnabled(0, true);
      assertTrue(supervisor.isDaemonEnabled(0));
      assertTrue(await(() -> supervisor.getRunningDaemon(0) != null), "re-enabled daemon should restart");
    }
    finally {
      supervisor.terminate();
    }
  }

  @Test
  public void testDecreaseDaemonNumKillsRunningDaemons() {
    TestSupervisor supervisor = new TestSupervisor("shrink", CHECK_INTERVAL, 0, 3);
    try {
      supervisor.start();
      assertTrue(await(() -> supervisor.getRunningDaemons().length == 3), "all daemons should start");

      supervisor.setDaemonNum(1);
      assertEquals(supervisor.getDaemonNum(), 1);
      assertTrue(await(() -> supervisor.getRunningDaemons().length == 1), "surplus daemons should be killed");
    }
    finally {
      supervisor.terminate();
    }
  }

  @Test
  public void testTerminationInfoAfterRestart() {
    TestSupervisor supervisor = new TestSupervisor("termInfo", CHECK_INTERVAL, 0, 1);
    Object cause = "boom";
    supervisor.terminationCause = cause;
    try {
      supervisor.start();
      assertTrue(await(() -> supervisor.getRunningDaemon(0) != null), "daemon should start");

      TestDaemon first = (TestDaemon) supervisor.getStartedDaemon(0);
      String expectedName = first.getName();
      first.keepRunning = false;    // terminate -> cleanup captures the termination info

      assertTrue(await(() -> supervisor.getTerminationInfo(0) != null), "termination info should be captured");

      DaemonSupervisor.TerminationInfo info = supervisor.getTerminationInfo(0);
      assertNotNull(info);
      assertEquals(info.getName(), expectedName);
      assertSame(info.getCause(), cause);
      assertTrue(info.getSince() > 0);
    }
    finally {
      supervisor.terminate();
    }
  }

  /**
   * Daemons are not required to be {@link Supervisable} (see {@link DaemonSupervisor#createDaemon(int)}).
   * Disabling such a daemon must not blow up the supervisor: the exception would escape run()
   * and silently end the supervision of all other daemons as well.
   */
  @Test
  public void testDisablePlainThreadDaemon() {
    TestSupervisor supervisor = new TestSupervisor("plain", CHECK_INTERVAL, 0, 1);
    supervisor.plainDaemons = true;
    try {
      supervisor.start();
      // wait until the daemon is really running: createDaemon() registers the thread before the
      // supervisor starts it, so a plain "has been created" check could observe an unstarted thread.
      // The termination check below would then pass trivially (an unstarted thread is not alive)
      // and the daemon would be started right afterwards.
      assertTrue(await(() -> supervisor.getRunningDaemon(0) != null), "daemon should start");
      Thread daemon = supervisor.plainCreated.get(0);

      supervisor.setDaemonEnabled(0, false);

      assertTrue(await(() -> !daemon.isAlive()), "disabled daemon should be interrupted until terminated");
      assertTrue(supervisor.isAlive(), "supervisor should have survived");
      assertNull(supervisor.getRunningDaemon(0), "disabled daemon should not be restarted");
      assertEquals(supervisor.plainCreated.size(), 1, "disabled daemon should not be restarted");
    }
    finally {
      supervisor.terminate();
    }
  }

  /**
   * {@link DaemonSupervisor#createDaemon(int)} is invoked while run() holds the read lock.
   * Requesting the termination from there must not try to acquire the write lock, because
   * a {@link java.util.concurrent.locks.ReentrantReadWriteLock} cannot be upgraded and the
   * supervisor would end up waiting for itself forever.
   */
  @Test(timeOut = AWAIT_TIMEOUT)
  public void testRequestTerminationFromCreateDaemon() {
    TestSupervisor supervisor = new TestSupervisor("selfterm", CHECK_INTERVAL, 0, 1);
    supervisor.terminateOnCreate = true;
    supervisor.start();
    // no terminate() in a finally: it would join() (and requestTermination() would block on the
    // write lock) if the supervisor ever deadlocks again. It is a daemon thread, so leaving it is safe.
    assertTrue(await(() -> !supervisor.isAlive()), "supervisor should have terminated");
  }

  /**
   * Shrinking must kill the surplus daemons, not just drop their slots: a dropped daemon would
   * keep running unsupervised and outlive even the supervisor. Ordinary threads cannot be killed
   * directly, so they are interrupted until they terminate.
   */
  @Test
  public void testDecreaseDaemonNumStopsPlainThreadDaemons() {
    TestSupervisor supervisor = new TestSupervisor("shrinkPlain", CHECK_INTERVAL, 0, 3);
    supervisor.plainDaemons = true;
    try {
      supervisor.start();
      assertTrue(await(() -> supervisor.getRunningDaemons().length == 3), "all daemons should start");
      List<Thread> started = new ArrayList<>(supervisor.plainCreated);
      assertEquals(started.size(), 3);

      supervisor.setDaemonNum(1);
      assertEquals(supervisor.getDaemonNum(), 1);

      assertTrue(await(() -> !started.get(1).isAlive() && !started.get(2).isAlive()),
                 "the surplus daemons should have been stopped");
      assertTrue(started.get(0).isAlive(), "the remaining daemon should keep running");
    }
    finally {
      supervisor.terminate();
    }
  }

  /**
   * The supervisor starts and clears daemons while holding only the read lock, i.e. concurrently
   * with the accessors of other threads. {@link DaemonSupervisor#getStartedDaemons()} must never
   * blow up or report null entries while that happens.
   */
  @Test(timeOut = AWAIT_TIMEOUT)
  public void testGetStartedDaemonsWhileDaemonsRestart() throws InterruptedException {
    TestSupervisor supervisor = new TestSupervisor("hammer", 1, 0, 8);
    supervisor.oneShotDaemons = true;   // daemons die at once and are restarted every round
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean stopReader = new AtomicBoolean();

    Thread reader = new Thread(() -> {
      try {
        while (!stopReader.get()) {
          for (Thread daemon : supervisor.getStartedDaemons()) {
            assertNotNull(daemon, "getStartedDaemons() must not contain null");
          }
        }
      }
      catch (Throwable tx) {
        failure.set(tx);
      }
    }, "getStartedDaemons-reader");
    reader.setDaemon(true);

    try {
      supervisor.start();
      reader.start();
      Thread.sleep(2000);
      stopReader.set(true);
      reader.join(AWAIT_TIMEOUT);
      Throwable thrown = failure.get();
      if (thrown != null) {
        fail("concurrent getStartedDaemons() failed: " + thrown, thrown);
      }
      assertTrue(supervisor.created.size() > 8, "daemons should have been restarted repeatedly");
    }
    finally {
      supervisor.terminate();
    }
  }
}

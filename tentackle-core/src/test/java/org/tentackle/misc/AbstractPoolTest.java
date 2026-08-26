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

package org.tentackle.misc;

import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link AbstractPool}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractPoolTest {

  /** generous upper bound for the tests that must not hang. */
  private static final long AWAIT_TIMEOUT = 30000;

  /** shutdown timeout used to detect a blocking shutdown(). */
  private static final long SHUTDOWN_TIMEOUT = 4000;


  @Test
  public void lendsAndReturnsTheSameResource() {
    TestPool pool = new TestPool();
    TestResource first = pool.get();
    pool.put(first);
    assertSame(pool.get(), first);
    assertEquals(pool.getCreatedCount(), 1);
  }

  /**
   * A pooled resource may die while sitting unused in the pool, without the pool being notified:
   * the peer closed it, a protocol-level idle timeout struck, the network went away, ...
   * Such a resource must be dropped and replaced instead of being handed out.
   */
  @Test
  public void replacesResourcesThatDiedWhileUnused() {
    TestPool pool = new TestPool();
    TestResource first = pool.get();
    pool.put(first);

    first.open = false;   // died silently while sitting in the pool

    TestResource second = pool.get();
    assertNotSame(second, first);
    assertTrue(second.isOpen());
    assertEquals(pool.getCreatedCount(), 2);
    assertEquals(first.getPoolId(), -1);    // removed from the pool
  }

  /**
   * All pooled resources may die at once, for example because they are multiplexed
   * over a single physical connection.
   */
  @Test
  public void replacesAllResourcesThatDiedWhileUnused() {
    TestPool pool = new TestPool();
    TestResource first = pool.get();
    TestResource second = pool.get();
    assertNotSame(first, second);
    pool.put(first);
    pool.put(second);

    first.open = false;
    second.open = false;

    TestResource third = pool.get();
    assertTrue(third.isOpen());
    assertNotSame(third, first);
    assertNotSame(third, second);
    assertEquals(pool.getCreatedCount(), 3);
  }

  /**
   * Resources that cannot be created in an open state must not make {@code get()} loop forever.
   */
  @Test
  public void givesUpIfResourcesAreNeverOpen() {
    TestPool pool = new TestPool();
    pool.createOpen = false;
    TentackleRuntimeException exception = expectThrows(TentackleRuntimeException.class, pool::get);
    assertTrue(exception.getMessage().contains("no usable resource"), exception.getMessage());
  }


  /**
   * {@link AbstractPool#isSlotRemovable} may be overridden to veto the removal of a slot.
   * Such a slot must be skipped: {@code monitor()} runs in the shared {@link PoolTimeoutThread}
   * and holds the pool's monitor, so looping on it would block the pool forever.
   */
  @Test(timeOut = AWAIT_TIMEOUT)
  public void monitorSkipsSlotsThatMustNotBeRemoved() {
    TestPool pool = new TestPool();
    TestResource resource = pool.get();
    pool.put(resource);           // sits in the pool's unused list now
    pool.slotsRemovable = false;  // the application vetoes removal

    pool.monitor();

    assertSame(pool.get(), resource, "vetoed slot must be kept in the pool");
  }


  /**
   * Interrupting the shared pool timeout thread must not stop it from sleeping.
   * Restoring the interrupt flag would make every subsequent sleep() throw at once,
   * turning the thread into a busy loop over all registered pools.
   */
  @Test(timeOut = AWAIT_TIMEOUT)
  public void interruptDoesNotMakeTheTimeoutThreadSpin() throws InterruptedException {
    TestPool pool = new TestPool("spin detection pool");
    pool.put(pool.get());     // first get() registers the pool with the timeout thread

    Thread timeoutThread = findPoolTimeoutThread();
    assertNotNull(timeoutThread, "pool timeout thread not started");

    timeoutThread.interrupt();

    int before = pool.monitorCount.get();
    Thread.sleep(500);
    int spins = pool.monitorCount.get() - before;

    // a sleeping thread monitors at most once in 500ms (the interval is 10s),
    // a spinning one does so thousands of times
    assertTrue(spins <= 5, "timeout thread is spinning: " + spins + " monitor() calls within 500ms");
  }


  /**
   * Closing a resource may well need the pool again. {@code shutdown()} must therefore not hold
   * the pool's monitor while waiting for the resources to be closed: the wait would block until
   * the timeout struck and leave the resource unclosed - and it would stall the shared
   * {@link PoolTimeoutThread}, which invokes this via {@code monitor()} for all pools.
   */
  @Test(timeOut = AWAIT_TIMEOUT)
  public void shutdownDoesNotBlockResourcesNeedingThePool() {
    TestPool pool = new TestPool("reentrant close pool");
    pool.setShutdownTimeout(SHUTDOWN_TIMEOUT);
    TestResource resource = pool.get();
    pool.put(resource);
    pool.reenterOnClose = true;

    long started = System.currentTimeMillis();
    pool.shutdown();
    long elapsed = System.currentTimeMillis() - started;

    assertTrue(pool.isShutdown());
    assertFalse(resource.isOpen(), "resource was not closed");
    assertTrue(elapsed < SHUTDOWN_TIMEOUT / 4,
               "shutdown() blocked the closing resource for " + elapsed + "ms");
  }


  /**
   * Pools are not required to have unique names, so the monitoring must not be keyed by name:
   * a colliding registration would silently drop a pool from the monitoring altogether.
   */
  @Test(timeOut = AWAIT_TIMEOUT)
  public void poolsWithTheSameNameAreBothMonitored() {
    TestPool first = new TestPool("same name pool");
    TestPool second = new TestPool("same name pool");
    first.put(first.get());     // the first get() registers with the timeout thread
    second.put(second.get());

    Thread timeoutThread = findPoolTimeoutThread();
    assertNotNull(timeoutThread, "pool timeout thread not started");

    // wake it up rather than waiting for its (10s) interval to elapse
    long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT / 2;
    while ((first.monitorCount.get() == 0 || second.monitorCount.get() == 0)
           && System.currentTimeMillis() < deadline) {
      timeoutThread.interrupt();
    }

    assertTrue(first.monitorCount.get() > 0, "the first pool was never monitored");
    assertTrue(second.monitorCount.get() > 0, "the second pool was never monitored");
  }


  /**
   * {@link AbstractPool#getSlotCountPerThread()} groups lent slots by the lending thread.
   */
  @Test
  public void getSlotCountPerThreadCountsLentSlotsByThread() {
    TestPool pool = new TestPool();
    pool.get();
    pool.get();   // second lease enlarges the pool by incrementSize=1

    List<Map.Entry<String, Integer>> slotCounts = pool.getSlotCountPerThread();

    assertEquals(slotCounts.size(), 1, "both slots were lent by the same (current) thread");
    Map.Entry<String, Integer> entry = slotCounts.get(0);
    assertTrue(entry.getKey().contains(Thread.currentThread().getName()), entry.getKey());
    assertEquals(entry.getValue().intValue(), 2);
  }

  /**
   * {@link AbstractPool#getSlotCountPerThread()} sorts by slot count, highest first.
   */
  @Test(timeOut = AWAIT_TIMEOUT)
  public void getSlotCountPerThreadSortsHighestCountFirst() throws InterruptedException {
    TestPool pool = new TestPool();
    pool.get();   // 1 slot lent by the current (main test) thread

    CountDownLatch leased = new CountDownLatch(1);
    CountDownLatch releaseLease = new CountDownLatch(1);
    Thread background = new Thread(() -> {
      pool.get();
      pool.get();   // 2 slots lent by the background thread
      leased.countDown();
      try {
        releaseLease.await();
      }
      catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }, "background-lease-thread");
    background.start();
    leased.await();

    try {
      List<Map.Entry<String, Integer>> slotCounts = pool.getSlotCountPerThread();

      assertEquals(slotCounts.size(), 2, "one entry per lending thread");
      assertEquals(slotCounts.get(0).getValue().intValue(), 2, "background thread leased the most slots");
      assertTrue(slotCounts.get(0).getKey().contains("background-lease-thread"), slotCounts.get(0).getKey());
      assertEquals(slotCounts.get(1).getValue().intValue(), 1);
      assertTrue(slotCounts.get(1).getKey().contains(Thread.currentThread().getName()), slotCounts.get(1).getKey());
    }
    finally {
      releaseLease.countDown();
      background.join(AWAIT_TIMEOUT);
    }
  }

  /**
   * Pool exhaustion is a resource leak candidate, so it must log which threads hold the slots.
   */
  @Test
  public void exhaustingThePoolLogsSlotCountPerThread() {
    TestPool pool = new TestPool("exhausted pool", 1);   // maximum size 1
    pool.get();   // the only slot the pool is allowed to create

    TentackleRuntimeException exception = expectThrows(TentackleRuntimeException.class, pool::get);

    assertTrue(exception.getMessage().contains("max. pool size"), exception.getMessage());
    assertEquals(pool.logSlotCountPerThreadInvocations.get(), 1);
  }


  /**
   * Finds the shared pool timeout thread.
   *
   * @return the thread, null if not running
   */
  private static Thread findPoolTimeoutThread() {
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      if (thread.getName().startsWith("Pool Timeout Thread")) {
        return thread;
      }
    }
    return null;
  }


  /**
   * A minimal poolable for testing.
   */
  private static class TestResource implements Poolable<TestResource> {

    private final int serial;
    private final TestPool owner;         // the creating pool, needed while closing
    private Pool<TestResource> pool;
    private int poolId;
    private volatile boolean open;

    private TestResource(TestPool owner, int serial, boolean open) {
      this.owner = owner;
      this.serial = serial;
      this.open = open;
    }

    @Override
    public void setPool(Pool<TestResource> pool) {
      this.pool = pool;
    }

    @Override
    public Pool<TestResource> getPool() {
      return pool;
    }

    @Override
    public void setPoolId(int poolId) {
      this.poolId = poolId;
    }

    @Override
    public int getPoolId() {
      return poolId;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void close() {
      if (owner.reenterOnClose) {
        owner.getSize();    // needs the pool's monitor
      }
      open = false;
    }

    @Override
    public String toString() {
      return "TestResource#" + serial;
    }
  }


  /**
   * A pool of {@link TestResource}s counting the created instances.
   */
  private static class TestPool extends AbstractPool<TestResource> {

    private final AtomicInteger monitorCount = new AtomicInteger();   // number of monitor() invocations
    private final AtomicInteger logSlotCountPerThreadInvocations = new AtomicInteger();
    private int createdCount;
    private boolean createOpen = true;
    private boolean slotsRemovable = true;
    private volatile boolean reenterOnClose;    // resources re-enter the pool while closing

    private TestPool() {
      this("test pool");
    }

    private TestPool(String name) {
      super(name, 1, 1, 1, 0, 0, 0);
    }

    private TestPool(String name, int maximumSize) {
      super(name, 1, 1, 1, maximumSize, 0, 0);
    }

    @Override
    public void logSlotCountPerThread(Logger.Level level) {
      logSlotCountPerThreadInvocations.incrementAndGet();
      super.logSlotCountPerThread(level);
    }

    private int getCreatedCount() {
      return createdCount;
    }

    @Override
    public void monitor() {
      // deliberately not synchronized: super.monitor() must be free to shut the pool down
      // without holding the pool's monitor
      monitorCount.incrementAndGet();
      super.monitor();
    }

    @Override
    protected boolean isSlotRemovable(PoolSlot<TestResource> slot) {
      return slotsRemovable && super.isSlotRemovable(slot);
    }

    @Override
    protected PoolSlot<TestResource> createSlot(int slotNumber) {
      return new AbstractPoolSlot<>(this, slotNumber, new TestResource(this, ++createdCount, createOpen)) {
        @Override
        protected TentackleRuntimeException createException(String msg) {
          return new TentackleRuntimeException(msg);
        }

        @Override
        public boolean isObsolete() {
          TestResource resource = getPoolable();
          return resource != null && !resource.isOpen();
        }
      };
    }

    @Override
    protected TentackleRuntimeException createException(String msg) {
      return new TentackleRuntimeException(msg);
    }

    @Override
    protected TentackleRuntimeException createException(TestResource resource, String msg) {
      return new TentackleRuntimeException(resource + ": " + msg);
    }
  }

}

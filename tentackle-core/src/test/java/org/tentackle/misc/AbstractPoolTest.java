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

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
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

    Thread timeoutThread = null;
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      if (thread.getName().startsWith("Pool Timeout Thread")) {
        timeoutThread = thread;
        break;
      }
    }
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
   * A minimal poolable for testing.
   */
  private static class TestResource implements Poolable<TestResource> {

    private final int serial;
    private Pool<TestResource> pool;
    private int poolId;
    private boolean open;

    private TestResource(int serial, boolean open) {
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
    private int createdCount;
    private boolean createOpen = true;
    private boolean slotsRemovable = true;

    private TestPool() {
      this("test pool");
    }

    private TestPool(String name) {
      super(name, 1, 1, 1, 0, 0, 0);
    }

    private int getCreatedCount() {
      return createdCount;
    }

    @Override
    public synchronized void monitor() {
      monitorCount.incrementAndGet();
      super.monitor();
    }

    @Override
    protected boolean isSlotRemovable(PoolSlot<TestResource> slot) {
      return slotsRemovable && super.isSlotRemovable(slot);
    }

    @Override
    protected PoolSlot<TestResource> createSlot(int slotNumber) {
      return new AbstractPoolSlot<>(this, slotNumber, new TestResource(++createdCount, createOpen)) {
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

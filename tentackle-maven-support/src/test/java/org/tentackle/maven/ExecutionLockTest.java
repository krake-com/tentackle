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

package org.tentackle.maven;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests that {@link AbstractTentackleMojo#getExecutionLock()} serializes concurrent executions.
 */
public class ExecutionLockTest {

  private static final int THREADS = 4;

  private Handler[] savedHandlers;

  @BeforeClass
  public void saveRootHandlers() {
    savedHandlers = Logger.getLogger("").getHandlers();
  }

  @AfterClass(alwaysRun = true)
  public void restoreRootHandlers() {
    Logger rootLogger = Logger.getLogger("");
    for (Handler handler : rootLogger.getHandlers()) {
      rootLogger.removeHandler(handler);
    }
    for (Handler handler : savedHandlers) {
      rootLogger.addHandler(handler);
    }
  }


  @Test
  public void executionsWithLockAreSerialized() throws InterruptedException {
    assertEquals(runConcurrently(new ReentrantLock()), 1);
  }

  @Test
  public void executionsWithoutLockRunConcurrently() throws InterruptedException {
    assertEquals(runConcurrently(null), THREADS);
  }

  @Test
  public void lockIsReleasedOnFailure() {
    ReentrantLock lock = new ReentrantLock();
    AbstractTentackleMojo mojo = new TestMojo(lock, null, null) {
      @Override
      public void prepareExecute() {
        throw new IllegalStateException("failed");
      }
    };
    try {
      mojo.execute();
    }
    catch (Exception ex) {
      assertTrue(ex instanceof IllegalStateException);
    }
    assertFalse(lock.isLocked());
  }


  /**
   * Runs mojos in parallel.
   *
   * @param lock the lock shared by all mojos, null if none
   * @return the maximum number of mojos executing at the same time
   */
  private int runConcurrently(Lock lock) throws InterruptedException {
    AtomicInteger active = new AtomicInteger();
    AtomicInteger maxActive = new AtomicInteger();
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      TestMojo mojo = new TestMojo(lock, active, maxActive);
      threads.add(new Thread(() -> {
        try {
          mojo.execute();
        }
        catch (Throwable t) {
          failures.add(t);
        }
      }));
    }
    threads.forEach(Thread::start);
    for (Thread thread : threads) {
      thread.join();
    }
    assertTrue(failures.isEmpty(), failures.toString());
    return maxActive.get();
  }


  /**
   * Mojo recording how many instances are within the execution at the same time.
   */
  private static class TestMojo extends AbstractTentackleMojo {

    private final Lock lock;
    private final AtomicInteger active;
    private final AtomicInteger maxActive;

    TestMojo(Lock lock, AtomicInteger active, AtomicInteger maxActive) {
      this.lock = lock;
      this.active = active;
      this.maxActive = maxActive;
    }

    @Override
    protected Lock getExecutionLock() {
      return lock;
    }

    @Override
    public void installJavaLoggingHandler() {
      // keep the test JVM's logging
    }

    @Override
    public void prepareExecute() {
      int count = active.incrementAndGet();
      maxActive.accumulateAndGet(count, Math::max);
      try {
        Thread.sleep(100);   // give the other threads a chance to enter
      }
      catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      active.decrementAndGet();
    }

    @Override
    protected boolean validate() {
      return false;   // skip executeImpl
    }

    @Override
    public void executeImpl() {
    }
  }
}

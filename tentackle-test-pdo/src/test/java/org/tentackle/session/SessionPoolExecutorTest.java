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

package org.tentackle.session;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.misc.Holder;
import org.tentackle.pdo.testng.AbstractPdoTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SessionPoolExecutorTest extends AbstractPdoTest {

  private static final int POOL_SIZE = 6;     // default conmgr's (local sessions) size is 8, -2 connections for main-thread and modtracker)
  private static final int TASK_NUM = 100;


  @Test
  public void testSequential() {
    Reporter.log("SessionPoolExecutorTest.testSequential <br>", true);

    SessionPool pool = SessionPoolFactory.getInstance().create("testpool", getSession(), 1,1,1, POOL_SIZE,0,0);
    SessionPooledExecutor executor = new SessionPooledExecutor(pool);
    AtomicInteger sum = new AtomicInteger();
    Set<RuntimeException> failures = ConcurrentHashMap.newKeySet();

    for (int i=0; i < TASK_NUM; i++) {
      executor.submit(this::run, v -> sum.incrementAndGet(), failures::add);
    }

    try {
      Thread.sleep(2000);   // 100 * 10 ms -> max 1000 ms, so 2000 should be enough
      executor.shutdown();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    if (!failures.isEmpty()) {
      Reporter.log(">>>>>>>>>>>>>>>>>>>>>>>> " + failures.size() + " exceptions! <<<<<<<<<<<<<<<<<<<<< <br>", true);
    }
    Assert.assertEquals(sum.get(), Integer.valueOf(TASK_NUM));
  }


  @Test
  public void testParallel() {
    Reporter.log("SessionPoolExecutorTest.testParallel <br>", true);

    SessionPool pool = SessionPoolFactory.getInstance().create("testpool", getSession(), 1,1,1, POOL_SIZE,0,0);
    SessionPooledExecutor executor = new SessionPooledExecutor(pool);

    List<Supplier<Integer>> runners = new ArrayList<>();
    for (int i=0; i < TASK_NUM; i++) {
      runners.add(this::run);
    }

    AtomicInteger sum = new AtomicInteger();
    Holder<Collection<RuntimeException>> failures = new Holder<>();

    executor.submit(runners, 0,
                    successMap -> successMap.values().forEach(v -> sum.incrementAndGet()),
                    failedMap -> failures.accept(failedMap.values())
    );

    try {
      Thread.sleep(2000);   // 100 * 10 ms -> max 1000 ms, so 2000 should be enough
      executor.shutdown();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    if (failures.get() != null) {
      Reporter.log(">>>>>>>>>>>>>>>>>>>>>>>> " + failures.get().size() + " exceptions! <<<<<<<<<<<<<<<<<<<<< <br>", true);
    }
    Assert.assertEquals(sum.get(), Integer.valueOf(TASK_NUM));
  }

  /**
   * A session pool of size one must not deadlock.
   * <p>
   * The waiter used to be submitted to the very executor that runs the tasks: with a single thread
   * it blocked that thread forever, so no task ever started and the latch was never counted down.
   */
  @Test(timeOut = 60000)
  public void testSingleSessionPoolDoesNotDeadlock() throws InterruptedException {
    Reporter.log("SessionPoolExecutorTest.testSingleSessionPoolDoesNotDeadlock <br>", true);

    SessionPool pool = SessionPoolFactory.getInstance().create("singlepool", getSession(), 1,1,1, 1,0,0);
    SessionPooledExecutor executor = new SessionPooledExecutor(pool);
    Assert.assertEquals(executor.getThreadPoolSize(), 1);

    List<Supplier<Integer>> runners = new ArrayList<>();
    for (int i=0; i < 5; i++) {
      runners.add(this::run);
    }

    AtomicInteger sum = new AtomicInteger();
    CountDownLatch done = new CountDownLatch(1);
    Holder<Collection<RuntimeException>> failures = new Holder<>();

    executor.submit(runners, 0,
                    successMap -> {
                      successMap.values().forEach(v -> sum.incrementAndGet());
                      done.countDown();
                    },
                    failedMap -> failures.accept(failedMap.values())
    );

    Assert.assertTrue(done.await(30, TimeUnit.SECONDS), "parallel execution on a single-session pool deadlocked");
    executor.shutdown();

    Assert.assertNull(failures.get());
    Assert.assertEquals(sum.get(), Integer.valueOf(runners.size()));
  }


  private Integer run() {
    try {
      Thread.sleep(10);
    }
    catch (InterruptedException e) {
      throw new InterruptedRuntimeException(e);
    }
    return 1;
  }

}

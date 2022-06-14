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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SessionPoolExecutorTest extends AbstractPdoTest {

  private static final int POOL_SIZE = 6;     // default conmgr's (local sessions) size is 8, -2 connections for main-thread and modtracker)
  private static final int TASK_NUM = 100;


  @Test
  public void testSequential() {
    SessionPool pool = SessionPoolFactory.getInstance().create("testpool", getSession(), 1,1,1, POOL_SIZE,0,0);
    SessionPooledExecutor executor = new SessionPooledExecutor(pool);
    AtomicInteger sum = new AtomicInteger();
    Set<RuntimeException> failures = ConcurrentHashMap.newKeySet();

    for (int i=0; i < TASK_NUM; i++) {
      executor.execute(this::run, v -> sum.incrementAndGet(), f -> failures.add(f));
    }

    try {
      Thread.sleep(2000);   // 100 * 10ms -> max 1000ms, so 2000 should be enough
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
    SessionPool pool = SessionPoolFactory.getInstance().create("testpool", getSession(), 1,1,1, POOL_SIZE,0,0);
    SessionPooledExecutor executor = new SessionPooledExecutor(pool);

    List<Supplier<Integer>> runners = new ArrayList<>();
    for (int i=0; i < TASK_NUM; i++) {
      runners.add(this::run);
    }

    AtomicInteger sum = new AtomicInteger();
    Holder<Collection<RuntimeException>> failures = new Holder<>();

    executor.execute(runners, 0,
                     successMap -> successMap.values().forEach(v -> sum.incrementAndGet()),
                     failedMap -> failures.accept(failedMap.values())
    );

    try {
      Thread.sleep(2000);   // 100 * 10ms -> max 1000ms, so 2000 should be enough
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

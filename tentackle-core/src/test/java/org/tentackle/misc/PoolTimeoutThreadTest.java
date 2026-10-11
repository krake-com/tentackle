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

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link PoolTimeoutThread}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PoolTimeoutThreadTest {

  /** Longer than two rounds of the timeout thread, which sleeps 10 seconds in between. */
  private static final long AWAIT_TIMEOUT = 30000;

  /**
   * A pool may fail to be monitored for a long time, for example a connection pool that cannot
   * re-establish its minimum size while its server is unreachable. Since all pools share the same
   * thread, that must not keep the pools registered after it from being monitored.
   */
  @Test(timeOut = AWAIT_TIMEOUT * 2)
  public void failingPoolDoesNotStarveTheOthers() throws InterruptedException {
    AtomicBoolean shutdown = new AtomicBoolean();
    CountDownLatch failed = new CountDownLatch(1);
    CountDownLatch monitored = new CountDownLatch(1);
    try {
      // the order matters: the pools are monitored in the order of their registration
      PoolTimeoutThread.register(pool("failing", shutdown, () -> {
        failed.countDown();
        throw new IllegalStateException("server unreachable");
      }));
      PoolTimeoutThread.register(pool("healthy", shutdown, monitored::countDown));

      assertTrue(failed.await(AWAIT_TIMEOUT, TimeUnit.MILLISECONDS), "failing pool was not monitored at all");
      assertTrue(monitored.await(AWAIT_TIMEOUT, TimeUnit.MILLISECONDS),
                 "pool registered after the failing one was not monitored");
    }
    finally {
      shutdown.set(true);   // unregisters both pools in the next round
    }
  }


  /**
   * Creates a pool that does nothing but being monitored.
   *
   * @param name the pool's name
   * @param shutdown tells whether the pool is shut down
   * @param monitor the monitoring operation
   * @return the pool
   */
  private static Pool<?> pool(String name, AtomicBoolean shutdown, Runnable monitor) {
    return (Pool<?>) Proxy.newProxyInstance(
      PoolTimeoutThreadTest.class.getClassLoader(), new Class<?>[] { Pool.class },
      (proxy, method, args) -> switch (method.getName()) {
        case "monitor" -> {
          monitor.run();
          yield null;
        }
        case "isShutdown" -> shutdown.get();
        case "toString" -> name;
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> throw new UnsupportedOperationException(method.getName());
      });
  }

}

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

package org.tentackle.io;

import org.tentackle.common.TentackleRuntimeException;

import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link Reconnector} and its interaction with a {@link ReconnectionPolicy}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ReconnectorTest {

  /**
   * A configurable policy over a plain string resource.
   */
  private static class TestPolicy implements ReconnectionPolicy<String> {

    private final boolean blocking;
    private final long millis;
    private final int failuresBeforeSuccess;   // number of reconnect() calls that throw before succeeding

    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile String reconnected;

    TestPolicy(boolean blocking, long millis, int failuresBeforeSuccess) {
      this.blocking = blocking;
      this.millis = millis;
      this.failuresBeforeSuccess = failuresBeforeSuccess;
    }

    @Override
    public boolean isBlocking() {
      return blocking;
    }

    @Override
    public long timeToReconnect() {
      return millis;
    }

    @Override
    public void reconnect(String resource) {
      int attempt = reconnectAttempts.incrementAndGet();
      if (attempt <= failuresBeforeSuccess) {
        throw new IllegalStateException("still down (attempt " + attempt + ")");
      }
      reconnected = resource;
      done.countDown();
    }
  }

  @Test
  public void testBlockingReconnectSucceeds() {
    TestPolicy policy = new TestPolicy(true, 1, 0);
    Reconnector.getInstance().submit(policy, "resource");
    // blocking submit returns only after a successful reconnect
    assertEquals(policy.reconnectAttempts.get(), 1);
    assertEquals(policy.reconnected, "resource");
  }

  @Test
  public void testBlockingReconnectRetriesUntilSuccess() {
    TestPolicy policy = new TestPolicy(true, 1, 3);   // fail three times, then succeed
    Reconnector.getInstance().submit(policy, "resource");
    assertEquals(policy.reconnectAttempts.get(), 4);
    assertEquals(policy.reconnected, "resource");
  }

  @Test
  public void testAbortWhenTimeToReconnectIsZero() {
    TestPolicy policy = new TestPolicy(true, 0, 0);
    assertThrows(TentackleRuntimeException.class,
                 () -> Reconnector.getInstance().submit(policy, "resource"));
    assertEquals(policy.reconnectAttempts.get(), 0);   // never attempted
  }

  @Test
  public void testAbortWhenTimeToReconnectIsNegative() {
    TestPolicy policy = new TestPolicy(true, -1, 0);
    assertThrows(TentackleRuntimeException.class,
                 () -> Reconnector.getInstance().submit(policy, "resource"));
    assertEquals(policy.reconnectAttempts.get(), 0);
  }

  @Test
  public void testNonBlockingReconnectRunsInBackground() throws InterruptedException {
    // regression: a non-blocking submit must actually run reconnect() on a background thread
    TestPolicy policy = new TestPolicy(false, 1, 0);
    Reconnector.getInstance().submit(policy, "resource");
    assertTrue(policy.done.await(5, TimeUnit.SECONDS), "non-blocking reconnect never ran");
    assertEquals(policy.reconnected, "resource");
  }

  @Test
  public void testSingletonIsStable() {
    assertEquals(Reconnector.getInstance(), Reconnector.getInstance());
  }
}

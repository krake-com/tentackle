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
import org.testng.annotations.Test;

import org.tentackle.common.EncryptedProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests the lazy loading of the session info's backend properties.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SessionInfoPropertiesTest {

  private static final int THREADS = 16;


  /**
   * A session info counting how often its properties were loaded.
   */
  @SuppressWarnings("serial")
  private static class CountingSessionInfo extends DefaultSessionInfo {

    private final AtomicInteger loadCount = new AtomicInteger();

    CountingSessionInfo() {
      super("hugo", "secret".toCharArray(), null);
    }

    @Override
    protected EncryptedProperties loadProperties(String propertiesName) {
      loadCount.incrementAndGet();
      try {
        Thread.sleep(5);    // widen the window a racy lazy load would lose
      }
      catch (InterruptedException ix) {
        Thread.currentThread().interrupt();
      }
      return new EncryptedProperties();
    }
  }


  @Test
  public void testPropertiesAreLoadedOnce() {
    CountingSessionInfo sessionInfo = new CountingSessionInfo();

    EncryptedProperties properties = sessionInfo.getProperties();
    Assert.assertNotNull(properties);
    Assert.assertSame(sessionInfo.getProperties(), properties);
    Assert.assertEquals(sessionInfo.loadCount.get(), 1);
  }


  @Test
  public void testConcurrentAccessYieldsTheSameProperties() throws Exception {
    CountingSessionInfo sessionInfo = new CountingSessionInfo();
    // by reference: two distinct EncryptedProperties are equal as long as both are empty
    Queue<EncryptedProperties> seen = new ConcurrentLinkedQueue<>();
    CyclicBarrier barrier = new CyclicBarrier(THREADS);
    List<Thread> threads = new ArrayList<>();

    for (int i = 0; i < THREADS; i++) {
      Thread thread = new Thread(() -> {
        try {
          barrier.await();    // all threads hit the lazy load at once
        }
        catch (Exception ex) {
          throw new IllegalStateException(ex);
        }
        seen.add(sessionInfo.getProperties());
      });
      thread.setDaemon(true);
      threads.add(thread);
      thread.start();
    }
    for (Thread thread: threads) {
      thread.join();
    }

    Assert.assertEquals(sessionInfo.loadCount.get(), 1, "the properties were loaded more than once");
    Assert.assertEquals(seen.size(), THREADS);
    for (EncryptedProperties properties: seen) {
      Assert.assertSame(properties, sessionInfo.getProperties(), "a thread got different properties");
    }
  }


  @Test
  public void testImmutableSessionInfoStillLoadsItsProperties() {
    CountingSessionInfo sessionInfo = new CountingSessionInfo();
    sessionInfo.setImmutable(true);

    // the lazy load is the one mutation an immutable session info still permits
    Assert.assertNotNull(sessionInfo.getProperties());
    Assert.assertEquals(sessionInfo.loadCount.get(), 1);
  }

}

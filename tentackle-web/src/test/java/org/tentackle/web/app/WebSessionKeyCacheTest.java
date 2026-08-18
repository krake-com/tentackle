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

package org.tentackle.web.app;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.session.SessionInfo;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.tentackle.web.app.WebTestSupport.await;
import static org.tentackle.web.app.WebTestSupport.cleanupThread;
import static org.tentackle.web.app.WebTestSupport.collectedReference;
import static org.tentackle.web.app.WebTestSupport.internalMap;
import static org.tentackle.web.app.WebTestSupport.sessionInfo;

/**
 * Tests for {@link WebSessionKeyCache}, the soft, self-cleaning map from a web framework's
 * session id to a Tentackle {@link SessionInfo}.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class WebSessionKeyCacheTest {

  private WebSessionKeyCache<String> cache;

  @BeforeMethod
  public void setUp() {
    // TestNG reuses one instance for all methods, so the cache must be recreated per test
    cache = new WebSessionKeyCache<>();
  }

  @AfterMethod
  public void tearDown() {
    // the cleanup thread is a daemon, but leaving one running per test would pile up
    if (cleanupThread(cache) != null) {
      cache.terminate();
    }
  }


  @Test
  public void storesAndRetrievesASessionInfo() {
    SessionInfo alice = sessionInfo("alice");
    cache.addSessionInfo("JSESSIONID-1", alice);
    assertSame(cache.getSessionInfo("JSESSIONID-1"), alice);
  }

  /**
   * A user logging in again on the same web session must not end up with two identities.
   */
  @Test
  public void replacesTheSessionInfoOfAKnownKey() {
    SessionInfo first = sessionInfo("alice");
    SessionInfo second = sessionInfo("alice");
    cache.addSessionInfo("JSESSIONID-1", first);
    cache.addSessionInfo("JSESSIONID-1", second);

    assertSame(cache.getSessionInfo("JSESSIONID-1"), second);
    assertEquals(internalMap(cache).size(), 1);
  }

  @Test
  public void returnsNullForAnUnknownKey() {
    assertNull(cache.getSessionInfo("never-seen"));
  }

  @Test
  public void removesAMapping() {
    cache.addSessionInfo("JSESSIONID-1", sessionInfo("alice"));
    cache.removeSessionInfo("JSESSIONID-1");
    assertNull(cache.getSessionInfo("JSESSIONID-1"));
    assertTrue(internalMap(cache).isEmpty());
  }

  /**
   * A logout listener may fire for a session the cache never saw.
   */
  @Test
  public void ignoresRemovalOfAnUnknownKey() {
    cache.addSessionInfo("JSESSIONID-1", sessionInfo("alice"));
    cache.removeSessionInfo("never-seen");
    assertEquals(internalMap(cache).size(), 1);
  }

  /**
   * The cache is only an accelerator: when the JVM reclaims a {@link SessionInfo} under memory
   * pressure, the lookup reports a miss and drops the dead entry, so the caller re-authenticates.
   */
  @Test
  public void forgetsACollectedSessionInfo() {
    internalMap(cache).put("JSESSIONID-1", collectedReference());
    assertNull(cache.getSessionInfo("JSESSIONID-1"));
    assertTrue(internalMap(cache).isEmpty(), "the dead entry must be dropped on lookup");
  }


  /**
   * Evicting a collected entry must not throw away a login that happened in the meantime.<br>
   * The window is between reading the reference and removing it: a request that finds the entry
   * dead is exactly the request that makes the caller re-authenticate and re-register the key, so
   * a blind {@code remove(key)} would delete the fresh mapping it just triggered.
   */
  @Test
  public void keepsAReplacedEntryWhenEvictingOnLookup() {
    SessionInfo relogin = sessionInfo("alice");
    internalMap(cache).put("JSESSIONID-1", reappearingReference("JSESSIONID-1", relogin));

    assertNull(cache.getSessionInfo("JSESSIONID-1"), "the dead reference must report a miss");
    assertSame(cache.getSessionInfo("JSESSIONID-1"), relogin, "the re-login must have survived");
  }

  /**
   * The reverse scan sweeps dead entries too, and must lose the same race.
   */
  @Test
  public void keepsAReplacedEntryWhenEvictingWhileScanning() {
    SessionInfo relogin = sessionInfo("alice");
    internalMap(cache).put("browser-1", reappearingReference("browser-1", relogin));

    cache.getSessionKeys(relogin);

    assertSame(cache.getSessionInfo("browser-1"), relogin, "the re-login must have survived");
  }

  /**
   * A soft reference that appears collected, but re-registers its key the moment it is read.<br>
   * This is the deterministic stand-in for a concurrent login slipping in between the read and the
   * eviction - the very window the conditional removal has to protect.
   *
   * @param sessionKey the key to re-register
   * @param relogin the session info of the re-login
   * @return the reference
   */
  private SoftReference<SessionInfo> reappearingReference(String sessionKey, SessionInfo relogin) {
    SoftReference<SessionInfo> reference = new SoftReference<>(sessionInfo("collected")) {
      @Override
      public SessionInfo get() {
        cache.addSessionInfo(sessionKey, relogin);    // the concurrent login wins the race ...
        return null;                                  // ... but this reader still sees a dead entry
      }
    };
    reference.clear();
    return reference;
  }


  @Test
  public void findsAllKeysOfASessionInfo() {
    SessionInfo alice = sessionInfo("alice");
    cache.addSessionInfo("browser-1", alice);
    cache.addSessionInfo("browser-2", alice);
    cache.addSessionInfo("browser-3", sessionInfo("bob"));

    assertEquals(Set.copyOf(cache.getSessionKeys(alice)), Set.of("browser-1", "browser-2"));
  }

  /**
   * Session infos compare by username, so "log this user out everywhere" works from any instance
   * describing that user - not only from the very object that was cached.
   */
  @Test
  public void matchesSessionInfosByUser() {
    cache.addSessionInfo("browser-1", sessionInfo("alice"));
    assertEquals(cache.getSessionKeys(sessionInfo("alice")), List.of("browser-1"));
  }

  @Test
  public void returnsAnEmptyCollectionForAnUnknownSessionInfo() {
    cache.addSessionInfo("browser-1", sessionInfo("alice"));
    Collection<String> keys = cache.getSessionKeys(sessionInfo("carol"));
    assertTrue(keys.isEmpty());
  }

  /**
   * The reverse scan is also an opportunity to sweep up dead entries.
   */
  @Test
  public void purgesCollectedEntriesWhileScanning() {
    SessionInfo alice = sessionInfo("alice");
    cache.addSessionInfo("browser-1", alice);
    internalMap(cache).put("browser-2", collectedReference());

    assertEquals(cache.getSessionKeys(alice), List.of("browser-1"));
    assertEquals(internalMap(cache).keySet(), Set.of("browser-1"));
  }

  @Test
  public void returnsAnIndependentCollectionOfKeys() {
    SessionInfo alice = sessionInfo("alice");
    cache.addSessionInfo("browser-1", alice);

    Collection<String> keys = cache.getSessionKeys(alice);
    keys.clear();
    assertEquals(cache.getSessionKeys(alice), List.of("browser-1"));
  }


  @Test
  public void rejectsANonPositiveCleanupInterval() {
    expectThrows(IllegalArgumentException.class, () -> cache.startup(0));
    expectThrows(IllegalArgumentException.class, () -> cache.startup(-1));
    assertNull(cleanupThread(cache), "no thread must have been created");
  }

  /**
   * The cleanup thread must never keep a shutting-down web container alive.
   */
  @Test
  public void runsTheCleanupAsADaemonThread() {
    cache.startup(50);
    Thread thread = cleanupThread(cache);
    assertTrue(thread.isDaemon());
    assertTrue(thread.isAlive());
  }

  @Test
  public void refusesToStartASecondCleanupThread() {
    cache.startup(50);
    Thread thread = cleanupThread(cache);
    expectThrows(TentackleRuntimeException.class, () -> cache.startup(50));
    assertSame(cleanupThread(cache), thread, "the running thread must be left alone");
  }

  /**
   * The backstop for web sessions the container never logged out: entries whose session info was
   * reclaimed are swept even if nobody ever looks them up again.
   */
  @Test
  public void evictsCollectedEntriesInTheBackground() {
    internalMap(cache).put("crashed-1", collectedReference());
    internalMap(cache).put("crashed-2", collectedReference());

    cache.startup(10);
    await(() -> internalMap(cache).isEmpty(), "the cleanup thread did not evict the collected entries");
  }

  /**
   * Sweeping must not touch sessions that are still alive.
   */
  @Test
  public void keepsLiveEntriesWhileEvicting() {
    SessionInfo alice = sessionInfo("alice");
    cache.addSessionInfo("browser-1", alice);
    internalMap(cache).put("crashed-1", collectedReference());

    cache.startup(10);
    await(() -> internalMap(cache).size() == 1, "the cleanup thread did not evict the collected entry");
    assertSame(cache.getSessionInfo("browser-1"), alice);
  }


  @Test
  public void refusesToTerminateACacheThatWasNeverStarted() {
    expectThrows(TentackleRuntimeException.class, cache::terminate);
  }

  /**
   * Since terminating an unstarted cache is an error, callers whose startup may have been cut
   * short - such as {@link WebApplication#cleanup()} - need to be able to ask first.
   */
  @Test
  public void reportsWhetherItIsRunning() {
    assertFalse(cache.isRunning(), "a cache that was never started is not running");
    cache.startup(50);
    assertTrue(cache.isRunning());
    cache.terminate();
    assertFalse(cache.isRunning(), "a terminated cache is not running anymore");
  }

  @Test
  public void terminatesTheCleanupThread() throws Exception {
    cache.startup(50);
    Thread thread = cleanupThread(cache);

    cache.terminate();

    assertFalse(thread.isAlive());
    assertNull(cleanupThread(cache));
  }

  /**
   * {@code terminate()} is called from the application's cleanup; calling it twice means the
   * application's lifecycle is broken and must not be swallowed.
   */
  @Test
  public void refusesToTerminateTwice() {
    cache.startup(50);
    cache.terminate();
    expectThrows(TentackleRuntimeException.class, cache::terminate);
  }

  /**
   * A container may stop and restart the application without discarding the cache.
   */
  @Test
  public void canBeRestartedAfterTermination() {
    cache.startup(50);
    cache.terminate();
    cache.startup(50);
    assertTrue(cleanupThread(cache).isAlive());
  }

  /**
   * Terminating must not throw away the mappings - only the sweeper stops.
   */
  @Test
  public void keepsItsContentAcrossTermination() {
    SessionInfo alice = sessionInfo("alice");
    cache.addSessionInfo("browser-1", alice);
    cache.startup(50);
    cache.terminate();
    assertSame(cache.getSessionInfo("browser-1"), alice);
  }


  /**
   * Web containers dispatch on a thread pool, so the cache is written to concurrently.
   */
  @Test
  public void survivesConcurrentUpdates() throws Exception {
    final int threadCount = 8;
    final int perThread = 200;
    SessionInfo alice = sessionInfo("alice");
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);
    List<Thread> threads = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      threads.add(Thread.ofVirtual().start(() -> {
        try {
          start.await();
          for (int n = 0; n < perThread; n++) {
            String key = "browser-" + index + '-' + n;
            cache.addSessionInfo(key, alice);
            cache.getSessionInfo(key);
          }
          done.countDown();
        }
        catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }));
    }

    cache.startup(10);    // sweep concurrently with the writers
    start.countDown();
    assertTrue(done.await(30, TimeUnit.SECONDS), "the writers did not finish");
    for (Thread thread : threads) {
      thread.join();
    }

    assertEquals(cache.getSessionKeys(alice).size(), threadCount * perThread);
  }

}

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

import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.SessionInfo;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.testng.Assert.fail;

/**
 * Common helpers for the web tests.
 */
public final class WebTestSupport {

  /** Maximum time to wait for the background cleanup to have happened. */
  private static final long AWAIT_TIMEOUT_MS = 10_000;

  /**
   * Creates a session info for the given user.<br>
   * Session infos compare by username, which is what the key cache's reverse lookup relies on.
   *
   * @param userName the username
   * @return the session info
   */
  public static SessionInfo sessionInfo(String userName) {
    return new DefaultSessionInfo(userName, null, "h2");
  }

  /**
   * Gets the cache's internal map.<br>
   * Needed to plant already-collected soft references: the tests must not depend on the JVM
   * actually deciding to clear a {@code SoftReference}, which only happens under memory pressure.
   *
   * @param cache the cache
   * @return the internal map
   * @param <T> the session key type
   */
  @SuppressWarnings("unchecked")
  public static <T> Map<T, SoftReference<SessionInfo>> internalMap(WebSessionKeyCache<T> cache) {
    return (Map<T, SoftReference<SessionInfo>>) read(cache, "sessionInfoMap");
  }

  /**
   * Gets the cache's cleanup thread.
   *
   * @param cache the cache
   * @return the cleanup thread, null if never started
   */
  public static Thread cleanupThread(WebSessionKeyCache<?> cache) {
    return (Thread) read(cache, "cleanupThread");
  }

  /**
   * Creates a soft reference that has already lost its referent, as the GC would leave it behind
   * after reclaiming a {@link SessionInfo} under memory pressure.
   *
   * @return the cleared reference
   */
  public static SoftReference<SessionInfo> collectedReference() {
    SoftReference<SessionInfo> reference = new SoftReference<>(sessionInfo("collected"));
    reference.clear();
    return reference;
  }

  /**
   * Waits until the given condition holds.
   *
   * @param condition the condition
   * @param message the message to fail with on timeout
   */
  public static void await(BooleanSupplier condition, String message) {
    long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline) {
        fail(message + " (timed out after " + AWAIT_TIMEOUT_MS + "ms)");
      }
      Thread.onSpinWait();
    }
  }


  private static Object read(Object target, String fieldName) {
    try {
      Field field = WebSessionKeyCache.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(target);
    }
    catch (ReflectiveOperationException ex) {
      throw new AssertionError("cannot read " + fieldName, ex);
    }
  }

  private WebTestSupport() {
  }

}

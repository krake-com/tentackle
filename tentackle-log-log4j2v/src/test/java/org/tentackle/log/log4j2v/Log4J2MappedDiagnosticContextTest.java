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

package org.tentackle.log.log4j2v;

import org.apache.logging.log4j.ThreadContext;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link Log4J2MappedDiagnosticContext}.
 * <p>
 * Unlike {@code DefaultMappedDiagnosticContext}, this class has no per-instance state at all: both
 * {@code new Log4J2MappedDiagnosticContext()} and {@link Log4J2MappedDiagnosticContext#getInstance()}
 * proxy the very same static {@link ThreadContext}, so every test cleans up via
 * {@link ThreadContext#clearMap()} rather than relying on instance isolation.
 * <p>
 * Two behaviors that differ from {@code DefaultMappedDiagnosticContext} were verified empirically
 * (not assumed) before being encoded as assertions here: {@link ThreadContext#getContext()} returns
 * a fresh snapshot copy on every call (not a live map), and log4j2's default context map is a plain,
 * non-inheritable {@code ThreadLocal} - a child thread does not see the parent's entries.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class Log4J2MappedDiagnosticContextTest {

  @AfterMethod
  private void tearDown() {
    ThreadContext.clearMap();
  }


  @Test
  public void testSingleton() {
    assertSame(Log4J2MappedDiagnosticContext.getInstance(), Log4J2MappedDiagnosticContext.getInstance());
  }

  @Test
  public void testNewInstanceIsNotIsolatedFromTheSingleton() {
    // unlike DefaultMappedDiagnosticContext, there is no instance-field thread-local here -
    // a fresh instance proxies the same static ThreadContext as the singleton
    Log4J2MappedDiagnosticContext mdc = new Log4J2MappedDiagnosticContext();
    mdc.put("user", "harald");
    assertEquals(Log4J2MappedDiagnosticContext.getInstance().get("user"), "harald");
  }

  @Test
  public void testPutAndGet() {
    Log4J2MappedDiagnosticContext mdc = Log4J2MappedDiagnosticContext.getInstance();
    assertNull(mdc.get("user"));
    mdc.put("user", "harald");
    assertEquals(mdc.get("user"), "harald");
    mdc.put("user", "someone");
    assertEquals(mdc.get("user"), "someone");
  }

  @Test
  public void testRemove() {
    Log4J2MappedDiagnosticContext mdc = Log4J2MappedDiagnosticContext.getInstance();
    mdc.put("user", "harald");
    mdc.remove("user");
    assertNull(mdc.get("user"));
    mdc.remove("user");                 // removing again is harmless
    assertTrue(mdc.getKeys().isEmpty());
  }

  @Test
  public void testClear() {
    Log4J2MappedDiagnosticContext mdc = Log4J2MappedDiagnosticContext.getInstance();
    mdc.put("user", "harald");
    mdc.put("host", "localhost");
    mdc.clear();
    assertTrue(mdc.getContext().isEmpty());
    assertNull(mdc.get("user"));
  }

  @Test
  public void testGetKeys() {
    Log4J2MappedDiagnosticContext mdc = Log4J2MappedDiagnosticContext.getInstance();
    mdc.put("user", "harald");
    mdc.put("host", "localhost");
    Set<String> keys = mdc.getKeys();
    assertEquals(keys.size(), 2);
    assertTrue(keys.contains("user"));
    assertTrue(keys.contains("host"));
  }

  @Test
  public void testContextIsASnapshotCopy() {
    // verified empirically: ThreadContext.getContext() returns a fresh copy on every call, and
    // mutating the returned map does not affect the real MDC (unlike DefaultMappedDiagnosticContext,
    // whose getContext() returns the same live Hashtable every time)
    Log4J2MappedDiagnosticContext mdc = Log4J2MappedDiagnosticContext.getInstance();
    mdc.put("user", "harald");
    Map<String, String> context = mdc.getContext();
    assertNotSame(mdc.getContext(), context);
    context.put("host", "localhost");
    assertNull(mdc.get("host"));
  }

  @Test
  public void testToString() {
    Log4J2MappedDiagnosticContext mdc = Log4J2MappedDiagnosticContext.getInstance();
    assertEquals(mdc.toString(), "");
    mdc.put("user", "harald");
    assertEquals(mdc.toString(), "user='harald'");
  }

  @Test
  public void testNullKeyIsRejectedButNullValueIsTolerated() {
    // verified empirically against the real log4j-core backend: ThreadContext throws NPE for a null
    // key (put/get/remove), same as the Hashtable-backed DefaultMappedDiagnosticContext, but - unlike
    // it - tolerates a null value (put("key", null) is a no-op, get("key") then returns null).
    // get(null)/remove(null) only throw once the backing map is non-empty (the empty-map fast path
    // returns null without dereferencing the key), so this seeds an entry first.
    Log4J2MappedDiagnosticContext mdc = Log4J2MappedDiagnosticContext.getInstance();
    assertThrows(NullPointerException.class, () -> mdc.put(null, "value"));
    mdc.put("user", "harald");
    assertThrows(NullPointerException.class, () -> mdc.get(null));
    assertThrows(NullPointerException.class, () -> mdc.remove(null));
    mdc.put("key", null);
    assertNull(mdc.get("key"));
  }

  @Test(timeOut = 10000)
  public void testChildThreadDoesNotInheritByDefault() throws InterruptedException {
    // verified empirically: log4j2's default context map is a plain, non-inheritable ThreadLocal
    // (DefaultThreadContextMap only becomes inheritable if isThreadContextMapInheritable is set at
    // JVM/first-use time, which cannot be flipped per-test in a shared surefire fork) - so this is
    // the opposite outcome of DefaultMappedDiagnosticContextTest#testChildThreadInheritsACopy
    Log4J2MappedDiagnosticContext mdc = Log4J2MappedDiagnosticContext.getInstance();
    mdc.put("inherited", "yes");

    CountDownLatch childDone = new CountDownLatch(1);
    AtomicReference<String> seen = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    Thread child = new Thread(() -> {
      try {
        seen.set(mdc.get("inherited"));
      }
      catch (Throwable t) {
        failure.set(t);
      }
      finally {
        childDone.countDown();
      }
    });
    child.start();
    childDone.await();
    child.join();

    assertNull(failure.get(), String.valueOf(failure.get()));
    assertNull(seen.get());
  }

}

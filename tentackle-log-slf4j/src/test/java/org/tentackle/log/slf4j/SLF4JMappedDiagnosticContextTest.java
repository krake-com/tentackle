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

package org.tentackle.log.slf4j;

import org.slf4j.MDC;

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
 * Tests for {@link SLF4JMappedDiagnosticContext}.
 * <p>
 * Like {@code Log4J2MappedDiagnosticContext}, this class has no per-instance state at all: both
 * {@code new SLF4JMappedDiagnosticContext()} and {@link SLF4JMappedDiagnosticContext#getInstance()}
 * proxy the very same static {@link MDC}, so every test cleans up via {@link MDC#clear()} rather than
 * relying on instance isolation.
 * <p>
 * The behaviors below were verified empirically against the module's test-scope binding (Logback's
 * {@code LogbackMDCAdapter}, see {@code tentackle-log-slf4j/pom.xml}) rather than assumed: unlike the
 * {@code Hashtable}-backed {@code DefaultMappedDiagnosticContext}, {@link MDC#getCopyOfContextMap()}
 * returns a fresh copy on every call, a null key is rejected but a null value is tolerated, and a
 * child thread does not inherit the parent's entries by default.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class SLF4JMappedDiagnosticContextTest {

  @AfterMethod
  private void tearDown() {
    MDC.clear();
  }


  @Test
  public void testSingleton() {
    assertSame(SLF4JMappedDiagnosticContext.getInstance(), SLF4JMappedDiagnosticContext.getInstance());
  }

  @Test
  public void testNewInstanceIsNotIsolatedFromTheSingleton() {
    // unlike DefaultMappedDiagnosticContext, there is no instance-field thread-local here -
    // a fresh instance proxies the same static MDC as the singleton
    SLF4JMappedDiagnosticContext mdc = new SLF4JMappedDiagnosticContext();
    mdc.put("user", "harald");
    assertEquals(SLF4JMappedDiagnosticContext.getInstance().get("user"), "harald");
  }

  @Test
  public void testPutAndGet() {
    SLF4JMappedDiagnosticContext mdc = SLF4JMappedDiagnosticContext.getInstance();
    assertNull(mdc.get("user"));
    mdc.put("user", "harald");
    assertEquals(mdc.get("user"), "harald");
    mdc.put("user", "someone");
    assertEquals(mdc.get("user"), "someone");
  }

  @Test
  public void testRemove() {
    SLF4JMappedDiagnosticContext mdc = SLF4JMappedDiagnosticContext.getInstance();
    mdc.put("user", "harald");
    mdc.remove("user");
    assertNull(mdc.get("user"));
    mdc.remove("user");                 // removing again is harmless
    assertTrue(mdc.getKeys().isEmpty());
  }

  @Test
  public void testClear() {
    SLF4JMappedDiagnosticContext mdc = SLF4JMappedDiagnosticContext.getInstance();
    mdc.put("user", "harald");
    mdc.put("host", "localhost");
    mdc.clear();
    assertTrue(mdc.getContext().isEmpty());
    assertNull(mdc.get("user"));
  }

  @Test
  public void testGetKeys() {
    SLF4JMappedDiagnosticContext mdc = SLF4JMappedDiagnosticContext.getInstance();
    mdc.put("user", "harald");
    mdc.put("host", "localhost");
    Set<String> keys = mdc.getKeys();
    assertEquals(keys.size(), 2);
    assertTrue(keys.contains("user"));
    assertTrue(keys.contains("host"));
  }

  @Test
  public void testContextIsASnapshotCopy() {
    // verified empirically: MDC.getCopyOfContextMap() returns a fresh copy on every call, and
    // mutating the returned map does not affect the real MDC (unlike DefaultMappedDiagnosticContext,
    // whose getContext() returns the same live Hashtable every time)
    SLF4JMappedDiagnosticContext mdc = SLF4JMappedDiagnosticContext.getInstance();
    mdc.put("user", "harald");
    Map<String, String> context = mdc.getContext();
    assertNotSame(mdc.getContext(), context);
    context.put("host", "localhost");
    assertNull(mdc.get("host"));
  }

  @Test
  public void testToString() {
    SLF4JMappedDiagnosticContext mdc = SLF4JMappedDiagnosticContext.getInstance();
    assertEquals(mdc.toString(), "");
    mdc.put("user", "harald");
    assertEquals(mdc.toString(), "user='harald'");
  }

  @Test
  public void testNullKeyIsRejectedButNullValueIsTolerated() {
    // verified empirically: unlike the Hashtable-backed DefaultMappedDiagnosticContext (which throws
    // NullPointerException for a null key), SLF4J's own MDC.put(null, ...) throws
    // IllegalArgumentException instead - but a null value is tolerated either way (put("key", null)
    // is accepted, get("key") then returns null)
    SLF4JMappedDiagnosticContext mdc = SLF4JMappedDiagnosticContext.getInstance();
    assertThrows(IllegalArgumentException.class, () -> mdc.put(null, "value"));
    mdc.put("key", null);
    assertNull(mdc.get("key"));
  }

  @Test(timeOut = 10000)
  public void testChildThreadDoesNotInheritByDefault() throws InterruptedException {
    // verified empirically: Logback's default MDCAdapter is backed by a plain, non-inheritable
    // ThreadLocal - so this is the opposite outcome of DefaultMappedDiagnosticContextTest#testChildThreadInheritsACopy
    SLF4JMappedDiagnosticContext mdc = SLF4JMappedDiagnosticContext.getInstance();
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

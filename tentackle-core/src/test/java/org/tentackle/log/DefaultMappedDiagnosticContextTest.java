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

package org.tentackle.log;

import org.testng.annotations.Test;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link DefaultMappedDiagnosticContext}.
 * <p>
 * Except for the singleton test, all tests work on a fresh instance: the thread-local map is an
 * instance field, so a new context is isolated from the singleton that {@link DefaultLogger} uses.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultMappedDiagnosticContextTest {

  @Test
  public void testSingleton() {
    assertSame(DefaultMappedDiagnosticContext.getInstance(), DefaultMappedDiagnosticContext.getInstance());
  }

  @Test
  public void testFreshInstanceIsIsolatedFromTheSingleton() {
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    assertNotSame(mdc, DefaultMappedDiagnosticContext.getInstance());
    assertNotSame(mdc.getContext(), DefaultMappedDiagnosticContext.getInstance().getContext());
  }

  @Test
  public void testPutAndGet() {
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    assertNull(mdc.get("user"));
    mdc.put("user", "harald");
    assertEquals(mdc.get("user"), "harald");
    mdc.put("user", "someone");
    assertEquals(mdc.get("user"), "someone");
  }

  @Test
  public void testRemove() {
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    mdc.put("user", "harald");
    mdc.remove("user");
    assertNull(mdc.get("user"));
    mdc.remove("user");                 // removing again is harmless
    assertTrue(mdc.getKeys().isEmpty());
  }

  @Test
  public void testClear() {
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    mdc.put("user", "harald");
    mdc.put("host", "localhost");
    mdc.clear();
    assertTrue(mdc.getContext().isEmpty());
    assertNull(mdc.get("user"));
  }

  @Test
  public void testGetKeys() {
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    mdc.put("user", "harald");
    mdc.put("host", "localhost");
    Set<String> keys = mdc.getKeys();
    assertEquals(keys.size(), 2);
    assertTrue(keys.contains("user"));
    assertTrue(keys.contains("host"));
  }

  @Test
  public void testContextIsTheLiveMap() {
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    mdc.put("user", "harald");
    Map<String, String> context = mdc.getContext();
    assertSame(mdc.getContext(), context);
    assertTrue(context instanceof Hashtable);
    context.put("host", "localhost");
    assertEquals(mdc.get("host"), "localhost");
  }

  @Test
  public void testToString() {
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    assertEquals(mdc.toString(), "");
    mdc.put("user", "harald");
    assertEquals(mdc.toString(), "user='harald'");
  }

  @Test
  public void testNullsAreRejected() {
    // the backing store is a Hashtable, which accepts neither null keys nor null values
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    assertThrows(NullPointerException.class, () -> mdc.put(null, "value"));
    assertThrows(NullPointerException.class, () -> mdc.put("key", null));
    assertThrows(NullPointerException.class, () -> mdc.get(null));
    assertThrows(NullPointerException.class, () -> mdc.remove(null));
  }

  @Test(timeOut = 10000)
  public void testChildThreadInheritsACopy() throws InterruptedException {
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    mdc.put("inherited", "yes");

    CountDownLatch childIsReady = new CountDownLatch(1);
    CountDownLatch parentHasWritten = new CountDownLatch(1);
    AtomicReference<String> seenInherited = new AtomicReference<>();
    AtomicReference<String> seenLateParentValue = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    Thread child = new Thread(() -> {
      try {
        seenInherited.set(mdc.get("inherited"));
        mdc.put("childOnly", "yes");
        childIsReady.countDown();
        parentHasWritten.await();
        seenLateParentValue.set(mdc.get("parentOnly"));
      }
      catch (Throwable t) {
        failure.set(t);
        childIsReady.countDown();
      }
    });
    child.start();
    childIsReady.await();
    mdc.put("parentOnly", "yes");
    parentHasWritten.countDown();
    child.join();

    assertNull(failure.get(), String.valueOf(failure.get()));
    assertEquals(seenInherited.get(), "yes");           // the child inherited the parent's entries
    assertNull(seenLateParentValue.get());              // but not what the parent wrote afterwards
    assertNull(mdc.get("childOnly"));                   // and the child's entries stay in the child
    assertFalse(mdc.getKeys().contains("childOnly"));
  }

  @Test(timeOut = 10000)
  public void testThreadStartedBeforeThePutInheritsNothing() throws InterruptedException {
    DefaultMappedDiagnosticContext mdc = new DefaultMappedDiagnosticContext();
    CountDownLatch parentHasWritten = new CountDownLatch(1);
    AtomicReference<String> seen = new AtomicReference<>("initial");
    AtomicReference<Throwable> failure = new AtomicReference<>();

    Thread child = new Thread(() -> {
      try {
        parentHasWritten.await();
        seen.set(mdc.get("late"));
      }
      catch (Throwable t) {
        failure.set(t);
      }
    });
    child.start();
    mdc.put("late", "yes");
    parentHasWritten.countDown();
    child.join();

    assertNull(failure.get(), String.valueOf(failure.get()));
    assertNull(seen.get());
  }

}

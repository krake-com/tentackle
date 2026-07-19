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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link NamedValue}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class NamedValueTest {

  @Test
  public void testGetUnsetReturnsNull() {
    NamedValue<String> value = new NamedValue<>();
    assertNull(value.get("missing"));
  }

  @Test
  public void testSetAndGet() {
    NamedValue<String> value = new NamedValue<>();
    value.set("a", "one");
    value.set("b", "two");
    assertEquals(value.get("a"), "one");
    assertEquals(value.get("b"), "two");
  }

  @Test
  public void testOverwrite() {
    NamedValue<Integer> value = new NamedValue<>();
    value.set("x", 1);
    value.set("x", 2);
    assertEquals(value.get("x").intValue(), 2);
  }

  @Test
  public void testSetNullValue() {
    NamedValue<String> value = new NamedValue<>();
    value.set("x", "present");
    value.set("x", null);
    assertNull(value.get("x"));
  }

  @Test
  public void testNamesReflectAccessedKeys() {
    NamedValue<String> value = new NamedValue<>();
    value.set("a", "1");
    value.set("b", "2");
    // a mere get creates the reference as well (computeIfAbsent)
    value.get("c");
    assertEquals(value.names().size(), 3);
    assertTrue(value.names().contains("a"));
    assertTrue(value.names().contains("b"));
    assertTrue(value.names().contains("c"));
  }

  @Test
  public void testNamesIsSnapshotCopy() {
    NamedValue<String> value = new NamedValue<>();
    value.set("a", "1");
    var names = value.names();
    value.set("b", "2");
    // the returned collection is a copy, unaffected by later changes
    assertEquals(names.size(), 1);
  }

  @Test
  public void testConcurrentSetIsThreadSafe() throws InterruptedException {
    final NamedValue<Integer> value = new NamedValue<>();
    final int threads = 50;
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(threads);
    final AtomicInteger errors = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
      final int id = i;
      new Thread(() -> {
        try {
          start.await();
          value.set("key" + id, id);
          if (value.get("key" + id) != id) {
            errors.incrementAndGet();
          }
        }
        catch (InterruptedException ix) {
          errors.incrementAndGet();
        }
        finally {
          done.countDown();
        }
      }).start();
    }

    start.countDown();
    done.await();
    assertEquals(errors.get(), 0);
    assertEquals(value.names().size(), threads);
  }
}

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

package org.tentackle.reflect;

import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link IndexedMethodCache}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class IndexedMethodCacheTest {

  @Test
  public void testAddAssignsSequentialIndexes() throws NoSuchMethodException {
    IndexedMethodCache cache = new IndexedMethodCache(String.class);
    assertSame(cache.getClazz(), String.class);

    Method length = String.class.getMethod("length");
    Method hashCode = String.class.getMethod("hashCode");

    assertEquals(cache.add(length), 0);
    assertEquals(cache.add(hashCode), 1);
    // re-adding the same method returns the same index
    assertEquals(cache.add(length), 0);

    assertSame(cache.get(0), length);
    assertSame(cache.get(1), hashCode);
    assertNull(cache.get(99));
  }

  @Test
  public void testRemove() throws NoSuchMethodException {
    IndexedMethodCache cache = new IndexedMethodCache(String.class);
    Method length = String.class.getMethod("length");
    Method hashCode = String.class.getMethod("hashCode");
    cache.add(length);    // 0
    cache.add(hashCode);  // 1

    assertSame(cache.remove(0), length);
    assertNull(cache.get(0));
    assertNull(cache.remove(0));   // already removed

    // re-adding after removal yields a fresh, monotonically increasing index
    assertEquals(cache.add(length), 2);
    assertSame(cache.get(2), length);
  }

  @Test
  public void testEqualsHashCodeAndToString() {
    IndexedMethodCache a = new IndexedMethodCache(String.class);
    IndexedMethodCache b = new IndexedMethodCache(String.class);
    IndexedMethodCache c = new IndexedMethodCache(Integer.class);

    assertEquals(a, b);   // equality is based on the class
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);
    assertTrue(a.toString().contains("String"));
  }
}

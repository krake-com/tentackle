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
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;

/**
 * Tests for {@link MethodCache}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MethodCacheTest {

  @Test
  public void testNameAndToString() {
    MethodCache cache = new MethodCache("myCache");
    assertEquals(cache.getName(), "myCache");
    assertEquals(cache.toString(), "myCache");
  }

  @Test
  public void testNullNameRejected() {
    assertThrows(NullPointerException.class, () -> new MethodCache(null));
  }

  @Test
  public void testAddGetRemoveNoParams() throws NoSuchMethodException {
    MethodCache cache = new MethodCache("c");
    Method length = String.class.getMethod("length");

    assertNull(cache.getMethod(String.class));
    assertNull(cache.addMethod(length, String.class));      // added -> returns null
    assertSame(cache.getMethod(String.class), length);

    Method hashCode = String.class.getMethod("hashCode");
    assertSame(cache.addMethod(hashCode, String.class), length);   // replaced -> returns old
    assertSame(cache.getMethod(String.class), hashCode);

    assertSame(cache.removeMethod(String.class), hashCode);
    assertNull(cache.getMethod(String.class));
    assertNull(cache.removeMethod(String.class));           // already gone
  }

  @Test
  public void testParameterTypesAreKeyed() throws NoSuchMethodException {
    MethodCache cache = new MethodCache("c");
    Method substring = String.class.getMethod("substring", int.class);
    cache.addMethod(substring, String.class, int.class);

    assertSame(cache.getMethod(String.class, int.class), substring);
    // different parameter types -> different key
    assertNull(cache.getMethod(String.class, long.class));
    assertNull(cache.getMethod(String.class));
  }
}

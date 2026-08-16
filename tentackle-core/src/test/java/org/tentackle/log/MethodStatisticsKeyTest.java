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

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link MethodStatisticsKey}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MethodStatisticsKeyTest {

  private static Method length() throws NoSuchMethodException {
    return String.class.getMethod("length");
  }

  private static Method isEmpty() throws NoSuchMethodException {
    return String.class.getMethod("isEmpty");
  }


  @Test
  public void testGetters() throws NoSuchMethodException {
    Method method = length();
    MethodStatisticsKey key = new MethodStatisticsKey(method, StringBuilder.class);
    assertSame(key.getMethod(), method);
    assertSame(key.getServicedClass(), StringBuilder.class);
  }

  @Test
  public void testServicedClassDefaultsToDeclaringClass() throws NoSuchMethodException {
    Method method = length();
    assertSame(new MethodStatisticsKey(method, null).getServicedClass(), String.class);
    assertSame(new MethodStatisticsKey(method).getServicedClass(), String.class);
  }

  @Test
  public void testShortConstructorEqualsExplicitNull() throws NoSuchMethodException {
    Method method = length();
    assertEquals(new MethodStatisticsKey(method), new MethodStatisticsKey(method, null));
    assertEquals(new MethodStatisticsKey(method).hashCode(), new MethodStatisticsKey(method, null).hashCode());
  }

  @Test
  public void testEqualKeys() throws NoSuchMethodException {
    MethodStatisticsKey key1 = new MethodStatisticsKey(length(), StringBuilder.class);
    MethodStatisticsKey key2 = new MethodStatisticsKey(length(), StringBuilder.class);
    assertEquals(key1, key2);
    assertEquals(key2, key1);
    assertEquals(key1.hashCode(), key2.hashCode());
  }

  @Test
  public void testDifferentMethodNotEqual() throws NoSuchMethodException {
    MethodStatisticsKey key1 = new MethodStatisticsKey(length());
    MethodStatisticsKey key2 = new MethodStatisticsKey(isEmpty());
    assertNotEquals(key1, key2);
  }

  @Test
  public void testDifferentServicedClassNotEqual() throws NoSuchMethodException {
    MethodStatisticsKey key1 = new MethodStatisticsKey(length(), StringBuilder.class);
    MethodStatisticsKey key2 = new MethodStatisticsKey(length(), StringBuffer.class);
    assertNotEquals(key1, key2);
  }

  @Test
  public void testExplicitDeclaringClassEqualsDefault() throws NoSuchMethodException {
    // an explicit serviced class equal to the declaring class must not create a distinct key
    MethodStatisticsKey key1 = new MethodStatisticsKey(length(), String.class);
    MethodStatisticsKey key2 = new MethodStatisticsKey(length());
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
  }

  @Test
  public void testEqualsItself() throws NoSuchMethodException {
    MethodStatisticsKey key = new MethodStatisticsKey(length());
    assertEquals(key, key);
  }

  @Test
  public void testEqualsNull() throws NoSuchMethodException {
    assertFalse(new MethodStatisticsKey(length()).equals(null));
  }

  @Test
  @SuppressWarnings("unlikely-arg-type")
  public void testEqualsForeignType() throws NoSuchMethodException {
    assertFalse(new MethodStatisticsKey(length()).equals("not a key"));
  }

  @Test
  public void testUsableAsMapKey() throws NoSuchMethodException {
    Set<MethodStatisticsKey> keys = new HashSet<>();
    assertTrue(keys.add(new MethodStatisticsKey(length())));
    assertFalse(keys.add(new MethodStatisticsKey(length())));
    assertTrue(keys.add(new MethodStatisticsKey(length(), StringBuilder.class)));
    assertTrue(keys.add(new MethodStatisticsKey(isEmpty())));
    assertEquals(keys.size(), 3);
  }

}

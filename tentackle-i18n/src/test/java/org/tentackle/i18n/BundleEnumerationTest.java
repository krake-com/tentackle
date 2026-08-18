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

package org.tentackle.i18n;

import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link BundleEnumeration}.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class BundleEnumerationTest {

  /**
   * Creates a set preserving the insertion order.
   *
   * @param keys the keys
   * @return the ordered set
   */
  private static Set<String> keys(String... keys) {
    return new LinkedHashSet<>(List.of(keys));
  }

  /**
   * Drains an enumeration into a list.
   *
   * @param enumeration the enumeration
   * @return the enumerated elements in order
   */
  private static List<String> drain(Enumeration<String> enumeration) {
    return Collections.list(enumeration);
  }


  @Test
  public void testEnumerateWithoutParent() {
    assertEquals(drain(new BundleEnumeration(keys("a", "b", "c"), null)), List.of("a", "b", "c"));
  }

  @Test
  public void testEnumerateOwnKeysBeforeParentKeys() {
    Enumeration<String> parent = Collections.enumeration(List.of("x", "y"));
    assertEquals(drain(new BundleEnumeration(keys("a", "b"), parent)), List.of("a", "b", "x", "y"));
  }

  @Test
  public void testParentKeysShadowedByOwnKeysAreSkipped() {
    Enumeration<String> parent = Collections.enumeration(List.of("a", "x", "b", "y"));
    assertEquals(drain(new BundleEnumeration(keys("a", "b"), parent)), List.of("a", "b", "x", "y"));
  }

  @Test
  public void testAllParentKeysShadowed() {
    Enumeration<String> parent = Collections.enumeration(List.of("a", "b"));
    assertEquals(drain(new BundleEnumeration(keys("a", "b"), parent)), List.of("a", "b"));
  }

  @Test
  public void testEmptyKeysWithParent() {
    Enumeration<String> parent = Collections.enumeration(List.of("x", "y"));
    assertEquals(drain(new BundleEnumeration(keys(), parent)), List.of("x", "y"));
  }

  @Test
  public void testEmptyEnumeration() {
    BundleEnumeration enumeration = new BundleEnumeration(keys(), null);
    assertFalse(enumeration.hasMoreElements());
    expectThrows(NoSuchElementException.class, enumeration::nextElement);
  }

  @Test
  public void testNextElementWithoutHasMoreElements() {
    BundleEnumeration enumeration = new BundleEnumeration(keys("a"), Collections.enumeration(List.of("x")));
    assertEquals(enumeration.nextElement(), "a");
    assertEquals(enumeration.nextElement(), "x");
    expectThrows(NoSuchElementException.class, enumeration::nextElement);
  }

  @Test
  public void testHasMoreElementsIsIdempotent() {
    BundleEnumeration enumeration = new BundleEnumeration(keys("a"), null);
    assertTrue(enumeration.hasMoreElements());
    assertTrue(enumeration.hasMoreElements());
    assertEquals(enumeration.nextElement(), "a");
    assertFalse(enumeration.hasMoreElements());
  }

}

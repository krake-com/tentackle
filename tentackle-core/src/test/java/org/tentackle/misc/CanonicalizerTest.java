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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link Canonicalizer}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CanonicalizerTest {

  @Test
  public void testFirstOccurrenceReturnedAsIs() {
    Canonicalizer<String> canon = new Canonicalizer<>();
    String first = new String("hello");
    assertSame(canon.canonicalize(first), first);
  }

  @Test
  public void testEqualObjectsShareReference() {
    Canonicalizer<String> canon = new Canonicalizer<>();
    String first = new String("hello");
    String second = new String("hello");
    assertSame(first, canon.canonicalize(first));
    // the second, equal-but-distinct instance is mapped to the first
    assertSame(first, canon.canonicalize(second));
  }

  @Test
  public void testNullCanonicalizesToNull() {
    assertNull(new Canonicalizer<String>().canonicalize(null));
  }

  @Test
  public void testGetObjects() {
    Canonicalizer<String> canon = new Canonicalizer<>();
    canon.canonicalize("a");
    canon.canonicalize("b");
    canon.canonicalize("a");
    assertEquals(canon.getObjects().size(), 2);
    assertTrue(canon.getObjects().contains("a"));
    assertTrue(canon.getObjects().contains("b"));
  }

  @Test
  public void testLimitClearsMap() {
    // limit 2: as soon as size exceeds the limit (> 2), the map is cleared before inserting
    Canonicalizer<Integer> canon = new Canonicalizer<>(false, 2);
    canon.canonicalize(1);
    canon.canonicalize(2);
    canon.canonicalize(3);   // size was 2, not > 2, so 3 is added -> size 3
    assertEquals(canon.getObjects().size(), 3);
    canon.canonicalize(4);   // size was 3, > 2, so map cleared, then 4 added
    assertEquals(canon.getObjects().size(), 1);
    assertTrue(canon.getObjects().contains(4));
  }

  @Test
  public void testUnlimitedNeverClears() {
    Canonicalizer<Integer> canon = new Canonicalizer<>(false, 0);
    for (int i = 0; i < 100; i++) {
      canon.canonicalize(i);
    }
    assertEquals(canon.getObjects().size(), 100);
  }

  @Test
  public void testThreadSafeVariantWorks() {
    Canonicalizer<String> canon = new Canonicalizer<>(true, 0);
    String first = new String("x");
    assertSame(first, canon.canonicalize(first));
    assertSame(first, canon.canonicalize(new String("x")));
  }
}

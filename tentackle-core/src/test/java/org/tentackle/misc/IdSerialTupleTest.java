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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link IdSerialTuple} and {@link IdSerialNameTuple}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class IdSerialTupleTest {

  @Test
  public void testGetters() {
    IdSerialTuple tuple = new IdSerialTuple(5, 3);
    assertEquals(tuple.getId(), 5);
    assertEquals(tuple.getSerial(), 3);
  }

  @Test
  public void testIsVirgin() {
    assertTrue(new IdSerialTuple(5, 0).isVirgin());
    assertFalse(new IdSerialTuple(5, 1).isVirgin());
    // even id 0 with serial 0 is virgin, virginity depends only on the serial
    assertTrue(new IdSerialTuple(0, 0).isVirgin());
  }

  @Test
  public void testToString() {
    assertEquals(new IdSerialTuple(5, 3).toString(), "[5/3]");
    assertEquals(new IdSerialTuple(5, 3).toGenericString(), "[5/3]");
  }

  @Test
  public void testEqualsIgnoresSerial() {
    IdSerialTuple a = new IdSerialTuple(5, 1);
    IdSerialTuple b = new IdSerialTuple(5, 99);
    IdSerialTuple c = new IdSerialTuple(6, 1);

    // equals and hashCode are based on the id only
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);
    assertNotEquals(a, null);
  }

  @Test
  public void testEqualsIsClassStrict() {
    IdSerialTuple base = new IdSerialTuple(5, 1);
    IdSerialNameTuple named = new IdSerialNameTuple(5, 1, "foo");
    // different classes are never equal even with identical id
    assertNotEquals(base, named);
    assertNotEquals(named, base);
  }

  @Test
  public void testCompareToOrdersById() {
    IdSerialTuple a = new IdSerialTuple(1, 100);
    IdSerialTuple b = new IdSerialTuple(2, 0);
    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(a) > 0);
    // compareTo only considers the id, so different serials still compare equal
    assertEquals(new IdSerialTuple(3, 1).compareTo(new IdSerialTuple(3, 2)), 0);
  }

  @Test
  public void testCompareToConsistentWithSorting() {
    List<IdSerialTuple> list = new ArrayList<>();
    list.add(new IdSerialTuple(3, 0));
    list.add(new IdSerialTuple(1, 0));
    list.add(new IdSerialTuple(2, 0));
    Collections.sort(list);
    assertEquals(list.get(0).getId(), 1);
    assertEquals(list.get(1).getId(), 2);
    assertEquals(list.get(2).getId(), 3);
  }

  @Test
  public void testClone() {
    IdSerialTuple original = new IdSerialTuple(5, 3);
    IdSerialTuple clone = original.clone();
    assertNotSame(clone, original);
    assertEquals(clone.getId(), 5);
    assertEquals(clone.getSerial(), 3);
    assertEquals(clone, original);
  }

  // ---- IdSerialNameTuple ----

  @Test
  public void testNamedGetters() {
    IdSerialNameTuple tuple = new IdSerialNameTuple(5, 3, "hello");
    assertEquals(tuple.getId(), 5);
    assertEquals(tuple.getSerial(), 3);
    assertEquals(tuple.getName(), "hello");
  }

  @Test
  public void testNamedToString() {
    assertEquals(new IdSerialNameTuple(5, 3, "hello").toString(), "[5/3/hello]");
  }

  @Test
  public void testNamedNullNameRejected() {
    assertThrows(NullPointerException.class, () -> new IdSerialNameTuple(5, 3, null));
  }

  @Test
  public void testNamedEqualsIgnoresNameAndSerial() {
    // equals is inherited from IdSerialTuple, so only the id matters,
    // even though the name is part of the value
    IdSerialNameTuple a = new IdSerialNameTuple(5, 1, "alpha");
    IdSerialNameTuple b = new IdSerialNameTuple(5, 2, "beta");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void testNamedIsVirgin() {
    assertTrue(new IdSerialNameTuple(5, 0, "x").isVirgin());
    assertFalse(new IdSerialNameTuple(5, 1, "x").isVirgin());
  }
}

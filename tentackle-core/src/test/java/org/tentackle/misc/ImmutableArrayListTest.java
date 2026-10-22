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

import org.tentackle.log.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link ImmutableArrayList}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ImmutableArrayListTest {

  private static ImmutableArrayList<String> sample() {
    ImmutableArrayList<String> list = new ImmutableArrayList<>();
    list.add("a");
    list.add("b");
    list.add("c");
    return list;
  }

  @Test
  public void testMutableByDefault() {
    ImmutableArrayList<String> list = new ImmutableArrayList<>();
    assertFalse(list.isImmutable());
    assertFalse(list.isFinallyImmutable());
    list.add("x");
    assertEquals(list, List.of("x"));
  }

  @Test
  public void testCollectionConstructor() {
    ImmutableArrayList<String> list = new ImmutableArrayList<>(List.of("a", "b"));
    assertEquals(list, List.of("a", "b"));
  }

  @Test
  public void testImmutableBlocksAdd() {
    ImmutableArrayList<String> list = sample();
    list.setImmutable(true);
    assertTrue(list.isImmutable());
    assertThrows(ImmutableException.class, () -> list.add("d"));
    assertThrows(ImmutableException.class, () -> list.addFirst("d"));
    assertThrows(ImmutableException.class, () -> list.addAll(List.of("d")));
    assertThrows(ImmutableException.class, () -> list.addAll(0, List.of("d")));
    // nothing changed
    assertEquals(list, List.of("a", "b", "c"));
  }

  @Test
  public void testImmutableBlocksRemove() {
    ImmutableArrayList<String> list = sample();
    list.setImmutable(true);
    assertThrows(ImmutableException.class, () -> list.remove("a"));
    assertThrows(ImmutableException.class, () -> list.removeFirst());
    assertEquals(list, List.of("a", "b", "c"));
  }

  /**
   * The bulk operations of {@link java.util.ArrayList} bypass {@code remove(int)} and {@code set(int, E)}.
   */
  @Test
  public void testImmutableBlocksBulkOperations() {
    ImmutableArrayList<String> list = sample();
    list.setImmutable(true);
    assertThrows(ImmutableException.class, () -> list.removeLast());
    assertThrows(ImmutableException.class, () -> list.removeAll(List.of("a")));
    assertThrows(ImmutableException.class, () -> list.removeIf("b"::equals));
    assertThrows(ImmutableException.class, list::clear);
    assertThrows(ImmutableException.class, () -> list.subList(0, 2).clear());
    assertThrows(ImmutableException.class, () -> list.replaceAll(String::toUpperCase));
    // nothing changed
    assertEquals(list, List.of("a", "b", "c"));
  }

  @Test
  public void testImmutableAllowsBulkOperationsWithoutEffect() {
    ImmutableArrayList<String> list = sample();
    list.setImmutable(true);
    assertFalse(list.removeAll(List.of("x")));
    assertFalse(list.removeIf("x"::equals));
    list.replaceAll(s -> s);    // same instances
    assertEquals(list, List.of("a", "b", "c"));
    ImmutableArrayList<String> empty = new ImmutableArrayList<>();
    empty.setImmutable(true);
    empty.clear();
  }

  @Test
  public void testSetSameInstanceAllowedWhenImmutable() {
    ImmutableArrayList<String> list = sample();
    String existing = list.get(1);
    list.setImmutable(true);
    // replacing with the exact same instance must not trigger the immutability check
    String previous = list.set(1, existing);
    assertEquals(previous, existing);
  }

  @Test
  public void testSetDifferentInstanceRejectedAndRolledBack() {
    ImmutableArrayList<String> list = sample();
    list.setImmutable(true);
    assertThrows(ImmutableException.class, () -> list.set(1, "different"));
    // the rejected set must have been rolled back
    assertEquals(list.get(1), "b");
    assertEquals(list, List.of("a", "b", "c"));
  }

  @Test
  public void testRetainAllRejectedWhenImmutable() {
    ImmutableArrayList<String> list = sample();
    list.setImmutable(true);
    assertThrows(ImmutableException.class, () -> list.retainAll(List.of("a")));
  }

  @Test
  public void testRetainAllNoRemovalDoesNotThrow() {
    ImmutableArrayList<String> list = sample();
    list.setImmutable(true);
    // nothing would be removed, so the mutability check is never reached
    assertFalse(list.retainAll(List.of("a", "b", "c")));
    assertEquals(list, List.of("a", "b", "c"));
  }

  @Test
  public void testToggleBackToMutable() {
    ImmutableArrayList<String> list = sample();
    list.setImmutable(true);
    list.setImmutable(false);
    assertFalse(list.isImmutable());
    list.add("d");
    assertEquals(list, List.of("a", "b", "c", "d"));
  }

  @Test
  public void testFinallyImmutableCannotBeReverted() {
    ImmutableArrayList<String> list = sample();
    list.setFinallyImmutable();
    assertTrue(list.isImmutable());
    assertTrue(list.isFinallyImmutable());
    assertThrows(ImmutableException.class, () -> list.setImmutable(false));
    // still immutable
    assertThrows(ImmutableException.class, () -> list.add("d"));
  }

  @Test
  public void testCloneIsMutableCopy() {
    ImmutableArrayList<String> list = sample();
    list.setFinallyImmutable();
    ImmutableArrayList<String> clone = list.clone();
    assertFalse(clone.isImmutable());
    assertFalse(clone.isFinallyImmutable());
    clone.add("d");
    assertEquals(clone, List.of("a", "b", "c", "d"));
    // original is untouched and still immutable
    assertEquals(list, List.of("a", "b", "c"));
    assertThrows(ImmutableException.class, () -> list.add("z"));
  }

  @Test
  public void testLoggingLevelSuppressesException() {
    ImmutableArrayList<String> list = sample();
    list.setImmutable(true);
    list.setImmutableLoggingLevel(Logger.Level.WARNING);
    assertEquals(list.getImmutableLoggingLevel(), Logger.Level.WARNING);
    // with a logging level set, the violation is logged instead of thrown, and the mutation proceeds
    list.add("d");
    assertEquals(list, List.of("a", "b", "c", "d"));
  }

  @Test
  public void testSimpleEqualsAndHashCode() {
    ImmutableArrayList<String> a = sample();
    ImmutableArrayList<String> b = sample();
    // default: content-based equality like ArrayList
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    a.setSimpleEqualsAndHashCode(true);
    b.setSimpleEqualsAndHashCode(true);
    assertTrue(a.isSimpleEqualsAndHashCode());
    // now equality is identity based
    assertEquals(a, a);
    assertNotEquals(a, b);
    // hashCode is stable for the same instance
    assertEquals(a.hashCode(), a.hashCode());
  }

  @Test
  public void testSetImmutableWithElements() {
    ImmutableArrayList<ImmutableArrayList<String>> outer = new ImmutableArrayList<>();
    ImmutableArrayList<String> inner = sample();
    outer.add(inner);
    // withElements=true cascades immutability to elements implementing Immutable
    outer.setImmutable(true, true);
    assertTrue(inner.isImmutable());
    assertThrows(ImmutableException.class, () -> inner.add("d"));
  }

  @Test
  public void testSetImmutableWithoutElements() {
    ImmutableArrayList<ImmutableArrayList<String>> outer = new ImmutableArrayList<>();
    ImmutableArrayList<String> inner = sample();
    outer.add(inner);
    outer.setImmutable(true, false);
    // elements are left mutable
    assertFalse(inner.isImmutable());
    inner.add("d");
    assertEquals(inner, List.of("a", "b", "c", "d"));
  }

  @Test
  public void testBulkRemovals() {
    ImmutableArrayList<Integer> list = new ImmutableArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
    assertTrue(list.removeAll(List.of(1, 3, 5)));
    assertEquals(list, List.of(0, 2, 4, 6, 7, 8, 9));
    assertFalse(list.removeAll(List.of(42)));
    assertTrue(list.retainAll(Set.of(0, 2, 4, 6)));
    assertEquals(list, List.of(0, 2, 4, 6));
    AtomicInteger evaluations = new AtomicInteger();
    assertTrue(list.removeIf(e -> {
      evaluations.incrementAndGet();
      return e > 3;
    }));
    assertEquals(list, List.of(0, 2));
    assertEquals(evaluations.get(), 4, "predicate must be evaluated once per element");
    assertFalse(list.removeIf(e -> false));
  }

  @Test
  public void testBulkRemovalsWhenImmutable() {
    ImmutableArrayList<Integer> list = new ImmutableArrayList<>(List.of(1, 2, 3));
    list.setImmutable(true);
    assertThrows(ImmutableException.class, () -> list.removeAll(List.of(2)));
    assertThrows(ImmutableException.class, () -> list.retainAll(List.of(2)));
    assertThrows(ImmutableException.class, () -> list.removeIf(e -> e == 2));
    assertEquals(list, List.of(1, 2, 3));
    // nothing to remove is not a modification
    assertFalse(list.removeAll(List.of(42)));
    assertFalse(list.retainAll(List.of(1, 2, 3)));
    assertFalse(list.removeIf(e -> false));
  }
}

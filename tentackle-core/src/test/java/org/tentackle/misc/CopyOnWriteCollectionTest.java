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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the copy-on-write semantics shared by {@link CopyOnWriteCollection},
 * exercised via {@link CopyOnWriteSet}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CopyOnWriteCollectionTest {

  private static CopyOnWriteSet<String> wrap(Set<String> backing) {
    return new CopyOnWriteSet<>(backing);
  }

  @Test
  public void testReadOperationsDoNotClone() {
    Set<String> backing = new HashSet<>(Set.of("a", "b", "c"));
    CopyOnWriteSet<String> cow = wrap(backing);
    assertFalse(cow.isCloned());
    assertEquals(cow.size(), 3);
    assertFalse(cow.isEmpty());
    assertTrue(cow.contains("a"));
    assertEquals(cow.toArray().length, 3);
    // reads must never trigger a clone
    assertFalse(cow.isCloned());
  }

  @Test
  public void testAddClonesAndLeavesOriginalUntouched() {
    Set<String> backing = new HashSet<>(Set.of("a", "b"));
    CopyOnWriteSet<String> cow = wrap(backing);
    assertTrue(cow.add("c"));
    assertTrue(cow.isCloned());
    assertTrue(cow.contains("c"));
    // original backing set is untouched
    assertEquals(backing.size(), 2);
    assertFalse(backing.contains("c"));
  }

  @Test
  public void testRemoveClones() {
    Set<String> backing = new HashSet<>(Set.of("a", "b"));
    CopyOnWriteSet<String> cow = wrap(backing);
    assertTrue(cow.remove("a"));
    assertTrue(cow.isCloned());
    assertEquals(backing.size(), 2);
  }

  @Test
  public void testClearClones() {
    Set<String> backing = new HashSet<>(Set.of("a", "b"));
    CopyOnWriteSet<String> cow = wrap(backing);
    cow.clear();
    assertTrue(cow.isCloned());
    assertTrue(cow.isEmpty());
    assertEquals(backing.size(), 2);
  }

  @Test
  public void testRemoveIfClones() {
    Set<String> backing = new HashSet<>(Set.of("a", "bb", "ccc"));
    CopyOnWriteSet<String> cow = wrap(backing);
    assertTrue(cow.removeIf(s -> s.length() > 1));
    assertTrue(cow.isCloned());
    assertEquals(cow.size(), 1);
    assertEquals(backing.size(), 3);
  }

  @Test
  public void testAddAllRemoveAllRetainAllClone() {
    CopyOnWriteSet<String> cow = wrap(new HashSet<>(Set.of("a", "b")));
    cow.addAll(List.of("c", "d"));
    assertTrue(cow.isCloned());
    assertTrue(cow.containsAll(List.of("a", "b", "c", "d")));

    CopyOnWriteSet<String> cow2 = wrap(new HashSet<>(Set.of("a", "b", "c")));
    cow2.removeAll(List.of("a"));
    assertTrue(cow2.isCloned());
    assertFalse(cow2.contains("a"));

    CopyOnWriteSet<String> cow3 = wrap(new HashSet<>(Set.of("a", "b", "c")));
    cow3.retainAll(List.of("a"));
    assertTrue(cow3.isCloned());
    assertEquals(cow3.size(), 1);
  }

  @Test
  public void testReadOnlyIteratorForbidsRemove() {
    CopyOnWriteSet<String> cow = wrap(new HashSet<>(Set.of("a", "b")));
    Iterator<String> iterator = cow.iterator();
    assertTrue(iterator.hasNext());
    iterator.next();
    assertThrows(UnsupportedOperationException.class, iterator::remove);
    // iterating without modifying must not have cloned
    assertFalse(cow.isCloned());
  }

  @Test
  public void testModifyingIteratorClonesAndAllowsRemove() {
    Set<String> backing = new HashSet<>(Set.of("a", "b", "c"));
    CopyOnWriteSet<String> cow = new CopyOnWriteSet<>(backing, true);
    Iterator<String> iterator = cow.iterator();
    // iteratorModifying=true clones eagerly on iterator()
    assertTrue(cow.isCloned());
    iterator.next();
    iterator.remove();
    assertEquals(cow.size(), 2);
    // original untouched
    assertEquals(backing.size(), 3);
  }

  @Test
  public void testDefaultConstructorIsEmpty() {
    CopyOnWriteSet<String> cow = new CopyOnWriteSet<>();
    assertTrue(cow.isEmpty());
    cow.add("x");
    assertEquals(cow.size(), 1);
  }

  @Test
  public void testNonCloneableRejected() {
    // List.of(...) returns an immutable collection that is not Cloneable
    assertThrows(IllegalArgumentException.class, () -> new CopyOnWriteCollection<>(List.of("a", "b")));
  }

  @Test
  public void testEqualsAndHashCodeDelegateToCollection() {
    Set<String> backing = new HashSet<>(Set.of("a", "b"));
    CopyOnWriteSet<String> cow = wrap(backing);
    // equals/hashCode delegate to the wrapped collection
    assertEquals(cow.hashCode(), backing.hashCode());
    assertEquals(backing, cow);
  }

  @Test
  public void testStreamReflectsContents() {
    CopyOnWriteSet<String> cow = wrap(new HashSet<>(Set.of("a", "bb", "ccc")));
    long count = cow.stream().filter(s -> s.length() >= 2).count();
    assertEquals(count, 2);
  }
}

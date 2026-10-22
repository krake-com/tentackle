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

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test for TrackedArrayList.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class TrackedArrayListTest {


  @Test
  public void testTracked() {

    final TrackedArrayList<Integer> list = new TrackedArrayList<>();
    TrackedArrayList<Integer> addedList = new TrackedArrayList<>();
    TrackedArrayList<Integer> removedList = new TrackedArrayList<>();

    TrackedListListener<Integer> listener = new TrackedListListener<>() {

      @Override
      public void added(Integer element) {
        addedList.add(element);
      }

      @Override
      public void removed(Integer element) {
        removedList.add(element);
      }
    };

    list.addListener(listener);

    addedList.addListener(new TrackedListListener<>() {

      @Override
      public void added(Integer element) {
        // 2nd round
        list.addIfAbsent(element);
      }

      @Override
      public void removed(Integer element) {
        // never called
        Assert.fail("how?");
      }
    });

    removedList.addListener(new TrackedListListener<>() {

      @Override
      public void added(Integer element) {
        list.remove(element);   // shouldn't loop
      }

      @Override
      public void removed(Integer element) {
        // never called
        Assert.fail("how?");
      }
    });

    Assert.assertTrue(list.isModified());
    list.setModified(false);
    Assert.assertFalse(list.isModified());

    for (int i = 9; i >= 0; i--) {
      list.add(i);
    }
    Assert.assertEquals(list.size(), 10);
    Assert.assertTrue(list.isModified());

    Collections.sort(list);
    Assert.assertEquals(list.size(), 10);
    Assert.assertTrue(list.getRemovedObjects().isEmpty());
    list.setModified(false);
    for (int i = 0; i < 4; i++) {
      list.remove(Integer.valueOf(i));
    }
    Assert.assertTrue(list.isModified());
    Assert.assertEquals(list.size(), 6);
    Assert.assertEquals(list.getRemovedObjects().size(), 4);
    for (int i = 0; i < 4; i++) {
      Assert.assertFalse(list.contains(i));
      Assert.assertTrue(list.getRemovedObjects().contains(i));
    }

    Assert.assertEquals(addedList.size(), 10);
    Assert.assertEquals(removedList.size(), 4);
    Assert.assertTrue(list.removeListener(listener));

    list.add(2);
    Assert.assertEquals(addedList.size(), 10);

    TrackedList<Integer> snapshot1 = list.createSnapshot();
    try {
      snapshot1.add(1234);
      Assert.fail("snapshot1 must be immutable");
    }
    catch (TentackleRuntimeException ex) {
      // ok
    }

    check1(list);

    Collection<Integer> col = new ArrayList<>();
    for (int i = 8; i < 12; i++) {
      col.add(i);
    }
    list.removeAll(col);

    TrackedList<Integer> snapshot2 = list.createSnapshot();

    check2(list);

    Assert.assertTrue(list.isModified());
    list.setModified(false);
    Assert.assertFalse(list.isModified());
    list.setModified(true);
    Assert.assertTrue(list.isModified());

    list.revertToSnapshot(snapshot2);
    check2(list);

    list.revertToSnapshot(snapshot1);
    check1(list);
    Assert.assertTrue(list.isModified());

    try {
      list.revertToSnapshot(snapshot2);
      Assert.fail("snapshot2 must be consumed already");
    }
    catch (TentackleRuntimeException ex) {
      // ok
    }

    try {
      list.revertToSnapshot(list.clone());
      Assert.fail("must not revert to non-snapshot");
    }
    catch (TentackleRuntimeException ex) {
      // ok
    }

    list.setModified(false);
    list.setImmutable(true);
    try {
      list.add(100);
      Assert.fail("must throw ImmutableException");
    }
    catch (ImmutableException imex) {
      // ok
    }

    list.setImmutableLoggingLevel(Logger.Level.WARNING);
    try {
      list.add(200);
      // ok, just logged
    }
    catch (ImmutableException imex) {
      Assert.fail("must not throw ImmutableException, just logging!");
    }

    list.setNullAllowed(false);
    try {
      list.add(null);
      Assert.fail("must throw NullPointerException");
    }
    catch (NullPointerException npe) {
      // ok
    }

    TrackedList<Integer> list2 = new TrackedArrayList<>();
    for (int i = 9; i >= 0; i--) {
      list2.add(i);
    }
    Assert.assertTrue(list2.removeIf(integer -> integer % 2 == 0));
    Assert.assertEquals(list2.size(), 5);
    Assert.assertEquals(list2.getRemovedObjects().size(), 5);
    Assert.assertEquals(list2.get(0), Integer.valueOf(9));
    Assert.assertEquals(list2.get(1), Integer.valueOf(7));
    Assert.assertEquals(list2.get(2), Integer.valueOf(5));
    Assert.assertEquals(list2.get(3), Integer.valueOf(3));
    Assert.assertEquals(list2.get(4), Integer.valueOf(1));

    List<Integer> subList = list2.subList(1, 3);
    Iterator<Integer> iterator = subList.iterator();
    Assert.assertEquals(iterator.next(), Integer.valueOf(7));
    iterator.remove();
    Assert.assertEquals(subList.get(0), Integer.valueOf(5));
    Assert.assertEquals(list2.size(), 4);
    Assert.assertEquals(list2.getRemovedObjects().size(), 6);
    subList.removeAll(new ArrayList<>(subList));
    Assert.assertEquals(list2.size(), 3);
    Assert.assertEquals(list2.getRemovedObjects().size(), 7);
    subList.add(7);
    subList.add(5);
    Assert.assertEquals(list2.size(), 5);
    Assert.assertEquals(list2.getRemovedObjects().size(), 5);
    subList.set(0, 77);
    Assert.assertEquals(list2.get(1), Integer.valueOf(77));
    Assert.assertEquals(list2.getRemovedObjects().size(), 6);
    subList.set(0, 7);
    Assert.assertEquals(list2.getRemovedObjects().size(), 6);
    Assert.assertEquals(list2.getRemovedObjects().toArray()[5], 77);
    Assert.assertEquals(list2.get(1), Integer.valueOf(7));
    subList.sort(Comparator.naturalOrder());
    Assert.assertEquals(list2.get(1), Integer.valueOf(5));
    Assert.assertEquals(list2.getRemovedObjects().size(), 6);
  }


  private void check1(TrackedArrayList<Integer> list) {
    Assert.assertEquals(list.size(), 7);
    Assert.assertEquals(list.getRemovedObjects().size(), 3);
    Assert.assertFalse(list.getRemovedObjects().contains(2));
    Assert.assertTrue(list.contains(2));
  }


  private void check2(TrackedArrayList<Integer> list) {
    Assert.assertEquals(list.size(), 5);
    Assert.assertEquals(list.getRemovedObjects().size(), 5);
    Assert.assertTrue(list.getRemovedObjects().contains(8));
    Assert.assertTrue(list.getRemovedObjects().contains(9));
    for (int i = 8; i < 12; i++) {
      Assert.assertFalse(list.contains(i));
    }
  }

  @Test
  public void testBulkRemovals() {
    TrackedArrayList<Integer> list = new TrackedArrayList<>();
    list.addAll(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
    List<Integer> removedEvents = new ArrayList<>();
    list.addListener(new TrackedListListener<>() {
      @Override
      public void added(Integer element) {
        // not of interest here
      }

      @Override
      public void removed(Integer element) {
        Assert.assertFalse(list.contains(element), "listener must be notified after the removal");
        removedEvents.add(element);
      }
    });

    Assert.assertTrue(list.removeAll(List.of(1, 3, 5)));
    Assert.assertEquals(list, List.of(0, 2, 4, 6, 7, 8, 9));
    Assert.assertEquals(removedEvents, List.of(1, 3, 5));

    Assert.assertTrue(list.retainAll(Set.of(0, 2, 4, 6)));
    Assert.assertEquals(list, List.of(0, 2, 4, 6));
    Assert.assertEquals(removedEvents, List.of(1, 3, 5, 7, 8, 9));

    AtomicInteger evaluations = new AtomicInteger();
    Assert.assertTrue(list.removeIf(e -> {
      evaluations.incrementAndGet();
      return e > 3;
    }));
    Assert.assertEquals(list, List.of(0, 2));
    Assert.assertEquals(evaluations.get(), 4, "predicate must be evaluated once per element");
    Assert.assertEquals(removedEvents, List.of(1, 3, 5, 7, 8, 9, 4, 6));
    Assert.assertEquals(new ArrayList<>(list.getRemovedObjects()), List.of(1, 3, 5, 7, 8, 9, 4, 6));
    Assert.assertTrue(list.isSomeRemoved());

    // nothing to remove
    Assert.assertFalse(list.removeAll(List.of(42)));
    Assert.assertFalse(list.retainAll(List.of(0, 2)));
    Assert.assertFalse(list.removeIf(e -> false));
    Assert.assertEquals(removedEvents.size(), 8);
  }

  @Test
  public void testBulkRemovalOfDuplicates() {
    TrackedArrayList<Integer> list = new TrackedArrayList<>();
    list.addAll(List.of(1, 2, 1, 3, 1));
    Assert.assertTrue(list.removeAll(List.of(1)));
    Assert.assertEquals(list, List.of(2, 3));
    Assert.assertEquals(new ArrayList<>(list.getRemovedObjects()), List.of(1));
  }

  @Test
  public void testBulkRemovalsWhenImmutable() {
    TrackedArrayList<Integer> list = new TrackedArrayList<>();
    list.addAll(List.of(1, 2, 3));
    list.setImmutable(true);
    Assert.assertThrows(ImmutableException.class, () -> list.removeAll(List.of(2)));
    Assert.assertThrows(ImmutableException.class, () -> list.retainAll(List.of(2)));
    Assert.assertThrows(ImmutableException.class, () -> list.removeIf(e -> e == 2));
    Assert.assertEquals(list, List.of(1, 2, 3));
    Assert.assertFalse(list.isSomeRemoved());
    Assert.assertFalse(list.removeIf(e -> false));
  }

}

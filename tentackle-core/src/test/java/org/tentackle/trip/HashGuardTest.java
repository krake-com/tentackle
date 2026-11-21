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

package org.tentackle.trip;

import org.testng.annotations.Test;

import org.tentackle.misc.TrackedArrayList;
import org.tentackle.misc.TrackedList;
import org.tentackle.trip.impl.DefaultTripStream;
import org.tentackle.trip.impl.EchoTransceiver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the hash guard, which applies without a read policy, i.e. to authenticated clients as well.
 * <p>
 * The payloads are built so that constructing them never hashes the shared graph:
 * a container is added to a hash set while it is still empty, and filled afterward.
 * Nothing may print them either, since {@code toString} is as exponential as {@code hashCode}.
 */
@SuppressWarnings({ "missing-explicit-ctor", "unchecked" })
public class HashGuardTest {

  /** A mutable bean hashed and compared by identity. */
  public static class Bean {
    public String name;
  }

  /** A record whose hash code depends on its component. */
  public record Holder(Object value) {}

  /** An entity hashed and compared by its ID, whatever it refers to. */
  @TripShallowHash
  public static class Entity {
    public long id;
    public Object ref;

    public Entity() {
    }

    Entity(long id, Object ref) {
      this.id = id;
      this.ref = ref;
    }

    @Override
    public final int hashCode() {
      return Long.hashCode(id);
    }

    @Override
    public final boolean equals(Object obj) {
      return obj != null && obj.getClass() == getClass() && ((Entity) obj).id == id;
    }
  }


  /**
   * Sends an object and reads it back without a read policy.
   */
  @SuppressWarnings("unchecked")
  private <T> T echo(T object) {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream out = transceiver.getStream();
    TripType<T> type = out.getType((Class<T>) object.getClass());
    type.write(object, out);
    transceiver.echo();
    TripStream in = transceiver.getStream();
    return type.read(in, in.getDeserializer().deserializeOpCode());
  }

  private void assertRefused(Object object) {
    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class, () -> echo(object));
    assertTrue(ex.getMessage().contains("within a hashed value"), ex.getMessage());
  }

  /**
   * Creates a list holding {@code fanOut} references to the same list, {@code levels} deep.
   */
  private List<Object> fanOut(int levels, int fanOut) {
    List<Object> list = new ArrayList<>(List.of("x"));
    for (int level = 0; level < levels; level++) {
      List<Object> next = new ArrayList<>(fanOut);
      for (int i = 0; i < fanOut; i++) {
        next.add(list);
      }
      list = next;
    }
    return list;
  }


  @Test(timeOut = 10000)
  void testSerialDosIsRefused() {
    // the classic payload: nested hash sets, each pair of sets sharing their children
    Set<Object> root = new HashSet<>();
    Set<Object> s1 = root;
    Set<Object> s2 = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      Set<Object> t1 = new HashSet<>();
      Set<Object> t2 = new HashSet<>();
      t1.add("foo");    // makes t1 != t2
      s1.add(t1);
      s1.add(t2);
      s2.add(t1);
      s2.add(t2);
      s1 = t1;
      s2 = t2;
    }
    assertRefused(root);
  }

  @Test(timeOut = 10000)
  void testFanOutIsRefused() {
    // only 3 levels deep, but hashing visits 10^9 lists
    List<Object> bomb = new ArrayList<>();
    Set<Object> set = new HashSet<>();
    set.add(bomb);    // still empty: hashed cheaply
    bomb.addAll(fanOut(3, 1000));
    assertRefused(set);
  }

  @Test(timeOut = 10000)
  void testEarlierBackReferenceIsRefused() {
    // the graph is read outside the set first, then referenced by the set's element
    List<Object> bomb = fanOut(3, 1000);
    Set<Object> set = new HashSet<>();
    List<Object> element = new ArrayList<>();
    set.add(element);   // still empty: hashed cheaply
    element.add(bomb);
    assertRefused(new ArrayList<>(List.of(bomb, set)));
  }

  @Test
  void testSharedMapKeyIsRefused() {
    List<String> shared = new ArrayList<>(List.of("a", "b"));
    Map<Object, String> map = new HashMap<>();
    map.put(shared, "v");
    assertRefused(new ArrayList<>(List.of(shared, map)));
  }

  @Test
  void testSharedMapValueIsAccepted() {
    // values are not hashed by put()
    List<String> shared = new ArrayList<>(List.of("a", "b"));
    Map<String, Object> map = new HashMap<>();
    map.put("k1", shared);
    map.put("k2", shared);
    List<Object> message = new ArrayList<>(List.of(shared, map));
    List<Object> result = echo(message);
    Map<String, Object> resultMap = (Map<String, Object>) result.get(1);
    assertSame(resultMap.get("k1"), result.get(0));
    assertSame(resultMap.get("k2"), result.get(0));
  }

  @Test
  void testSharedValueWithinHashedKeyIsRefused() {
    // a map used as a set element is hashed including its values
    List<String> shared = new ArrayList<>(List.of("a"));
    Map<String, Object> map = new HashMap<>();
    map.put("k", shared);
    Set<Object> set = new HashSet<>();
    set.add(map);
    assertRefused(new ArrayList<>(List.of(shared, set)));
  }

  @Test
  void testSelfContainingSetIsRefused() {
    Set<Object> set = new HashSet<>();
    set.add(set);   // hashed while empty
    assertRefused(set);
  }

  @Test
  void testSharedRecordComponentIsRefused() {
    List<String> shared = new ArrayList<>(List.of("a"));
    Set<Object> set = new LinkedHashSet<>();
    set.add(new Holder(shared));
    set.add(new Holder(List.of(shared)));
    assertRefused(set);
  }

  @Test
  void testImmutableSetIsGuarded() {
    List<String> shared = new ArrayList<>(List.of("a"));
    assertRefused(new ArrayList<>(List.of(shared, Set.of(shared))));
    assertRefused(new ArrayList<>(List.of(shared, Map.of(shared, "v"))));
  }

  @Test
  void testWrappedSetIsGuarded() {
    List<String> shared = new ArrayList<>(List.of("a"));
    Set<Object> set = new HashSet<>();
    set.add(shared);
    assertRefused(new ArrayList<>(List.of(shared, Collections.unmodifiableSet(set))));
  }

  @Test
  void testShareableValuesAreAccepted() {
    UUID uuid = UUID.randomUUID();
    String text = "shared text";
    Set<Object> set = new HashSet<>();
    Map<Object, Object> map = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      set.add(new ArrayList<>(List.of(i, uuid, text, TimeUnit.SECONDS)));
      List<Object> key = new ArrayList<>(List.of(i, uuid, text, TimeUnit.MINUTES));
      map.put(key, key);    // the value refers to the key, but values are not hashed
    }
    List<Object> message = new ArrayList<>(List.of(uuid, text, set, map));
    List<Object> result = echo(message);
    assertEquals(result, message);
  }

  @Test
  void testIdentityHashedObjectsAreAccepted() {
    // neither a bean nor an array hashes its contents
    Bean bean = new Bean();
    bean.name = "bean";
    Object[] array = { "a", new ArrayList<>() };
    Set<Object> set = new LinkedHashSet<>();
    set.add(new Holder(bean));
    set.add(new Holder(array));
    set.add(new Holder(List.of(bean, array)));
    List<Object> message = new ArrayList<>(List.of(bean, array, set));
    List<Object> result = echo(message);
    for (Object element : (Set<?>) result.get(2)) {
      Object value = ((Holder) element).value();
      assertTrue(value == result.get(0) || value == result.get(1) || value instanceof List<?>);
    }
  }

  @Test(timeOut = 10000)
  void testNonHashingContainersAreAccepted() {
    // sharing is fine as long as nothing hashes
    List<Object> shared = fanOut(3, 10);
    Map<Object, Object> identityMap = new IdentityHashMap<>();
    identityMap.put(shared, shared);
    Map<String, Object> treeMap = new TreeMap<>();
    treeMap.put("a", shared);
    treeMap.put("b", shared);
    List<Object> message = new ArrayList<>(List.of(shared, shared, identityMap, treeMap));
    List<Object> result = echo(message);
    assertSame(result.get(1), result.get(0));
    assertSame(((Map<?, ?>) result.get(3)).get("b"), result.get(0));
    assertSame(((Map<?, ?>) result.get(2)).keySet().iterator().next(), result.get(0));
  }

  @Test
  void testShallowHashedContentsMayBeShared() {
    // the hash code of an entity does not depend on what it refers to
    List<String> shared = new ArrayList<>(List.of("a", "b"));
    Set<Object> set = new HashSet<>();
    set.add(new Entity(1, shared));
    set.add(new Entity(2, new ArrayList<>(List.of(shared, shared))));
    List<Object> result = echo(new ArrayList<>(List.of(shared, set)));
    for (Object element : (Set<?>) result.get(1)) {
      Entity entity = (Entity) element;
      Object ref = entity.id == 1 ? entity.ref : ((List<?>) entity.ref).get(1);
      assertSame(ref, result.get(0));
    }
  }

  @Test
  void testShallowHashedObjectMayBeBackReferenced() {
    Entity entity = new Entity(1, new ArrayList<>(List.of("a")));
    Set<Object> set = new HashSet<>();
    set.add(new Holder(entity));
    set.add(entity);
    List<Object> result = echo(new ArrayList<>(List.of(entity, set)));
    assertEquals(((Set<?>) result.get(1)).size(), 2);
    for (Object element : (Set<?>) result.get(1)) {
      assertSame(element instanceof Holder holder ? holder.value() : element, result.get(0));
    }
  }

  @Test(timeOut = 10000)
  void testHashedValueWithinShallowHashedIsGuarded() {
    // the contents of an entity are not hashed by the outer set, but a set within them is still guarded
    List<Object> bomb = fanOut(3, 1000);
    List<Object> element = new ArrayList<>();
    Set<Object> inner = new HashSet<>();
    inner.add(element);   // still empty: hashed cheaply
    element.add(bomb);
    Set<Object> outer = new HashSet<>();
    outer.add(new Entity(1, new ArrayList<>(List.of(bomb, inner))));
    assertRefused(outer);
  }

  @Test
  void testTrackedListWithShallowHashedRemovedObjects() {
    // the removed objects of a tracked list are a hash set
    List<String> shared = new ArrayList<>(List.of("a"));
    TrackedList<Entity> list = new TrackedArrayList<>();
    list.add(new Entity(1, shared));
    list.add(new Entity(2, shared));
    list.remove(1);
    List<Object> result = echo(new ArrayList<>(List.of(shared, list)));
    TrackedList<Entity> resultList = (TrackedList<Entity>) result.get(1);
    assertSame(resultList.getFirst().ref, result.get(0));
    assertEquals(resultList.getRemovedObjects().size(), 1);
    Entity removed = resultList.getRemovedObjects().iterator().next();
    assertEquals(removed.id, 2);
    assertSame(removed.ref, result.get(0));

    // without the annotation, the removed objects are hashed including their contents
    TrackedList<Object> holderList = new TrackedArrayList<>();
    holderList.add(new Holder(shared));
    holderList.remove(0);
    assertRefused(new ArrayList<>(List.of(shared, holderList)));
  }

  @Test
  void testGuardCanBeDisabled() {
    List<String> shared = new ArrayList<>(List.of("a", "b"));
    Set<Object> set = new HashSet<>();
    set.add(shared);
    List<Object> message = new ArrayList<>(List.of(shared, set));
    DefaultTripStream.setHashGuardEnabled(false);
    try {
      List<Object> result = echo(message);
      assertSame(((Set<?>) result.get(1)).iterator().next(), result.get(0));
    }
    finally {
      DefaultTripStream.setHashGuardEnabled(true);
    }
  }

}

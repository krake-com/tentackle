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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.trip.impl.EchoTransceiver;
import org.tentackle.trip.impl.TestClass;
import org.tentackle.trip.impl.TestEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Verifies that the collections and maps shipped with the JDK survive a TRIP round trip.
 * <p>
 * Unlike {@link TripTypeTest}, every round trip runs on a <em>fresh</em> {@link EchoTransceiver}.
 * Sharing one transceiver across test methods makes the outcome depend on the execution order,
 * because a dictionary entry registered by an earlier test may mask a missing one in a later test.
 * <p>
 * The test is organized in three parts:
 * <ol>
 *   <li>the supported matrix: what round trips, and which semantics are retained,</li>
 *   <li>the known lossy conversions: what round trips, but arrives reconfigured,</li>
 *   <li>the unsupported types: what is rejected, and rejected loudly.</li>
 * </ol>
 */
@SuppressWarnings({ "unchecked", "rawtypes", "missing-explicit-ctor" })
public class JdkCollectionsTest {

  /**
   * A comparator that is serializable by TRIP.<br>
   * It must be a static (or top-level) class, since an inner class' constructor
   * would expect the enclosing instance as an argument.
   */
  private static class ReverseComparator implements Comparator<String>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Override
    public int compare(String o1, String o2) {
      return o2.compareTo(o1);
    }
  }

  private static final Comparator<String> REVERSE = new ReverseComparator();

  private static final List<String> ELEMENTS = List.of("one", "two", "three");
  private static final Map<String, Integer> ENTRIES = Map.of("one", 1, "two", 2, "three", 3);


  // ------------------------------------------------------------------------------------------
  // the supported matrix
  // ------------------------------------------------------------------------------------------

  /**
   * Collections with a defined iteration order, hence the order must survive as well.
   */
  @DataProvider(name = "orderedCollections")
  public Object[][] orderedCollections() {
    return new Object[][] {
      { "ArrayList",              new ArrayList<>(ELEMENTS) },
      { "LinkedList",             new LinkedList<>(ELEMENTS) },
      { "Vector",                 new Vector<>(ELEMENTS) },
      { "Stack",                  fill(new Stack<>()) },
      { "CopyOnWriteArrayList",   new CopyOnWriteArrayList<>(ELEMENTS) },
      { "LinkedHashSet",          new LinkedHashSet<>(ELEMENTS) },
      { "CopyOnWriteArraySet",    new CopyOnWriteArraySet<>(ELEMENTS) },
      { "TreeSet",                new TreeSet<>(ELEMENTS) },
      { "ConcurrentSkipListSet",  new ConcurrentSkipListSet<>(ELEMENTS) },
      { "EnumSet",                EnumSet.of(TestEnum.RED, TestEnum.BLUE) },
      { "ArrayDeque",             new ArrayDeque<>(ELEMENTS) },
      { "ConcurrentLinkedQueue",  new ConcurrentLinkedQueue<>(ELEMENTS) },
      { "ConcurrentLinkedDeque",  new ConcurrentLinkedDeque<>(ELEMENTS) },
      { "LinkedBlockingQueue",    new LinkedBlockingQueue<>(ELEMENTS) },
      { "LinkedBlockingDeque",    new LinkedBlockingDeque<>(ELEMENTS) },
      { "ArrayBlockingQueue",     new ArrayBlockingQueue<>(ELEMENTS.size(), false, ELEMENTS) },
      { "LinkedTransferQueue",    new LinkedTransferQueue<>(ELEMENTS) },
      { "List.of",                List.of("one", "two", "three") },
      { "List.of (single)",       List.of("one") },
      { "List.copyOf",            List.copyOf(ELEMENTS) },
      { "Stream.toList",          Stream.of("one", "two", "three").toList() },
      { "Arrays.asList",          Arrays.asList("one", "two", "three") },
      { "unmodifiableCollection", Collections.unmodifiableCollection(new ArrayList<>(ELEMENTS)) },
      { "unmodifiableList",       Collections.unmodifiableList(new ArrayList<>(ELEMENTS)) },
      { "unmodifiableSortedSet",  Collections.unmodifiableSortedSet(new TreeSet<>(ELEMENTS)) },
      { "unmodifiableNavigableSet", Collections.unmodifiableNavigableSet(new TreeSet<>(ELEMENTS)) },
      { "synchronizedCollection", Collections.synchronizedCollection(new ArrayList<>(ELEMENTS)) },
      { "synchronizedList",       Collections.synchronizedList(new ArrayList<>(ELEMENTS)) },
      { "synchronizedSortedSet",  Collections.synchronizedSortedSet(new TreeSet<>(ELEMENTS)) },
      { "synchronizedNavigableSet", Collections.synchronizedNavigableSet(new TreeSet<>(ELEMENTS)) },
      { "singletonList",          Collections.singletonList("one") },
      { "singleton",              Collections.singleton("one") },
      { "nCopies",                Collections.nCopies(3, "one") },
      { "emptyList",              Collections.emptyList() },
      { "emptySet",               Collections.emptySet() }
    };
  }

  /**
   * Collections without a defined iteration order, hence only the content is compared.
   */
  @DataProvider(name = "unorderedCollections")
  public Object[][] unorderedCollections() {
    return new Object[][] {
      { "HashSet",                new HashSet<>(ELEMENTS) },
      { "Set.of",                 Set.of("one", "two", "three") },
      { "Set.of (single)",        Set.of("one") },
      { "Set.copyOf",             Set.copyOf(ELEMENTS) },
      { "PriorityQueue",          new PriorityQueue<>(ELEMENTS) },
      { "PriorityBlockingQueue",  new PriorityBlockingQueue<>(ELEMENTS) },
      { "unmodifiableSet",        Collections.unmodifiableSet(new HashSet<>(ELEMENTS)) },
      { "synchronizedSet",        Collections.synchronizedSet(new HashSet<>(ELEMENTS)) },
      { "newSetFromMap",          fill(Collections.newSetFromMap(new HashMap<>())) },
      { "ConcurrentHashMap.newKeySet", fill(ConcurrentHashMap.newKeySet()) }
    };
  }

  @DataProvider(name = "maps")
  public Object[][] maps() {
    return new Object[][] {
      { "HashMap",                new HashMap<>(ENTRIES) },
      { "LinkedHashMap",          new LinkedHashMap<>(ENTRIES) },
      { "TreeMap",                new TreeMap<>(ENTRIES) },
      { "Hashtable",              new Hashtable<>(ENTRIES) },
      { "Properties",             properties() },
      { "ConcurrentHashMap",      new ConcurrentHashMap<>(ENTRIES) },
      { "ConcurrentSkipListMap",  new ConcurrentSkipListMap<>(ENTRIES) },
      { "IdentityHashMap",        new IdentityHashMap<>(ENTRIES) },
      { "WeakHashMap",            new WeakHashMap<>(ENTRIES) },
      { "EnumMap",                enumMap() },
      { "Map.of",                 Map.of("one", 1, "two", 2, "three", 3) },
      { "Map.of (single)",        Map.of("one", 1) },
      { "Map.copyOf",             Map.copyOf(ENTRIES) },
      { "unmodifiableMap",        Collections.unmodifiableMap(new HashMap<>(ENTRIES)) },
      { "unmodifiableSortedMap",  Collections.unmodifiableSortedMap(new TreeMap<>(ENTRIES)) },
      { "unmodifiableNavigableMap", Collections.unmodifiableNavigableMap(new TreeMap<>(ENTRIES)) },
      { "synchronizedMap",        Collections.synchronizedMap(new HashMap<>(ENTRIES)) },
      { "synchronizedSortedMap",  Collections.synchronizedSortedMap(new TreeMap<>(ENTRIES)) },
      { "synchronizedNavigableMap", Collections.synchronizedNavigableMap(new TreeMap<>(ENTRIES)) },
      { "singletonMap",           Collections.singletonMap("one", 1) },
      { "emptyMap",               Collections.emptyMap() }
    };
  }

  @Test(dataProvider = "orderedCollections")
  void testOrderedCollection(String name, Collection<?> collection) {
    Collection<?> tunneled = roundTrip(collection);
    assertNotNull(tunneled, name);
    assertEquals(tunneled.getClass(), collection.getClass(), name);
    assertEquals(new ArrayList<>(tunneled), new ArrayList<>(collection), name);
  }

  @Test(dataProvider = "unorderedCollections")
  void testUnorderedCollection(String name, Collection<?> collection) {
    Collection<?> tunneled = roundTrip(collection);
    assertNotNull(tunneled, name);
    assertEquals(tunneled.getClass(), collection.getClass(), name);
    assertEquals(sorted(tunneled), sorted(collection), name);
  }

  @Test(dataProvider = "maps")
  void testMap(String name, Map<?, ?> map) {
    Map<?, ?> tunneled = roundTrip(map);
    assertNotNull(tunneled, name);
    assertEquals(tunneled.getClass(), map.getClass(), name);
    assertEquals(entries(tunneled), entries(map), name);
  }

  /**
   * All collections and maps must round trip when empty as well.<br>
   * Since the size is only a capacity hint, an empty collection exercises another code path.
   */
  @Test
  void testEmptyCollectionsAndMaps() {
    for (Object[] args : allCollections()) {
      Collection<?> empty = emptyLike((Collection<?>) args[1]);
      if (empty != null) {
        Collection<?> tunneled = roundTrip(empty);
        assertEquals(tunneled.getClass(), empty.getClass(), (String) args[0]);
        assertTrue(tunneled.isEmpty(), (String) args[0]);
      }
    }
    for (Object[] args : maps()) {
      Map<?, ?> empty = emptyLike((Map<?, ?>) args[1]);
      if (empty != null) {
        Map<?, ?> tunneled = roundTrip(empty);
        assertEquals(tunneled.getClass(), empty.getClass(), (String) args[0]);
        assertTrue(tunneled.isEmpty(), (String) args[0]);
      }
    }
  }

  /**
   * Null elements, keys and values are legal for most JDK collections and must survive.
   */
  @Test
  void testNullElementsKeysAndValues() {
    List<String> list = new ArrayList<>(Arrays.asList("one", null, "two", null));
    assertEquals(roundTrip(list), list);

    List<String> linked = new LinkedList<>(Arrays.asList(null, "one", null));
    assertEquals(roundTrip(linked), linked);

    List<String> unmodifiable = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("one", null)));
    assertEquals(roundTrip(unmodifiable), unmodifiable);

    Set<String> set = new LinkedHashSet<>(Arrays.asList("one", null, "two"));
    assertEquals(roundTrip(set), set);

    Map<String, Integer> map = new HashMap<>();
    map.put("one", 1);
    map.put(null, 0);         // null key
    map.put("null", null);    // null value
    assertEquals(roundTrip(map), map);

    EnumMap<TestEnum, String> enumMap = new EnumMap<>(TestEnum.class);
    enumMap.put(TestEnum.RED, null);
    assertEquals(roundTrip(enumMap), enumMap);

    // the whole collection/map being null
    assertNull(roundTrip(null, TripFactory.getInstance().getType(ArrayList.class)));
    assertNull(roundTrip(null, TripFactory.getInstance().getType(HashMap.class)));
  }

  /**
   * The insertion order of the linked collections is part of their contract.
   */
  @Test
  void testInsertionOrderIsRetained() {
    LinkedHashSet<String> set = new LinkedHashSet<>(List.of("zulu", "alpha", "mike"));
    assertEquals(new ArrayList<>(roundTrip(set)), List.of("zulu", "alpha", "mike"));

    LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
    map.put("zulu", 1);
    map.put("alpha", 2);
    map.put("mike", 3);
    assertEquals(new ArrayList<>(roundTrip(map).keySet()), List.of("zulu", "alpha", "mike"));

    Deque<String> deque = new ArrayDeque<>(List.of("zulu", "alpha", "mike"));
    assertEquals(new ArrayList<>(roundTrip(deque)), List.of("zulu", "alpha", "mike"));

    Stack<String> stack = new Stack<>();
    stack.push("zulu");
    stack.push("alpha");
    assertEquals(roundTrip(stack).pop(), "alpha");
  }

  /**
   * The comparator of the sorted collections and of their {@code Collections} wrappers
   * is serialized along with the elements. Without it, the receiver would silently
   * re-sort by natural ordering, or fail altogether for non-comparable elements.
   */
  @Test
  void testComparatorIsRetained() {
    TreeSet<String> treeSet = new TreeSet<>(REVERSE);
    treeSet.addAll(ELEMENTS);
    TreeSet<String> tunneledSet = roundTrip(treeSet);
    assertNotNull(tunneledSet.comparator());
    assertEquals(new ArrayList<>(tunneledSet), new ArrayList<>(treeSet));

    TreeMap<String, Integer> treeMap = new TreeMap<>(REVERSE);
    treeMap.putAll(ENTRIES);
    TreeMap<String, Integer> tunneledMap = roundTrip(treeMap);
    assertNotNull(tunneledMap.comparator());
    assertEquals(new ArrayList<>(tunneledMap.keySet()), new ArrayList<>(treeMap.keySet()));

    ConcurrentSkipListSet<String> skipListSet = new ConcurrentSkipListSet<>(REVERSE);
    skipListSet.addAll(ELEMENTS);
    ConcurrentSkipListSet<String> tunneledSkipListSet = roundTrip(skipListSet);
    assertNotNull(tunneledSkipListSet.comparator());
    assertEquals(new ArrayList<>(tunneledSkipListSet), new ArrayList<>(skipListSet));

    ConcurrentSkipListMap<String, Integer> skipListMap = new ConcurrentSkipListMap<>(REVERSE);
    skipListMap.putAll(ENTRIES);
    ConcurrentSkipListMap<String, Integer> tunneledSkipListMap = roundTrip(skipListMap);
    assertNotNull(tunneledSkipListMap.comparator());
    assertEquals(new ArrayList<>(tunneledSkipListMap.keySet()), new ArrayList<>(skipListMap.keySet()));

    SortedSet<String> sortedSet = Collections.unmodifiableSortedSet(treeSet);
    SortedSet<String> tunneledSortedSet = roundTrip(sortedSet);
    assertNotNull(tunneledSortedSet.comparator());
    assertEquals(new ArrayList<>(tunneledSortedSet), new ArrayList<>(sortedSet));

    NavigableSet<String> navigableSet = Collections.synchronizedNavigableSet(treeSet);
    NavigableSet<String> tunneledNavigableSet = roundTrip(navigableSet);
    assertNotNull(tunneledNavigableSet.comparator());
    assertEquals(new ArrayList<>(tunneledNavigableSet), new ArrayList<>(navigableSet));

    SortedMap<String, Integer> sortedMap = Collections.unmodifiableSortedMap(treeMap);
    SortedMap<String, Integer> tunneledSortedMap = roundTrip(sortedMap);
    assertNotNull(tunneledSortedMap.comparator());
    assertEquals(new ArrayList<>(tunneledSortedMap.keySet()), new ArrayList<>(sortedMap.keySet()));

    NavigableMap<String, Integer> navigableMap = Collections.synchronizedNavigableMap(treeMap);
    NavigableMap<String, Integer> tunneledNavigableMap = roundTrip(navigableMap);
    assertNotNull(tunneledNavigableMap.comparator());
    assertEquals(new ArrayList<>(tunneledNavigableMap.keySet()), new ArrayList<>(navigableMap.keySet()));
  }

  /**
   * An immutable collection must not arrive as a modifiable one.
   */
  @Test
  void testImmutabilityIsRetained() {
    assertUnmodifiable(List.of("one", "two"));
    assertUnmodifiable(Set.of("one", "two"));
    assertUnmodifiable(List.copyOf(ELEMENTS));
    assertUnmodifiable(Stream.of("one", "two").toList());
    assertUnmodifiable(Collections.unmodifiableCollection(new ArrayList<>(ELEMENTS)));
    assertUnmodifiable(Collections.unmodifiableList(new ArrayList<>(ELEMENTS)));
    assertUnmodifiable(Collections.unmodifiableSet(new HashSet<>(ELEMENTS)));
    assertUnmodifiable(Collections.unmodifiableSortedSet(new TreeSet<>(ELEMENTS)));
    assertUnmodifiable(Collections.singletonList("one"));
    assertUnmodifiable(Collections.singleton("one"));
    assertUnmodifiable(Collections.nCopies(3, "one"));
    assertUnmodifiable(Collections.emptyList());
    assertUnmodifiable(Collections.emptySet());

    assertUnmodifiable(Map.of("one", 1));
    assertUnmodifiable(Map.copyOf(ENTRIES));
    assertUnmodifiable(Collections.unmodifiableMap(new HashMap<>(ENTRIES)));
    assertUnmodifiable(Collections.unmodifiableSortedMap(new TreeMap<>(ENTRIES)));
    assertUnmodifiable(Collections.singletonMap("one", 1));
    assertUnmodifiable(Collections.emptyMap());

    // Arrays.asList is fixed-size, but its elements are settable
    List<String> asList = roundTrip(Arrays.asList("one", "two"));
    expectThrows(UnsupportedOperationException.class, () -> asList.add("three"));
    asList.set(0, "uno");
  }

  /**
   * The canonical empty collections are singletons and must stay so.
   */
  @Test
  void testEmptySingletonsRemainIdentical() {
    assertSame(roundTrip(Collections.emptyList()), Collections.emptyList());
    assertSame(roundTrip(Collections.emptySet()), Collections.emptySet());
    assertSame(roundTrip(Collections.emptyMap()), Collections.emptyMap());
  }

  /**
   * Nesting must work in both directions and to any depth.
   */
  @Test
  void testNestedCollectionsAndMaps() {
    Map<String, List<Set<Integer>>> nested = new LinkedHashMap<>();
    nested.put("first", List.of(Set.of(1, 2), new LinkedHashSet<>(List.of(3, 4))));
    nested.put("second", new ArrayList<>(List.of(new TreeSet<>(List.of(5, 6)))));
    nested.put("empty", List.of());
    Map<String, List<Set<Integer>>> tunneled = roundTrip(nested);
    assertEquals(tunneled, nested);
    assertEquals(tunneled.get("first").get(1).getClass(), LinkedHashSet.class);
    assertEquals(tunneled.get("second").get(0).getClass(), TreeSet.class);

    List<Map<TestEnum, Deque<String>>> deeplyNested = new ArrayList<>();
    Map<TestEnum, Deque<String>> inner = new EnumMap<>(TestEnum.class);
    inner.put(TestEnum.GREEN, new ArrayDeque<>(ELEMENTS));
    deeplyNested.add(inner);
    List<Map<TestEnum, Deque<String>>> tunneledDeep = roundTrip(deeplyNested);
    assertEquals(tunneledDeep.size(), 1);
    assertEquals(new ArrayList<>(tunneledDeep.get(0).get(TestEnum.GREEN)), ELEMENTS);
  }

  /**
   * Object identity within one message must be preserved, cycles included.
   */
  @Test
  void testSharedAndSelfReferences() {
    List<String> shared = new ArrayList<>(ELEMENTS);
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("a", shared);
    map.put("b", shared);
    map.put("c", List.of(shared));
    Map<String, Object> tunneled = roundTrip(map);
    assertSame(tunneled.get("a"), tunneled.get("b"));
    assertSame(((List<?>) tunneled.get("c")).get(0), tunneled.get("a"));

    List<Object> selfReferencing = new ArrayList<>();
    selfReferencing.add("element");
    selfReferencing.add(selfReferencing);
    List<Object> tunneledSelf = roundTrip(selfReferencing);
    assertSame(tunneledSelf.get(1), tunneledSelf);

    Map<String, Object> selfReferencingMap = new HashMap<>();
    selfReferencingMap.put("self", selfReferencingMap);
    Map<String, Object> tunneledSelfMap = roundTrip(selfReferencingMap);
    assertSame(tunneledSelfMap.get("self"), tunneledSelfMap);
  }

  /**
   * The common application case: the declared type is an interface, the runtime type is not.
   */
  @Test
  void testCollectionInAnObjectField() {
    for (Object candidate : new Object[] { new ArrayList<>(ELEMENTS),
                                           new LinkedHashSet<>(ELEMENTS),
                                           new TreeMap<>(ENTRIES),
                                           List.of("one"),
                                           Map.of("one", 1),
                                           Collections.unmodifiableList(new ArrayList<>(ELEMENTS)),
                                           EnumSet.of(TestEnum.RED) }) {
      TestClass object = new TestClass();
      object.name = "holder";
      object.someObject = candidate;
      TestClass tunneled = roundTrip(object);
      assertEquals(tunneled.someObject.getClass(), candidate.getClass());
      assertEquals(tunneled, object);
    }
  }

  /**
   * Sending the same collection twice within one message yields the identical instance.
   */
  @Test
  void testBackReferences() {
    EchoTransceiver transceiver = new EchoTransceiver();
    List<String> list = new ArrayList<>(ELEMENTS);
    TripType<ArrayList> type = TripFactory.getInstance().getType(ArrayList.class);
    List<String> first = tunnel((ArrayList<String>) list, type, transceiver);
    List<String> second = tunnel((ArrayList<String>) list, type, transceiver);
    assertEquals(first, list);
    assertSame(second, first);
  }


  // ------------------------------------------------------------------------------------------
  // known lossy conversions
  //
  // These types round trip with their content intact, but arrive reconfigured, because only the
  // elements are serialized, never the JDK-internal fields (see CollectionTripType#isFieldSerializable).
  // The assertions below pin the current behavior so that a change becomes visible.
  // ------------------------------------------------------------------------------------------

  /**
   * The priority queues keep a comparator too, but they are no {@link SortedSet}s and are
   * therefore not covered by {@code SortedSetTripType}. They fall back to natural ordering.
   * ({@code PriorityBlockingQueue} could not be covered anyway: it provides a
   * capacity/comparator constructor only.)
   */
  @Test
  void testComparatorIsLostForPriorityQueues() {
    PriorityQueue<String> priorityQueue = new PriorityQueue<>(REVERSE);
    priorityQueue.addAll(ELEMENTS);
    assertEquals(priorityQueue.peek(), "two");                  // reverse order
    assertEquals(roundTrip(priorityQueue).peek(), "one");       // natural order

    PriorityBlockingQueue<String> blockingQueue = new PriorityBlockingQueue<>(11, REVERSE);
    blockingQueue.addAll(ELEMENTS);
    assertNull(roundTrip(blockingQueue).comparator());
  }

  /**
   * A bounded queue arrives with its capacity reduced to the number of elements, i.e. full.
   */
  @Test
  void testBoundedQueueCapacityIsLost() {
    ArrayBlockingQueue<String> arrayQueue = new ArrayBlockingQueue<>(10);
    arrayQueue.addAll(ELEMENTS);
    assertEquals(arrayQueue.remainingCapacity(), 7);
    assertEquals(roundTrip(arrayQueue).remainingCapacity(), 0);

    LinkedBlockingQueue<String> linkedQueue = new LinkedBlockingQueue<>(10);
    linkedQueue.addAll(ELEMENTS);
    assertEquals(linkedQueue.remainingCapacity(), 7);
    assertEquals(roundTrip(linkedQueue).remainingCapacity(), 0);
  }

  /**
   * Configuration held in JDK-internal fields is not serialized.
   */
  @Test
  void testCollectionConfigurationIsLost() {
    // the access-order flag of a LinkedHashMap (used for LRU caches)
    LinkedHashMap<String, Integer> accessOrdered = new LinkedHashMap<>(16, 0.75f, true);
    accessOrdered.put("one", 1);
    accessOrdered.put("two", 2);
    LinkedHashMap<String, Integer> tunneled = roundTrip(accessOrdered);
    tunneled.get("one");
    assertEquals(new ArrayList<>(tunneled.keySet()), List.of("one", "two"));   // unchanged: insertion order

    // the defaults of a Properties
    Properties defaults = new Properties();
    defaults.setProperty("inherited", "value");
    Properties properties = new Properties(defaults);
    properties.setProperty("own", "value");
    Properties tunneledProperties = roundTrip(properties);
    assertEquals(tunneledProperties.getProperty("own"), "value");
    assertNull(tunneledProperties.getProperty("inherited"));

    // the backing map of Collections.newSetFromMap: always a LinkedHashMap afterwards
    Set<String> setFromMap = Collections.newSetFromMap(new TreeMap<>(REVERSE));
    setFromMap.addAll(ELEMENTS);
    assertEquals(new ArrayList<>(setFromMap), List.of("two", "three", "one"));   // sorted in reverse
    Set<String> tunneledSetFromMap = roundTrip(setFromMap);
    tunneledSetFromMap.add("zulu");
    // the sender's order is retained, but new elements are no longer sorted in
    assertEquals(new ArrayList<>(tunneledSetFromMap), List.of("two", "three", "one", "zulu"));
  }

  /**
   * The {@code Collections} wrappers are rebuilt around an {@link ArrayList}, hence the
   * {@link java.util.RandomAccess} variant of the wrapper is delivered, no matter what
   * the sender wrapped.
   */
  @Test
  void testWrappedListsBecomeRandomAccess() {
    List<String> unmodifiable = Collections.unmodifiableList(new LinkedList<>(ELEMENTS));
    assertEquals(roundTrip(unmodifiable).getClass(),
                 Collections.unmodifiableList(new ArrayList<>(ELEMENTS)).getClass());

    List<String> synchronizedList = Collections.synchronizedList(new LinkedList<>(ELEMENTS));
    assertEquals(roundTrip(synchronizedList).getClass(),
                 Collections.synchronizedList(new ArrayList<>(ELEMENTS)).getClass());
  }

  /**
   * The immutable lists of the JDK have several implementation classes, selected by size.
   * The receiver ends up with the class matching the number of elements, which is not
   * necessarily the sender's one, but is always equal and equally immutable.
   */
  @Test
  void testImmutableListImplementationClassMayDiffer() {
    List<String> streamed = Stream.of("one", "two").toList();       // ImmutableCollections.ListN
    List<String> tunneled = roundTrip(streamed);
    assertEquals(tunneled, streamed);
    assertEquals(tunneled.getClass(), List.of("one", "two").getClass());   // ImmutableCollections.List12
    assertNotSame(tunneled.getClass(), streamed.getClass());
  }


  // ------------------------------------------------------------------------------------------
  // unsupported types
  //
  // Views and type-checked wrappers cannot be reconstructed without their backing collection
  // or type token. TRIP rejects them instead of silently delivering a detached copy.
  // ------------------------------------------------------------------------------------------

  @DataProvider(name = "unsupported")
  public Object[][] unsupported() {
    Map<String, Integer> map = new HashMap<>(ENTRIES);
    return new Object[][] {
      { "List.subList",           new ArrayList<>(ELEMENTS).subList(0, 2) },
      { "Map.keySet",             map.keySet() },
      { "Map.values",             map.values() },
      { "Map.entrySet",           map.entrySet() },
      { "TreeMap.descendingMap",  new TreeMap<>(ENTRIES).descendingMap() },
      { "TreeSet.descendingSet",  new TreeSet<>(ELEMENTS).descendingSet() },
      { "checkedList",            Collections.checkedList(new ArrayList<>(ELEMENTS), String.class) },
      { "checkedMap",             Collections.checkedMap(new HashMap<>(ENTRIES), String.class, Integer.class) },
      { "Map.entry",              Map.entry("one", 1) },
      { "BitSet",                 bitSet() }
    };
  }

  @Test(dataProvider = "unsupported")
  void testUnsupportedJdkType(String name, Object value) {
    // must fail fast and with a TRIP exception, never with a corrupt stream or a wrong result
    expectThrows(TripRuntimeException.class, () -> roundTrip(value));
  }

  /**
   * A view is unsupported, but copying it into a regular collection is the documented workaround.
   */
  @Test
  void testViewsCanBeCopiedBeforeSending() {
    Map<String, Integer> map = new HashMap<>(ENTRIES);
    assertEquals(sorted(roundTrip(new LinkedHashSet<>(map.keySet()))), sorted(map.keySet()));
    assertEquals(sorted(roundTrip(new ArrayList<>(map.values()))), sorted(map.values()));
    assertEquals(roundTrip(new ArrayList<>(new ArrayList<>(ELEMENTS).subList(0, 2))), ELEMENTS.subList(0, 2));
  }


  // ------------------------------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------------------------------

  /**
   * Round trips a value through a <em>fresh</em> transceiver, i.e. an empty dictionary
   * and an empty reference table, which makes the test independent of the execution order.
   *
   * @param value the value to serialize
   * @return the deserialized value
   * @param <T> the value's type
   */
  private <T> T roundTrip(T value) {
    return roundTrip(value, (TripType<T>) TripFactory.getInstance().getType(value.getClass()));
  }

  private <T> T roundTrip(T value, TripType<T> tripType) {
    return tunnel(value, tripType, new EchoTransceiver());
  }

  private <T> T tunnel(T value, TripType<T> tripType, EchoTransceiver transceiver) {
    try {
      tripType.write(value, transceiver.getStream());
      transceiver.echo();
      OpCode opCode = transceiver.getStream().getDeserializer().deserializeOpCode();
      return tripType.read(transceiver.getStream(), opCode);
    }
    finally {
      transceiver.reset();
    }
  }

  private void assertUnmodifiable(Collection<?> collection) {
    Collection<?> tunneled = roundTrip(collection);
    expectThrows(UnsupportedOperationException.class, () -> ((Collection<Object>) tunneled).add("added"));
  }

  private void assertUnmodifiable(Map<?, ?> map) {
    Map<?, ?> tunneled = roundTrip(map);
    expectThrows(UnsupportedOperationException.class, () -> ((Map<Object, Object>) tunneled).put("added", 0));
  }

  private Object[][] allCollections() {
    Object[][] ordered = orderedCollections();
    Object[][] unordered = unorderedCollections();
    Object[][] all = new Object[ordered.length + unordered.length][];
    System.arraycopy(ordered, 0, all, 0, ordered.length);
    System.arraycopy(unordered, 0, all, ordered.length, unordered.length);
    return all;
  }

  /**
   * Creates an empty collection of the same class as the given one.
   *
   * @param collection the template
   * @return the empty collection, null if the class cannot hold zero elements
   */
  private Collection<?> emptyLike(Collection<?> collection) {
    if (collection instanceof EnumSet) {
      return EnumSet.noneOf(TestEnum.class);
    }
    Class<?> clazz = collection.getClass();
    if (clazz == Collections.singletonList("").getClass() ||
        clazz == Collections.singleton("").getClass()) {
      return null;    // a singleton always holds exactly one element
    }
    try {
      Collection<Object> empty = (Collection<Object>) roundTrip(collection);
      empty.clear();
      return empty;
    }
    catch (UnsupportedOperationException ux) {
      // immutable: build an empty one from scratch, if the class provides one
      if (collection instanceof List) {
        Collection<?> emptyList = List.of();
        return emptyList.getClass() == clazz ? emptyList : null;
      }
      if (collection instanceof Set) {
        Collection<?> emptySet = Set.of();
        return emptySet.getClass() == clazz ? emptySet : null;
      }
      return null;
    }
  }

  private Map<?, ?> emptyLike(Map<?, ?> map) {
    if (map instanceof EnumMap) {
      return new EnumMap<TestEnum, String>(TestEnum.class);
    }
    Class<?> clazz = map.getClass();
    if (clazz == Collections.singletonMap("", "").getClass()) {
      return null;    // a singleton always holds exactly one entry
    }
    try {
      Map<Object, Object> empty = (Map<Object, Object>) roundTrip(map);
      empty.clear();
      return empty;
    }
    catch (UnsupportedOperationException ux) {
      Map<?, ?> emptyMap = Map.of();
      return emptyMap.getClass() == clazz ? emptyMap : null;
    }
  }

  private List<String> sorted(Collection<?> collection) {
    List<String> elements = new ArrayList<>();
    for (Object element : collection) {
      elements.add(String.valueOf(element));
    }
    Collections.sort(elements);
    return elements;
  }

  private List<String> entries(Map<?, ?> map) {
    List<String> entries = new ArrayList<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      entries.add(entry.getKey() + "=" + entry.getValue());
    }
    Collections.sort(entries);
    return entries;
  }

  private static Stack<String> fill(Stack<String> stack) {
    for (String element : ELEMENTS) {
      stack.push(element);
    }
    return stack;
  }

  private static Set<String> fill(Set<String> set) {
    set.addAll(ELEMENTS);
    return set;
  }

  private static Properties properties() {
    Properties properties = new Properties();
    for (Map.Entry<String, Integer> entry : ENTRIES.entrySet()) {
      properties.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
    }
    return properties;
  }

  private static EnumMap<TestEnum, String> enumMap() {
    EnumMap<TestEnum, String> map = new EnumMap<>(TestEnum.class);
    map.put(TestEnum.RED, "red");
    map.put(TestEnum.BLUE, "blue");
    return map;
  }

  private static BitSet bitSet() {
    BitSet bitSet = new BitSet();
    bitSet.set(3);
    return bitSet;
  }

}

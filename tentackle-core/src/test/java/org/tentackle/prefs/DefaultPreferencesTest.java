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

package org.tentackle.prefs;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link DefaultPreferences} wrapper, which delegates to a plain {@link java.util.prefs.Preferences} node.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultPreferencesTest {

  private static final long EVENT_TIMEOUT_MS = 5000;

  private InMemoryPreferences backing;
  private DefaultPreferences prefs;

  @BeforeMethod
  public void setUp() {
    backing = new InMemoryPreferences(true);
    prefs = new DefaultPreferences(backing);
  }


  @Test
  public void testPutAndGetString() {
    prefs.put("key", "value");
    assertEquals(prefs.get("key", "def"), "value");
    assertEquals(prefs.get("missing", "def"), "def");
  }

  @Test
  public void testRemove() {
    prefs.put("key", "value");
    prefs.remove("key");
    assertEquals(prefs.get("key", "def"), "def");
  }

  @Test
  public void testClear() throws Exception {
    prefs.put("a", "1");
    prefs.put("b", "2");
    prefs.clear();
    assertEquals(prefs.keys().length, 0);
  }

  @Test
  public void testInt() {
    prefs.putInt("i", 42);
    assertEquals(prefs.getInt("i", -1), 42);
    assertEquals(prefs.getInt("missing", -1), -1);
  }

  @Test
  public void testLong() {
    prefs.putLong("l", 9_000_000_000L);
    assertEquals(prefs.getLong("l", -1L), 9_000_000_000L);
    assertEquals(prefs.getLong("missing", -1L), -1L);
  }

  @Test
  public void testBoolean() {
    prefs.putBoolean("b", true);
    assertTrue(prefs.getBoolean("b", false));
    assertFalse(prefs.getBoolean("missing", false));
  }

  @Test
  public void testFloat() {
    prefs.putFloat("f", 1.5f);
    assertEquals(prefs.getFloat("f", -1f), 1.5f, 0.0f);
    assertEquals(prefs.getFloat("missing", -1f), -1f, 0.0f);
  }

  @Test
  public void testDouble() {
    prefs.putDouble("d", 2.5d);
    assertEquals(prefs.getDouble("d", -1d), 2.5d, 0.0d);
    assertEquals(prefs.getDouble("missing", -1d), -1d, 0.0d);
  }

  @Test
  public void testByteArray() {
    byte[] data = {1, 2, 3, 42};
    prefs.putByteArray("ba", data);
    assertTrue(Arrays.equals(prefs.getByteArray("ba", null), data));
    assertNull(prefs.getByteArray("missing", null));
  }

  @Test
  public void testKeys() throws Exception {
    prefs.put("one", "1");
    prefs.put("two", "2");
    assertEquals(Arrays.stream(prefs.keys()).sorted().toArray(), new String[] {"one", "two"});
  }

  @Test
  public void testChildrenNamesAndNode() throws Exception {
    prefs.node("child1");
    prefs.node("child2");
    assertEquals(Arrays.stream(prefs.childrenNames()).sorted().toArray(), new String[] {"child1", "child2"});
  }

  @Test
  public void testNodeWrapsInDefaultPreferences() {
    DefaultPreferences child = prefs.node("child");
    assertEquals(child.name(), "child");
    assertEquals(child.absolutePath(), "/child");
  }

  @Test
  public void testParent() {
    DefaultPreferences child = prefs.node("child");
    DefaultPreferences parent = child.parent();
    assertEquals(parent.absolutePath(), "/");
  }

  @Test
  public void testNodeExists() throws Exception {
    assertFalse(prefs.nodeExists("child"));
    prefs.node("child");
    assertTrue(prefs.nodeExists("child"));
  }

  @Test
  public void testAbsolutePathWithRootNameIsUnchanged() {
    // the test factory's root node has an empty name, so absolute paths are not rewritten
    assertEquals(prefs.node("/a/b").absolutePath(), "/a/b");
  }

  @Test
  public void testIsUserNode() {
    assertTrue(new DefaultPreferences(new InMemoryPreferences(true)).isUserNode());
    assertFalse(new DefaultPreferences(new InMemoryPreferences(false)).isUserNode());
  }

  @Test
  public void testRemoveNode() throws Exception {
    DefaultPreferences child = prefs.node("child");
    child.removeNode();
    assertFalse(prefs.nodeExists("child"));
  }

  @Test
  public void testFlushAndSyncDoNotThrow() throws Exception {
    prefs.put("key", "value");
    prefs.flush();
    prefs.sync();
    assertEquals(prefs.get("key", null), "value");
  }

  @Test
  public void testToStringDelegates() {
    assertEquals(prefs.toString(), backing.toString());
  }

  @Test
  public void testPreferenceChangeListener() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PreferenceChangeEvent> received = new AtomicReference<>();
    PreferenceChangeListener listener = evt -> {
      received.set(evt);
      latch.countDown();
    };
    prefs.addPreferenceChangeListener(listener);
    prefs.put("key", "value");
    assertTrue(latch.await(EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS), "no preference change event received");
    assertEquals(received.get().getKey(), "key");
    assertEquals(received.get().getNewValue(), "value");

    // after removal no more events must arrive
    prefs.removePreferenceChangeListener(listener);
    received.set(null);
    prefs.put("key2", "value2");
    prefs.sync();   // give the dispatch thread a chance
    assertNull(received.get());
  }

  @Test
  public void testNodeChangeListener() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<NodeChangeEvent> received = new AtomicReference<>();
    NodeChangeListener listener = new NodeChangeListener() {
      @Override
      public void childAdded(NodeChangeEvent evt) {
        received.set(evt);
        latch.countDown();
      }

      @Override
      public void childRemoved(NodeChangeEvent evt) {
      }
    };
    prefs.addNodeChangeListener(listener);
    prefs.node("newChild");
    assertTrue(latch.await(EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS), "no node change event received");
    assertEquals(received.get().getChild().name(), "newChild");
    prefs.removeNodeChangeListener(listener);
  }

  @Test
  public void testExportNode() throws Exception {
    prefs.put("key", "value");
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    prefs.exportNode(bos);
    String xml = bos.toString(StandardCharsets.UTF_8);
    assertTrue(xml.contains("key"), xml);
    assertTrue(xml.contains("value"), xml);
  }

  @Test
  public void testExportSubtree() throws Exception {
    prefs.put("rootKey", "rootValue");
    prefs.node("child").put("childKey", "childValue");
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    prefs.exportSubtree(bos);
    String xml = bos.toString(StandardCharsets.UTF_8);
    assertTrue(xml.contains("childKey"), xml);
    assertTrue(xml.contains("childValue"), xml);
  }

  @Test
  public void testRemovedNodeThrowsOnAccess() throws Exception {
    DefaultPreferences child = prefs.node("child");
    child.removeNode();
    assertThrows(IllegalStateException.class, () -> child.put("key", "value"));
  }
}

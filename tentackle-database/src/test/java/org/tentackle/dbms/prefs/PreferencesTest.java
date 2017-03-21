/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.dbms.prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import org.tentackle.dbms.AbstractDbTest;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.testng.Reporter;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests for persisted preferences.
 *
 * @author harald
 */
public class PreferencesTest extends AbstractDbTest implements NodeChangeListener, PreferenceChangeListener {

  private static final String KEY_1 = "key1";
  private static final String KEY_2 = "key2";
  private static final String KEY_3 = "key3";
  private static final String VALUE_1 = "value1";
  private static final String VALUE_2 = "value2";
  private static final String VALUE_3 = "value3";

  private final List<NodeChangeEvent> nodeEvents = new ArrayList<>();
  private final List<PreferenceChangeEvent> keyEvents = new ArrayList<>();


  @Override
  public void childAdded(NodeChangeEvent evt) {
    nodeEvents.add(evt);
    Reporter.log("child added: " + evt + "<br/>");
  }

  @Override
  public void childRemoved(NodeChangeEvent evt) {
    nodeEvents.add(evt);
    Reporter.log("child removed: " + evt + "<br/>");
  }

  @Override
  public void preferenceChange(PreferenceChangeEvent evt) {
    keyEvents.add(evt);
    Reporter.log("key changed: " + evt + "<br/>");
  }

  @BeforeSuite
  public void cleanup() {
    // always cleanup for sure (there is no DbPreNode/Key.deleteAll)
    for (DbPreferencesNode node : new DbPreferencesNode(getSession()).selectAllObjects()) {
      node.deletePlain();     // don't trigger modcounts
      for (DbPreferencesKey key : new DbPreferencesKey(getSession()).selectByNodeId(node.getId())) {
        key.deletePlain();
      }
    }
  }

  @Test(priority = 0)
  public void writePreferences() throws Exception {

    PersistedPreferencesFactory.getInstance().invalidate();
    PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(PreferencesTest.class);
    prefs.put(KEY_1, VALUE_1);
    prefs.put(KEY_2, VALUE_2);

    PersistedPreferences.systemRoot().addNodeChangeListener(this);
    prefs.addNodeChangeListener(this);
    prefs.addPreferenceChangeListener(this);

    prefs.flush();

    // verify database contents
    DbPreferencesNode prefNode = new DbPreferencesNode(getSession()).selectByUserAndName(null, "/");
    assertNotNull(prefNode);
    long rootId = prefNode.getRootNodeId();
    assertTrue(rootId > 0);
    long parentId = prefNode.getId();
    assertEquals(parentId, rootId);
    prefNode = new DbPreferencesNode(getSession()).selectByUserAndName(null, "/org");
    assertNotNull(prefNode);
    assertEquals(prefNode.getRootNodeId(), rootId);
    assertEquals(prefNode.getParentId(), parentId);
    parentId = prefNode.getId();
    prefNode = new DbPreferencesNode(getSession()).selectByUserAndName(null, "/org/tentackle");
    assertNotNull(prefNode);
    assertEquals(prefNode.getRootNodeId(), rootId);
    assertEquals(prefNode.getParentId(), parentId);
    parentId = prefNode.getId();
    prefNode = new DbPreferencesNode(getSession()).selectByUserAndName(null, "/org/tentackle/dbms");
    assertNotNull(prefNode);
    assertEquals(prefNode.getRootNodeId(), rootId);
    assertEquals(prefNode.getParentId(), parentId);
    parentId = prefNode.getId();
    prefNode = new DbPreferencesNode(getSession()).selectByUserAndName(null, "/org/tentackle/dbms/prefs");
    assertNotNull(prefNode);
    assertEquals(prefNode.getRootNodeId(), rootId);
    assertEquals(prefNode.getParentId(), parentId);
    parentId = prefNode.getId();
    prefNode = new DbPreferencesNode(getSession()).selectByUserAndName(null, "/org/tentackle/dbms/prefs/PreferencesTest");
    assertNull(prefNode);   // must not exist!
    List<DbPreferencesKey> prefKeys = new DbPreferencesKey(getSession()).selectByNodeId(parentId);
    assertEquals(prefKeys.size(), 2);
    boolean found1 = false;
    boolean found2 = false;
    for (DbPreferencesKey prefKey: prefKeys) {
      assertEquals(prefKey.getRootNodeId(), rootId);
      // nodeId must be ok, otherwise the select above wouldnt catch it
      switch (prefKey.getKey()) {
        case KEY_1:
          assertEquals(prefKey.getValue(), VALUE_1);
          found1 = true;
          break;
        case KEY_2:
          assertEquals(prefKey.getValue(), VALUE_2);
          found2 = true;
          break;
        default:
          fail("unexpected key " + prefKey.getKey());
      }
    }
    if (!found1) {
      fail("missing prefkey for " + KEY_1);
    }
    if (!found2) {
      fail("missing prefkey for " + KEY_2);
    }

    assertEquals(nodeEvents.size(), 1);
    NodeChangeEvent nodeEvent = nodeEvents.get(0);
    assertEquals(nodeEvent.getParent(), PersistedPreferences.systemRoot());
    assertEquals(nodeEvent.getChild().absolutePath(), "/org");
    assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isRemoved());
    nodeEvents.clear();

    assertEquals(keyEvents.size(), 2);
    PreferenceChangeEvent keyEvent = keyEvents.get(0);
    assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/dbms/prefs");
    assertEquals(keyEvent.getKey(), KEY_1);
    assertEquals(keyEvent.getNewValue(), VALUE_1);
    keyEvent = keyEvents.get(1);
    assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/dbms/prefs");
    assertEquals(keyEvent.getKey(), KEY_2);
    assertEquals(keyEvent.getNewValue(), VALUE_2);
    keyEvents.clear();
  }

  @Test(dependsOnMethods = {"writePreferences"})
  public void readPreferences() throws Exception {
    PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(PreferencesTest.class);
    assertEquals(prefs.get(KEY_1, ""), VALUE_1);
    assertEquals(prefs.get(KEY_2, ""), VALUE_2);

    PersistedPreferencesFactory.getInstance().invalidate();   // force reloading from storage
    prefs = PersistedPreferences.systemNodeForPackage(PreferencesTest.class);
    assertEquals(prefs.get(KEY_1, ""), VALUE_1);
    assertEquals(prefs.get(KEY_2, ""), VALUE_2);
  }

  @Test(dependsOnMethods = {"readPreferences"})
  public void addAndRemoveNode() throws Exception {
    PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(PreferencesTest.class).node("node3");
    prefs.addPreferenceChangeListener(PreferencesTest.this);
    prefs.put(KEY_3, VALUE_3);
    PersistedPreferences.systemRoot().flush();

    assertEquals(nodeEvents.size(), 1);
    NodeChangeEvent nodeEvent = nodeEvents.get(0);
    assertEquals(nodeEvent.getParent(), PersistedPreferences.systemNodeForPackage(PreferencesTest.class));
    assertEquals(nodeEvent.getChild().absolutePath(), "/org/tentackle/dbms/prefs/node3");
    assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isRemoved());
    nodeEvents.clear();

    assertEquals(keyEvents.size(), 1);
    PreferenceChangeEvent keyEvent = keyEvents.get(0);
    assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/dbms/prefs/node3");
    assertEquals(keyEvent.getKey(), KEY_3);
    assertEquals(keyEvent.getNewValue(), VALUE_3);
    keyEvents.clear();

    // now remove prefs node again
    prefs.removeNode();
    PersistedPreferences.systemRoot().flush();

    assertEquals(nodeEvents.size(), 1);
    nodeEvent = nodeEvents.get(0);
    assertEquals(nodeEvent.getParent(), PersistedPreferences.systemNodeForPackage(PreferencesTest.class));
    assertEquals(nodeEvent.getChild().absolutePath(), "/org/tentackle/dbms/prefs/node3");
    assertTrue(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isRemoved());
    nodeEvents.clear();

    assertEquals(keyEvents.size(), 1);
    keyEvent = keyEvents.get(0);
    assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/dbms/prefs/node3");
    assertEquals(keyEvent.getKey(), KEY_3);
    assertNull(keyEvent.getNewValue());
    keyEvents.clear();
  }

}

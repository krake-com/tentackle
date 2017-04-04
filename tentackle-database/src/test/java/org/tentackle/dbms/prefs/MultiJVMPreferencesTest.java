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
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.dbms.AbstractDbTest;
import org.tentackle.dbms.DbModification;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.testng.Reporter;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


/**
 * Tests for persisted preferences.
 *
 * @author harald
 */
public class MultiJVMPreferencesTest extends AbstractDbTest implements NodeChangeListener, PreferenceChangeListener {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(MultiJVMPreferencesTest.class);

  private static final String KEY_1 = "key1";
  private static final String KEY_2 = "key2";
  private static final String VALUE_1 = "value1";
  private static final String VALUE_2 = "value2";

  public static final String KEY_4 = "key4";
  public static final String VALUE_4 = "value4";

  private final List<NodeChangeEvent> nodeEvents = new ArrayList<>();
  private final List<PreferenceChangeEvent> keyEvents = new ArrayList<>();

  public MultiJVMPreferencesTest() {
    super(TransactionType.NONE);
  }

  // synchronized because it's the modtracker's thread
  // Reporter.log doesnt work from other than test thread!
  @Override
  public synchronized void childAdded(NodeChangeEvent evt) {
    nodeEvents.add(evt);
    LOGGER.info("child added: " + evt);
  }

  @Override
  public synchronized void childRemoved(NodeChangeEvent evt) {
    nodeEvents.add(evt);
    LOGGER.info("child removed: " + evt);
  }

  @Override
  public synchronized void preferenceChange(PreferenceChangeEvent evt) {
    keyEvents.add(evt);
    LOGGER.info("key changed: " + evt);
  }

  @Test(priority = 1)
  public void writePreferences() throws Exception {
    try {

      Thread.sleep(2000);   // give time to startup modtracker thread
      
      dumpSerials();

      PersistedPreferencesFactory.getInstance().invalidate();
      PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(MultiJVMPreferencesTest.class);

      prefs.put(KEY_1, VALUE_1);
      prefs.put(KEY_2, VALUE_2);
      prefs.flush();

      // register after flush
      prefs.addNodeChangeListener(this);
      prefs.node("other").addPreferenceChangeListener(this);    // not persisted so far!
      // so events are still empty
      assertTrue(getNodeEvents().isEmpty());
      assertTrue(getKeyEvents().isEmpty());

      // add node and key in other jvm
      assertEquals(runInOtherJVM(AddPrefsInOtherJVM.class), 0);
      Thread.sleep(2000);   // give time for modtracker to fire events

      dumpSerials();

      assertEquals(getNodeEvents().size(), 1);
      NodeChangeEvent nodeEvent = getNodeEvents().get(0);
      assertEquals(nodeEvent.getParent(), prefs);
      assertEquals(nodeEvent.getChild().absolutePath(), "/org/tentackle/dbms/prefs/other");
      assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isRemoved());
      assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isNew());    // and persisted!
      getNodeEvents().clear();

      assertEquals(getKeyEvents().size(), 1);
      PreferenceChangeEvent keyEvent = getKeyEvents().get(0);
      assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/dbms/prefs/other");
      assertEquals(keyEvent.getKey(), KEY_4);
      assertEquals(keyEvent.getNewValue(), VALUE_4);
      getKeyEvents().clear();

      // remove node and key in other jvm
      assertEquals(runInOtherJVM(RemovePrefsInOtherJVM.class), 0);
      Thread.sleep(2000);   // give time for modtracker to fire events

      dumpSerials();

      assertEquals(getNodeEvents().size(), 1);
      nodeEvent = getNodeEvents().get(0);
      assertEquals(nodeEvent.getParent(), prefs);
      assertEquals(nodeEvent.getChild().absolutePath(), "/org/tentackle/dbms/prefs/other");
      assertTrue(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isRemoved());
      assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isNew());    // and persisted!

      assertEquals(getKeyEvents().size(), 1);
      keyEvent = getKeyEvents().get(0);
      assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/dbms/prefs/other");
      assertEquals(keyEvent.getKey(), KEY_4);
      assertNull(keyEvent.getNewValue());
    }
    finally {
      // always cleanup because we're not running within a transaction (there is no DbPreNode/Key.deleteAll)
      for (DbPreferencesNode node: new DbPreferencesNode(getSession()).selectAllObjects()) {
        node.deletePlain();     // don't trigger modcounts
        Reporter.log("removed node " + node.getName() + "<br>");
        for (DbPreferencesKey key: new DbPreferencesKey(getSession()).selectByNodeId(node.getId())) {
          key.deletePlain();
          Reporter.log("removed key " + key.getKey() + "<br>");
        }
      }
    }
  }


  private synchronized List<NodeChangeEvent> getNodeEvents() {
    return nodeEvents;
  }

  private synchronized List<PreferenceChangeEvent> getKeyEvents() {
    return keyEvents;
  }

  private void dumpSerials() {
    for (DbModification mod : new DbModification(getSession()).selectAllObjects()) {
      Reporter.log(mod.getTrackedName() + ":= " + mod.getSerial() + "<br>");
    }
  }

}

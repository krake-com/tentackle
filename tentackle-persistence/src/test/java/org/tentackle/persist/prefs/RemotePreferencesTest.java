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

package org.tentackle.persist.prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.AbstractSessionTask;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.persist.AbstractDbTest;
import org.tentackle.persist.Db;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


/**
 * Tests for persisted preferences.
 * <p>
 * IMPORTANT: must be the last test in suite!
 *
 * @author harald
 */
public class RemotePreferencesTest implements NodeChangeListener, PreferenceChangeListener {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(RemotePreferencesTest.class);

  private static final String KEY_1 = "key1";
  private static final String KEY_2 = "key2";
  private static final String VALUE_1 = "value1";
  private static final String VALUE_2 = "value2";

  public static final String KEY_4 = "key4";
  public static final String VALUE_4 = "value4";

  private final List<NodeChangeEvent> nodeEvents = new ArrayList<>();
  private final List<PreferenceChangeEvent> keyEvents = new ArrayList<>();

  private Process process;    // server process
  private Db session;


  @BeforeClass
  public void openSession() throws Exception {
    process = AbstractDbTest.runClass(AddAndRemovePrefsInServer.class);
    Thread.sleep(1000);
    SessionInfo sessionInfo = Pdo.createSessionInfo("client");
    session = (Db) Pdo.createSession(sessionInfo);
    session.open();
    session.makeCurrent();
    PdoTracker tracker = PdoTracker.getInstance();
    if (tracker.isAlive()) {
        // open from previous tests (we're in testsuite), else single test
        tracker.addTaskAndWait(new AbstractSessionTask() {
          private static final long serialVersionUID = 1L;
          @Override
          public void run() {
            // switch session from within running tracker's thread is allowed
            getSession().close();
            setSession(session.clone());
          }
        });
    }
    else {
      tracker.setSession(session.clone());    // set or change session
      tracker.setSleepInterval(500);   // fast polling for tests
      tracker.start();
    }
  }

  @AfterClass
  public void closeSession() throws Exception {
    PdoTracker.getInstance().terminate();
    session.close();
    AbstractDbTest.waitForProcess(process);
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

  @Test(priority = 999)   // must be the last test!
  public void writePreferences() throws Exception {

    PersistedPreferencesFactory.getInstance().invalidate();
    PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(RemotePreferencesTest.class);

    prefs.put(KEY_1, VALUE_1);
    prefs.put(KEY_2, VALUE_2);
    PersistedPreferences.systemRoot().flush();

    // register after flush
    prefs.addNodeChangeListener(this);
    prefs.node("other").addPreferenceChangeListener(this);    // not persisted so far!
    // so events are still empty
    assertTrue(nodeEvents.isEmpty());
    assertTrue(keyEvents.isEmpty());

    // add node and key in other jvm
    Thread.sleep(2000);   // give time for modtracker to fire events


    assertEquals(nodeEvents.size(), 1);
    NodeChangeEvent nodeEvent = nodeEvents.get(0);
    assertEquals(nodeEvent.getParent(), prefs);
    assertEquals(nodeEvent.getChild().absolutePath(), "/org/tentackle/persist/prefs/other");
    assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isRemoved());
    assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isNew());    // and persisted!
    nodeEvents.clear();

    assertEquals(keyEvents.size(), 1);
    PreferenceChangeEvent keyEvent = keyEvents.get(0);
    assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/persist/prefs/other");
    assertEquals(keyEvent.getKey(), KEY_4);
    assertEquals(keyEvent.getNewValue(), VALUE_4);
    keyEvents.clear();

    // remove node and key in other jvm
    Thread.sleep(3000);   // give time for modtracker to fire events


    assertEquals(nodeEvents.size(), 1);
    nodeEvent = nodeEvents.get(0);
    assertEquals(nodeEvent.getParent(), prefs);
    assertEquals(nodeEvent.getChild().absolutePath(), "/org/tentackle/persist/prefs/other");
    assertTrue(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isRemoved());
    assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isNew());    // and persisted!

    assertEquals(keyEvents.size(), 1);
    keyEvent = keyEvents.get(0);
    assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/persist/prefs/other");
    assertEquals(keyEvent.getKey(), KEY_4);
    assertNull(keyEvent.getNewValue());
  }

}

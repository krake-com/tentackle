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

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.dbms.prefs.DbPreferences;
import org.tentackle.log.Logger;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.pdo.testng.ServerProcessWrapper;
import org.tentackle.prefs.other.AddAndRemovePrefsInServer;
import org.tentackle.session.AbstractSessionTask;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for persisted preferences.
 * <p>
 * IMPORTANT: must be the last test in suite!
 *
 * @author harald
 */
public class RemotePreferencesTest implements NodeChangeListener, PreferenceChangeListener {

  private static final Logger LOGGER = Logger.get(RemotePreferencesTest.class);

  private static final String KEY_1 = "key1";
  private static final String KEY_2 = "key2";
  private static final String VALUE_1 = "value1";
  private static final String VALUE_2 = "value2";

  public static final String KEY_4 = "key4";
  public static final String VALUE_4 = "value4";

  private final List<NodeChangeEvent> nodeEvents = new ArrayList<>();
  private final List<PreferenceChangeEvent> keyEvents = new ArrayList<>();


  // synchronized because it's the modification tracker's thread
  // Reporter.log doesn't work from other than the test thread!
  // notifyAll wakes the test thread blocked in awaitEvents.
  @Override
  public synchronized void childAdded(NodeChangeEvent evt) {
    nodeEvents.add(evt);
    LOGGER.info("child added: " + evt);
    notifyAll();
  }

  @Override
  public synchronized void childRemoved(NodeChangeEvent evt) {
    nodeEvents.add(evt);
    LOGGER.info("child removed: " + evt);
    notifyAll();
  }

  @Override
  public synchronized void preferenceChange(PreferenceChangeEvent evt) {
    keyEvents.add(evt);
    LOGGER.info("key changed: " + evt);
    notifyAll();
  }

  /**
   * Waits until the expected number of node- and key-events have been observed.
   * <p>
   * The events are fired asynchronously by the modification tracker after the server commits its
   * changes. Waiting for the actual counts (rather than a fixed delay) makes the test independent of
   * the tracker's polling interval and the current system load.
   *
   * @param expectedNodeEvents the number of node-change events to wait for
   * @param expectedKeyEvents the number of key-change events to wait for
   * @param timeoutMs the maximum time to wait in milliseconds
   * @throws InterruptedException if interrupted while waiting
   */
  private synchronized void awaitEvents(int expectedNodeEvents, int expectedKeyEvents, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (nodeEvents.size() < expectedNodeEvents || keyEvents.size() < expectedKeyEvents) {
      long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) {
        throw new IllegalStateException(
            "timed out waiting for events: nodeEvents=" + nodeEvents.size() + " (expected " + expectedNodeEvents
            + "), keyEvents=" + keyEvents.size() + " (expected " + expectedKeyEvents + ")");
      }
      wait(remaining);
    }
  }

  @Test(groups = "remote2", dependsOnGroups = "remote1")  // must be the last test!
  public void writePreferences() throws Exception {
    Reporter.log("RemotePreferencesTest.writePreferences <br>", true);

    ServerProcessWrapper server = new ServerProcessWrapper(AbstractPdoTest.runClass(AddAndRemovePrefsInServer.class));
    server.awaitReady(30000);   // block until the server is listening instead of guessing with sleep

    SessionInfo sessionInfo = Pdo.createSessionInfo("client");
    Session session = Pdo.createSession(sessionInfo);

    try {
      session.makeCurrent();
      ModificationTracker tracker = ModificationTracker.getInstance();
      if (tracker.isAlive()) {
          // open from previous tests (we're in testsuite), else single test
          tracker.addTaskAndWait(new AbstractSessionTask() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            public void run() {
              // switch session from within the running tracker's thread is allowed
              tracker.getSession().close();
              tracker.setSession(session.clone(null));
              tracker.setSleepInterval(250);   // fast polling for tests
            }
          });
      }
      else {
        tracker.setSession(session.clone(null));    // set or change the session
        tracker.setSleepInterval(250);          // fast polling for tests
        tracker.start();
      }

      PersistedPreferencesFactory.getInstance().invalidate();
      PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(RemotePreferencesTest.class);

      prefs.put(KEY_1, VALUE_1);
      prefs.put(KEY_2, VALUE_2);
      prefs.flush();

      // register after flush
      prefs.addNodeChangeListener(this);
      prefs.node("other").addPreferenceChangeListener(this);    // not persisted so far!
      // so events are still empty
      assertTrue(nodeEvents.isEmpty());
      assertTrue(keyEvents.isEmpty());

      // add node and key in the other jvm, then wait for the change to propagate to us
      server.sendCommand(AddAndRemovePrefsInServer.ADD_COMMAND);
      server.awaitOutput(AddAndRemovePrefsInServer.ADDED_MARKER, 30000);   // server committed
      awaitEvents(1, 1, 15000);                                            // tracker fired the events

      assertEquals(nodeEvents.size(), 1);
      NodeChangeEvent nodeEvent = nodeEvents.getFirst();
      assertEquals(nodeEvent.getParent(), prefs);
      assertEquals(nodeEvent.getChild().absolutePath(), "/org/tentackle/prefs/other");
      assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isRemoved());
      assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isNew());    // and persisted!
      assertEquals(prefs.childrenNames().length, 1);
      assertEquals(prefs.childrenNames()[0], "other");
      PersistedPreferences otherPrefs = prefs.node("other");
      assertEquals(otherPrefs.keys().length, 1);
      nodeEvents.clear();

      assertEquals(keyEvents.size(), 1);
      PreferenceChangeEvent keyEvent = keyEvents.getFirst();
      assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/prefs/other");
      assertEquals(keyEvent.getKey(), KEY_4);
      assertEquals(keyEvent.getNewValue(), VALUE_4);
      assertEquals(otherPrefs.get(KEY_4, ""), VALUE_4);
      keyEvents.clear();

      // remove node and key in the other jvm, then wait for the change to propagate to us
      server.sendCommand(AddAndRemovePrefsInServer.REMOVE_COMMAND);
      server.awaitOutput(AddAndRemovePrefsInServer.REMOVED_MARKER, 30000);   // server committed
      awaitEvents(1, 1, 15000);                                              // tracker fired the events

      assertEquals(nodeEvents.size(), 1);
      nodeEvent = nodeEvents.getFirst();
      assertEquals(nodeEvent.getParent(), prefs);
      assertEquals(nodeEvent.getChild().absolutePath(), "/org/tentackle/prefs/other");
      assertTrue(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isRemoved());
      assertFalse(((DbPreferences) nodeEvent.getChild()).getPersistentNode().isNew());    // and persisted!

      assertEquals(keyEvents.size(), 1);
      keyEvent = keyEvents.getFirst();
      assertEquals(keyEvent.getNode().absolutePath(), "/org/tentackle/prefs/other");
      assertEquals(keyEvent.getKey(), KEY_4);
      assertNull(keyEvent.getNewValue());

      assertEquals(prefs.childrenNames().length, 0);
      otherPrefs = prefs.node("other");
      assertEquals(otherPrefs.keys().length, 0);
    }
    finally {
      ModificationTracker.getInstance().terminate();
      server.stopAndWait();   // signal shutdown via stdin EOF and wait for the server to terminate
      session.close();
    }
  }

}

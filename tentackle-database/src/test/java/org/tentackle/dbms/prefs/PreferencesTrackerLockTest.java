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

package org.tentackle.dbms.prefs;

import org.testng.annotations.Test;

import org.tentackle.dbms.AbstractDbTest;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbModification;
import org.tentackle.dbms.DbModificationTracker;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.session.ModificationTracker;
import org.tentackle.task.AbstractTask;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the modification tracker does not wait for the monitor of the {@link DbPreferencesFactory}
 * to deliver the changes made by another JVM.
 * <p>
 * The monitor is held while the preferences are modified or flushed. The modification count of a flush
 * needs the tracker thread to configure a counter that is not configured yet, e.g., on the first flush
 * of a table. Any other configuration task, such as registering a new name, waits for the tracker
 * thread as well. If the tracker thread is blocked by the monitor meanwhile, both wait for each other
 * until the wait interval of the tracker elapsed and the flush fails.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class PreferencesTrackerLockTest extends AbstractDbTest {

  private static final String NODE_NAME = "trackerlocktest";
  private static final String KEY = "key";
  private static final long WAIT_INTERVAL = 3000;
  private static final long AWAIT_MILLIS = 10000;

  public PreferencesTrackerLockTest() {
    super(TransactionType.NONE);    // changes must be committed to be seen by the tracker
  }


  @Test    // no timeOut: would run in another thread without the thread-local session
  public void testFlushWhileTrackerDeliversChanges() throws Exception {
    DbModificationTracker tracker = (DbModificationTracker) ModificationTracker.getInstance();
    DbPreferencesFactory factory = DbPreferencesFactory.getInstance();
    PersistedPreferences prefs = PersistedPreferences.systemRoot().node(NODE_NAME);
    Db otherDb = getSession().clone("other JVM");
    long waitInterval = tracker.getWaitInterval();
    tracker.setWaitInterval(WAIT_INTERVAL);   // fail fast if blocked
    try {
      prefs.put(KEY, "1");
      prefs.flush();

      Throwable failure = null;
      long flushMillis;
      long taskMillis;
      synchronized (factory) {
        // another JVM modified the preferences: the tracker delivers the change to the factory
        new DbModification(otherDb, DbPreferencesNode.CLASSVARIABLES.tableName).countModification(1);
        awaitBlockedOnFactory(tracker);

        prefs.put(KEY, "2");
        long start = System.currentTimeMillis();
        try {
          prefs.flush();
        }
        catch (Exception ex) {
          failure = ex;
        }
        flushMillis = System.currentTimeMillis() - start;

        // as a flush configuring a counter does
        start = System.currentTimeMillis();
        tracker.addTaskAndWait(new AbstractTask() {
          @Override
          public void run() {
            // nothing to do
          }
        });
        taskMillis = System.currentTimeMillis() - start;
      }

      assertEquals(failure, null, "flush failed");
      assertTrue(flushMillis < WAIT_INTERVAL, "flush waited " + flushMillis + "ms for the tracker");
      assertTrue(taskMillis < WAIT_INTERVAL, "the tracker was blocked for " + taskMillis + "ms");
      assertEquals(prefs.get(KEY, null), "2");
    }
    finally {
      tracker.setWaitInterval(waitInterval);
      otherDb.close();
      prefs.removeNode();
      prefs.flush();
    }
  }


  /**
   * Waits until the tracker thread, or the thread it handed the change over to, waits for the factory's monitor.
   */
  private void awaitBlockedOnFactory(Thread tracker) throws InterruptedException {
    long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      for (Thread thread : Thread.getAllStackTraces().keySet()) {
        if ((thread == tracker || thread.getName().startsWith("preferences update")) &&
            thread.getState() == Thread.State.BLOCKED) {
          return;
        }
      }
      Thread.sleep(20);
    }
    throw new AssertionError("the change was not delivered to the preferences factory");
  }

}

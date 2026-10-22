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
import org.tentackle.prefs.PersistedPreferences;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.PreferenceChangeListener;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests that preferences listeners are not invoked with the monitor of the {@link DbPreferencesFactory} held.
 * <p>
 * A listener that waits for another thread accessing the preferences, as UI toolkits do when
 * dispatching to their event thread synchronously, would deadlock otherwise.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class PreferencesListenerLockTest extends AbstractDbTest {

  private static final String NODE_NAME = "listenerlocktest";
  private static final String KEY = "key";
  private static final long AWAIT_MILLIS = 10000;


  @Test    // no timeOut: would run in another thread without the thread-local session
  public void testListenerMayWaitForOtherThreads() throws Exception {
    PersistedPreferences prefs = PersistedPreferences.systemRoot().node(NODE_NAME);

    CountDownLatch invoked = new CountDownLatch(1);
    AtomicBoolean readerBlocked = new AtomicBoolean();
    AtomicReference<String> readValue = new AtomicReference<>();
    PreferenceChangeListener listener = evt -> {
      // e.g., dispatching to the UI thread and waiting for it
      Thread reader = new Thread(() -> readValue.set(prefs.get(KEY, null)), "preferences reader");
      reader.setDaemon(true);
      reader.start();
      try {
        reader.join(2000);
      }
      catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      readerBlocked.set(reader.isAlive());
      invoked.countDown();
    };
    prefs.addPreferenceChangeListener(listener);
    try {
      prefs.put(KEY, "value");
      prefs.flush();

      assertTrue(invoked.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "the listener was not invoked");
      assertFalse(readerBlocked.get(), "the listener was invoked with the monitor of the preferences factory held");
      assertEquals(readValue.get(), "value");
    }
    finally {
      prefs.removePreferenceChangeListener(listener);
      prefs.removeNode();
      prefs.flush();
    }
  }

}

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
import org.tentackle.prefs.PersistedPreferencesFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Concurrent in-memory modifications of a preferences tree.
 * <p>
 * Nothing is flushed, so the database is left untouched.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class ConcurrentPreferencesTest extends AbstractDbTest {

  private static final int THREADS = 8;
  private static final int NODES = 5000;

  @Test
  public void concurrentNodesAndKeys() throws Exception {
    PersistedPreferences stress = PersistedPreferences.systemRoot().node("concurrent-stress");
    try {
      AtomicReference<Throwable> failure = new AtomicReference<>();
      CountDownLatch start = new CountDownLatch(1);
      List<Thread> threads = new ArrayList<>();

      for (int t = 0; t < THREADS; t++) {
        String key = "t" + t;
        threads.add(new Thread(() -> {
          try {
            start.await();
            for (int n = 0; n < NODES; n++) {
              // all threads create the same nodes, via relative and absolute paths, i.e., starting at different nodes
              PersistedPreferences node = (n & 1) == 0 ?
                                          stress.node("shared/n" + n) :
                                          PersistedPreferences.systemRoot().node("/concurrent-stress/shared/n" + n);
              node.put(key, key);
            }
          }
          catch (Throwable ex) {
            failure.compareAndSet(null, ex);
          }
        }));
      }

      // a reader iterating the shared maps while they are modified
      threads.add(new Thread(() -> {
        try {
          start.await();
          for (int i = 0; i < NODES; i++) {
            stress.keys();
            stress.childrenNames();
          }
        }
        catch (Throwable ex) {
          failure.compareAndSet(null, ex);
        }
      }));

      threads.forEach(Thread::start);
      start.countDown();
      for (Thread thread : threads) {
        thread.join(60000);
      }

      assertNull(failure.get(), "concurrent access failed: " + failure.get());
      PersistedPreferences shared = stress.node("shared");
      assertEquals(shared.childrenNames().length, NODES);
      for (int n = 0; n < NODES; n++) {
        // a node created twice concurrently loses the keys put into the overwritten instance
        assertEquals(shared.node("n" + n).keys().length, THREADS, "keys lost for node n" + n);
      }
    }
    finally {
      // drop the unflushed in-memory tree
      PersistedPreferencesFactory.getInstance().invalidate();
    }
  }

}

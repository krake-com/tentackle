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

package org.tentackle.dbms;

import org.testng.annotations.Test;

import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationListenerAdapter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertTrue;

/**
 * Tests that delayed listeners sharing the same invocation time are all invoked.
 * <p>
 * Listeners with the same time delay and no time frame are scheduled within the same poll,
 * hence mostly for the same millisecond. None of them must get lost.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ModificationTrackerDelayedListenerTest extends AbstractDbTest {

  private static final String TRACKED_NAME = "mtdelayedlistenertest";
  private static final int LISTENER_COUNT = 10;
  private static final long TIME_DELAY = 200;
  private static final long AWAIT_MILLIS = 10000;

  public ModificationTrackerDelayedListenerTest() {
    super(TransactionType.NONE);    // the modifications must be committed to be seen by the tracker
  }


  @Test(timeOut = 60000)
  public void testDelayedListenersWithSameInvocationTime() throws Exception {
    Db db = getSession();

    DbModificationTracker tracker = new DbModificationTracker("delayed listener test tracker");
    tracker.setSession(db.clone(null));
    tracker.setSleepInterval(100);

    CountDownLatch invoked = new CountDownLatch(LISTENER_COUNT);
    for (int i = 0; i < LISTENER_COUNT; i++) {
      tracker.addModificationListener(new ModificationListenerAdapter(TRACKED_NAME) {
        @Override
        public long getTimeDelay() {
          return TIME_DELAY;
        }

        @Override
        public void dataChanged(ModificationEvent event) {
          invoked.countDown();
        }
      });
    }

    tracker.getMasterSerial();    // configure the master serial before the modification, else the first poll misses it
    tracker.start();
    try {
      new DbModification(db, TRACKED_NAME).countModification(1);
      assertTrue(invoked.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS),
                 (LISTENER_COUNT - invoked.getCount()) + " of " + LISTENER_COUNT + " delayed listeners invoked");
    }
    finally {
      tracker.terminate();
    }
  }

}

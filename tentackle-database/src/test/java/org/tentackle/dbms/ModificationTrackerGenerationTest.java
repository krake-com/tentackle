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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests that a tracker detects the re-initialization of the modification table by another tracker.
 * <p>
 * The re-initialization resets the master serial to 1 and re-creates the rows with new ids.
 * If the master serial has been counted beyond the serial known to the other tracker until its next poll,
 * the re-initialization cannot be detected by a decreasing master serial. The other tracker would keep
 * the stale ids and miss all modifications from then on.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ModificationTrackerGenerationTest extends AbstractDbTest {

  private static final String TRACKED_NAME = "mtgenerationtest";
  private static final String FILLER_NAME = "mtgenerationfiller";
  private static final long AWAIT_MILLIS = 10000;

  public ModificationTrackerGenerationTest() {
    super(TransactionType.NONE);    // the modifications must be committed to be seen by the trackers
  }


  @Test(timeOut = 60000)
  public void testReinitializationDetectedAfterCountedBeyond() throws Exception {
    Db db = getSession();

    // the tracked name must get another id when re-created, i.e., must not be the first row
    DbModification filler = new DbModification(db, FILLER_NAME);
    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate("DELETE FROM " + filler.getTableName() + " WHERE " +
                       DbModification.CN_TRACKEDNAME + "='" + TRACKED_NAME + "'");
    }
    filler.refresh();
    if (filler.getId() == 0) {
      filler.addToModificationTable(false, null);
    }

    // the tracker of another JVM, not polling yet
    DbModificationTracker tracker = new DbModificationTracker("generation test tracker");
    tracker.setSession(db.clone(null));
    tracker.setSleepInterval(100);
    CountDownLatch changed = new CountDownLatch(1);
    tracker.addModificationListener(new ModificationListenerAdapter(TRACKED_NAME) {
      @Override
      public void dataChanged(ModificationEvent event) {
        changed.countDown();
      }
    });
    long oldId = tracker.getIdSerialForName(TRACKED_NAME).getId();
    long oldMasterSerial = tracker.getMasterSerial();

    // the re-initializing tracker, not started: runs its tasks in this thread
    DbModificationTracker reinitializer = new DbModificationTracker("re-initializing tracker");
    reinitializer.setSession(db.clone(null));
    try {
      reinitializer.invalidate();
      long newId = reinitializer.getIdSerialForName(TRACKED_NAME).getId();   // re-creates the row
      assertNotEquals(newId, oldId, "id not changed by the re-initialization");

      // counted beyond the master serial known to the other tracker before it polls
      new DbModification(db, TRACKED_NAME).countModification(oldMasterSerial + 10);
      assertTrue(new DbModification(db).selectMasterSerial() > oldMasterSerial);

      tracker.start();
      assertTrue(changed.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "re-initialization not detected");
      assertEquals(tracker.getIdSerialForName(TRACKED_NAME).getId(), newId, "stale id after re-initialization");
    }
    finally {
      tracker.terminate();
      reinitializer.terminate();
    }
  }

}

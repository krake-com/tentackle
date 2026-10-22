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

package org.tentackle.pdo;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.session.PersistenceException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests PDO snapshots and copies.
 */
public class SnapshotTest extends AbstractPdoTest {

  private static final String SNAPSHOT_POOL = "snapshot";
  private static final String SNAPSHOT_REALM = "test";
  private static final String COPY_POOL = "copy";

  @Test(groups = "local")
  public void createSnapshots() {
    Reporter.log("SnapshotTest.createSnapshots <br>", true);

    // pool is a composite, fine for testing
    NumberPool numberPool = on(NumberPool.class);
    numberPool.setName(SNAPSHOT_POOL);
    numberPool.setRealm(SNAPSHOT_REALM);
    numberPool.setOnline(true);
    NumberRange numberRange = numberPool.on(NumberRange.class);
    numberRange.setBegin(2000);
    numberRange.setEnd(3000);
    numberPool.getNumberRangeList().add(numberRange);
    numberRange = numberRange.on();
    numberRange.setBegin(1);
    numberRange.setEnd(1000);   // 1 - 999
    numberPool.getNumberRangeList().add(numberRange);
    numberPool = numberPool.persist();
    long id = numberPool.getId();
    assertFalse(numberPool.isModified());

    NumberPool snapshot = numberPool.createSnapshot();
    assertEquals(snapshot.getSerial(), 1);

    numberRange = numberRange.on();
    numberRange.setBegin(5000);
    numberRange.setEnd(6000);
    numberPool.getNumberRangeList().add(numberRange);
    numberPool.setRealm("none");
    assertEquals(numberPool.getNumberRangeList().size(), 3);
    assertTrue(numberPool.isModified());

    numberPool.revertToSnapshot(snapshot);
    assertFalse(numberPool.isModified());
    assertEquals(numberPool.getId(), id);
    assertEquals(numberPool.getRealm(), SNAPSHOT_REALM);
    assertEquals(numberPool.getNumberRangeList().size(), 2);

    numberRange = numberRange.on();
    numberRange.setBegin(5000);
    numberRange.setEnd(6000);
    numberPool.getNumberRangeList().add(numberRange);
    numberPool.setRealm("none");
    numberPool = numberPool.persist();
    assertEquals(numberPool.getSerial(), 2);
    assertFalse(numberPool.isModified());

    // the snapshot was discarded by the revert above
    NumberPool pool = numberPool;
    assertTrue(expectThrows(PersistenceException.class, () -> pool.revertToSnapshot(snapshot))
                   .getMessage().contains("not my snapshot"));
  }

  @Test(groups = "local")
  public void createCopies() {
    Reporter.log("SnapshotTest.createCopies <br>", true);

    NumberPool numberPool = on(NumberPool.class);
    numberPool.setName(COPY_POOL);
    numberPool.setOnline(true);
    NumberRange numberRange = numberPool.on(NumberRange.class);
    numberRange.setBegin(2000);
    numberRange.setEnd(3000);
    numberPool.getNumberRangeList().add(numberRange);
    numberRange = numberRange.on();
    numberRange.setBegin(1);
    numberRange.setEnd(1000);   // 1 - 999
    numberPool.getNumberRangeList().add(numberRange);
    numberPool = numberPool.persist();

    NumberPool copy = numberPool.copy();
    assertTrue(copy.isCopy());
    assertTrue(copy.isNew());
    assertEquals(copy.getSerial(), 0);
    assertEquals(copy.getName(), COPY_POOL);
    assertEquals(copy.getNumberRangeList().size(), 2);
    for (NumberRange range: copy.getNumberRangeList()) {
      assertTrue(range.isNew());
      assertEquals(range.getSerial(), 0);
    }
    assertEquals(copy.getNumberRangeList().get(1).getEnd(), 1000);

    numberPool.setFinallyImmutable();
    assertTrue(numberPool.createSnapshot().isImmutable());
    assertFalse(numberPool.copy().isImmutable());    // copies are always mutable!
    for (NumberRange range: copy.getNumberRangeList()) {
      assertFalse(range.isImmutable());
    }
  }

}

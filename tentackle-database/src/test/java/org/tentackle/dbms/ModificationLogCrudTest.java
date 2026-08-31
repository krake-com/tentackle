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

import org.tentackle.session.NotFoundException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the generic {@link AbstractDbObject} insert/update/delete/select engine directly against
 * a real H2 database, using {@link ModificationLog} (this module's own {@code modlog} entity) as
 * the concrete test subject.
 * <p>
 * Until now this engine was only exercised indirectly, through {@code prefs/PreferencesTest.java}'s
 * use of the {@code java.util.prefs.Preferences} API.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ModificationLogCrudTest extends AbstractDbTest {

  public ModificationLogCrudTest() {
    super(TransactionType.METHOD);
  }


  @Test
  public void testInsertSelectUpdateDelete() {
    Db db = getSession();

    ModificationLog log = new ModificationLog(db);
    log.setModType(DbModificationType.INSERT);
    log.setUserId(4711);
    log.setTxName("insert");
    log.newId();
    log.insertObject();

    long id = log.getId();
    assertTrue(id > 0, "insertObject() must not leave the id unset once newId() was called");

    ModificationLog selected = new ModificationLog(db).selectObject(id);
    assertNotNull(selected, "the inserted row must be selectable");
    assertEquals(selected.getUserId(), 4711);
    assertEquals(selected.getTxName(), "insert");

    selected.setUserId(4712);
    selected.updateObject();

    ModificationLog updated = new ModificationLog(db).selectObject(id);
    assertEquals(updated.getUserId(), 4712, "the update must have persisted the new value");

    updated.deleteObject();

    assertNull(new ModificationLog(db).selectObject(id), "the row must be gone after deleteObject()");
  }


  @Test
  public void testSerialAdvancesOnUpdate() {
    Db db = getSession();

    ModificationLog log = new ModificationLog(db);
    log.setModType(DbModificationType.INSERT);
    log.setUserId(1);
    log.newId();
    log.insertObject();

    long serialAfterInsert = log.getSerial();

    log.setUserId(2);
    log.updateObject();

    assertEquals(log.getSerial(), serialAfterInsert + 1, "updateObject() must advance the serial by exactly 1");
  }


  @Test
  public void testOptimisticLockConflictThrowsNotFoundException() {
    Db db = getSession();

    ModificationLog log = new ModificationLog(db);
    log.setModType(DbModificationType.INSERT);
    log.setUserId(1);
    log.newId();
    log.insertObject();
    long id = log.getId();

    // two "editors" load the same row
    ModificationLog editor1 = new ModificationLog(db).selectObject(id);
    ModificationLog editor2 = new ModificationLog(db).selectObject(id);

    editor1.setUserId(2);
    editor1.updateObject();   // wins the race, serial advances

    editor2.setUserId(3);
    NotFoundException nfx = expectThrows(NotFoundException.class, editor2::updateObject);
    assertTrue(nfx.isTemporary(), "a stale-serial conflict must be reported as a temporary failure");
  }


  @Test
  public void testDeleteWithStaleSerialThrowsNotFoundException() {
    Db db = getSession();

    ModificationLog log = new ModificationLog(db);
    log.setModType(DbModificationType.INSERT);
    log.setUserId(1);
    log.newId();
    log.insertObject();
    long id = log.getId();

    ModificationLog editor1 = new ModificationLog(db).selectObject(id);
    ModificationLog editor2 = new ModificationLog(db).selectObject(id);

    editor1.setUserId(2);
    editor1.updateObject();

    expectThrows(NotFoundException.class, editor2::deleteObject);
  }


  @Test
  public void testDuplicateInsertIsSilentlySkippedByVisitor() {
    Db db = getSession();

    DbTransactionHandle handle = db.registerPersistenceVisitor(new IgnoreDuplicatesPersistenceVisitor());
    try {
      ModificationLog log = new ModificationLog(db);
      log.setModType(DbModificationType.INSERT);
      log.setUserId(1);
      log.newId();
      log.insertObject();          // the visitor sees this object for the first time -> allowed
      long id = log.getId();

      // inserting the very same instance again must be silently skipped, not raise a duplicate-key error
      log.insertObject();

      assertNotNull(new ModificationLog(db).selectObject(id), "the row from the first insert must still be there");

      ModificationLog other = new ModificationLog(db);
      other.setModType(DbModificationType.INSERT);
      other.setUserId(2);
      other.newId();
      other.insertObject();        // a distinct object must still be inserted normally
      assertNotEquals(id, other.getId(), "a distinct object must get its own id");
      assertNotNull(new ModificationLog(db).selectObject(other.getId()));
    }
    finally {
      db.unregisterPersistenceVisitor(handle);
    }
  }

}

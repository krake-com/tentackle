/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.persist;

import org.testng.annotations.Test;

import org.tentackle.common.DateHelper;
import org.tentackle.common.Timestamp;
import org.tentackle.pdo.LockException;
import org.tentackle.pdo.Pdo;
import org.tentackle.persist.lock.DbTokenLock;
import org.tentackle.session.NotFoundException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Database-backed tests for the token lock.
 * <p>
 * Two things are covered: the {@code tokenlock} table behind
 * {@link org.tentackle.persist.lock.DbTokenLock}, whose composite key makes it the one entity of
 * the reactor that is not keyed by its ID alone, and the token-lock columns of a PDO, whose
 * statements {@link AbstractPersistentObjectSqlTest} asserts as strings but never executes.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class TokenLockDbTest extends AbstractPersistenceDbTest {

  private static final int OTHER_USER = 4711;


  private DbTokenLock tokenLock(int pdoClassId, long pdoId, long lockedBy) {
    DbTokenLock lock = new DbTokenLock(getSession());
    lock.setId(pdoId);
    lock.setPdoClassId(pdoClassId);
    lock.setLockedBy(lockedBy);
    lock.setLockedSince(DateHelper.now());
    lock.setLockExpiry(DateHelper.now(60000));
    return lock;
  }

  private DemoLockablePersistenceImpl lockable(String text) {
    DemoLockablePersistenceImpl po =
        (DemoLockablePersistenceImpl) Pdo.create(DemoLockable.class, getDomainContext()).getPersistenceDelegate();
    po.setText(text);
    return po;
  }

  private DemoLockablePersistenceImpl finder() {
    return (DemoLockablePersistenceImpl) Pdo.create(DemoLockable.class, getDomainContext()).getPersistenceDelegate();
  }


  // --- DbTokenLock: the tokenlock table -------------------------------------------------------

  @Test
  public void testInsertAndSelectATokenLock() {
    DbTokenLock lock = tokenLock(9005, 100, USER_ID);
    lock.insertObject();

    DbTokenLock loaded = new DbTokenLock(getSession()).selectByPdoClassIdAndId(9005, 100);
    assertNotNull(loaded);
    assertEquals(loaded.getLockedBy(), USER_ID);
    assertEquals(loaded.getPdoClassId(), 9005);
    assertEquals(loaded.getPdoId(), 100L);
  }

  @Test
  public void testTheSameIdInDifferentPdoClassesAreDifferentLocks() {
    // the tokenlock table is keyed by (id, pdoclassid), not by id alone
    tokenLock(9001, 100, USER_ID).insertObject();
    tokenLock(9005, 100, OTHER_USER).insertObject();

    assertEquals(new DbTokenLock(getSession()).selectByPdoClassIdAndId(9001, 100).getLockedBy(), USER_ID);
    assertEquals(new DbTokenLock(getSession()).selectByPdoClassIdAndId(9005, 100).getLockedBy(), OTHER_USER);
  }

  @Test
  public void testReloadSelectsByTheCompositeKey() {
    tokenLock(9001, 100, USER_ID).insertObject();
    DbTokenLock lock = tokenLock(9005, 100, OTHER_USER);
    lock.insertObject();

    DbTokenLock reloaded = lock.reloadObject();
    assertEquals(reloaded.getPdoClassId(), 9005, "must not pick up the lock of the other PDO class");
    assertEquals(reloaded.getLockedBy(), OTHER_USER);
  }

  @Test
  public void testUpdateAndDeleteUseTheCompositeKey() {
    tokenLock(9001, 100, USER_ID).insertObject();
    DbTokenLock lock = tokenLock(9005, 100, OTHER_USER);
    lock.insertObject();

    lock.setLockedBy(USER_ID);
    lock.updateObject();
    assertEquals(lock.getSerial(), 2L);
    assertEquals(new DbTokenLock(getSession()).selectByPdoClassIdAndId(9005, 100).getLockedBy(), USER_ID);
    assertEquals(new DbTokenLock(getSession()).selectByPdoClassIdAndId(9001, 100).getLockedBy(), USER_ID,
                 "the other class's lock is untouched");

    lock.deleteObject();
    assertNull(new DbTokenLock(getSession()).selectByPdoClassIdAndId(9005, 100));
    assertNotNull(new DbTokenLock(getSession()).selectByPdoClassIdAndId(9001, 100),
                  "deleting by id alone would have removed this one too");
  }

  @Test
  public void testDeleteWithAStaleSerialIsRejected() {
    DbTokenLock lock = tokenLock(9005, 100, USER_ID);
    lock.insertObject();
    DbTokenLock other = new DbTokenLock(getSession()).selectByPdoClassIdAndId(9005, 100);
    other.setLockedBy(OTHER_USER);
    other.updateObject();

    expectThrows(NotFoundException.class, lock::deleteObject);
  }

  @Test
  public void testDiagnosticString() {
    DbTokenLock lock = tokenLock(9005, 100, USER_ID);
    String text = lock.toDiagnosticString();
    assertTrue(text.startsWith("classId 9005, id 100, locked by " + USER_ID + ", since "), text);
  }


  // --- the token-lock columns of a PDO --------------------------------------------------------

  @Test
  public void testANewObjectIsNeverTokenLocked() {
    DemoLockablePersistenceImpl po = lockable("fresh");
    assertFalse(po.isTokenLocked());
  }

  @Test
  public void testRequestingTheTokenLock() {
    DemoLockablePersistenceImpl po = lockable("locked");
    po.save();

    Timestamp expiry = DateHelper.now(60000);
    po.updateTokenLock(expiry, USER_ID, DateHelper.now());

    assertEquals(po.getEditedBy(), USER_ID);
    assertTrue(po.isTokenLocked());
    assertTrue(po.isTokenLockedBy(USER_ID));
    assertFalse(po.isTokenLockedBy(OTHER_USER));

    // and it is persisted
    DemoLockablePersistenceImpl loaded = (DemoLockablePersistenceImpl) finder().select(po.getId()).getPersistenceDelegate();
    assertEquals(loaded.getEditedBy(), USER_ID);
    assertTrue(loaded.isTokenLocked());
  }

  @Test
  public void testReleasingTheTokenLock() {
    DemoLockablePersistenceImpl po = lockable("released");
    po.save();
    po.updateTokenLock(DateHelper.now(60000), USER_ID, DateHelper.now());
    po.updateTokenLock(null, USER_ID, DateHelper.now());

    assertEquals(po.getEditedBy(), 0L);
    assertNull(po.getEditedExpiry());
    assertFalse(po.isTokenLocked());
    assertFalse(((DemoLockablePersistenceImpl) finder().select(po.getId()).getPersistenceDelegate()).isTokenLocked());
  }

  @Test
  public void testTheLockOfAnotherUserIsRefused() {
    DemoLockablePersistenceImpl po = lockable("contended");
    po.save();
    po.updateTokenLock(DateHelper.now(60000), OTHER_USER, DateHelper.now());

    DemoLockablePersistenceImpl mine =
        (DemoLockablePersistenceImpl) finder().select(po.getId()).getPersistenceDelegate();
    LockException lx = expectThrows(LockException.class,
                                    () -> mine.updateTokenLock(DateHelper.now(60000), USER_ID, DateHelper.now()));
    // the PDO is updated with what the database holds, so the caller can tell who is holding it
    assertEquals(lx.getTokenLockInfo().editedBy(), (long) OTHER_USER);
    assertEquals(mine.getEditedBy(), (long) OTHER_USER);
  }

  @Test
  public void testAnExpiredLockCanBeTakenOver() {
    DemoLockablePersistenceImpl po = lockable("expired");
    po.save();
    // a lock that expired one minute ago
    po.updateTokenLock(DateHelper.now(-60000), OTHER_USER, DateHelper.now());
    assertFalse(po.isTokenLocked(), "an expired lock is no lock");

    DemoLockablePersistenceImpl mine =
        (DemoLockablePersistenceImpl) finder().select(po.getId()).getPersistenceDelegate();
    mine.updateTokenLock(DateHelper.now(60000), USER_ID, DateHelper.now());
    assertEquals(mine.getEditedBy(), USER_ID);
  }

  @Test
  public void testUpdatingTheTokenLockOfAModifiedObjectIsRejected() {
    DemoLockablePersistenceImpl po = lockable("moved");
    po.save();

    DemoLockablePersistenceImpl other =
        (DemoLockablePersistenceImpl) finder().select(po.getId()).getPersistenceDelegate();
    other.setText("moved on");
    other.save();

    // po still carries serial 1, the row is at 2
    NotFoundException nfx = expectThrows(NotFoundException.class,
                                         () -> po.updateTokenLock(DateHelper.now(60000), USER_ID, DateHelper.now()));
    assertTrue(nfx.getMessage().contains("updated in the meantime with serial 2"), nfx.getMessage());
  }

  @Test
  public void testTransferTheTokenLockToAnotherUser() {
    DemoLockablePersistenceImpl po = lockable("transferred");
    po.save();
    po.updateTokenLock(DateHelper.now(60000), USER_ID, DateHelper.now());

    DemoLockable transferred = po.transferTokenLock(OTHER_USER);
    assertNotNull(transferred);
    assertEquals(((DemoLockablePersistenceImpl) transferred.getPersistenceDelegate()).getEditedBy(), (long) OTHER_USER);
    assertEquals(((DemoLockablePersistenceImpl) finder().select(po.getId()).getPersistenceDelegate()).getEditedBy(),
                 (long) OTHER_USER);
  }

  @Test
  public void testSelectTokenLockedAcquiresTheLock() {
    DemoLockablePersistenceImpl po = lockable("selected");
    po.save();

    DemoLockable locked = finder().selectTokenLocked(po.getId());
    assertNotNull(locked);
    DemoLockablePersistenceImpl lockedPo = (DemoLockablePersistenceImpl) locked.getPersistenceDelegate();
    assertEquals(lockedPo.getEditedBy(), USER_ID, "locked for the session's user");
    assertTrue(lockedPo.isTokenLocked());
  }

  @Test
  public void testSavingClearsTheTokenLockUnlessRenewIsRequested() {
    DemoLockablePersistenceImpl po = lockable("cleared");
    po.save();
    po.updateTokenLock(DateHelper.now(60000), USER_ID, DateHelper.now());
    assertTrue(po.isTokenLocked());

    po.setText("cleared again");
    po.save();
    assertEquals(po.getEditedBy(), 0L, "a plain save releases the token");
    assertFalse(((DemoLockablePersistenceImpl) finder().select(po.getId()).getPersistenceDelegate()).isTokenLocked());
  }

  @Test
  public void testSavingKeepsTheTokenLockIfRenewIsRequested() {
    DemoLockablePersistenceImpl po = lockable("renewed");
    po.save();
    po.updateTokenLock(DateHelper.now(60000), USER_ID, DateHelper.now());

    po.setRenewTokenLockRequested(true);
    po.setText("renewed again");
    po.save();

    assertEquals(po.getEditedBy(), USER_ID);
    assertTrue(((DemoLockablePersistenceImpl) finder().select(po.getId()).getPersistenceDelegate()).isTokenLocked());
    assertFalse(po.isRenewTokenLockRequested(), "the flag is consumed by the save");
  }

  @Test
  public void testRenewingTheTokenLockOfAnotherUserIsRejected() {
    DemoLockablePersistenceImpl po = lockable("foreign");
    po.save();
    po.updateTokenLock(DateHelper.now(60000), OTHER_USER, DateHelper.now());

    expectThrows(LockException.class, () -> po.setRenewTokenLockRequested(true));
  }

}

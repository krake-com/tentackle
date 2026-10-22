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

package org.tentackle.persist.lock;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.DateHelper;
import org.tentackle.pdo.Pdo;
import org.tentackle.persist.AbstractPersistenceDbTest;
import org.tentackle.persist.DemoLockable;
import org.tentackle.persist.DemoLockablePersistenceImpl;
import org.tentackle.persist.DemoServerApplication;
import org.tentackle.persist.TokenLock;

import java.util.Collection;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Database-backed tests for the bookkeeping of {@link DefaultLockManager}.
 * <p>
 * {@link DefaultLockManagerTest} covers the passive case: outside a server application the lock
 * manager does nothing at all. Everything else needs a registered server application, a session and
 * the {@code tokenlock} table, and is covered here &mdash; in particular the in-flight maps, which
 * have to survive a rollback of the transaction that created, updated or removed a lock.
 * <p>
 * The lock manager is driven directly rather than through the PDO: the
 * {@link org.tentackle.persist.LockManager} singleton was created when the first test of this JVM
 * touched it, i.e. without a server application, and is therefore disabled for good.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class DefaultLockManagerDbTest extends AbstractPersistenceDbTest {

  private static final int DEMO_LOCKABLE_CLASS_ID = 9005;
  private static final long OTHER_USER = 4711;

  private DemoServerApplication application;
  private DefaultLockManager lockManager;
  private long txVoucher;


  /**
   * Creates the test with no automatic transaction: the tests commit and roll back themselves.
   */
  public DefaultLockManagerDbTest() {
    super(TransactionType.NONE);
  }


  @BeforeMethod
  public void registerServerApplication() {
    application = new DemoServerApplication(getDomainContext());
    application.register();
    // the lock manager reads the application in its constructor
    lockManager = new DefaultLockManager();
    txVoucher = getSession().begin("lock manager test");
  }

  @AfterMethod
  public void unregisterServerApplication() {
    if (getSession().isTxRunning()) {
      getSession().rollback(txVoucher);
    }
    // leave no rows behind: unlike the other tests of this module, these commit
    getSession().runScript("cleanup", "DELETE FROM tokenlock; DELETE FROM demolockable;");
    application.unregister();
  }


  /**
   * Creates a saved and token-locked PDO.
   *
   * @param text the text
   * @param lockedBy the user holding the token
   * @return the persistence delegate
   */
  private DemoLockablePersistenceImpl lockedPdo(String text, long lockedBy) {
    DemoLockablePersistenceImpl po =
        (DemoLockablePersistenceImpl) Pdo.create(DemoLockable.class, getDomainContext()).getPersistenceDelegate();
    po.setText(text);
    po.save();
    // through updateTokenLock, not the setters: cleanupUserTokens compares the token columns of
    // the PDO row against the lock's owner, so they have to be persisted
    po.updateTokenLock(DateHelper.now(60000), lockedBy, DateHelper.now());
    assertTrue(po.isTokenLocked());
    return po;
  }

  private DbTokenLock persistedLock(long pdoId) {
    return new DbTokenLock(getSession()).selectByPdoClassIdAndId(DEMO_LOCKABLE_CLASS_ID, pdoId);
  }

  /**
   * Loads a PDO fresh from the database.
   *
   * @param pdoId the object ID
   * @return the persistence delegate
   */
  private DemoLockablePersistenceImpl selectLockable(long pdoId) {
    return (DemoLockablePersistenceImpl) Pdo.create(DemoLockable.class, getDomainContext())
                                            .getPersistenceDelegate().select(pdoId).getPersistenceDelegate();
  }


  @Test
  public void testEnabledWithinAServerApplication() {
    assertTrue(lockManager.isEnabled(), "a server application with a local session enables the lock manager");
  }

  @Test
  public void testUpdateCreatesTheLock() {
    DemoLockablePersistenceImpl po = lockedPdo("created", USER_ID);
    lockManager.update(po.getPdo());

    TokenLock lock = lockManager.getLock(po.getPdo());
    assertNotNull(lock, "the lock is registered in the map");
    assertEquals(lock.getLockedBy(), USER_ID);
    assertEquals(lock.getPdoId(), po.getId());

    DbTokenLock persisted = persistedLock(po.getId());
    assertNotNull(persisted, "and written to the tokenlock table");
    assertEquals(persisted.getLockedBy(), USER_ID);
  }

  @Test
  public void testUpdateOfAnUnlockedPdoReleasesTheLock() {
    DemoLockablePersistenceImpl po = lockedPdo("released", USER_ID);
    lockManager.update(po.getPdo());
    assertNotNull(lockManager.getLock(po.getPdo()));

    po.clearTokenLock();
    assertFalse(po.isTokenLocked());
    lockManager.update(po.getPdo());

    assertNull(lockManager.getLock(po.getPdo()), "gone from the map");
    assertNull(persistedLock(po.getId()), "and from the tokenlock table");
  }

  @Test
  public void testGetLocks() {
    DemoLockablePersistenceImpl mine = lockedPdo("mine", USER_ID);
    DemoLockablePersistenceImpl theirs = lockedPdo("theirs", OTHER_USER);
    lockManager.update(mine.getPdo());
    lockManager.update(theirs.getPdo());

    assertEquals(lockManager.getLocks().size(), 2);
    Collection<TokenLock> ofUser = lockManager.getLocks(USER_ID);
    assertEquals(ofUser.size(), 1);
    assertEquals(ofUser.iterator().next().getPdoId(), mine.getId());
    assertEquals(lockManager.getLocks(OTHER_USER).size(), 1);
    assertTrue(lockManager.getLocks(1).isEmpty(), "nothing for a user without locks");
  }

  @Test
  public void testCommitKeepsACreatedLock() {
    DemoLockablePersistenceImpl po = lockedPdo("committed", USER_ID);
    lockManager.update(po.getPdo());

    long txNumber = getSession().getTxNumber();
    getSession().commit(txVoucher);
    lockManager.commit(getSession(), txNumber);

    assertNotNull(lockManager.getLock(po.getPdo()), "a committed lock stays in the map");
  }

  @Test
  public void testRollbackRemovesACreatedLock() {
    DemoLockablePersistenceImpl po = lockedPdo("rolled back", USER_ID);
    lockManager.update(po.getPdo());
    assertNotNull(lockManager.getLock(po.getPdo()));

    long txNumber = getSession().getTxNumber();
    getSession().rollback(txVoucher);
    lockManager.rollback(getSession(), txNumber);

    assertNull(lockManager.getLock(po.getPdo()), "the map follows the database back");
    assertTrue(lockManager.getLocks().isEmpty());
  }

  @Test
  public void testRollbackRestoresARemovedLock() {
    // create and commit a lock first
    DemoLockablePersistenceImpl po = lockedPdo("restored", USER_ID);
    lockManager.update(po.getPdo());
    long firstTx = getSession().getTxNumber();
    getSession().commit(txVoucher);
    lockManager.commit(getSession(), firstTx);

    // release it in a second transaction, then roll that back
    txVoucher = getSession().begin("release");
    po.clearTokenLock();
    lockManager.update(po.getPdo());
    assertNull(lockManager.getLock(po.getPdo()));

    long secondTx = getSession().getTxNumber();
    getSession().rollback(txVoucher);
    lockManager.rollback(getSession(), secondTx);

    TokenLock restored = lockManager.getLock(po.getPdo());
    assertNotNull(restored, "the release was rolled back, so the lock is back in the map");
    assertEquals(restored.getLockedBy(), USER_ID);
  }

  @Test
  public void testALockCreatedAndRemovedInOneTransactionNetsToNothingOnRollback() {
    DemoLockablePersistenceImpl po = lockedPdo("transient", USER_ID);
    lockManager.update(po.getPdo());        // created in flight
    po.clearTokenLock();
    lockManager.update(po.getPdo());        // removed again, same transaction

    long txNumber = getSession().getTxNumber();
    getSession().rollback(txVoucher);
    lockManager.rollback(getSession(), txNumber);

    // the removal must not be restored: the lock never existed before this transaction
    assertNull(lockManager.getLock(po.getPdo()));
    assertTrue(lockManager.getLocks().isEmpty());
  }

  @Test
  public void testInitializeRemovesTheStaleLocksOfTheLastRun() {
    // a lock left behind by a crashed server: the row exists, the map does not know it
    DemoLockablePersistenceImpl po = lockedPdo("stale", USER_ID);
    lockManager.update(po.getPdo());
    long txNumber = getSession().getTxNumber();
    getSession().commit(txVoucher);
    lockManager.commit(getSession(), txNumber);

    DefaultLockManager restarted = new DefaultLockManager();
    assertTrue(restarted.getLocks().isEmpty(), "a fresh lock manager knows nothing");
    restarted.initialize(getSession());

    // isTokenLockToKeep() is false by default, so the stale lock is dropped
    assertTrue(restarted.getLocks().isEmpty());
    assertNull(persistedLock(po.getId()), "and removed from the tokenlock table");
    assertFalse(selectLockable(po.getId()).isTokenLocked(), "the token columns of the PDO are cleared as well");

    txVoucher = getSession().begin("after initialize");
  }

  @Test
  public void testCleanupUserTokensRemovesTheRowsOfOneUserOnly() {
    DemoLockablePersistenceImpl mine = lockedPdo("mine", USER_ID);
    DemoLockablePersistenceImpl theirs = lockedPdo("theirs", OTHER_USER);
    lockManager.update(mine.getPdo());
    lockManager.update(theirs.getPdo());
    long txNumber = getSession().getTxNumber();
    getSession().commit(txVoucher);
    lockManager.commit(getSession(), txNumber);

    lockManager.cleanupUserTokens(getSession(), USER_ID);

    assertNull(persistedLock(mine.getId()), "the tokenlock row of the user is gone");
    assertNotNull(persistedLock(theirs.getId()), "the other user keeps his lock");

    // and the token columns of the PDO itself are cleared
    assertFalse(selectLockable(mine.getId()).isTokenLocked());
    assertTrue(selectLockable(theirs.getId()).isTokenLocked());

    /*
     * Not asserted: that the lock also disappears from the lock manager's map. cleanupUserTokens()
     * goes through updateTokenLockOnly(), which notifies LockManager.getInstance() -- the
     * singleton, created by ServiceFactory when the first test of this JVM touched it, i.e.
     * without a server application and therefore disabled for good. In a real server the singleton
     * and the lock manager doing the cleanup are the same object.
     */

    txVoucher = getSession().begin("after cleanup");
  }

}

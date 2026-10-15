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

import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests returning sessions to a {@link DbPool}.
 * <p>
 * According to {@link org.tentackle.misc.AbstractPool#put}, a session may be returned again
 * after it has been returned to or removed from the pool. Its pool ID is 0 or -1 then,
 * i.e. its slot index is not known anymore and must not be used to access the pool's slots.
 * <p>
 * Cleaning up a returned session may perform I/O, such as rolling back a pending transaction or
 * a remote round trip. This must not block the other users of the pool.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DbPoolReturnTest {

  /**
   * A session that can pretend to be remote and within a transaction.<br>
   * Both are only switched on <em>after</em> the session has been created, since the real
   * behavior is needed while the session is opened.
   */
  private static class PretendingDb extends Db {

    private boolean pretendRemoteTx;
    private volatile CountDownLatch clearBlocked;     // != null if clearProperties blocks until counted down
    private volatile CountDownLatch clearEntered;     // counted down when clearProperties started blocking

    private PretendingDb(ConnectionManager conMgr, SessionInfo sessionInfo) {
      super(conMgr, sessionInfo);
    }

    @Override
    public boolean isRemote() {
      return pretendRemoteTx || super.isRemote();
    }

    @Override
    public boolean isTxRunning() {
      return pretendRemoteTx || super.isTxRunning();
    }

    @Override
    public void clearProperties() {
      CountDownLatch blocked = clearBlocked;
      if (blocked != null) {
        clearBlocked = null;
        clearEntered.countDown();
        try {
          blocked.await();    // as a slow remote round trip
        }
        catch (InterruptedException ix) {
          Thread.currentThread().interrupt();
        }
      }
      if (pretendRemoteTx) {
        return;   // there is no remote delegate to forward this to
      }
      super.clearProperties();
    }
  }


  private static class PretendingDbPool extends DbPool {

    private PretendingDbPool(ConnectionManager conMgr, SessionInfo sessionInfo) {
      super("pool-return-test", conMgr, sessionInfo, 0, 1, 1, 1, 4, 0, 0);
    }

    @Override
    protected Db createSession(int slotNumber) {
      return new PretendingDb(getConnectionManager(), createSessionInfo(slotNumber));
    }
  }


  /**
   * Returning a remote session running a transaction a second time must be reported,
   * but must not access the pool's slots with the meanwhile invalidated pool ID.
   */
  @Test
  public void testReturningRemoteSessionInTransactionTwice() {
    ConnectionManager conMgr = new DefaultConnectionManager("pool-return-test-mgr", 2, 8, 1, 720, 2160);
    DbPool pool = new PretendingDbPool(conMgr, new DefaultSessionInfo());
    try {
      PretendingDb db = (PretendingDb) pool.get();
      assertTrue(db.getPoolId() > 0, "a lent session must have a pool ID");

      pool.put(db);   // regular return
      assertEquals(db.getPoolId(), 0, "a returned session has no pool ID anymore");
      int size = pool.getSize();

      // now the session pretends to be a remote one running a transaction
      db.pretendRemoteTx = true;

      // returning it again must throw a PersistenceException, not an ArrayIndexOutOfBoundsException
      assertThrows(PersistenceException.class, () -> pool.put(db));

      assertEquals(pool.getSize(), size, "the pool must be left intact");
      db.pretendRemoteTx = false;

      // the pool must still be usable
      Session again = pool.get();
      pool.put(again);
    }
    finally {
      pool.shutdown();
      conMgr.shutdown();
    }
  }


  /**
   * Returning a session must not block the pool while the session is cleaned up.
   */
  @Test(timeOut = 60000)
  public void testReturnDoesNotBlockPool() throws Exception {
    ConnectionManager conMgr = new DefaultConnectionManager("pool-return-block-test-mgr", 2, 8, 1, 720, 2160);
    DbPool pool = new PretendingDbPool(conMgr, new DefaultSessionInfo());
    try {
      PretendingDb db = (PretendingDb) pool.get();
      CountDownLatch blocked = new CountDownLatch(1);
      db.clearEntered = new CountDownLatch(1);
      db.clearBlocked = blocked;

      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread returningThread = new Thread(() -> {
        try {
          pool.put(db);
        }
        catch (Throwable t) {
          failure.set(t);
        }
      }, "returning thread");
      returningThread.start();
      assertTrue(db.clearEntered.await(10, TimeUnit.SECONDS), "cleanup not started");

      // the pool must be usable by other threads meanwhile
      AtomicReference<Throwable> otherFailure = new AtomicReference<>();
      Thread otherThread = new Thread(() -> {
        try {
          Session other = pool.get();
          pool.put(other);
        }
        catch (Throwable t) {
          otherFailure.set(t);
        }
      }, "other pool user");
      otherThread.start();
      otherThread.join(5000);
      boolean otherBlocked = otherThread.isAlive();

      blocked.countDown();
      returningThread.join(10000);
      otherThread.join(10000);

      assertFalse(otherBlocked, "pool blocked while a returned session was cleaned up");
      assertEquals(failure.get(), null, "returning the session failed");
      assertEquals(otherFailure.get(), null, "using the pool failed");
      assertEquals(db.getPoolId(), 0, "session not returned to the pool");
    }
    finally {
      pool.shutdown();
      conMgr.shutdown();
    }
  }

  /**
   * A local session returned within a transaction is rolled back and kept in the pool.
   */
  @Test
  public void testLocalTransactionRolledBackOnReturn() {
    ConnectionManager conMgr = new DefaultConnectionManager("pool-return-tx-test-mgr", 2, 8, 1, 720, 2160);
    DbPool pool = new PretendingDbPool(conMgr, new DefaultSessionInfo());
    try {
      Db db = (Db) pool.get();
      int size = pool.getSize();
      db.begin("returned within transaction");
      assertTrue(db.isTxRunning());

      PersistenceException px = expectThrows(PersistenceException.class, () -> pool.put(db));
      assertTrue(px.getMessage().contains("rolled back"), px.getMessage());
      assertFalse(db.isTxRunning(), "transaction not rolled back");
      assertEquals(db.getPoolId(), 0, "session not returned to the pool");
      assertEquals(pool.getSize(), size, "session removed from the pool");

      // the session is usable again
      Session again = pool.get();
      assertEquals(again, db);
      pool.put(again);
    }
    finally {
      pool.shutdown();
      conMgr.shutdown();
    }
  }

}

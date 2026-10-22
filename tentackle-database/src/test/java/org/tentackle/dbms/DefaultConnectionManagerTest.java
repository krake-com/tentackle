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
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendVersionInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link DefaultConnectionManager}, the strict 1:1 session-to-connection manager — the
 * variant not exercised by {@link DbPoolTest}/{@link MultiUserDbPoolTest}, which both drive
 * {@link MpxConnectionManager} instead.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class DefaultConnectionManagerTest {

  private static final StatementId SELECT_ONE_STATEMENT_ID = new StatementId();

  private void assertConnectionWorks(Db db) {
    PreparedStatementWrapper st = db.getPreparedStatement(new StatementKey(SELECT_ONE_STATEMENT_ID, getClass()),
                                                           false, Backend::getDummySelect);
    try (ResultSetWrapper rs = st.executeQuery()) {
      assertTrue(rs.next());
    }
  }


  @Test
  public void testAttachDetachRoundTrip() {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("attach-detach-test", 2, 8, 1, 720, 2160);
    try {
      Db db = new Db(conMgr, new DefaultSessionInfo());
      try {
        db.attach();
        assertConnectionWorks(db);
        assertTrue(db.connection().getBackendVersionInfo().majorVersion() > 0);
        db.detach();

        // a second session must be able to attach cleanly afterward
        Db db2 = new Db(conMgr, new DefaultSessionInfo());
        try {
          db2.attach();
          assertConnectionWorks(db2);
          db2.detach();
        }
        finally {
          db2.close();
        }
      }
      finally {
        db.close();
      }
    }
    finally {
      conMgr.shutdown();
    }
  }


  @Test
  public void testMaxSizeIsEnforced() {
    int maxSize = 2;
    DefaultConnectionManager conMgr = new DefaultConnectionManager("max-size-test", maxSize, maxSize, 1, 720, 2160);
    List<Db> sessions = new ArrayList<>();
    try {
      for (int i = 0; i < maxSize; i++) {
        sessions.add(new Db(conMgr, new DefaultSessionInfo()));
      }
      assertEquals(conMgr.getNumSessions(), maxSize);

      assertThrows(PersistenceException.class, () -> new Db(conMgr, new DefaultSessionInfo()));
    }
    finally {
      sessions.forEach(Db::close);
      conMgr.shutdown();
    }
  }


  @Test
  public void testForceDetach() {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("force-detach-test", 2, 8, 1, 720, 2160);
    try {
      Db db = new Db(conMgr, new DefaultSessionInfo());
      try {
        db.attach();
        conMgr.forceDetach(db);

        // the session must still be usable afterward: attach transparently reopens if necessary
        db.attach();
        assertConnectionWorks(db);
        db.detach();
      }
      finally {
        db.close();
      }
    }
    finally {
      conMgr.shutdown();
    }
  }


  @Test
  public void testShutdownClosesAllConnections() {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("shutdown-test", 2, 8, 1, 720, 2160);
    new Db(conMgr, new DefaultSessionInfo());
    new Db(conMgr, new DefaultSessionInfo());

    assertTrue(conMgr.getNumConnections() > 0, "logging in must have created physical connections");
    conMgr.shutdown();
    assertEquals(conMgr.getNumConnections(), 0, "shutdown must close every managed connection");
  }


  @Test
  public void testShutdownWhileReopeningDeadConnection() throws Exception {
    CountDownLatch connecting = new CountDownLatch(1);
    CountDownLatch proceed = new CountDownLatch(1);
    AtomicReference<ManagedConnection> reopened = new AtomicReference<>();
    DefaultConnectionManager conMgr = new DefaultConnectionManager("reopen-shutdown-test", 2, 8, 1, 720, 2160) {
      private volatile boolean block;

      @Override
      public void attach(Db session) {
        block = true;   // only the reopen within attach blocks, not the login
        super.attach(session);
      }

      @Override
      protected Connection connect(BackendInfo backendInfo) throws SQLException {
        if (block) {
          connecting.countDown();
          try {
            proceed.await();
          }
          catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
        return super.connect(backendInfo);
      }

      @Override
      protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
        ManagedConnection mc = super.createManagedConnection(backend, versionInfo, connection);
        if (block) {
          reopened.set(mc);
        }
        return mc;
      }
    };

    Db db = new Db(conMgr, new DefaultSessionInfo());
    db.attach();
    db.connection().setDead(true);   // force a reopen on the next attach
    db.detach();

    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread attacher = new Thread(() -> {
      try {
        db.attach();
      }
      catch (Throwable t) {
        failure.set(t);
      }
    });
    attacher.start();
    assertTrue(connecting.await(10, TimeUnit.SECONDS), "reopen did not start");

    conMgr.shutdown();    // releases the slot while the connection is being reopened
    proceed.countDown();
    attacher.join(10000);

    assertTrue(failure.get() instanceof PersistenceException, "attach must fail after shutdown, got " + failure.get());
    assertEquals(conMgr.getNumConnections(), 0, "the reopened connection must not be stored in a released slot");
    assertNotNull(reopened.get());
    assertTrue(reopened.get().isClosed(), "the reopened connection must be closed");
  }


  @Test
  public void testShutdownWhileLoggingIn() throws Exception {
    CountDownLatch connecting = new CountDownLatch(1);
    CountDownLatch proceed = new CountDownLatch(1);
    AtomicReference<ManagedConnection> created = new AtomicReference<>();
    DefaultConnectionManager conMgr = new DefaultConnectionManager("login-shutdown-test", 2, 8, 1, 720, 2160) {
      @Override
      protected Connection connect(BackendInfo backendInfo) throws SQLException {
        connecting.countDown();
        try {
          proceed.await();
        }
        catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
        return super.connect(backendInfo);
      }

      @Override
      protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
        ManagedConnection mc = super.createManagedConnection(backend, versionInfo, connection);
        created.set(mc);
        return mc;
      }
    };

    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread loginThread = new Thread(() -> {
      try {
        new Db(conMgr, new DefaultSessionInfo());
      }
      catch (Throwable t) {
        failure.set(t);
      }
    });
    loginThread.start();
    assertTrue(connecting.await(10, TimeUnit.SECONDS), "login did not start");

    conMgr.shutdown();    // closes all connections while the login is still connecting
    proceed.countDown();
    loginThread.join(10000);

    assertTrue(failure.get() instanceof PersistenceException, "login must fail after shutdown, got " + failure.get());
    assertEquals(conMgr.getNumConnections(), 0, "the new connection must not be stored after shutdown");
    assertEquals(conMgr.getNumSessions(), 0, "the session slot must be released");
    assertNotNull(created.get());
    assertTrue(created.get().isClosed(), "the new connection must be closed");
  }


  /**
   * Sessions may still be attached, detached and logged out after the connection manager has been shut down,
   * for example, if a server shuts down while its sessions are still in use.
   */
  @Test
  public void testSessionAfterShutdown() {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("after-shutdown-test", 2, 8, 1, 720, 2160);
    Db db = new Db(conMgr, new DefaultSessionInfo());
    Db db2 = new Db(conMgr, new DefaultSessionInfo());
    db.attach();
    ManagedConnection con = db.getConnection();
    assertNotNull(con);

    conMgr.shutdown();    // closes all connections, detaching their sessions
    assertTrue(con.isClosed());
    assertNull(db.getConnection());

    assertThrows(PersistenceException.class, db::detach);
    db.forceDetached();   // nothing to clean up

    conMgr.logout(db);
    assertEquals(db.getSessionId(), 0, "the session must be logged out");
    // cleanup is meant for unreferenced sessions: their cleaner must not clean up the session once more
    conMgr.cleanup(db2.getSessionId(), null);
    db2.clearSessionId();
    assertEquals(conMgr.getNumSessions(), 0, "the session slots must be released");
    assertEquals(conMgr.getNumConnections(), 0);
  }

  /**
   * Releasing a session slot twice must be rejected.
   * Otherwise, the slot would be handed out to two sessions.
   */
  @Test
  public void testSessionSlotReleasedTwice() {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("released-twice-test", 2, 8, 1, 720, 2160);
    try {
      Db db = new Db(conMgr, new DefaultSessionInfo());
      int sessionId = db.getSessionId();
      conMgr.cleanup(sessionId, null);
      db.clearSessionId();    // as if unreferenced
      assertEquals(conMgr.getNumSessions(), 0);

      assertThrows(PersistenceException.class, () -> conMgr.cleanup(sessionId, null));
      assertEquals(conMgr.getNumSessions(), 0, "the slot must not be released twice");

      Db db1 = new Db(conMgr, new DefaultSessionInfo());
      Db db2 = new Db(conMgr, new DefaultSessionInfo());
      try {
        assertNotEquals(db1.getSessionId(), db2.getSessionId(), "the same slot was handed out to two sessions");
        assertEquals(conMgr.getNumSessions(), 2);
      }
      finally {
        db2.close();
        db1.close();
      }
    }
    finally {
      conMgr.shutdown();
    }
  }

}

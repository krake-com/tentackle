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

import org.tentackle.dbms.BlockingConnection.Blocker;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendVersionInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.tentackle.dbms.BlockingConnection.AWAIT_MILLIS;
import static org.tentackle.dbms.BlockingConnection.assertNotBlocked;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests that lengthy I/O of one session does not block the connection manager or session pool for all others.
 * <p>
 * Closing a dead connection, for example, may block until a network timeout strikes.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ConnectionManagerBlockingIoTest {

  /**
   * A multiplexing manager starting with a single connection, creating blocking connections.
   */
  private static class TestMpxManager extends MpxConnectionManager {

    private final List<BlockingConnection> connections = new CopyOnWriteArrayList<>();
    private final AtomicReference<Blocker> connectBlocker = new AtomicReference<>();

    private TestMpxManager(String name) {
      super(name, new BackendInfo(new DefaultSessionInfo().getProperties()), 4, 1, 1, 1, 1, 2, 720, 2160);
    }

    @Override
    protected Connection connect(BackendInfo backendInfo) throws SQLException {
      Blocker blocker = connectBlocker.getAndSet(null);
      if (blocker != null) {
        blocker.block();
      }
      return super.connect(backendInfo);
    }

    @Override
    protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
      BlockingConnection con = new BlockingConnection(this, backend, versionInfo, connection);
      connections.add(con);
      return con;
    }
  }

  /**
   * A 1:1 manager whose next connect blocks, creating blocking connections.
   */
  private static class TestDefaultManager extends DefaultConnectionManager {

    private final AtomicReference<Blocker> connectBlocker = new AtomicReference<>();

    private TestDefaultManager(String name) {
      super(name, 2, 16, 1, 720, 2160);
    }

    @Override
    protected Connection connect(BackendInfo backendInfo) throws SQLException {
      Blocker blocker = connectBlocker.getAndSet(null);
      if (blocker != null) {
        blocker.block();
      }
      return super.connect(backendInfo);
    }

    @Override
    protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
      return new BlockingConnection(this, backend, versionInfo, connection);
    }
  }


  private static Thread start(String name, AtomicReference<Throwable> failure, Runnable action) {
    Thread thread = new Thread(() -> {
      try {
        action.run();
      }
      catch (Throwable t) {
        failure.compareAndSet(null, t);
      }
    }, name);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }


  @Test(timeOut = 60000)
  public void testAttachClosesDeadConnectionWithoutBlocking() throws Exception {
    TestMpxManager conMgr = new TestMpxManager("blocking-attach");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      BlockingConnection con = conMgr.connections.getFirst();
      con.setDead(true);
      Blocker blocker = con.blockClose();

      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread attacher = start("attacher", failure, () -> conMgr.attach(db));   // finds the dead connection and closes it
      blocker.awaitBlocked();
      assertNotBlocked("the connection manager", blocker, conMgr::getNumConnections);

      attacher.join(AWAIT_MILLIS);
      assertNull(failure.get(), "attach failed: " + failure.get());
      assertNotNull(db.getConnection());
      assertNotSame(db.getConnection(), con, "attached to the dead connection");
      conMgr.detach(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }


  @Test(timeOut = 60000)
  public void testDetachClosesDeadConnectionWithoutBlocking() throws Exception {
    TestMpxManager conMgr = new TestMpxManager("blocking-detach");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      conMgr.attach(db);
      BlockingConnection con = (BlockingConnection) db.getConnection();
      con.setDead(true);
      Blocker blocker = con.blockClose();

      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread detacher = start("detacher", failure, () -> conMgr.detach(db));   // closes the dead connection and the session
      blocker.awaitBlocked();
      assertNotBlocked("the connection manager", blocker, conMgr::getNumConnections);

      detacher.join(AWAIT_MILLIS);
      assertNull(failure.get(), "detach failed: " + failure.get());
      assertTrue(con.isClosed());
      assertFalse(db.isOpen(), "the session of a dead connection must be closed");
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }


  @Test(timeOut = 60000)
  public void testForceDetachCleansUpWithoutBlocking() throws Exception {
    TestMpxManager conMgr = new TestMpxManager("blocking-force-detach");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      conMgr.attach(db);
      BlockingConnection con = (BlockingConnection) db.getConnection();
      Blocker blocker = con.blockAutoCommit();    // checked to roll back a pending transaction

      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread detacher = start("force-detacher", failure, () -> conMgr.forceDetach(db));
      blocker.awaitBlocked();
      assertNotBlocked("the connection manager", blocker, conMgr::getNumConnections);

      detacher.join(AWAIT_MILLIS);
      assertNull(failure.get(), "force detach failed: " + failure.get());
      assertNull(db.getConnection());

      // the cleaned up connection is unattached again
      conMgr.attach(db);
      assertSame(db.getConnection(), con);
      conMgr.detach(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }


  @Test(timeOut = 60000)
  public void testAttachTimeoutCleansUpWithoutBlocking() throws Exception {
    TestMpxManager conMgr = new TestMpxManager("blocking-attach-timeout");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      conMgr.attach(db);
      BlockingConnection con = (BlockingConnection) db.getConnection();
      Blocker blocker = con.blockAutoCommit();    // checked to roll back a pending transaction
      conMgr.setAttachTimeout(100);               // the timeout thread force-detaches the connection

      blocker.awaitBlocked();
      assertNotBlocked("the connection manager", blocker, conMgr::getNumConnections);
      await("cleanup of the timed out connection", () -> {
        synchronized (conMgr) {
          return conMgr.unConCount > 0;   // back to the unattached connections
        }
      });

      // the cleaned up connection is unattached again
      conMgr.setAttachTimeout(0);
      conMgr.attach(db);
      assertSame(db.getConnection(), con);
      conMgr.detach(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }


  @Test(timeOut = 60000)
  public void testDefaultForceDetachCleansUpWithoutBlocking() throws Exception {
    TestDefaultManager conMgr = new TestDefaultManager("blocking-default-force-detach");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    Db db2 = new Db(conMgr, new DefaultSessionInfo());
    try {
      conMgr.attach(db);
      BlockingConnection con = (BlockingConnection) db.getConnection();
      Blocker blocker = con.blockAutoCommit();    // checked to roll back a pending transaction

      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread detacher = start("force-detacher", failure, () -> conMgr.forceDetach(db));
      blocker.awaitBlocked();
      assertNull(db.getConnection());

      // the session must wait for the cleanup to complete before re-attaching
      AtomicReference<Throwable> attachFailure = new AtomicReference<>();
      Thread attacher = start("attacher", attachFailure, () -> conMgr.attach(db));
      attacher.join(500);
      assertTrue(attacher.isAlive(), "attached while the connection is being cleaned up");

      assertNotBlocked("another session", blocker, () -> {
        conMgr.attach(db2);
        conMgr.detach(db2);
      });

      detacher.join(AWAIT_MILLIS);
      assertNull(failure.get(), "force detach failed: " + failure.get());
      attacher.join(AWAIT_MILLIS);
      assertNull(attachFailure.get(), "attach failed: " + attachFailure.get());
      assertSame(db.getConnection(), con);
      conMgr.detach(db);
    }
    finally {
      db.close();
      db2.close();
      conMgr.shutdown();
    }
  }


  @Test(timeOut = 60000)
  public void testDefaultAttachTimeoutCleansUpWithoutBlocking() throws Exception {
    TestDefaultManager conMgr = new TestDefaultManager("blocking-default-attach-timeout");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      conMgr.attach(db);
      BlockingConnection con = (BlockingConnection) db.getConnection();
      Blocker blocker = con.blockAutoCommit();    // checked to roll back a pending transaction
      conMgr.setAttachTimeout(100);               // the timeout thread force-detaches the connection

      blocker.awaitBlocked();
      assertNotBlocked("the connection manager", blocker, conMgr::getNumConnections);
      await("cleanup of the timed out connection", () -> {
        synchronized (conMgr) {
          return db.getConnection() == null && !con.isReserved();
        }
      });

      conMgr.setAttachTimeout(0);
      conMgr.attach(db);
      assertSame(db.getConnection(), con);
      conMgr.detach(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }


  @Test(timeOut = 60000)
  public void testDefaultAttachVerifiesWithoutBlocking() throws Exception {
    TestDefaultManager conMgr = new TestDefaultManager("blocking-default-verify");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      conMgr.attach(db);
      BlockingConnection con = (BlockingConnection) db.getConnection();
      con.setConnectionInactivityTimeoutMs(1);
      conMgr.detach(db);
      Thread.sleep(10);   // idle long enough to be verified on the next attach
      assertTrue(con.isConnectionVerificationNecessary());
      Blocker blocker = con.blockVerify();

      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread attacher = start("attacher", failure, () -> conMgr.attach(db));
      blocker.awaitBlocked();
      assertNotBlocked("the connection manager", blocker, conMgr::getNumConnections);

      attacher.join(AWAIT_MILLIS);
      assertNull(failure.get(), "attach failed: " + failure.get());
      assertSame(db.getConnection(), con);
      conMgr.detach(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }


  @Test(timeOut = 60000)
  public void testDefaultLogoutClosesWithoutBlocking() throws Exception {
    TestDefaultManager conMgr = new TestDefaultManager("blocking-default-logout");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      conMgr.attach(db);
      BlockingConnection con = (BlockingConnection) db.getConnection();
      conMgr.detach(db);
      Blocker blocker = con.blockClose();

      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread closer = start("closer", failure, db::close);
      blocker.awaitBlocked();
      assertNotBlocked("the connection manager", blocker, conMgr::getNumConnections);

      closer.join(AWAIT_MILLIS);
      assertNull(failure.get(), "close failed: " + failure.get());
      assertFalse(db.isOpen());
      assertTrue(con.isClosed());
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }


  /**
   * Waits until a condition holds, for example, until the attach-timeout thread has cleaned up a connection.
   *
   * @param what what is awaited, for the failure message
   * @param condition the condition
   */
  private static void await(String what, BooleanSupplier condition) throws InterruptedException {
    long timeout = System.currentTimeMillis() + AWAIT_MILLIS;
    while (!condition.getAsBoolean()) {
      assertTrue(System.currentTimeMillis() < timeout, what + " timed out");
      Thread.sleep(10);
    }
  }


  @Test(timeOut = 60000)
  public void testFirstLoginConnectsWithoutBlocking() throws Exception {
    TestMpxManager conMgr = new TestMpxManager("blocking-first-login");
    Blocker blocker = new Blocker();
    conMgr.connectBlocker.set(blocker);
    AtomicReference<Db> db = new AtomicReference<>();
    try {
      AtomicReference<Throwable> failure = new AtomicReference<>();
      // the first login brings up the initial connections
      Thread login = start("login", failure, () -> db.set(new Db(conMgr, new DefaultSessionInfo())));
      blocker.awaitBlocked();
      assertNotBlocked("the connection manager", blocker, conMgr::getNumSessions);

      login.join(AWAIT_MILLIS);
      assertNull(failure.get(), "login failed: " + failure.get());
      assertNotNull(db.get());
      assertTrue(conMgr.getNumConnections() > 0);
    }
    finally {
      if (db.get() != null) {
        db.get().close();
      }
      conMgr.shutdown();
    }
  }


  @Test(timeOut = 60000)
  public void testMultiUserPoolCreatesSessionWithoutBlocking() throws Exception {
    TestDefaultManager conMgr = new TestDefaultManager("blocking-multi-user");
    MultiUserDbPool pool = new MultiUserDbPool("blocking-multi-user", conMgr, 0, 4, 16, 0, 0);
    Blocker blocker = new Blocker();
    conMgr.connectBlocker.set(blocker);
    try {
      AtomicReference<Throwable> failure = new AtomicReference<>();
      AtomicReference<Session> session = new AtomicReference<>();
      // the first user's session connects to the database
      Thread getter = start("first user", failure, () -> session.set(pool.get(createSessionInfo(1))));
      blocker.awaitBlocked();
      assertNotBlocked("the session of another user", blocker, () -> pool.put(pool.get(createSessionInfo(2))));

      getter.join(AWAIT_MILLIS);
      assertNull(failure.get(), "getting the session failed: " + failure.get());
      assertNotNull(session.get());
      pool.put(session.get());
      assertTrue(pool.getSize() == 2, "one session per user expected, got " + pool.getSize());
    }
    finally {
      pool.shutdown();
      conMgr.shutdown();
    }
  }

  private SessionInfo createSessionInfo(int index) {
    SessionInfo sessionInfo = new DefaultSessionInfo();
    sessionInfo.setUserName("blockingtestuser" + index);    // the user name selects the sub-pool
    return sessionInfo;
  }

}

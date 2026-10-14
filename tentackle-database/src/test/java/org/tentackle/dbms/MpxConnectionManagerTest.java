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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the shutdown of a {@link MpxConnectionManager} racing with sessions and the connect thread.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class MpxConnectionManagerTest {

  private static final long AWAIT_SECONDS = 10;


  /**
   * Creates a manager with a single initial connection, growing by one.
   */
  private static class TestManager extends MpxConnectionManager {

    private volatile Thread admittedAttacher;   // thread admitted to the monitor while the connections are closed
    private volatile boolean blockConnects;     // true if connect() should block
    private final CountDownLatch connecting = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);
    private final AtomicReference<ManagedConnection> blockedConnection = new AtomicReference<>();

    private TestManager(String name) {
      super(name, new BackendInfo(new DefaultSessionInfo().getProperties()), 4, 1, 1, 1, 1, 4, 720, 2160);
    }

    @Override
    protected ManagedConnection removeConnection(int index) {
      ManagedConnection con = super.removeConnection(index);
      Thread attacher = admittedAttacher;
      if (attacher != null) {
        admittedAttacher = null;
        // releases the monitor, as if attach() acquired it right after the connection was removed
        try {
          while (attacher.isAlive()) {
            wait(10);
          }
        }
        catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
      return con;
    }

    @Override
    protected Connection connect(BackendInfo backendInfo) throws SQLException {
      boolean blocked = blockConnects;
      if (blocked) {
        connecting.countDown();
        // like a lengthy connect against an unreachable backend: not interruptible.
        // the interrupt is swallowed, since H2 refuses to connect from an interrupted thread
        for (;;) {
          try {
            proceed.await();
            break;
          }
          catch (InterruptedException ex) {
            // keep on waiting
          }
        }
      }
      return super.connect(backendInfo);
    }

    @Override
    protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
      ManagedConnection mc = super.createManagedConnection(backend, versionInfo, connection);
      if (blockConnects) {
        blockedConnection.set(mc);
      }
      return mc;
    }
  }


  @Test(timeOut = 60000)
  public void testAttachWhileShuttingDown() throws Exception {
    TestManager conMgr = new TestManager("attach-shutdown-test");
    Db db = new Db(conMgr, new DefaultSessionInfo());   // the login creates the single unattached connection
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread attacher = new Thread(() -> {
      try {
        conMgr.attach(db);
        fail("attach must fail after shutdown");
      }
      catch (Throwable t) {
        failure.set(t);
      }
    });

    synchronized (conMgr) {
      // the attacher passes the shutdown check and waits for the monitor
      attacher.start();
      long deadline = System.currentTimeMillis() + AWAIT_SECONDS * 1000;
      while (attacher.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
      assertEquals(attacher.getState(), Thread.State.BLOCKED, "attach() did not wait for the monitor");

      // the attacher acquires the monitor while the connections are being closed
      conMgr.admittedAttacher = attacher;
      conMgr.shutdown();
    }

    attacher.join(AWAIT_SECONDS * 1000);
    assertTrue(failure.get() instanceof PersistenceException, "attach must fail after shutdown, got " + failure.get());
    assertEquals(conMgr.getNumConnections(), 0);
  }


  @Test(timeOut = 60000)
  public void testShutdownWhileConnectThreadConnecting() throws Exception {
    TestManager conMgr = new TestManager("connect-shutdown-test");
    Db db1 = new Db(conMgr, new DefaultSessionInfo());   // the login creates the single connection
    Db db2 = new Db(conMgr, new DefaultSessionInfo());
    conMgr.attach(db1);    // no more unattached connections
    conMgr.blockConnects = true;

    // requests a new connection from the connect thread and waits for it
    Thread attacher = new Thread(() -> {
      try {
        conMgr.attach(db2);
      }
      catch (RuntimeException ex) {
        // the manager is shut down while waiting
      }
    });
    attacher.start();
    assertTrue(conMgr.connecting.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the connect thread did not start connecting");

    // the connect thread is stuck: the shutdown gives up waiting for it (after 5 seconds) and closes all connections
    conMgr.shutdown();
    assertEquals(conMgr.getNumConnections(), 0);

    // the connect thread completes the pending connection after shutdown
    conMgr.proceed.countDown();
    Thread connectThread = Thread.getAllStackTraces().keySet().stream()
                                 .filter(t -> t.getName().equals(conMgr.getName() + " Connection Management Thread"))
                                 .findFirst().orElse(null);
    if (connectThread != null) {
      connectThread.join(AWAIT_SECONDS * 1000);
    }
    attacher.join(AWAIT_SECONDS * 1000);

    ManagedConnection con = conMgr.blockedConnection.get();
    assertNotNull(con, "the connect thread did not create the connection");
    assertTrue(con.isClosed(), "the connection established after shutdown must be closed");
    assertEquals(conMgr.getNumConnections(), 0, "the connection established after shutdown must not be stored");
  }

}

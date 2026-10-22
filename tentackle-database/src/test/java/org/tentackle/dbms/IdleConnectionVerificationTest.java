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
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendVersionInfo;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.tentackle.dbms.BlockingConnection.AWAIT_MILLIS;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Tests that the keep-alive verification of the {@link ManagedConnectionMonitor} does not race
 * with a session attaching the very same connection.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class IdleConnectionVerificationTest {

  private static final long LATER_MILLIS = 60000;   // the time of the inspection, relative to now


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

  /**
   * Makes the connection a candidate for the keep-alive verification.
   *
   * @param con the connection
   * @return the thread inspecting the connection like the monitor
   */
  private static Thread inspect(BlockingConnection con, AtomicReference<Throwable> failure) {
    con.setConnectionInactivityTimeoutMs(1);
    con.setConnectionKeepAliveEnabled(true);
    long inspectionTime = System.currentTimeMillis() + LATER_MILLIS;
    return start("monitor", failure, () -> ManagedConnectionMonitor.getInstance().inspect(con, inspectionTime));
  }


  @Test(timeOut = 60000)
  public void testDefaultManagerAttachWaitsForVerification() throws Exception {
    List<BlockingConnection> connections = new CopyOnWriteArrayList<>();
    DefaultConnectionManager conMgr = new DefaultConnectionManager("verify-default", 2, 8, 1, 720, 2160) {
      @Override
      protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
        BlockingConnection con = new BlockingConnection(this, backend, versionInfo, connection);
        connections.add(con);
        return con;
      }
    };
    Db db = new Db(conMgr, new DefaultSessionInfo());   // the connection of the session is not attached yet
    try {
      BlockingConnection con = connections.getFirst();
      Blocker blocker = con.blockVerify();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread monitor = inspect(con, failure);
      blocker.awaitBlocked();

      Thread attacher = start("attacher", failure, db::attach);
      attacher.join(1000);
      boolean attachedWhileVerifying = con.isAttached();
      blocker.release();
      monitor.join(AWAIT_MILLIS);
      attacher.join(AWAIT_MILLIS);

      assertNull(failure.get(), "failed: " + failure.get());
      assertFalse(attachedWhileVerifying, "the connection was attached while being verified");
      assertSame(db.getConnection(), con, "the session must attach its connection after the verification");
      db.detach();
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }


  @Test(timeOut = 60000)
  public void testMpxManagerDoesNotAttachConnectionBeingVerified() throws Exception {
    List<BlockingConnection> connections = new CopyOnWriteArrayList<>();
    MpxConnectionManager conMgr = new MpxConnectionManager("verify-mpx", new BackendInfo(new DefaultSessionInfo().getProperties()),
                                                           4, 1, 1, 1, 1, 2, 720, 2160) {
      @Override
      protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
        BlockingConnection con = new BlockingConnection(this, backend, versionInfo, connection);
        connections.add(con);
        return con;
      }
    };
    Db db = new Db(conMgr, new DefaultSessionInfo());   // the login brings up the single unattached connection
    try {
      BlockingConnection con = connections.getFirst();
      Blocker blocker = con.blockVerify();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread monitor = inspect(con, failure);
      blocker.awaitBlocked();

      try {
        conMgr.attach(db);
      }
      finally {
        blocker.release();
        monitor.join(AWAIT_MILLIS);
      }

      assertNull(failure.get(), "failed: " + failure.get());
      assertNotNull(db.getConnection());
      assertNotSame(db.getConnection(), con, "attached the connection being verified");
      conMgr.detach(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }

}

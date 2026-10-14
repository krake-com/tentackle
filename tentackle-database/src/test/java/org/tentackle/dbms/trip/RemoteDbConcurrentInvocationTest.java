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

package org.tentackle.dbms.trip;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.dbms.Db;
import org.tentackle.dbms.DefaultConnectionManager;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the delegates of a {@link RemoteDbSessionImpl} reject invocations running in parallel,
 * except for the client's keep-alive, which must not interfere with the invocation in progress.
 * Neither must the client close the session while an invocation is in progress.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class RemoteDbConcurrentInvocationTest {

  private static final long AWAIT_MILLIS = 10000;

  /**
   * The local session for the next {@link TestSession}.<br>
   * Static, since {@link RemoteDbSessionImpl#openDb()} is invoked from the super constructor,
   * before the fields of the subclass are initialized.
   */
  private static volatile Db nextDb;

  private Session previousSession;    // the thread's current session, replaced by the constructor of RemoteDbSessionImpl

  @BeforeMethod
  public void saveCurrentSession() {
    previousSession = Session.getCurrentSession();
  }

  @AfterMethod(alwaysRun = true)
  public void restoreCurrentSession() {
    Session.setCurrentSession(previousSession);   // don't leave the closed session for the other tests
  }


  /**
   * A remote session without a TRIP-server, authentication or client.
   */
  private static class TestSession extends RemoteDbSessionImpl {

    private TestSession(RemoteDbConnectionImpl con, SessionInfo sessionInfo) {
      super(con, sessionInfo, sessionInfo);
    }

    @Override
    protected Db openDb() {
      return nextDb;
    }

    @Override
    public void verifySessionInfo(SessionInfo sessionInfo) {
      // no authentication
    }

    @Override
    protected int determineTimeout(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
      return 1;   // no TRIP-server to ask
    }
  }


  @Test(timeOut = 60000)
  public void testConcurrentInvocations() throws Exception {
    SessionInfo sessionInfo = new DefaultSessionInfo();
    DefaultConnectionManager conMgr = new DefaultConnectionManager("remote-invocation-test", 2, 2, 1, 0, 0);
    Db db = new Db(conMgr, sessionInfo);
    nextDb = db;
    TestSession session = new TestSession(RemoteSessionTestSupport.createConnection(), sessionInfo);
    try {
      DbRemoteDelegate rdel = session.getRemoteDelegate();
      rdel.setProperty("key", "value");

      AtomicReference<Object> result = new AtomicReference<>();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread invoker = new Thread(() -> {
        try {
          result.set(rdel.getProperty("key"));
        }
        catch (Throwable t) {
          failure.set(t);
        }
      }, "invoker");
      invoker.setDaemon(true);

      synchronized (db) {   // getProperty waits for the monitor of the local session: an invocation in progress
        invoker.start();
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (invoker.getState() != Thread.State.BLOCKED) {
          assertTrue(System.currentTimeMillis() < deadline, "invocation did not start");
          Thread.sleep(10);
        }
        assertSame(db.getOwnerThread(), invoker);

        // the client's keep-alive is accepted and does not interfere with the invocation in progress
        rdel.setAlive(true);
        assertTrue(rdel.isAlive());
        assertSame(db.getOwnerThread(), invoker, "keep-alive changed the session's owner thread");

        // any other invocation in parallel is rejected
        assertThrows(PersistenceException.class, () -> rdel.getProperty("key"));
        assertSame(db.getOwnerThread(), invoker, "rejected invocation changed the session's owner thread");
        assertSame(session.getInvokingThread(), invoker);
      }

      invoker.join(AWAIT_MILLIS);
      assertNull(failure.get(), "invocation failed: " + failure.get());
      assertEquals(result.get(), "value");
      assertNull(session.getInvokingThread());
      assertNull(db.getOwnerThread());

      // successive invocations from different threads are fine
      assertEquals(rdel.getProperty("key"), "value");
    }
    finally {
      nextDb = null;
      session.closeDb(false);
      conMgr.shutdown();
    }
  }

  @Test(timeOut = 60000)
  public void testCloseWhileInvoking() throws Exception {
    SessionInfo sessionInfo = new DefaultSessionInfo();
    DefaultConnectionManager conMgr = new DefaultConnectionManager("remote-close-invocation-test", 2, 2, 1, 0, 0);
    Db db = new Db(conMgr, sessionInfo);
    nextDb = db;
    TestSession session = new TestSession(RemoteSessionTestSupport.createConnection(), sessionInfo);
    try {
      DbRemoteDelegate rdel = session.getRemoteDelegate();
      rdel.setProperty("key", "value");

      AtomicReference<Object> result = new AtomicReference<>();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread invoker = new Thread(() -> {
        try {
          result.set(rdel.getProperty("key"));
        }
        catch (Throwable t) {
          failure.set(t);
        }
      }, "invoker");
      invoker.setDaemon(true);

      synchronized (db) {   // getProperty waits for the monitor of the local session: an invocation in progress
        invoker.start();
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (invoker.getState() != Thread.State.BLOCKED) {
          assertTrue(System.currentTimeMillis() < deadline, "invocation did not start");
          Thread.sleep(10);
        }

        // the client's logout must not close the local session under the hands of the invocation in progress
        assertThrows(PersistenceException.class, session::close);
        assertTrue(session.isOpen(), "session closed while an invocation is in progress");
        assertSame(session.getSession(), db);
        assertTrue(db.isOpen(), "local session closed while an invocation is in progress");
        assertSame(session.getInvokingThread(), invoker);
      }

      invoker.join(AWAIT_MILLIS);
      assertNull(failure.get(), "invocation failed: " + failure.get());
      assertEquals(result.get(), "value");

      // once the invocation is finished, the session can be closed
      session.close();
      assertFalse(session.isOpen());
      assertFalse(db.isOpen());
      assertNull(session.getInvokingThread());
    }
    finally {
      nextDb = null;
      session.closeDb(false);
      conMgr.shutdown();
    }
  }

  @Test(timeOut = 60000)
  public void testInvocationAfterClose() {
    SessionInfo sessionInfo = new DefaultSessionInfo();
    DefaultConnectionManager conMgr = new DefaultConnectionManager("remote-closed-invocation-test", 2, 2, 1, 0, 0);
    nextDb = new Db(conMgr, sessionInfo);
    TestSession session = new TestSession(RemoteSessionTestSupport.createConnection(), sessionInfo);
    try {
      DbRemoteDelegate rdel = session.getRemoteDelegate();
      // the filter runs after the invocation passed the first check whether the session is open,
      // but before it is entered: close the session in between
      session.setInvocationFilter((_, _, _) -> session.close());

      assertThrows(RemoteSessionClosedException.class, () -> rdel.getProperty("key"));
      assertFalse(session.isOpen());
      assertNull(session.getInvokingThread());
    }
    finally {
      nextDb = null;
      session.closeDb(false);
      conMgr.shutdown();
    }
  }

}

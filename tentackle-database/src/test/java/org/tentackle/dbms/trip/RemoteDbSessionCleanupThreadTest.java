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

import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DefaultConnectionManager;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the {@link RemoteDbSessionCleanupThread} closes only sessions that really timed out.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class RemoteDbSessionCleanupThreadTest {

  private static final int GROUP_ID = 4711;
  private static final int NO_TIMEOUT = 1000;     // polling intervals
  private static final long AWAIT_MILLIS = 10000;

  /**
   * The session for the next {@link TestSession}.<br>
   * Static, since {@link RemoteDbSessionImpl#openDb()} and {@link RemoteDbSessionImpl#determineTimeout}
   * are invoked from the super constructor, before the fields of the subclass are initialized.
   */
  private static volatile Db nextDb;

  /** the timeout of the next {@link TestSession}. */
  private static volatile int nextTimeout;

  /** the delay of {@link RemoteDbSessionImpl#determineTimeout} in milliseconds. */
  private static volatile long nextSetupDelay;


  /**
   * A remote session without a TRIP-server, authentication or client.
   */
  private static class TestSession extends RemoteDbSessionImpl {

    private volatile Runnable pollHook;   // invoked when polled by the cleanup thread
    private volatile boolean cleanupFailing;    // true if the regular cleanup fails

    private TestSession() {
      this(new DefaultSessionInfo());
    }

    private TestSession(SessionInfo sessionInfo) {
      super(RemoteSessionTestSupport.createConnection(), sessionInfo, sessionInfo);
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
      long delay = nextSetupDelay;
      if (delay > 0) {
        // some lengthy setup
        try {
          Thread.sleep(delay);
        }
        catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
      return nextTimeout;
    }

    @Override
    protected void cleanup(boolean crashed) {
      if (cleanupFailing) {
        throw new IllegalStateException("cleanup failed");
      }
      super.cleanup(crashed);
    }

    @Override
    protected void polled() {
      Runnable hook = pollHook;
      if (hook != null) {
        pollHook = null;
        hook.run();
      }
      super.polled();
    }
  }


  private ConnectionManager conMgr;
  private Db db;
  private Session previousSession;    // the thread's current session, replaced by the constructor of RemoteDbSessionImpl

  @BeforeMethod
  public void openSession() {
    previousSession = Session.getCurrentSession();
    conMgr = new DefaultConnectionManager("remote-cleanup-test", 2, 2, 1, 0, 0);
    db = new Db(conMgr, new DefaultSessionInfo());
    db.setAlive(false);   // no activity so far
    nextDb = db;
    nextTimeout = NO_TIMEOUT;
    nextSetupDelay = 0;
  }

  @AfterMethod(alwaysRun = true)
  public void closeSession() {
    RemoteDbSessionImpl.stopCleanupThread();
    nextDb = null;
    db.close();
    conMgr.shutdown();
    Session.setCurrentSession(previousSession);   // don't leave the closed session for the other tests
  }

  private RemoteDbSessionCleanupThread createCleanupThread(RemoteDbSessionImpl session) {
    Set<WeakReference<RemoteDbSessionImpl>> sessions = ConcurrentHashMap.newKeySet();
    sessions.add(new WeakReference<>(session));
    return new RemoteDbSessionCleanupThread(1000, sessions, 3);   // not started: verifySessions() invoked directly
  }


  /**
   * Starts a client's invocation, which stays in progress as long as the caller holds the monitor of {@link #db}.
   *
   * @param session the session
   * @return the invoking thread
   */
  private Thread startInvocation(RemoteDbSessionImpl session) {
    assertTrue(Thread.holdsLock(db));
    DbRemoteDelegate rdel = session.getRemoteDelegate();
    Thread invoker = new Thread(() -> rdel.getProperty("key"), "invoker");   // waits for the monitor of the local session
    invoker.setDaemon(true);
    invoker.start();
    long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
    while (invoker.getState() != Thread.State.BLOCKED) {
      assertTrue(System.currentTimeMillis() < deadline, "invocation did not start");
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError("interrupted", ex);
      }
    }
    return invoker;
  }


  @Test(timeOut = 60000)
  public void testSessionNotCleanedUpWhileInvoking() throws Exception {
    nextTimeout = 0;    // times out as soon as not alive
    TestSession session = new TestSession();
    RemoteDbSessionCleanupThread cleanupThread = createCleanupThread(session);

    Thread invoker;
    synchronized (db) {
      invoker = startInvocation(session);
      cleanupThread.verifySessions();   // alive due to the invocation
      cleanupThread.verifySessions();   // polled meanwhile, but the invocation is still in progress
      assertTrue(session.isOpen(), "the session was cleaned up while an invocation is in progress");
    }
    invoker.join(AWAIT_MILLIS);

    // still verified by the cleanup thread: times out now that the invocation is completed
    cleanupThread.verifySessions();
    assertFalse(session.isOpen(), "the idle session must be cleaned up");
  }


  @Test(timeOut = 60000)
  public void testSessionInvokedWhileVerifiedNotCleanedUp() throws Exception {
    nextTimeout = 0;    // times out at the first verification
    TestSession session = new TestSession();
    db.setExportedSessionGroupId(GROUP_ID);
    RemoteDbSessionCleanupThread cleanupThread = createCleanupThread(session);

    // the client invokes a method after the session was found timed out, but before the dead groups are closed
    AtomicReference<Thread> invoker = new AtomicReference<>();
    session.pollHook = () -> invoker.set(startInvocation(session));
    synchronized (db) {
      cleanupThread.verifySessions();
      assertNotNull(invoker.get());
      assertTrue(session.isOpen(), "the session was cleaned up while an invocation is in progress");
    }
    invoker.get().join(AWAIT_MILLIS);

    // still verified by the cleanup thread
    cleanupThread.verifySessions();
    assertFalse(session.isOpen(), "the idle session must be cleaned up");
  }


  @Test(timeOut = 60000)
  public void testSessionNotCleanedUpWhileBeingSetUp() {
    RemoteDbSessionImpl.startCleanupThread(20);
    nextSetupDelay = 300;   // gives the cleanup thread a couple of rounds

    TestSession session = new TestSession();
    RemoteDbSessionImpl.stopCleanupThread();

    assertTrue(session.isOpen(), "the session was cleaned up before its timeout was set");
    assertTrue(db.isOpen());
    session.closeDb(false);
  }


  @Test
  public void testSessionGroupedWhileVerifiedNotCleanedUp() {
    TestSession session = new TestSession();
    // the client groups the session after the cleanup thread verified it, but before it closes the dead groups
    session.pollHook = () -> db.setExportedSessionGroupId(GROUP_ID);

    createCleanupThread(session).verifySessions();

    assertTrue(session.isOpen(), "the session joined a group after verification and must not be treated as dead");
    session.closeDb(false);
  }


  @Test
  public void testTimedOutGroupCleanedUp() {
    nextTimeout = 0;    // times out at the first verification
    TestSession session = new TestSession();
    db.setExportedSessionGroupId(GROUP_ID);

    createCleanupThread(session).verifySessions();

    assertFalse(session.isOpen(), "the only session of the group timed out and must be cleaned up");
  }


  @Test(timeOut = 60000)
  public void testFailedVerificationNotForcedWhileInvoking() throws Exception {
    TestSession session = new TestSession();
    RemoteDbSessionCleanupThread cleanupThread = createCleanupThread(session);

    Thread invoker;
    synchronized (db) {
      invoker = startInvocation(session);
      session.pollHook = () -> {
        throw new IllegalStateException("verification failed");
      };
      cleanupThread.verifySessions();   // forces a cleanup, but not while the invocation is in progress
      assertTrue(session.isOpen(), "the session was forced to clean up while an invocation is in progress");
      assertTrue(db.isOpen(), "the local session was closed while an invocation is in progress");
    }
    invoker.join(AWAIT_MILLIS);

    // still verified by the cleanup thread: forced to clean up now that the invocation is completed
    session.pollHook = () -> {
      throw new IllegalStateException("verification failed");
    };
    cleanupThread.verifySessions();
    assertFalse(session.isOpen(), "the session must be forced to clean up");
  }


  @Test(timeOut = 60000)
  public void testZombieNotForcedWhileInvoking() throws Exception {
    nextTimeout = 0;    // times out as soon as not alive
    TestSession session = new TestSession();
    RemoteDbSessionCleanupThread cleanupThread = createCleanupThread(session);

    session.cleanupFailing = true;
    cleanupThread.verifySessions();   // timed out, but the cleanup fails -> zombie
    assertTrue(session.isOpen());

    Thread invoker;
    synchronized (db) {
      invoker = startInvocation(session);
      // more often than the maximum number of retries (3): a zombie in use is not a failed attempt
      for (int i = 0; i < 5; i++) {
        cleanupThread.cleanupZombieSessions();
      }
      assertTrue(session.isOpen(), "the zombie was forced to clean up while an invocation is in progress");
      assertTrue(db.isOpen(), "the local session was closed while an invocation is in progress");
    }
    invoker.join(AWAIT_MILLIS);

    session.cleanupFailing = false;
    cleanupThread.cleanupZombieSessions();
    assertFalse(session.isOpen(), "the zombie must be cleaned up");
  }

}

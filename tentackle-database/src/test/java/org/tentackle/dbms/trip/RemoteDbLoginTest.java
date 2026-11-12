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
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.auth.Authentication;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Logins must not be serialized by the verification of the credentials.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class RemoteDbLoginTest {

  /** The connection manager of the sessions. Static, since {@link RemoteDbSessionImpl#openDb()} is invoked by the super constructor. */
  private static volatile ConnectionManager conMgr;

  /** Invoked when the credentials are verified. */
  private static volatile Runnable authenticationHook;

  private static final List<Db> DBS = new CopyOnWriteArrayList<>();

  /**
   * A remote session without a TRIP-server or client, verifying the credentials with a hook.
   */
  private static class TestSession extends RemoteDbSessionImpl {

    private TestSession(SessionInfo sessionInfo) {
      super(RemoteSessionTestSupport.createConnection(), sessionInfo, sessionInfo);
    }

    @Override
    protected Db openDb() {
      Db db = new Db(conMgr, new DefaultSessionInfo());
      DBS.add(db);
      return db;
    }

    @Override
    protected Authentication authenticate(SessionInfo sessionInfo) {
      authenticationHook.run();
      return null;
    }

    @Override
    protected void applyAuthentication(SessionInfo sessionInfo, Authentication authentication) {
      // nothing to apply
    }

    @Override
    protected int determineTimeout(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
      return 1000;    // no server
    }
  }


  private Session previousSession;

  @BeforeMethod
  public void setUp() {
    previousSession = Session.getCurrentSession();
    conMgr = new DefaultConnectionManager("remote-login-test", 8, 8, 1, 0, 0);
  }

  @AfterMethod(alwaysRun = true)
  public void tearDown() {
    RemoteDbSessionImpl.setMaxConcurrentLogins(RemoteDbSessionImpl.DEFAULT_MAX_CONCURRENT_LOGINS);
    RemoteDbSessionImpl.setLoginWaitTime(RemoteDbSessionImpl.DEFAULT_LOGIN_WAIT_TIME);
    authenticationHook = null;
    DBS.forEach(Db::close);
    DBS.clear();
    conMgr.shutdown();
    Session.setCurrentSession(previousSession);
  }

  private Thread login(List<Throwable> failures) {
    Thread thread = new Thread(() -> {
      try {
        new TestSession(new DefaultSessionInfo());
      }
      catch (Throwable t) {
        failures.add(t);
      }
    });
    thread.start();
    return thread;
  }


  @Test
  public void credentialsAreVerifiedConcurrently() throws Exception {
    // each verification waits for the other: deadlocks (and times out) if logins are serialized
    CyclicBarrier barrier = new CyclicBarrier(2);
    authenticationHook = () -> {
      try {
        barrier.await(5, TimeUnit.SECONDS);
      }
      catch (Exception e) {
        throw new LoginFailedException((Session) null, "verifications not concurrent", e);
      }
    };
    List<Throwable> failures = new CopyOnWriteArrayList<>();
    Thread t1 = login(failures);
    Thread t2 = login(failures);
    t1.join();
    t2.join();
    assertTrue(failures.isEmpty(), failures.toString());
  }

  @Test
  public void tooManyConcurrentLogins() throws Exception {
    RemoteDbSessionImpl.setMaxConcurrentLogins(1);
    RemoteDbSessionImpl.setLoginWaitTime(200);
    CountDownLatch verifying = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    authenticationHook = () -> {
      verifying.countDown();
      try {
        release.await(10, TimeUnit.SECONDS);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    };
    List<Throwable> failures = new CopyOnWriteArrayList<>();
    Thread slow = login(failures);
    assertTrue(verifying.await(10, TimeUnit.SECONDS));

    LoginFailedException ex = expectThrows(LoginFailedException.class, () -> new TestSession(new DefaultSessionInfo()));
    assertTrue(ex.getMessage().contains("busy"), ex.getMessage());

    release.countDown();
    slow.join();
    assertTrue(failures.isEmpty(), failures.toString());
    assertEquals(DBS.size(), 1, "the refused login must not open a session");

    // the permit is released again
    authenticationHook = () -> { };
    new TestSession(new DefaultSessionInfo());
  }

}

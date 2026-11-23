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
import org.tentackle.session.VersionIncompatibleException;
import org.tentackle.session.auth.Authentication;
import org.tentackle.session.auth.AuthenticationException;
import org.tentackle.trip.RejectionDelay;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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

  /** True if the server considers the client's version incompatible. */
  private static volatile boolean versionIncompatible;

  private static final List<Db> DBS = new CopyOnWriteArrayList<>();

  /**
   * A remote session without a TRIP-server or client, verifying the credentials with a hook.
   */
  private static class TestSession extends RemoteDbSessionImpl {

    private TestSession(SessionInfo sessionInfo) {
      super(createConnection(), sessionInfo, sessionInfo);
    }

    private static RemoteDbConnectionImpl createConnection() {
      return new RemoteDbConnectionImpl(null, RemoteSessionTestSupport.createTransport()) {
        @Override
        public RemoteDbSession createSession(SessionInfo clientInfo) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void checkClientVersion(String clientVersion) {
          if (versionIncompatible) {
            throw new VersionIncompatibleException("incompatible client", clientVersion, "4711");
          }
        }
      };
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
    RejectionDelay.setInterval(0, 0);
  }

  @AfterMethod(alwaysRun = true)
  public void tearDown() {
    RemoteDbSessionImpl.setMaxConcurrentLogins(RemoteDbSessionImpl.DEFAULT_MAX_CONCURRENT_LOGINS);
    RemoteDbSessionImpl.setLoginWaitTime(RemoteDbSessionImpl.DEFAULT_LOGIN_WAIT_TIME);
    RejectionDelay.setInterval(RejectionDelay.DEFAULT_MIN_DELAY, RejectionDelay.DEFAULT_MAX_DELAY);
    authenticationHook = null;
    versionIncompatible = false;
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


  /**
   * Performs a login and measures its duration.
   *
   * @param expectFailure true if the login must fail
   * @return the milliseconds until the login was answered
   */
  private static long timedLogin(boolean expectFailure) {
    long start = System.nanoTime();
    if (expectFailure) {
      expectThrows(AuthenticationException.class, () -> new TestSession(new DefaultSessionInfo()));
    }
    else {
      new TestSession(new DefaultSessionInfo());
    }
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  @Test
  public void failedLoginIsDelayed() {
    RejectionDelay.setInterval(300, 400);
    authenticationHook = () -> { throw new AuthenticationException("wrong password"); };
    long elapsed = timedLogin(true);
    assertTrue(elapsed >= 300, "failed login answered after " + elapsed + "ms");
  }

  @Test
  public void delayPadsVerificationTime() {
    RejectionDelay.setInterval(300, 300);
    authenticationHook = () -> {
      try {
        Thread.sleep(200);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      throw new AuthenticationException("wrong password");
    };
    long elapsed = timedLogin(true);
    assertTrue(elapsed >= 300, "failed login answered after " + elapsed + "ms");
    assertTrue(elapsed < 480, "verification time added to the delay instead of padded: " + elapsed + "ms");
  }

  @Test
  public void successfulLoginNotDelayed() {
    RejectionDelay.setInterval(1000, 1000);
    authenticationHook = () -> { };
    long elapsed = timedLogin(false);
    assertTrue(elapsed < 1000, "successful login answered after " + elapsed + "ms");
  }

  @Test
  public void delayHoldsNoPermit() throws Exception {
    RemoteDbSessionImpl.setMaxConcurrentLogins(1);
    RemoteDbSessionImpl.setLoginWaitTime(200);
    RejectionDelay.setInterval(2000, 2000);
    CountDownLatch failing = new CountDownLatch(1);
    authenticationHook = () -> {
      failing.countDown();
      throw new AuthenticationException("wrong password");
    };
    List<Throwable> failures = new CopyOnWriteArrayList<>();
    Thread failed = login(failures);
    assertTrue(failing.await(10, TimeUnit.SECONDS));
    Thread.sleep(100);    // let the failed login release its permit and enter the delay

    authenticationHook = () -> { };
    long elapsed = timedLogin(false);
    assertTrue(failed.isAlive(), "failed login not delayed");
    assertTrue(elapsed < 1000, "login blocked by a delayed failed login for " + elapsed + "ms");

    failed.join();
    assertEquals(failures.size(), 1);
    assertTrue(failures.getFirst() instanceof AuthenticationException, failures.toString());
  }

  @Test
  public void versionCheckedAfterCredentials() {
    versionIncompatible = true;
    authenticationHook = () -> { throw new AuthenticationException("wrong password"); };
    // an unauthenticated client must not learn that its version is incompatible
    LoginFailedException lx = expectThrows(LoginFailedException.class, () -> new TestSession(new DefaultSessionInfo()));
    assertFalse(lx instanceof VersionIncompatibleException, lx.toString());
    assertTrue(lx instanceof AuthenticationException, lx.toString());
  }

  @Test
  public void incompatibleVersionIsDelayed() {
    versionIncompatible = true;
    RejectionDelay.setInterval(300, 400);
    authenticationHook = () -> { };
    int openSessions = RemoteDbSessionImpl.getOpenSessions().size();
    long start = System.nanoTime();
    VersionIncompatibleException vx = expectThrows(VersionIncompatibleException.class,
                                                   () -> new TestSession(new DefaultSessionInfo()));
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertEquals(vx.getServerVersion(), "4711");
    assertTrue(elapsed >= 300, "incompatible version answered after " + elapsed + "ms");
    assertEquals(RemoteDbSessionImpl.getOpenSessions().size(), openSessions, "incompatible session registered");
  }

  @Test
  public void invalidDelays() {
    expectThrows(IllegalArgumentException.class, () -> RejectionDelay.setInterval(-1, 100));
    expectThrows(IllegalArgumentException.class, () -> RejectionDelay.setInterval(200, 100));
    RejectionDelay.setInterval(200, 0);    // disabled
  }

}

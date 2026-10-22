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

package org.tentackle.web.app;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.app.Application;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.dbms.Db;
import org.tentackle.session.Session;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionInfoFactory;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.tentackle.web.app.WebTestSupport.sessionInfo;

/**
 * Tests for {@link WebApplication}, the Tentackle side of the web bridge.
 * <p>
 * The interesting behavior is how a borrowed persistence session is bound to the identity behind a
 * web session key, and that this works for both deployment shapes - DB-connected (local
 * {@code SessionPool}) and proxy (remote {@code MultiUserSessionPool}). The session handed out is
 * a real {@code Db} on an in-memory H2 database, so {@code setSessionInfo}/{@code getPool} are
 * the production ones, not stubs.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class WebApplicationTest {

  private static final String WEB_KEY = "JSESSIONID-4711";

  private Db session;
  private TestWebApplication application;

  @BeforeMethod
  public void setUp() {
    session = (Db) SessionFactory.getInstance().create(SessionInfoFactory.getInstance().create("h2"));
  }

  @AfterMethod
  public void tearDown() {
    if (application != null) {
      application.cleanup();          // terminates the key cache and closes the session
      application.unregister();       // startup() registered it as the running application
      application = null;
    }
    else if (session.isOpen()) {
      session.close();
    }
  }


  /**
   * Boots a DB-connected node: it owns a local session pool.
   *
   * @return the pool the application hands sessions out from
   */
  private RecordingSessionPool startDbConnectedNode() {
    RecordingSessionPool pool = new RecordingSessionPool(session);
    application = new TestWebApplication(session, pool, null);
    application.startup();
    return pool;
  }

  /**
   * Boots a proxy node: it owns a remote, per-user session pool.
   *
   * @return the remote pool the application hands sessions out from
   */
  private RecordingMultiUserSessionPool startProxyNode() {
    new RecordingSessionPool(session);   // the session's own (inner, per-user) pool
    RecordingMultiUserSessionPool pool = new RecordingMultiUserSessionPool(session);
    application = new TestWebApplication(session, null, pool);
    application.startup();
    return pool;
  }


  /**
   * The key cache exists from the very beginning, so the web layer can register sessions as soon
   * as the node is up.
   */
  @Test
  public void acceptsWebSessionsOnceStarted() {
    startDbConnectedNode();
    SessionInfo alice = sessionInfo("alice");
    application.addWebSession(WEB_KEY, alice);
    assertEquals(application.getSessionKeys(alice), List.of(WEB_KEY));
  }

  /**
   * A web container drains in-flight requests concurrently with the application's lifecycle, so
   * the bridge must answer - with the documented "no such session" - rather than blow up when a
   * request arrives before the node is up or after it has been cleaned up.
   */
  @Test
  public void answersRequestsOutsideTheApplicationLifecycle() {
    application = new TestWebApplication(session, null, null);   // no pool: nothing to hand out
    SessionInfo alice = sessionInfo("alice");

    // before startup
    application.addWebSession(WEB_KEY, alice);
    assertEquals(application.getSessionKeys(alice), List.of(WEB_KEY));
    assertNull(application.getSession(WEB_KEY));

    application.startup();
    application.cleanup();

    // after cleanup
    assertNull(application.getSession(WEB_KEY));
    application.removeWebSession(WEB_KEY);
    assertTrue(application.getSessionKeys(alice).isEmpty());

    application.unregister();
    application = null;   // already cleaned up
  }

  @Test
  public void registersItselfAsTheRunningApplication() {
    startDbConnectedNode();
    assertSame(Application.getInstance(), application);
  }

  @Test
  public void forgetsAWebSessionOnLogout() {
    startDbConnectedNode();
    SessionInfo alice = sessionInfo("alice");
    application.addWebSession(WEB_KEY, alice);
    application.removeWebSession(WEB_KEY);

    assertTrue(application.getSessionKeys(alice).isEmpty());
    assertNull(application.getSession(WEB_KEY));
  }

  /**
   * One user may hold several browser sessions at once.
   */
  @Test
  public void reportsEveryWebSessionOfAUser() {
    startDbConnectedNode();
    SessionInfo alice = sessionInfo("alice");
    application.addWebSession("browser-1", alice);
    application.addWebSession("browser-2", alice);
    application.addWebSession("browser-3", sessionInfo("bob"));

    assertEquals(Set.copyOf(application.getSessionKeys(alice)), Set.of("browser-1", "browser-2"));
  }


  /**
   * The whole point of the bridge: the borrowed session runs as the user behind the web key.
   */
  @Test
  public void bindsAPooledSessionToTheUserOfTheWebKey() {
    RecordingSessionPool pool = startDbConnectedNode();
    SessionInfo alice = sessionInfo("alice");
    application.addWebSession(WEB_KEY, alice);

    Session borrowed = application.getSession(WEB_KEY);

    assertSame(borrowed, session);
    assertEquals(pool.getBorrowed(), List.of(session));
    assertSame(borrowed.getSessionInfo(), alice, "the session must execute as the authenticated user");
  }

  /**
   * A proxy node keeps one upstream session per user, so its pool is asked <em>for</em> the
   * identity rather than for just any session.
   */
  @Test
  public void asksTheRemotePoolForTheUsersSession() {
    RecordingMultiUserSessionPool pool = startProxyNode();
    SessionInfo alice = sessionInfo("alice");
    application.addWebSession(WEB_KEY, alice);

    Session borrowed = application.getSession(WEB_KEY);

    assertSame(borrowed, session);
    assertEquals(pool.getRequestedFor(), List.of(alice));
    assertSame(borrowed.getSessionInfo(), alice);
  }

  /**
   * An unknown or expired web key is not an error - the caller re-authenticates and tries again.
   */
  @Test
  public void refusesToHandOutASessionForAnUnknownWebKey() {
    RecordingSessionPool pool = startDbConnectedNode();
    assertNull(application.getSession("never-seen"));
    assertTrue(pool.getBorrowed().isEmpty(), "no session must be borrowed for an unknown key");
  }

  /**
   * A node that is neither DB-connected nor a proxy has nothing to hand out.
   */
  @Test
  public void refusesToHandOutASessionWithoutAnyPool() {
    application = new TestWebApplication(session, null, null);
    application.startup();
    application.addWebSession(WEB_KEY, sessionInfo("alice"));
    assertNull(application.getSession(WEB_KEY));
  }


  /**
   * On a DB-connected node the pooled session is shared, so the identity is stripped before it
   * goes back. The key cache's soft reference is then the only thing holding the user's
   * {@code SessionInfo}.
   */
  @Test
  public void detachesTheIdentityWhenReturningAPooledSession() {
    RecordingSessionPool pool = startDbConnectedNode();
    application.addWebSession(WEB_KEY, sessionInfo("alice"));
    Session borrowed = application.getSession(WEB_KEY);

    application.putSession(borrowed);

    assertNull(borrowed.getSessionInfo(), "a shared session must not keep the user's identity");
    assertEquals(pool.getReturned(), List.of(session));
  }

  /**
   * An upstream session stays bound to its user, so a proxy node must not strip the identity.
   */
  @Test
  public void keepsTheIdentityWhenReturningARemoteSession() {
    startProxyNode();
    SessionInfo alice = sessionInfo("alice");
    application.addWebSession(WEB_KEY, alice);
    Session borrowed = application.getSession(WEB_KEY);

    application.putSession(borrowed);

    assertSame(borrowed.getSessionInfo(), alice);
    assertEquals(((RecordingSessionPool) session.getPool()).getReturned(), List.of(session));
  }

  /**
   * A request that borrowed a session must always be able to give it back, whichever pool it
   * came from - this runs in the web layer's {@code finally} block.
   */
  @Test
  public void alwaysReturnsTheSessionToItsOwnPool() {
    RecordingSessionPool pool = startDbConnectedNode();
    application.addWebSession(WEB_KEY, sessionInfo("alice"));

    for (int request = 0; request < 3; request++) {
      application.putSession(application.getSession(WEB_KEY));
    }

    assertEquals(pool.getBorrowed().size(), 3);
    assertEquals(pool.getReturned().size(), 3);
  }


  /**
   * {@code startup()} ends in {@code finishStartup()}, which starts the cache's eviction thread;
   * {@code cleanup()} stops it again. A cache that was never started refuses to terminate, so a
   * clean {@code cleanup()} proves the pair is wired up.
   */
  @Test
  public void startsAndStopsTheKeyCacheWithTheApplication() {
    RecordingSessionPool pool = startDbConnectedNode();

    WebSessionKeyCache<?> cache = keyCache(application);
    assertNotNull(WebTestSupport.cleanupThread(cache), "finishStartup must have started the eviction thread");

    application.cleanup();
    assertNull(WebTestSupport.cleanupThread(cache), "cleanup must have terminated the eviction thread");
    assertTrue(pool.isShutdown(), "cleanup must have released the session pool");
    assertFalse(session.isOpen(), "cleanup must have closed the backend session");

    application.unregister();
    application = null;   // already cleaned up
  }

  /**
   * A node that never came up - an unreachable database, a wrong backend password - still has to
   * release everything it did acquire.
   * <p>
   * The key cache exists from construction on, but its eviction thread is only started at the
   * very end of {@code startup()}, in {@code finishStartup()}. A failing login therefore leaves an
   * unstarted cache behind, and terminating one of those is an error. The cleanup must not trip over that: it is
   * invoked from {@code stop()}, which swallows what it throws, so an exception here would
   * silently skip closing the session, shutting down the pools and deregistering the JDBC drivers
   * - and inside a container there is no {@code System.exit()} to clean up after it.
   */
  @Test
  public void cleansUpAfterAFailedStartup() {
    application = new TestWebApplication(session, new RecordingSessionPool(session), null) {
      @Override
      protected void login() {
        throw new TentackleRuntimeException("backend unreachable");
      }
    };

    TentackleRuntimeException ex = expectThrows(TentackleRuntimeException.class, application::startup);
    assertEquals(ex.getMessage(), "backend unreachable", "the real cause must not be masked");
    assertNull(WebTestSupport.cleanupThread(keyCache(application)), "the eviction thread never started");

    application.cleanup();    // must not throw

    assertFalse(session.isOpen(), "cleanup must have closed the backend session");

    application.unregister();
    application = null;   // already cleaned up
  }


  /**
   * Reads the application's key cache.
   *
   * @param application the application
   * @return the cache
   */
  private static WebSessionKeyCache<?> keyCache(WebApplication<?> application) {
    try {
      var field = WebApplication.class.getDeclaredField("sessionInfoCache");
      field.setAccessible(true);
      return (WebSessionKeyCache<?>) field.get(application);
    }
    catch (ReflectiveOperationException ex) {
      throw new AssertionError("cannot read the key cache", ex);
    }
  }

}

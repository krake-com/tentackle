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
package org.tentackle.session;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tests that a keep-alive daemon dying does not take the registered sessions with it,
 * and that the session task dispatcher publishes its state safely.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SessionUtilitiesKeepAliveTest {

  private static final long KEEP_ALIVE_INTERVAL = 60000;


  /**
   * A daemon that never actually runs, so it is never {@link Thread#isAlive() alive}.<br>
   * That is what {@link SessionUtilities} sees once a daemon has died.
   */
  private static class DeadOnArrivalDaemon extends SessionKeepAliveDaemon {

    DeadOnArrivalDaemon() {
      super(1000);
    }

    @Override
    public void start() {
      // deliberately not started
    }
  }


  /**
   * Session utilities recording the daemons they create.
   */
  private static class TestSessionUtilities extends SessionUtilities {

    private final List<SessionKeepAliveDaemon> daemons = new ArrayList<>();

    @Override
    protected SessionKeepAliveDaemon createKeepAliveDaemon() {
      SessionKeepAliveDaemon daemon = new DeadOnArrivalDaemon();
      daemons.add(daemon);
      return daemon;
    }
  }


  @Test
  public void testRestartedDaemonTakesOverTheSessions() {
    TestSessionUtilities utilities = new TestSessionUtilities();
    Session first = createSession("first");
    Session second = createSession("second");

    utilities.keepAliveIntervalChanged(first);
    Assert.assertEquals(utilities.daemons.size(), 1);
    Assert.assertEquals(utilities.daemons.get(0).getKeepAliveSessions(), List.of(first));

    // the daemon is not alive, so the next call has to replace it -- and carry the sessions over
    utilities.keepAliveIntervalChanged(second);
    Assert.assertEquals(utilities.daemons.size(), 2);

    Collection<Session> keptAlive = utilities.daemons.get(1).getKeepAliveSessions();
    Assert.assertTrue(keptAlive.contains(first), "the restarted daemon dropped an already registered session");
    Assert.assertTrue(keptAlive.contains(second));
    Assert.assertEquals(keptAlive.size(), 2);
  }


  @Test
  public void testClosedSessionsAreNotCarriedOver() {
    TestSessionUtilities utilities = new TestSessionUtilities();
    Session open = createSession("open");
    Session closed = createSession("closed");

    utilities.keepAliveIntervalChanged(open);
    utilities.keepAliveIntervalChanged(closed);
    Assert.assertEquals(utilities.daemons.get(1).getKeepAliveSessions().size(), 2);

    setOpen(closed, false);
    utilities.keepAliveIntervalChanged(open);   // triggers the next restart

    Collection<Session> keptAlive = utilities.daemons.get(2).getKeepAliveSessions();
    Assert.assertEquals(keptAlive, List.of(open));
  }


  /**
   * The dispatcher's session is written by the dispatcher thread and read by every thread
   * submitting a task, so the fields have to be volatile. There is no way to observe a missing
   * volatile deterministically, hence the reflective check.
   */
  @Test
  public void testDispatcherStateIsVolatile() throws NoSuchFieldException {
    for (String fieldName: List.of("session", "sessionKeptAlive", "sessionClosedOnTermination")) {
      Assert.assertTrue(
          Modifier.isVolatile(DefaultSessionTaskDispatcher.class.getDeclaredField(fieldName).getModifiers()),
          DefaultSessionTaskDispatcher.class.getSimpleName() + '.' + fieldName + " must be volatile");
    }
  }


  @Test
  public void testTerminatedDaemonStartsWithoutSessions() {
    TestSessionUtilities utilities = new TestSessionUtilities();
    utilities.keepAliveIntervalChanged(createSession("first"));

    utilities.terminateHelperThreads();         // deliberate shutdown, not a death
    utilities.startKeepAliveDaemonIfNotRunning();

    Assert.assertEquals(utilities.daemons.size(), 2);
    Assert.assertTrue(utilities.daemons.get(1).getKeepAliveSessions().isEmpty());
  }


  private final Collection<Session> closedSessions = new ArrayList<>();

  private void setOpen(Session session, boolean open) {
    if (open) {
      closedSessions.remove(session);
    }
    else {
      closedSessions.add(session);
    }
  }

  /**
   * Creates a session providing just what the keep-alive bookkeeping needs.
   *
   * @param name the session name
   * @return the session
   */
  private Session createSession(String name) {
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(), new Class<?>[] { Session.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "getKeepAliveInterval" -> KEEP_ALIVE_INTERVAL;
          case "isOpen" -> !closedSessions.contains((Session) proxy);
          case "getName", "toString" -> name;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

}

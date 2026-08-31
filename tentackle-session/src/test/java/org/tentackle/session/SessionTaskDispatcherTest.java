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

import org.testng.annotations.Test;

import java.lang.reflect.Proxy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the session bookkeeping of {@link DefaultSessionTaskDispatcher} and the tasks it runs.
 * <p>
 * None of the dispatchers below is ever started - what is under test is the state a dispatcher is
 * configured with before it runs, and the session it hands to its tasks.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SessionTaskDispatcherTest {

  /** the dispatchers below never run, but the interval must be valid. */
  private static final long KEEP_ALIVE_MS = 1000;

  /**
   * A session task remembering whether it ran.
   */
  private static class TestTask extends AbstractSessionTask {

    @Override
    public void run() {
      // never started
    }
  }

  /**
   * Creates a session.
   *
   * @param pooled true if the session belongs to a pool
   * @return the session
   */
  private static Session session(boolean pooled) {
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(), new Class<?>[] { Session.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals"   -> proxy == args[0];
          case "toString" -> pooled ? "pooled session" : "exclusive session";
          case "isPooled" -> pooled;
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }


  /**
   * A dispatcher owns its session, so it must not take one that a pool may hand to someone else.
   */
  @Test
  public void testPooledSessionRejected() {
    assertThrows(IllegalArgumentException.class,
                 () -> new DefaultSessionTaskDispatcher("test", session(true), false, KEEP_ALIVE_MS, 0));
  }

  @Test
  public void testExclusiveSessionAccepted() {
    Session session = session(false);
    DefaultSessionTaskDispatcher dispatcher = new DefaultSessionTaskDispatcher("test", session, false, KEEP_ALIVE_MS, 0);
    assertSame(dispatcher.getSession(), session);
    assertFalse(dispatcher.isAlive());
  }

  @Test
  public void testDispatcherWithoutASessionCanBeConfiguredLater() {
    DefaultSessionTaskDispatcher dispatcher = new DefaultSessionTaskDispatcher("test");
    assertNull(dispatcher.getSession());

    Session session = session(false);
    dispatcher.setSession(session);   // allowed as long as the dispatcher is not running
    assertSame(dispatcher.getSession(), session);
  }

  @Test
  public void testFlags() {
    DefaultSessionTaskDispatcher dispatcher = new DefaultSessionTaskDispatcher("test");
    assertFalse(dispatcher.isSessionKeptAlive());
    assertFalse(dispatcher.isSessionClosedOnTermination());

    dispatcher.setSessionKeptAlive(true);
    dispatcher.setSessionClosedOnTermination(true);
    assertTrue(dispatcher.isSessionKeptAlive());
    assertTrue(dispatcher.isSessionClosedOnTermination());
  }

  /**
   * A task must run on the dispatcher's session, not on whatever the submitting thread happens to
   * use, so the dispatcher hands its own session to every session task it queues.
   */
  @Test
  public void testQueuedTaskGetsTheDispatchersSession() {
    Session session = session(false);
    DefaultSessionTaskDispatcher dispatcher = new DefaultSessionTaskDispatcher("test", session, false, KEEP_ALIVE_MS, 0);
    TestTask task = new TestTask();
    assertNull(task.getSession());

    assertTrue(dispatcher.addTask(task));
    assertSame(task.getSession(), session);
    assertEquals(dispatcher.getQueueSize(), 1);
  }

  @Test
  public void testTaskIsNotQueuedTwice() {
    DefaultSessionTaskDispatcher dispatcher = new DefaultSessionTaskDispatcher("test", session(false), false, KEEP_ALIVE_MS, 0);
    TestTask task = new TestTask();
    assertTrue(dispatcher.addTask(task));
    assertFalse(dispatcher.addTask(task), "already queued");
    assertEquals(dispatcher.getQueueSize(), 1);
  }

  /**
   * A task whose session was made immutable belongs to that session for good.
   */
  @Test
  public void testImmutableTaskSessionCannotBeChanged() {
    Session session = session(false);
    TestTask task = new TestTask();
    task.setSession(session);
    task.setSessionImmutable(true);
    assertTrue(task.isSessionImmutable());

    task.setSession(session);   // no change is fine
    assertThrows(PersistenceException.class, () -> task.setSession(session(false)));
    assertSame(task.getSession(), session);
  }

}

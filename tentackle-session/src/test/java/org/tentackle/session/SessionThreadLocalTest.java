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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.tentackle.misc.TrackedArrayList;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the thread-local session and the session dependables it is applied to.
 * <p>
 * The thread-local session is what makes a {@code DomainContext} without an explicit session work,
 * so the bookkeeping in {@link Session} and {@link ThreadLocalSessionHolder} must be exact.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SessionThreadLocalTest {

  /**
   * A session dependable counting how often its session was set.
   */
  private static class Dependable implements SessionDependable {

    private Session session;
    private boolean sessionImmutable;
    private int setCount;

    @Override
    public Session getSession() {
      return session;
    }

    @Override
    public void setSession(Session session) {
      this.session = session;
      setCount++;
    }

    @Override
    public void setSessionImmutable(boolean sessionImmutable) {
      this.sessionImmutable = sessionImmutable;
    }

    @Override
    public boolean isSessionImmutable() {
      return sessionImmutable;
    }
  }


  /**
   * Creates a session that is never actually used.
   *
   * @param name the name to report
   * @return the session
   */
  private static Session session(String name) {
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(), new Class<?>[] { Session.class },
        (proxy, method, args) -> {
          if (method.isDefault()) {
            // the default methods are the ones under test
            return InvocationHandler.invokeDefault(proxy, method, args);
          }
          return switch (method.getName()) {
            case "hashCode"          -> System.identityHashCode(proxy);
            case "equals"            -> proxy == args[0];
            case "toString"          -> name;
            case "getInstanceNumber" -> 4711;
            default -> throw new UnsupportedOperationException(method.getName());
          };
        });
  }


  /**
   * No test may leave a session behind for the next one.
   */
  @AfterMethod
  public void clearThreadLocalSession() {
    Session.setCurrentSession(null);
  }


  @Test
  public void testNoSessionByDefault() {
    assertNull(Session.getCurrentSession());
    assertThrows(PersistenceException.class, Session::getSession);
    assertThrows(PersistenceException.class, Session::assertCurrentSessionValid);
  }

  @Test
  public void testMakeCurrentAndClear() {
    Session session = session("one");
    assertFalse(session.isCurrent());

    session.makeCurrent();
    assertTrue(session.isCurrent());
    assertSame(Session.getCurrentSession(), session);
    assertSame(Session.getSession(), session);
    Session.assertCurrentSessionValid();

    session.clearCurrent();
    assertNull(Session.getCurrentSession());
    assertFalse(session.isCurrent());
  }

  /**
   * Only the current session may clear the thread's session - clearing a foreign one would pull it
   * out from under its owner.
   */
  @Test
  public void testForeignSessionCannotClear() {
    Session current = session("current");
    Session other = session("other");
    current.makeCurrent();

    assertThrows(PersistenceException.class, other::clearCurrent);
    assertSame(Session.getCurrentSession(), current, "the current session must survive");
  }

  @Test
  public void testSetCurrentSessionToNullClears() {
    session("one").makeCurrent();
    Session.setCurrentSession(null);
    assertNull(Session.getCurrentSession());
  }

  /**
   * The session is thread-local, so a session made current here is invisible to another thread.
   */
  @Test
  public void testSessionIsLocalToItsThread() throws InterruptedException {
    Session session = session("one");
    session.makeCurrent();

    Session[] seenByOtherThread = new Session[1];
    boolean[] otherThreadSawOne = new boolean[1];
    Thread thread = new Thread(() -> {
      seenByOtherThread[0] = Session.getCurrentSession();
      otherThreadSawOne[0] = session.isCurrent();
    });
    thread.start();
    thread.join();

    assertNull(seenByOtherThread[0]);
    assertFalse(otherThreadSawOne[0]);
    assertSame(Session.getCurrentSession(), session, "this thread keeps its session");
  }

  /**
   * A session must be applied only if it really differs. Objects referring to each other in a cycle
   * would loop forever otherwise.
   */
  @Test
  public void testApplyToSetsTheSessionOnlyOnce() {
    Session session = session("one");
    Dependable dependable = new Dependable();

    session.applyTo(dependable);
    assertSame(dependable.getSession(), session);
    assertEquals(dependable.setCount, 1);

    session.applyTo(dependable);
    assertEquals(dependable.setCount, 1, "the session did not change, so it must not be set again");
  }

  @Test
  public void testApplyToIgnoresNull() {
    session("one").applyTo((SessionDependable) null);
    session("one").applyTo((List<SessionDependable>) null);
  }

  @Test
  public void testApplyToCollection() {
    Session session = session("one");
    List<Dependable> dependables = new ArrayList<>(List.of(new Dependable(), new Dependable()));

    session.applyTo(dependables);
    for (Dependable dependable : dependables) {
      assertSame(dependable.getSession(), session);
    }
  }

  /**
   * Objects removed from a tracked list still have to be saved, i.e., deleted, so they need the
   * session as much as the ones still in it.
   */
  @Test
  public void testApplyToReachesTheRemovedObjects() {
    Session session = session("one");
    Dependable removed = new Dependable();
    TrackedArrayList<Dependable> list = new TrackedArrayList<>(false);
    list.add(removed);
    list.remove(removed);

    session.applyTo(list);
    assertSame(removed.getSession(), session, "the removed object must be reached too");
  }

  @Test
  public void testThreadLocalSessionHolder() {
    ThreadLocalSessionHolder holder = new ThreadLocalSessionHolder();
    assertTrue(holder.isSessionThreadLocal());
    assertFalse(holder.isSessionImmutable());
    holder.setSessionImmutable(true);
    assertTrue(holder.isSessionImmutable());

    assertThrows(PersistenceException.class, holder::getSession);

    Session session = session("one");
    session.makeCurrent();
    assertSame(holder.getSession(), session);
    assertEquals(holder.getSessionInstanceNumber(), 4711);
  }

  /**
   * The holder is thread-local by definition: it accepts the current session but nothing else.
   */
  @Test
  public void testThreadLocalSessionHolderRejectsAnotherSession() {
    ThreadLocalSessionHolder holder = new ThreadLocalSessionHolder();
    Session session = session("one");
    session.makeCurrent();

    holder.setSession(session);   // the current one is fine
    assertThrows(UnsupportedOperationException.class, () -> holder.setSession(session("other")));
    assertThrows(UnsupportedOperationException.class, () -> holder.setSession(null));
  }

}

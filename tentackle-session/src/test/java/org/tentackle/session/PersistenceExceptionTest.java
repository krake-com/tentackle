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

import org.tentackle.misc.Identifiable;

import java.lang.reflect.Proxy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link PersistenceException}.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class PersistenceExceptionTest {

  /**
   * An identifiable that is not attached to a session.
   */
  private static class Thing implements Identifiable {

    @Override
    public long getId() {
      return 7;
    }

    @Override
    public String toGenericString() {
      return "Thing[7]";
    }
  }

  /**
   * An identifiable that knows its session, as a PDO does.
   */
  private static class AttachedThing extends Thing implements SessionProvider {

    private final Session session;

    AttachedThing(Session session) {
      this.session = session;
    }

    @Override
    public Session getSession() {
      return session;
    }
  }

  /**
   * Creates a session that only reports its name.
   *
   * @return the session
   */
  private static Session session() {
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(), new Class<?>[] { Session.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals"   -> proxy == args[0];
          case "toString" -> "mysession";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }


  @Test
  public void testExtractsDirectException() {
    PersistenceException pex = new PersistenceException("boom");
    assertSame(PersistenceException.extractPersistenceException(pex), pex);
  }

  @Test
  public void testExtractsNestedException() {
    PersistenceException pex = new PersistenceException("boom");
    Throwable wrapped = new RuntimeException("outer", new IllegalStateException("middle", pex));
    assertSame(PersistenceException.extractPersistenceException(wrapped), pex);
  }

  /**
   * Walking from the head, the outermost one is returned.
   */
  @Test
  public void testReturnsFirstExceptionInChain() {
    PersistenceException inner = new PersistenceException("inner");
    PersistenceException outer = new PersistenceException("outer", inner);
    assertSame(PersistenceException.extractPersistenceException(outer), outer);
  }

  @Test
  public void testReturnsNullWhenAbsent() {
    assertNull(PersistenceException.extractPersistenceException(null));
    assertNull(PersistenceException.extractPersistenceException(
        new RuntimeException("outer", new IllegalStateException("nothing persistent here"))));
  }

  @Test
  public void testPlainExceptionHasNeitherSessionNorIdentifiable() {
    PersistenceException pex = new PersistenceException("boom");
    assertNull(pex.getSession());
    assertNull(pex.getIdentifiable());
    assertEquals(pex.getMessage(), "boom");
  }

  @Test
  public void testSessionIsReported() {
    Session session = session();
    PersistenceException pex = new PersistenceException(session, "boom");
    assertSame(pex.getSession(), session);
    assertEquals(pex.getMessage(), "boom [_ mysession _]");
  }

  @Test
  public void testIdentifiableIsReported() {
    Thing thing = new Thing();
    PersistenceException pex = new PersistenceException(thing, "boom");
    assertSame(pex.getIdentifiable(), thing);
    assertNull(pex.getSession(), "the thing is not attached to a session");
    assertEquals(pex.getMessage(), "boom [_ Thing[7] _]");
  }

  /**
   * An identifiable that provides a session hands it to the exception, so that the message names
   * the session the failure happened on.
   */
  @Test
  public void testSessionIsTakenFromTheIdentifiable() {
    Session session = session();
    PersistenceException pex = new PersistenceException(new AttachedThing(session), "boom");
    assertSame(pex.getSession(), session);
    assertEquals(pex.getMessage(), "boom [_ Thing[7] -- mysession _]");
  }

  /**
   * The message is built once and kept because it has to survive the transaction it was created in.
   */
  @Test
  public void testMessageIsBuiltOnlyOnce() {
    PersistenceException pex = new PersistenceException(new Thing(), "boom");
    assertSame(pex.getMessage(), pex.getMessage());
  }

  @Test
  public void testEvaluateForcesTheMessage() {
    PersistenceException pex = new PersistenceException(new Thing(), "boom");
    assertSame(pex.evaluate(), pex);
    assertEquals(pex.getMessage(), "boom [_ Thing[7] _]");
  }

  /**
   * The identifiable is added by a layer that knows it - but never replaces one already set.
   */
  @Test
  public void testUpdateDbObjectFillsInTheMissingIdentifiable() {
    PersistenceException pex = new PersistenceException("boom");
    Thing thing = new Thing();
    pex.updateDbObject(thing);
    assertSame(pex.getIdentifiable(), thing);

    pex.updateDbObject(new Thing());
    assertSame(pex.getIdentifiable(), thing, "an identifiable already set must not be replaced");
  }

  @Test
  public void testCauseIsKept() {
    IllegalStateException cause = new IllegalStateException("cause");
    assertSame(new PersistenceException("boom", cause).getCause(), cause);
    assertSame(new PersistenceException(cause).getCause(), cause);
    assertSame(new PersistenceException(session(), "boom", cause).getCause(), cause);
    assertSame(new PersistenceException(new Thing(), "boom", cause).getCause(), cause);
  }

  @Test
  public void testNotTemporaryByDefault() {
    PersistenceException pex = new PersistenceException("boom");
    assertFalse(pex.isTemporary());
    pex.setTemporary(true);
    assertTrue(pex.isTemporary());
  }

}

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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.Identifiable;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the class/table registry, the exception helpers, and the exception message decoration of
 * {@link SessionUtilities}.
 * <p>
 * The keep-alive part is covered by {@link SessionUtilitiesKeepAliveTest}.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SessionUtilitiesTest {

  /** a PDO-like test class carrying both annotations. */
  @TableName("testtable")
  @ClassId(31_001)
  static class AnnotatedEntity {
  }

  /** the schema of the tablename is mapped to a flat name. */
  @TableName(value = "md.category", mapSchema = true)
  static class SchemaMappedEntity {
  }

  /** the tablename is prefixed. */
  @TableName(value = "md.category", prefix = "pls")
  static class PrefixedEntity {
  }

  /** both options combined. */
  @TableName(value = "md.category", mapSchema = true, prefix = "pls")
  static class PrefixedAndMappedEntity {
  }

  /** an empty tablename means "not annotated at all", e.g., a single-inherited entity. */
  @TableName("")
  static class EmptyTableNameEntity {
  }

  /** not annotated at all. */
  static class PlainClass {
  }


  /**
   * An identifiable that is not attached to a session.
   */
  private record Thing(long id) implements Identifiable {

    @Override
    public long getId() {
      return id;
    }

    @Override
    public String toGenericString() {
      return "Thing[" + id + "]";
    }
  }

  /**
   * Creates a session that only reports its name.
   *
   * @param name the name to report
   * @return the session
   */
  private static Session session(String name) {
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(), new Class<?>[] { Session.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals"   -> proxy == args[0];
          case "toString" -> name;
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }


  // ---------------- the tablename and class id derived from the annotations ----------------

  @Test
  public void testDetermineTablename() {
    SessionUtilities utilities = SessionUtilities.getInstance();
    assertEquals(utilities.determineTablename(AnnotatedEntity.class), "testtable");
    assertEquals(utilities.determineTablename(SchemaMappedEntity.class), "md_category");
    assertEquals(utilities.determineTablename(PrefixedEntity.class), "plsmd.category");
    assertEquals(utilities.determineTablename(PrefixedAndMappedEntity.class), "plsmd_category");
  }

  @Test
  public void testNoTablename() {
    SessionUtilities utilities = SessionUtilities.getInstance();
    assertNull(utilities.determineTablename(PlainClass.class), "not annotated");
    assertNull(utilities.determineTablename(EmptyTableNameEntity.class), "an empty name is no name");
  }

  @Test
  public void testDetermineClassId() {
    assertEquals(SessionUtilities.getInstance().determineClassId(AnnotatedEntity.class), 31_001);
    assertEquals(SessionUtilities.getInstance().determineClassId(PlainClass.class), 0, "not annotated");
  }


  // ---------------- the registry built from the service index ----------------

  /**
   * The registry maps in both directions, and unknown keys are answered with null resp. 0.
   */
  @Test
  public void testRegistry() {
    SessionUtilities utilities = new SessionUtilities();
    String className = RegisteredTestEntity.class.getName();

    assertEquals(utilities.getTableName(className), "testregistered");
    assertEquals(utilities.getClassName("testregistered"), className);
    assertEquals(utilities.getClassId(className), 31_002);
    assertEquals(utilities.getClassName(31_002), className);

    assertNull(utilities.getTableName("no.such.Class"));
    assertNull(utilities.getClassName("nosuchtable"));
    assertEquals(utilities.getClassId("no.such.Class"), 0, "an unknown class has no class id");
    assertNull(utilities.getClassName(-4711));
  }

  @Test
  public void testClassNamesAreSorted() {
    List<String> classNames = new ArrayList<>(new SessionUtilities().getClassNames());
    assertTrue(classNames.contains(RegisteredTestEntity.class.getName()));
    List<String> sorted = new ArrayList<>(classNames);
    sorted.sort(null);
    assertEquals(classNames, sorted);
  }


  // ---------------- deciding whether a rollback has to be logged ----------------

  /**
   * A rollback caused by something other than a persistence problem is logged in full.
   */
  @Test
  public void testRollbackOfAPersistenceExceptionIsLogged() {
    SessionUtilities utilities = SessionUtilities.getInstance();
    assertFalse(utilities.isSilentRollbackSufficient(new PersistenceException("boom")));
    assertFalse(utilities.isSilentRollbackSufficient(
        new PersistenceException("outer", new PersistenceException("inner"))));
  }

  @Test
  public void testRollbackWithoutAPersistenceExceptionIsSilent() {
    SessionUtilities utilities = SessionUtilities.getInstance();
    assertTrue(utilities.isSilentRollbackSufficient(null));
    assertTrue(utilities.isSilentRollbackSufficient(new IllegalStateException("not a persistence problem")));
  }

  /**
   * Temporary exceptions - a lock held by another user, for example - are expected and must not
   * flood the log with the statements that were rolled back.
   */
  @Test
  public void testRollbackOfATemporaryExceptionIsSilent() {
    SessionUtilities utilities = SessionUtilities.getInstance();

    PersistenceException temporary = new PersistenceException("locked");
    temporary.setTemporary(true);
    assertTrue(utilities.isSilentRollbackSufficient(temporary));

    // a temporary cause anywhere in the chain is enough
    TentackleRuntimeException temporaryCause = new TentackleRuntimeException("temporary cause");
    temporaryCause.setTemporary(true);
    assertTrue(utilities.isSilentRollbackSufficient(new PersistenceException("outer", temporaryCause)));
  }


  // ---------------- the message decoration of a PersistenceException ----------------

  @Test
  public void testPlainMessageIsNotDecorated() {
    assertEquals(message("boom", null, null), "boom");
    assertEquals(message(null, null, null), "", "a missing message becomes the empty one");
  }

  @Test
  public void testIdentifiableAndSessionAreAppended() {
    assertEquals(message("boom", new Thing(7), null), "boom [_ Thing[7] _]");
    assertEquals(message("boom", null, session("mysession")), "boom [_ mysession _]");
    assertEquals(message("boom", new Thing(7), session("mysession")), "boom [_ Thing[7] -- mysession _]");
  }

  /**
   * Whatever the message already says is not repeated.
   */
  @Test
  public void testAlreadyMentionedPartsAreNotRepeated() {
    assertEquals(message("cannot save Thing[7]", new Thing(7), null), "cannot save Thing[7]");
    assertEquals(message("cannot save Thing[7] on mysession", new Thing(7), session("mysession")),
                 "cannot save Thing[7] on mysession");
    assertEquals(message("cannot save Thing[7]", new Thing(7), session("mysession")),
                 "cannot save Thing[7] [_ mysession _]");
  }

  /**
   * A message spanning multiple lines - either literally or by its sheer length - gets the
   * decoration on a line of its own.
   */
  @Test
  public void testMultiLineMessagesAreDecoratedOnTheirOwnLine() {
    assertEquals(message("first\nsecond", new Thing(7), null), "first\nsecond\n[_ Thing[7] _]");

    String longMessage = "x".repeat(81);
    assertEquals(message(longMessage, new Thing(7), null), longMessage + "\n[_ Thing[7] _]");
    String borderlineMessage = "x".repeat(80);
    assertEquals(message(borderlineMessage, new Thing(7), null), borderlineMessage + " [_ Thing[7] _]");
  }

  /**
   * Builds the lazy exception message.
   *
   * @param msg the original message
   * @param identifiable the identifiable, null if none
   * @param session the session, null if none
   * @return the message
   */
  private String message(String msg, Identifiable identifiable, Session session) {
    return SessionUtilities.getInstance().createLazyExceptionMessage(msg, null, identifiable, session).toString();
  }

}

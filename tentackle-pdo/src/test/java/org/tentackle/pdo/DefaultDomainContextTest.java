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

package org.tentackle.pdo;

import org.testng.annotations.Test;

import org.tentackle.session.PersistenceException;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DefaultDomainContext}.
 * <p>
 * All contexts here are created without a session, i.e., they use the thread-local one, which is
 * never set in these tests. Everything below, therefore, exercises the context logic only - the
 * session-dependent parts belong to the database-backed tests.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultDomainContextTest {

  @Test
  public void testUnnamedContext() {
    DefaultDomainContext context = new DefaultDomainContext();
    assertEquals(context.getNames(), List.of(""));
    assertEquals(context.toGenericString(), "");
    assertEquals(context.toString(), "");
    assertTrue(context.isSessionThreadLocal());
    assertFalse(context.isSessionImmutable());
    assertEquals(context.getSessionInstanceNumber(), 0, "no session means instance number 0");
  }

  @Test
  public void testNamesAreImmutable() {
    List<String> names = new DefaultDomainContext().getNames();
    assertThrows(UnsupportedOperationException.class, () -> names.add("nope"));
  }

  /**
   * The unnamed context is within the unnamed context only.
   */
  @Test
  public void testIsWithinContextByName() {
    DefaultDomainContext context = new DefaultDomainContext();
    assertTrue(context.isWithinContext(""), "the name chain always starts with the unnamed context");
    assertFalse(context.isWithinContext("edit"));

    DomainContext edit = context.clone("edit");
    assertTrue(edit.isWithinContext(""));
    assertTrue(edit.isWithinContext("edit"));
    assertFalse(edit.isWithinContext("detail"));
    assertFalse(context.isWithinContext("edit"), "the template must not be modified");
  }

  @Test
  public void testNameChain() {
    DomainContext edit = new DefaultDomainContext().clone("edit");
    assertEquals(edit.getNames(), List.of("", "edit"));
    assertEquals(edit.toGenericString(), "edit");

    DomainContext detail = edit.clone("detail");
    assertEquals(detail.getNames(), List.of("", "edit", "detail"));
    assertEquals(detail.toGenericString(), "edit:detail");
    assertTrue(detail.isWithinContext("edit"));
  }

  /**
   * The chained name is computed lazily and cached, and {@code clone} copies all fields.
   * A named clone must therefore drop the cached name of its template, or it would report the
   * template's chain instead of its own.
   */
  @Test
  public void testNamedCloneDropsTheCachedChainedName() {
    DefaultDomainContext context = new DefaultDomainContext();
    assertEquals(context.toGenericString(), "", "caches the chained name of the unnamed context");

    DomainContext edit = context.clone("edit");
    assertEquals(edit.toGenericString(), "edit");
    assertEquals(edit.toGenericString(), "edit", "the cached name must be stable");

    DomainContext detail = edit.clone("detail");
    assertEquals(detail.toGenericString(), "edit:detail");
  }

  @Test
  public void testIllegalContextNamesRejected() {
    DefaultDomainContext context = new DefaultDomainContext();
    assertThrows(DomainException.class, () -> context.clone((String) null));
    assertThrows(DomainException.class, () -> context.clone(""));
    assertThrows(DomainException.class, () -> context.clone("   "));
    assertThrows("the colon separates the names of the chain and must not appear in a name",
                 DomainException.class, () -> context.clone("edit:detail"));
  }

  @Test
  public void testDuplicateContextNameRejected() {
    DomainContext edit = new DefaultDomainContext().clone("edit");
    assertThrows(DomainException.class, () -> edit.clone("edit"));
  }

  /**
   * A clone refers back to the context it was cloned from.
   */
  @Test
  public void testClonedContextReference() {
    DefaultDomainContext context = new DefaultDomainContext();
    assertNull(context.getClonedContext());
    assertSame(context.clone().getClonedContext(), context);
    assertSame(context.clone("edit").getClonedContext(), context);
  }

  /**
   * A clone is always mutable, no matter whether the template was immutable.
   */
  @Test
  public void testCloneIsMutable() {
    DefaultDomainContext context = new DefaultDomainContext(null, true);
    assertTrue(context.isSessionImmutable());
    assertFalse(context.clone().isSessionImmutable());
    assertFalse(context.cloneKeepRoot().isSessionImmutable());
  }

  @Test
  public void testThreadLocalSessionContext() {
    DefaultDomainContext context = new DefaultDomainContext();
    DomainContext tls = context.getThreadLocalSessionContext();
    assertSame(context.getThreadLocalSessionContext(), tls, "must be cached");
    assertTrue(tls.isSessionThreadLocal());
    assertTrue(tls.isSessionImmutable());
    assertSame(tls.getThreadLocalSessionContext(), tls, "a thread-local context is its own");

    context.clearThreadLocalSessionContext();
    assertNotSame(context.getThreadLocalSessionContext(), tls);
  }

  /**
   * Contexts are compared by class, context ID, and session instance number only.
   * The name chain deliberately does not take part in it.
   */
  @Test
  public void testEqualsAndCompareTo() {
    DefaultDomainContext one = new DefaultDomainContext();
    DefaultDomainContext two = new DefaultDomainContext();
    assertEquals(one, two);
    assertEquals(one.hashCode(), two.hashCode());
    assertEquals(one.compareTo(two), 0);
    assertEquals(one, one.clone("edit"));
    assertNotEquals(one, null);
  }

  @Test
  public void testNoRootEntity() {
    DefaultDomainContext context = new DefaultDomainContext();
    assertFalse(context.isRootContext());
    assertNull(context.getRootEntity());
    assertEquals(context.getRootId(), 0);
    assertEquals(context.getRootClassId(), 0);
    assertSame(context.getNonRootContext(), context);
    assertThrows(PersistenceException.class, () -> context.getRootContext(null));
  }

  @Test
  public void testDiagnosticString() {
    assertEquals(new DefaultDomainContext().toDiagnosticString(),
                 "context '' using thread-local session <none>");
    assertEquals(new DefaultDomainContext().clone("edit").toDiagnosticString(),
                 "context 'edit' using thread-local session <none>");
  }

  /**
   * The session info comes from the session, so there is none without a session.
   */
  @Test
  public void testSessionInfoNeedsASession() {
    assertThrows(PdoRuntimeException.class, () -> new DefaultDomainContext().getSessionInfo());
  }

}

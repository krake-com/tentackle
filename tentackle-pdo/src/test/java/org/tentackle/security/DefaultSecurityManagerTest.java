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

package org.tentackle.security;

import org.testng.annotations.Test;

import org.tentackle.pdo.DefaultDomainContext;
import org.tentackle.pdo.DomainContext;
import org.tentackle.security.permissions.ReadPermissionImpl;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.SessionInfo;

import java.util.Collection;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DefaultSecurityManager}.
 * <p>
 * Everything that reaches the ACL itself needs the {@link org.tentackle.security.pdo.Security}
 * rules from the database and is covered by the database-backed tests. What remains - and is
 * tested here - is the behavior before the rules are consulted: the disabled manager, the
 * argument checks, and the determination of the grantee.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultSecurityManagerTest {

  /**
   * A domain context providing a session-info without a session.
   */
  private static class TestDomainContext extends DefaultDomainContext {

    private final SessionInfo sessionInfo;

    TestDomainContext(long userId, int userClassId) {
      sessionInfo = new DefaultSessionInfo();
      sessionInfo.setUserId(userId);
      sessionInfo.setUserClassId(userClassId);
    }

    @Override
    public SessionInfo getSessionInfo() {
      return sessionInfo;
    }
  }


  private static final Permission READ = new ReadPermissionImpl();

  /**
   * The system user, who is granted everything.
   */
  private static DomainContext systemContext() {
    return new TestDomainContext(0, 0);
  }

  /**
   * A regular user.
   */
  private static DomainContext userContext() {
    return new TestDomainContext(4711, 42);
  }


  @Test
  public void testDefaults() {
    DefaultSecurityManager manager = new DefaultSecurityManager();
    assertFalse(manager.isEnabled(), "the manager must be enabled explicitly on startup");
    assertTrue(manager.isAcceptByDefault());
  }

  @Test
  public void testEnabledAndAcceptByDefaultAreSettable() {
    DefaultSecurityManager manager = new DefaultSecurityManager();
    manager.setEnabled(true);
    assertTrue(manager.isEnabled());
    manager.setAcceptByDefault(false);
    assertFalse(manager.isAcceptByDefault());
  }

  /**
   * While disabled, every request is granted - no matter whether the manager denies by default.
   */
  @Test
  public void testDisabledManagerGrantsEverything() {
    DefaultSecurityManager manager = new DefaultSecurityManager();
    manager.setAcceptByDefault(false);

    for (SecurityResult result : List.of(manager.evaluate(userContext(), READ, 5, 42),
                                         manager.evaluate(userContext(), READ, String.class))) {
      assertTrue(result.isAccepted());
      assertTrue(result.isDefault());
      assertEquals(result.explain("denied"), "denied\nsecurity manager is disabled");
    }
  }

  /**
   * A session without a user - a background server thread, for example - has no grantee and is
   * granted everything.
   */
  @Test
  public void testNoGranteeGrantsEverything() {
    DefaultSecurityManager manager = new DefaultSecurityManager();
    manager.setEnabled(true);
    manager.setAcceptByDefault(false);

    SecurityResult result = manager.evaluate(systemContext(), READ, 5, 42);
    assertTrue(result.isAccepted());
    assertTrue(result.isDefault());
    assertEquals(result.explain("denied"), "denied\nno grantee");
  }

  @Test
  public void testIllegalArgumentsRejected() {
    DefaultSecurityManager manager = new DefaultSecurityManager();
    manager.setEnabled(true);

    assertThrows(SecurityException.class, () -> manager.evaluate(null, READ, 5, 42));
    assertThrows(SecurityException.class, () -> manager.evaluate(userContext(), null, 5, 42));
    assertThrows(SecurityException.class, () -> manager.evaluate(userContext(), READ, 5, -1));
    assertThrows("neither a class nor a class id given",
                 SecurityException.class, () -> manager.evaluate(userContext(), READ, 0, 42));
    assertThrows(SecurityException.class, () -> manager.evaluate(userContext(), READ, null));
  }

  /**
   * The arguments are only checked while enabled - a disabled manager returns before.
   */
  @Test
  public void testDisabledManagerDoesNotCheckArguments() {
    assertTrue(new DefaultSecurityManager().evaluate(null, null, 0, 0).isAccepted());
  }

  @Test
  public void testGranteeOfTheSystemUser() {
    DefaultSecurityManager manager = new DefaultSecurityManager();
    assertNull(manager.determineGrantee(systemContext()), "the system user has no grantee");
    assertNull(manager.determineGrantee(new TestDomainContext(-1, 42)));
  }

  @Test
  public void testGranteeOfAUser() {
    DefaultSecurityManager manager = new DefaultSecurityManager();
    GranteeDescriptor grantee = manager.determineGrantee(userContext());
    assertEquals(grantee, new GranteeDescriptor(42, 4711));
  }

  /**
   * A user without a class ID cannot be matched against the rules.
   */
  @Test
  public void testGranteeWithoutClassIdRejected() {
    DefaultSecurityManager manager = new DefaultSecurityManager();
    assertThrows(SecurityException.class, () -> manager.determineGrantee(new TestDomainContext(4711, 0)));
  }

  /**
   * By default, the rules of the user and the rules for all grantees are checked, in that order.
   */
  @Test
  public void testGranteesToCheck() {
    DefaultSecurityManager manager = new DefaultSecurityManager();
    DomainContext context = userContext();
    GranteeDescriptor grantee = manager.determineGrantee(context);
    Collection<GranteeDescriptor> grantees = manager.determineGranteesToCheck(context, grantee);
    assertEquals(List.copyOf(grantees), List.of(grantee, new GranteeDescriptor(0, 0)));
  }

}

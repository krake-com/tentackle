/*
 * Tentackle - https://tentackle.org.
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
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.security.pdo.Security;
import org.tentackle.security.permissions.ExecutePermission;
import org.tentackle.security.permissions.ReadPermission;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.io.Serial;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link DefaultSecurityManager} against rules stored in the database.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultSecurityManagerDbTest extends AbstractPdoTest {

  private static final long USER_ID = 4711;
  private static final int USER_CLASS_ID = 42;

  /**
   * A domain context of a user other than the system user.
   */
  private static class UserContext extends DefaultDomainContext {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SessionInfo sessionInfo;

    UserContext(Session session) {
      super(session);
      sessionInfo = new DefaultSessionInfo();
      sessionInfo.setUserId(USER_ID);
      sessionInfo.setUserClassId(USER_CLASS_ID);
    }

    @Override
    public SessionInfo getSessionInfo() {
      return sessionInfo;
    }
  }


  /**
   * Creates and saves a rule for the user and a non-PDO class.
   *
   * @param clazz the secured class
   * @param priority the priority
   * @param permission the permission name
   * @param allowed true if allowed, false if denied
   */
  private void createRule(Class<?> clazz, int priority, String permission, boolean allowed) {
    Security rule = on(Security.class);
    rule.setObjectClassName(clazz.getName());
    rule.setGranteeClassId(USER_CLASS_ID);
    rule.setGranteeId(USER_ID);
    rule.setPriority(priority);
    rule.setPermissions(permission);
    rule.setAllowed(allowed);
    rule.save();
  }

  @Test(groups = "local")
  public void allRulesAreEvaluated() {
    try {
      // two rules differing only in their permissions -> same grantee, target and priority
      createRule(DefaultSecurityManagerDbTest.class, 0, ExecutePermission.NAME, true);
      createRule(DefaultSecurityManagerDbTest.class, 0, ReadPermission.NAME, false);
      // a negative priority
      createRule(String.class, -1, ExecutePermission.NAME, true);

      DefaultSecurityManager manager = new DefaultSecurityManager();
      manager.setEnabled(true);
      manager.setAcceptByDefault(false);
      DomainContext context = new UserContext(getSession());

      SecurityResult result = manager.evaluate(context, SecurityFactory.getInstance().getExecutePermission(),
                                               DefaultSecurityManagerDbTest.class);
      assertTrue(result.isAccepted());
      assertFalse(result.isDefault(), "EXEC rule lost");

      result = manager.evaluate(context, SecurityFactory.getInstance().getReadPermission(),
                                DefaultSecurityManagerDbTest.class);
      assertFalse(result.isAccepted());
      assertFalse(result.isDefault(), "READ rule lost");

      result = manager.evaluate(context, SecurityFactory.getInstance().getExecutePermission(), String.class);
      assertTrue(result.isAccepted());
      assertFalse(result.isDefault(), "rule with negative priority ignored");
    }
    finally {
      // don't leave the rules of this (rolled back) transaction in the shared cache
      Pdo.getCache(Security.class).invalidate();
    }
  }

}

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
import org.tentackle.pdo.PdoListener;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.security.pdo.Security;
import org.tentackle.security.permissions.ExecutePermission;
import org.tentackle.security.permissions.ReadPermission;
import org.tentackle.security.permissions.ViewPermission;
import org.tentackle.security.permissions.WritePermission;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationListener;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.io.Serial;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
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

  /**
   * Creates and saves a rule for the user and a PDO class or instance.
   *
   * @param objectClassId the PDO class id
   * @param objectId the object id, 0 for all instances
   * @param priority the priority
   * @param permission the permission name
   * @param allowed true if allowed, false if denied
   */
  private void createRule(int objectClassId, long objectId, int priority, String permission, boolean allowed) {
    Security rule = on(Security.class);
    rule.setObjectClassId(objectClassId);
    rule.setObjectId(objectId);
    rule.setGranteeClassId(USER_CLASS_ID);
    rule.setGranteeId(USER_ID);
    rule.setPriority(priority);
    rule.setPermissions(permission);
    rule.setAllowed(allowed);
    rule.save();
  }

  /**
   * Forces the manager to reload the rules modified so far.
   *
   * @param manager the security manager
   */
  private void reload(SecurityManager manager) {
    Pdo.getCache(Security.class).invalidate();
    manager.invalidate();
  }

  @Test(groups = "local")
  public void objectRulesDenyAllObjects() {
    try {
      int classId = on(Security.class).getClassId();    // any PDO class will do
      Permission write = SecurityFactory.getInstance().getWritePermission();
      Permission view = SecurityFactory.getInstance().getViewPermission();

      DefaultSecurityManager manager = new DefaultSecurityManager();
      manager.setEnabled(true);
      manager.setAcceptByDefault(true);
      DomainContext context = new UserContext(getSession());

      // object 1: denied to write
      createRule(classId, 1, 0, WritePermission.NAME, false);
      // object 2: denied to write, but allowed by a rule with higher precedence
      createRule(classId, 2, 0, WritePermission.NAME, true);
      createRule(classId, 2, 1, WritePermission.NAME, false);
      // object 3: denied to view only (unrelated to writing)
      createRule(classId, 3, 0, ViewPermission.NAME, false);
      reload(manager);

      // the class itself has no rules
      assertTrue(manager.evaluate(context, write, classId, 0).isAccepted());
      assertFalse(manager.evaluate(context, write, classId, 1).isAccepted());
      assertTrue(manager.evaluate(context, write, classId, 2).isAccepted());

      // writing all objects of the class must be denied due to object 1
      SecurityResult result = manager.evaluateAllObjects(context, write, classId);
      assertFalse(result.isAccepted(), "object rule ignored");
      assertFalse(result.isDefault());

      // viewing all objects of the class must be denied due to object 3
      assertFalse(manager.evaluateAllObjects(context, view, classId).isAccepted(), "object rule ignored");

      // a class without object rules is unaffected
      assertTrue(manager.evaluateAllObjects(context, write, classId + 1).isAccepted());

      // a disabled manager grants all
      manager.setEnabled(false);
      assertTrue(manager.evaluateAllObjects(context, write, classId).isAccepted());
      manager.setEnabled(true);

      // remove the denial of object 1 -> allowed, since object 2 is allowed and 3 is about viewing only
      on(Security.class).selectAll().stream()
                        .filter(r -> r.getObjectId() == 1)
                        .forEach(Security::delete);
      reload(manager);
      result = manager.evaluateAllObjects(context, write, classId);
      assertTrue(result.isAccepted(), "object rules wrongly deny");
      assertTrue(result.isDefault());

      // a denying class rule wins
      createRule(classId, 0, 0, WritePermission.NAME, false);
      reload(manager);
      result = manager.evaluateAllObjects(context, write, classId);
      assertFalse(result.isAccepted(), "class rule ignored");
      assertFalse(result.isDefault());
    }
    finally {
      Pdo.getCache(Security.class).invalidate();
    }
  }

  /**
   * Creates and saves a rule for the user and a non-PDO class, restricted to a domain context.
   *
   * @param clazz the secured class
   * @param contextId the context id, negative for any context
   * @param permission the permission name
   * @param allowed true if allowed, false if denied
   */
  private void createContextRule(Class<?> clazz, long contextId, String permission, boolean allowed) {
    Security rule = on(Security.class);
    rule.setObjectClassName(clazz.getName());
    rule.setGranteeClassId(USER_CLASS_ID);
    rule.setGranteeId(USER_ID);
    if (contextId > 0) {
      rule.setDomainContextClassId(USER_CLASS_ID);    // any class id will do
    }
    rule.setDomainContextId(contextId);
    rule.setPermissions(permission);
    rule.setAllowed(allowed);
    rule.save();
  }

  @Test(groups = "local")
  public void nullContextMeansAllContexts() {
    Session oldSession = Session.getCurrentSession();
    try {
      createContextRule(String.class, 7, WritePermission.NAME, false);
      createContextRule(Integer.class, 7, ExecutePermission.NAME, true);
      createContextRule(Long.class, -1, ExecutePermission.NAME, true);
      createContextRule(Double.class, 0, ExecutePermission.NAME, true);

      // the session belongs to the system user: check the rules of the user instead
      DefaultSecurityManager manager = new DefaultSecurityManager() {
        @Override
        protected GranteeDescriptor determineGrantee(DomainContext context) {
          return new GranteeDescriptor(USER_CLASS_ID, USER_ID);
        }
      };
      manager.setEnabled(true);
      reload(manager);
      DomainContext context = new UserContext(getSession());
      Permission write = SecurityFactory.getInstance().getWritePermission();
      Permission exec = SecurityFactory.getInstance().getExecutePermission();

      // without a thread-local session, the grantee is unknown
      Session.setCurrentSession(null);
      assertThrows(SecurityException.class, () -> manager.evaluate(null, write, String.class));
      getSession().makeCurrent();

      // accept by default: a deny-rule of some context denies in all contexts
      manager.setAcceptByDefault(true);
      assertTrue(manager.evaluate(context, write, String.class).isAccepted(), "rule of context 7 fired");
      SecurityResult result = manager.evaluate(null, write, String.class);
      assertFalse(result.isAccepted(), "deny-rule of context 7 ignored");
      assertFalse(result.isDefault());

      // deny by default: only allow-rules that apply to any context accept in all contexts
      manager.setAcceptByDefault(false);
      assertFalse(manager.evaluate(null, exec, Integer.class).isAccepted(), "allow-rule of context 7 fired");
      result = manager.evaluate(null, exec, Long.class);
      assertTrue(result.isAccepted(), "allow-rule of any context ignored");
      assertFalse(result.isDefault());
      assertTrue(manager.evaluate(context, exec, Double.class).isAccepted(), "allow-rule of the default context ignored");
      assertFalse(manager.evaluate(null, exec, Double.class).isAccepted(), "allow-rule of the default context fired");
    }
    finally {
      Session.setCurrentSession(oldSession);
      Pdo.getCache(Security.class).invalidate();
    }
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

  @Test(groups = "local")
  public void entityRuleRoundTrip() {
    try {
      // any entity will do as the protected object: use another rule
      createRule(DefaultSecurityManagerDbTest.class, 0, ReadPermission.NAME, true);
      Security protectedPdo = on(Security.class).selectAll().getFirst();

      Security rule = on(Security.class);
      rule.setObject(protectedPdo);
      assertEquals(rule.getObjectId(), protectedPdo.getId());
      assertEquals(rule.getObjectClassId(), protectedPdo.getClassId());
      assertNull(rule.getObjectClassName(), "entity rule must not have a class name");
      assertEquals(rule.getObjectClass(), Security.class);

      rule.setGranteeClassId(USER_CLASS_ID);
      rule.setGranteeId(USER_ID);
      rule.setPermissions(ReadPermission.NAME);
      rule.setAllowed(true);
      rule = rule.persist();

      Security object = (Security) on(Security.class).select(rule.getId()).getObject();
      assertNotNull(object, "protected object not found");
      assertEquals(object.getId(), protectedPdo.getId());

      rule.setObject(null);
      assertEquals(rule.getObjectId(), 0);
      assertEquals(rule.getObjectClassId(), 0);
      assertNull(rule.getObject());
    }
    finally {
      Pdo.getCache(Security.class).invalidate();
    }
  }

  /**
   * An invalidation while the rules are being loaded must not get lost.
   * Otherwise, a rule modified during the load would be ignored until the next modification.
   */
  @Test(groups = "local")
  public void invalidationDuringLoadIsNotLost() {
    try {
      AtomicInteger loads = new AtomicInteger();
      DefaultSecurityManager manager = new DefaultSecurityManager() {

        @Override
        protected Security createSecurityInstance(DomainContext context) {
          if (loads.incrementAndGet() == 1) {
            invalidate();   // as if the listener fired while the rules are being loaded
          }
          return super.createSecurityInstance(context);
        }

        @Override
        protected ModificationListener createModificationListener() {
          // ignore modifications from other tests: only the invalidation above counts
          return new PdoListener(Security.class) {
            @Override
            public void dataChanged(ModificationEvent ev) {
              // nothing to do
            }
          };
        }
      };
      manager.setEnabled(true);

      DomainContext context = new UserContext(getSession());
      Permission read = SecurityFactory.getInstance().getReadPermission();
      int classId = on(Security.class).getClassId();

      manager.evaluate(context, read, classId, 0);
      assertEquals(loads.get(), 1);
      manager.evaluate(context, read, classId, 0);
      assertEquals(loads.get(), 2, "invalidation during load lost");
      manager.evaluate(context, read, classId, 0);
      assertEquals(loads.get(), 2, "reloaded without invalidation");
    }
    finally {
      Pdo.getCache(Security.class).invalidate();
    }
  }

}

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

package org.tentackle.domain.security;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.pdo.Security;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link SecurityDomainImpl}.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class SecurityDomainImplTest {

  private Security rule;
  private SecurityPersistenceMock persistence;

  @BeforeMethod
  public void setUp() {
    rule = Pdo.create(Security.class);
    persistence = (SecurityPersistenceMock) rule.getPersistenceDelegate();
  }

  /**
   * Creates a domain context recording the arguments of {@code isWithinContext}.
   *
   * @param withinContext the answer of {@code isWithinContext}
   * @param arguments receives the arguments of the last {@code isWithinContext} invocation
   * @return the domain context
   */
  private static DomainContext createContext(boolean withinContext, AtomicReference<Object[]> arguments) {
    return (DomainContext) Proxy.newProxyInstance(
        DomainContext.class.getClassLoader(), new Class<?>[] { DomainContext.class },
        (proxy, method, args) -> {
          if ("isWithinContext".equals(method.getName()) && args != null && args.length == 2) {
            arguments.set(args);
            return withinContext;
          }
          return null;
        });
  }

  /**
   * Creates some PDO to be used as a grantee or context object.
   *
   * @param name the pool's name
   * @return the PDO
   */
  private static NumberPool createPool(String name) {
    NumberPool pool = Pdo.create(NumberPool.class);
    pool.setName(name);
    return pool;
  }


  @Test
  public void testFactoryWiresTheDomainDelegate() {
    assertEquals(rule.getDomainDelegate().getClass(), SecurityDomainImpl.class);
  }

  @Test
  public void testUniqueDomainKeyIsTheObjectId() {
    persistence.setId(4711);
    assertEquals(rule.getUniqueDomainKey(), 4711L);
  }

  @Test
  public void testFindByUniqueDomainKeySelectsById() {
    Security found = Pdo.create(Security.class);
    persistence.setSelected(found);
    assertSame(rule.findByUniqueDomainKey(4711L), found);
  }

  @Test
  public void testAllowRuleAcceptsTheGrantedPermission() {
    rule.setAllowed(true);
    rule.setPermissions("READ");
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getReadPermission()));
  }

  @Test
  public void testAllowRuleRejectsAnotherPermission() {
    rule.setAllowed(true);
    rule.setPermissions("READ");
    assertFalse(rule.evaluate(null, SecurityFactory.getInstance().getWritePermission()));
  }

  @Test
  public void testAllowRuleAcceptsAnyPermissionOfTheList() {
    rule.setAllowed(true);
    rule.setPermissions("READ, WRITE");
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getReadPermission()));
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getWritePermission()));
    assertFalse(rule.evaluate(null, SecurityFactory.getInstance().getExecutePermission()));
  }

  @Test
  public void testAllPermissionCoversEveryRequestedPermission() {
    rule.setAllowed(true);
    rule.setPermissions("ALL");
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getReadPermission()));
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getWritePermission()));
  }

  @Test
  public void testAllowRuleAcceptsImpliedPermissions() {
    // write implies read, so granting write grants read as well
    rule.setAllowed(true);
    rule.setPermissions("WRITE");
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getReadPermission()));
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getWritePermission()));
    assertFalse(rule.evaluate(null, SecurityFactory.getInstance().getExecutePermission()));
  }

  @Test
  public void testDenyRuleDeniesTheConfiguredPermission() {
    rule.setAllowed(false);
    rule.setPermissions("READ");
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getReadPermission()));
    // write implies read, so denying read denies write as well
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getWritePermission()));
    // execute is unrelated to read
    assertFalse(rule.evaluate(null, SecurityFactory.getInstance().getExecutePermission()));
  }

  @Test
  public void testRuleWithoutPermissionsNeverApplies() {
    rule.setAllowed(true);
    assertFalse(rule.evaluate(null, SecurityFactory.getInstance().getReadPermission()));
  }

  @Test
  public void testUnknownPermissionIsRejected() {
    rule.setAllowed(true);
    rule.setPermissions("NOSUCHPERMISSION");
    Permission permission = SecurityFactory.getInstance().getReadPermission();
    SecurityException sex = expectThrows(SecurityException.class, () -> rule.evaluate(null, permission));
    assertEquals(sex.getMessage(), "unknown permission 'NOSUCHPERMISSION'");
  }

  @Test
  public void testRuleAppliesOnlyWithinItsDomainContext() {
    rule.setAllowed(true);
    rule.setPermissions("READ");
    rule.setDomainContextId(42);
    rule.setDomainContextClassId(7);
    AtomicReference<Object[]> arguments = new AtomicReference<>();
    assertTrue(rule.evaluate(createContext(true, arguments), SecurityFactory.getInstance().getReadPermission()));
    assertEquals(arguments.get(), new Object[] { 42L, 7 });
  }

  @Test
  public void testRuleDoesNotApplyOutsideItsDomainContext() {
    rule.setAllowed(true);
    rule.setPermissions("READ");
    AtomicReference<Object[]> arguments = new AtomicReference<>();
    assertFalse(rule.evaluate(createContext(false, arguments), SecurityFactory.getInstance().getReadPermission()));
  }

  @Test
  public void testPermissionsAreParsedOnlyOnce() {
    rule.setAllowed(true);
    rule.setPermissions("READ");
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getReadPermission()));
    // the permissions are cached, so changing the attribute has no effect anymore
    rule.setPermissions("WRITE");
    assertTrue(rule.evaluate(null, SecurityFactory.getInstance().getReadPermission()));
    assertFalse(rule.evaluate(null, SecurityFactory.getInstance().getWritePermission()));
  }

  @Test
  public void testSecurableToStringOfUnsecuredRule() {
    assertEquals(rule.securableToString(), "");
  }

  @Test
  public void testSecurableToStringOfClassName() {
    rule.setObjectClassName("com.example.model.Invoice");
    assertEquals(rule.securableToString(), "Invoice");
  }

  @Test
  public void testGranteeToStringWithoutGrantee() {
    assertEquals(rule.granteeToString(), "");
  }

  @Test
  public void testGranteeToStringWithGrantee() {
    rule.setGrantee(createPool("operators"));
    assertEquals(rule.granteeToString(), "number pool operators");
  }

  @Test
  public void testContextToStringWithoutContextObject() {
    assertNull(rule.contextToString());
  }

  @Test
  public void testContextToStringWithContextObject() {
    rule.setDomainContextObject(createPool("acme"));
    assertEquals(rule.contextToString(), "acme");
  }

  @Test
  public void testSingularAndPlural() {
    assertEquals(rule.getSingular(), "security rule");
    assertEquals(rule.getPlural(), "security rules");
  }

}

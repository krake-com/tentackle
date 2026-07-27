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

package org.tentackle.domain;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainException;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.Session;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link AbstractDomainObject}.
 * <p>
 * The PDO is created by the real factory, so the domain delegate under test is wired
 * to a real dynamic proxy; only the persistence delegate is a mock.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class AbstractDomainObjectTest {

  private DemoPdo pdo;
  private DemoPdoDomainImpl domain;
  private DemoPdoPersistenceMock persistence;

  @BeforeMethod
  public void setUp() {
    pdo = Pdo.create(DemoPdo.class);
    domain = (DemoPdoDomainImpl) pdo.getDomainDelegate();
    persistence = (DemoPdoPersistenceMock) pdo.getPersistenceDelegate();
    persistence.setRootEntity(true);
  }


  @Test
  public void testFactoryWiresTheAnnotatedDomainDelegate() {
    // proves the @DomainObjectService mapping is picked up and linked to the proxy
    assertSame(domain.getPdo(), pdo);
    assertSame(domain.me(), pdo);
  }

  @Test
  public void testConstructorLinksPdo() {
    DemoPdoDomainImpl linked = new DemoPdoDomainImpl(pdo);
    assertSame(linked.getPdo(), pdo);
    assertSame(linked.me(), pdo);
  }

  @Test
  public void testDefaultConstructorLeavesPdoUnset() {
    assertNull(new DemoPdoDomainImpl().getPdo());
  }

  @Test
  public void testSetPdo() {
    DemoPdo other = Pdo.create(DemoPdo.class);
    domain.setPdo(other);
    assertSame(domain.getPdo(), other);
    assertSame(domain.me(), other);
  }

  @Test
  public void testGetPersistenceDelegateIsTakenFromThePdo() {
    assertSame(domain.getPersistenceDelegate(), persistence);
  }

  @Test
  public void testEffectiveClassIsTakenFromThePdo() {
    assertEquals(domain.getEffectiveClass(), DemoPdo.class);
    assertEquals(domain.getEffectiveSuperClasses(), List.of(PersistentDomainObject.class));
  }

  @Test
  public void testDomainContextIsTakenFromThePersistenceDelegate() {
    assertNull(domain.getDomainContext());
    DomainContext context = (DomainContext) Proxy.newProxyInstance(
        DomainContext.class.getClassLoader(), new Class<?>[] { DomainContext.class },
        (proxy, method, args) -> null);
    persistence.setDomainContext(context);
    assertSame(domain.getDomainContext(), context);
  }

  @Test
  public void testSessionIsTakenFromThePersistenceDelegate() {
    assertNull(domain.getSession());
    Session session = (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(), new Class<?>[] { Session.class },
        (proxy, method, args) -> null);
    persistence.setSession(session);
    assertSame(domain.getSession(), session);
  }

  @Test
  public void testToGenericString() {
    persistence.setId(4711);
    persistence.setSerial(3);
    assertEquals(domain.toGenericString(), DemoPdoDomainImpl.class.getName() + "[4711/3]");
  }

  @Test
  public void testToGenericStringOfVirginObject() {
    assertEquals(domain.toGenericString(), DemoPdoDomainImpl.class.getName() + "[0/0]");
  }

  @Test
  public void testToStringOfComponentIsTheSingular() {
    persistence.setRootEntity(false);
    assertEquals(domain.toString(), "DemoPdo");
  }

  @Test
  public void testToStringOfRootEntityFallsBackToGenericStringOnException() {
    persistence.setId(42);
    persistence.setSerial(1);
    // the default getUniqueDomainKey throws, but toString must never propagate it
    String str = domain.toString();
    assertTrue(str.startsWith(DemoPdoDomainImpl.class.getName() + "[42/1]("), str);
    assertTrue(str.endsWith(")"), str);
    assertTrue(str.contains(DomainException.class.getName()), str);
  }

  @Test
  public void testUniqueDomainKeyNotProvidedByDefault() {
    assertFalse(domain.isUniqueDomainKeyProvided());
  }

  @Test
  public void testUniqueDomainKeyMethodsAreNotImplementedByDefault() {
    assertNotImplemented(() -> domain.getUniqueDomainKeyType());
    assertNotImplemented(() -> domain.getUniqueDomainKey());
    assertNotImplemented(() -> domain.setUniqueDomainKey("key"));
    assertNotImplemented(() -> domain.findByUniqueDomainKey("key"));
  }

  @Test
  public void testUniqueDomainKeyMethodsRejectComponents() {
    persistence.setRootEntity(false);
    assertNotARootEntity(() -> domain.getUniqueDomainKeyType());
    assertNotARootEntity(() -> domain.getUniqueDomainKey());
    assertNotARootEntity(() -> domain.setUniqueDomainKey("key"));
    assertNotARootEntity(() -> domain.findByUniqueDomainKey("key"));
  }

  @Test
  public void testAssertRootEntity() {
    domain.assertRootEntity();      // must not throw
    persistence.setRootEntity(false);
    assertNotARootEntity(() -> domain.assertRootEntity());
  }

  @Test
  public void testSingularAndPluralDefaultToTheEffectiveClassName() {
    assertEquals(domain.getSingular(), "DemoPdo");
    assertEquals(domain.getPlural(), "DemoPdos");
  }

  @Test
  public void testDefaultPluralOfNameEndingWithS() {
    DemoGlass glass = Pdo.create(DemoGlass.class);
    assertEquals(glass.getSingular(), "DemoGlass");
    assertEquals(glass.getPlural(), "DemoGlasses");
  }


  private void assertNotImplemented(Runnable runnable) {
    DomainException dex = expectThrows(DomainException.class, runnable::run);
    assertTrue(dex.getMessage().contains("not implemented in " + DemoPdoDomainImpl.class),
               dex.getMessage());
  }

  private void assertNotARootEntity(Runnable runnable) {
    DomainException dex = expectThrows(DomainException.class, runnable::run);
    assertTrue(dex.getMessage().contains("not a root-entity"), dex.getMessage());
  }

}

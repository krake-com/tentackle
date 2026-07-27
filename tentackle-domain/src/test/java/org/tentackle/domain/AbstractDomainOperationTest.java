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
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.Session;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for {@link AbstractDomainOperation}.
 * <p>
 * The operation is created by the real factory, so the domain delegate under test is wired
 * to a real dynamic proxy; only the persistence delegate is a mock.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class AbstractDomainOperationTest {

  private DemoOperation operation;
  private DemoOperationDomainImpl domain;
  private DemoOperationPersistenceMock persistence;

  @BeforeMethod
  public void setUp() {
    operation = Pdo.createOperation(DemoOperation.class);
    domain = (DemoOperationDomainImpl) operation.getDomainDelegate();
    persistence = (DemoOperationPersistenceMock) operation.getPersistenceDelegate();
  }


  @Test
  public void testFactoryWiresTheAnnotatedDomainDelegate() {
    // proves the @DomainOperationService mapping is picked up and linked to the proxy
    assertSame(domain.getOperation(), operation);
    assertSame(domain.me(), operation);
  }

  @Test
  public void testConstructorLinksOperation() {
    DemoOperationDomainImpl linked = new DemoOperationDomainImpl(operation);
    assertSame(linked.getOperation(), operation);
    assertSame(linked.me(), operation);
  }

  @Test
  public void testDefaultConstructorLeavesOperationUnset() {
    assertNull(new DemoOperationDomainImpl().getOperation());
  }

  @Test
  public void testSetOperation() {
    DemoOperation other = Pdo.createOperation(DemoOperation.class);
    domain.setOperation(other);
    assertSame(domain.getOperation(), other);
    assertSame(domain.me(), other);
  }

  @Test
  public void testGetPersistenceDelegateIsTakenFromTheOperation() {
    assertSame(domain.getPersistenceDelegate(), persistence);
  }

  @Test
  public void testEffectiveClassIsTakenFromTheOperation() {
    assertEquals(domain.getEffectiveClass(), DemoOperation.class);
    assertEquals(domain.getEffectiveSuperClasses(), List.of(Operation.class));
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

}

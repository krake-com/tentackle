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

package org.tentackle.persist;

import org.testng.annotations.Test;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link AbstractPersistentOperation} and {@link PersistentOperationClassVariables}.
 * <p>
 * Operations carry the behavior that is not tied to a single entity. Their persistence delegate is
 * mostly about the domain context they run in &mdash; which is what can be verified without a session.
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractPersistentOperationTest {

  /**
   * Creates the persistence delegate of a freshly created operation.
   *
   * @return the persistence delegate
   */
  private DemoOperationPersistenceImpl createOperation() {
    return (DemoOperationPersistenceImpl) Pdo.createOperation(DemoOperation.class).getPersistenceDelegate();
  }


  @Test
  public void testOperationIsCreatedFromTheRegisteredServices() {
    DemoOperation operation = Pdo.createOperation(DemoOperation.class);
    assertTrue(operation.getPersistenceDelegate() instanceof DemoOperationPersistenceImpl);
    assertTrue(operation.getDomainDelegate() instanceof DemoOperationDomainMock);
  }

  @Test
  public void testTheDelegateKnowsItsOperation() {
    DemoOperation operation = Pdo.createOperation(DemoOperation.class);
    DemoOperationPersistenceImpl po = (DemoOperationPersistenceImpl) operation.getPersistenceDelegate();
    assertSame(po.getOperation(), operation);
    assertSame(po.me(), operation);
    assertSame(po.getEffectiveClass(), DemoOperation.class);
  }

  @Test
  public void testSetOperation() {
    DemoOperationPersistenceImpl po = new DemoOperationPersistenceImpl();
    assertNull(po.getOperation());

    DemoOperation operation = Pdo.createOperation(DemoOperation.class);
    po.setOperation(operation);
    assertSame(po.getOperation(), operation);
  }

  @Test
  public void testClassVariables() {
    PersistentOperationClassVariables<DemoOperation, DemoOperationPersistenceImpl> cv =
        DemoOperationPersistenceImpl.CLASSVARIABLES;
    assertSame(cv.operationClass, DemoOperation.class);
    assertSame(cv.clazz, DemoOperationPersistenceImpl.class);
    assertEquals(cv.className, DemoOperationPersistenceImpl.class.getName());
    assertSame(createOperation().getOperationMethodCache(), cv.methodCache);
  }

  @Test
  public void testUndefinedClassVariables() {
    // the base class has no class variables: implementations must provide them
    AbstractPersistentOperation<DemoOperation, DemoOperationPersistenceImpl> po =
        new AbstractPersistentOperation<>() { };
    assertTrue(expectThrows(PersistenceException.class, po::getClassVariables)
                   .getMessage().contains("class variables undefined"));
  }

  @Test
  public void testDomainContext() {
    DemoOperationPersistenceImpl po = createOperation();
    assertNull(po.getDomainContext(), "no context without a session");

    DomainContext context = Pdo.createDomainContext(null);
    po.setDomainContext(context);
    assertSame(po.getDomainContext(), context);
    assertSame(po.getBaseContext(), context);
    assertEquals(po.getContextId(), -1, "the default operation does not depend on another object");
  }

  @Test
  public void testDomainContextMustNotBeCleared() {
    DemoOperationPersistenceImpl po = createOperation();
    po.setDomainContext(null);    // still null: no change at all

    po.setDomainContext(Pdo.createDomainContext(null));
    expectThrows(NullPointerException.class, () -> po.setDomainContext(null));
  }

  @Test
  public void testImmutableDomainContextCannotBeReplaced() {
    DemoOperationPersistenceImpl po = createOperation();
    po.setDomainContext(Pdo.createDomainContext(null));

    assertFalse(po.isDomainContextImmutable());
    po.setDomainContextImmutable(true);

    DomainContext otherContext = Pdo.createDomainContext(null);
    assertTrue(expectThrows(PersistenceException.class, () -> po.setDomainContext(otherContext))
                   .getMessage().contains("domain context is immutable"));

    po.setDomainContext(po.getDomainContext());   // the very same context is no change at all
  }

  @Test
  public void testCreateValidContext() {
    DemoOperationPersistenceImpl po = createOperation();
    DomainContext context = po.createValidContext();
    assertNull(context.getSession(), "the thread-local session, none in this test");
  }

  @Test
  public void testOperationMethodsAreInvokedThroughTheProxy() {
    DemoOperation operation = Pdo.createOperation(DemoOperation.class);
    DemoOperationPersistenceImpl po = (DemoOperationPersistenceImpl) operation.getPersistenceDelegate();
    po.setDomainContext(Pdo.createDomainContext(null));
    assertEquals(operation.execute(), po.execute(),
                 "the proxy dispatches to the persistence delegate");
  }

}

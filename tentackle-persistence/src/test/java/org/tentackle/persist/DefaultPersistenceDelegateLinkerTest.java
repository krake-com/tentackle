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

import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistenceDelegateLinker;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for {@link DefaultPersistenceDelegateLinker}.
 * <p>
 * The linker connects a PDO proxy with its persistence delegate. It is what makes
 * {@code getPdo()} work on a delegate that was created separately.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultPersistenceDelegateLinkerTest {

  @Test
  public void testLinkPersistentObject() {
    DemoEntityPersistenceImpl persistence = new DemoEntityPersistenceImpl();
    assertNull(persistence.getPdo());

    DemoEntity pdo = Pdo.create(DemoEntity.class);
    new DefaultPersistenceDelegateLinker().linkPersistentObject(pdo, persistence);
    assertSame(persistence.getPdo(), pdo);
  }

  @Test
  public void testLinkPersistentObjectReplacesPreviousPdo() {
    DemoEntity first = Pdo.create(DemoEntity.class);
    DemoEntity second = Pdo.create(DemoEntity.class);
    DemoEntityPersistenceImpl persistence = new DemoEntityPersistenceImpl(first);

    new DefaultPersistenceDelegateLinker().linkPersistentObject(second, persistence);
    assertSame(persistence.getPdo(), second);
  }

  @Test
  public void testLinkPersistentOperation() {
    DemoOperationPersistenceImpl persistence = new DemoOperationPersistenceImpl();
    assertNull(persistence.getOperation());

    DemoOperation operation = Pdo.createOperation(DemoOperation.class);
    new DefaultPersistenceDelegateLinker().linkPersistentOperation(operation, persistence);
    assertSame(persistence.getOperation(), operation);
  }

  @Test
  public void testRegisteredAsThePersistenceDelegateLinkerService() {
    PersistenceDelegateLinker linker = ServiceFactory.createService(PersistenceDelegateLinker.class);
    assertEquals(linker.getClass(), DefaultPersistenceDelegateLinker.class);
  }

}

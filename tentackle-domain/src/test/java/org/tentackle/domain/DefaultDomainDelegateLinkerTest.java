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

import org.testng.annotations.Test;

import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.DomainDelegateLinker;
import org.tentackle.pdo.Pdo;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for {@link DefaultDomainDelegateLinker}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultDomainDelegateLinkerTest {

  @Test
  public void testLinkDomainObject() {
    DemoPdoDomainImpl domain = new DemoPdoDomainImpl();
    assertNull(domain.getPdo());
    DemoPdo pdo = Pdo.create(DemoPdo.class);
    new DefaultDomainDelegateLinker().linkDomainObject(pdo, domain);
    assertSame(domain.getPdo(), pdo);
  }

  @Test
  public void testLinkDomainObjectReplacesPreviousPdo() {
    DemoPdo first = Pdo.create(DemoPdo.class);
    DemoPdo second = Pdo.create(DemoPdo.class);
    DemoPdoDomainImpl domain = new DemoPdoDomainImpl(first);
    new DefaultDomainDelegateLinker().linkDomainObject(second, domain);
    assertSame(domain.getPdo(), second);
  }

  @Test
  public void testLinkDomainOperation() {
    DemoOperationDomainImpl domain = new DemoOperationDomainImpl();
    assertNull(domain.getOperation());
    DemoOperation operation = Pdo.createOperation(DemoOperation.class);
    new DefaultDomainDelegateLinker().linkDomainOperation(operation, domain);
    assertSame(domain.getOperation(), operation);
  }

  @Test
  public void testRegisteredAsTheDomainDelegateLinkerService() {
    DomainDelegateLinker linker = ServiceFactory.createService(DomainDelegateLinker.class);
    assertEquals(linker.getClass(), DefaultDomainDelegateLinker.class);
  }

}

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
import org.tentackle.pdo.AdminExtension;
import org.tentackle.session.PersistenceException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link AdminExtensionAdapter}.
 * <p>
 * The adapter forwards the administrative session commands to the server. Everything it does with a
 * remote session belongs to the database-backed tests; what can be verified here is that its domain
 * context is nailed down: it is set once by the session adapter and is immutable by definition.
 */
@SuppressWarnings("missing-explicit-ctor")
public class AdminExtensionAdapterTest {

  @Test
  public void testRegisteredAsTheAdminExtensionService() {
    assertEquals(ServiceFactory.createService(AdminExtension.class).getClass(), AdminExtensionAdapter.class);
  }

  @Test
  public void testNoDomainContextBeforeTheSessionSetsIt() {
    AdminExtensionAdapter adapter = new AdminExtensionAdapter();
    assertNull(adapter.getDomainContext());
    assertNull(adapter.getBaseContext());
    adapter.determineContextId();   // does nothing
  }

  @Test
  public void testDomainContextIsAlwaysImmutable() {
    AdminExtensionAdapter adapter = new AdminExtensionAdapter();
    assertTrue(adapter.isDomainContextImmutable());

    adapter.setDomainContextImmutable(true);    // allowed: that's the state anyway
    assertTrue(expectThrows(PersistenceException.class, () -> adapter.setDomainContextImmutable(false))
                   .getMessage().contains("illegal attempt to set domain context to mutable"));
  }

  @Test
  public void testNoValidContextCanBeCreated() {
    AdminExtensionAdapter adapter = new AdminExtensionAdapter();
    assertTrue(expectThrows(PersistenceException.class, adapter::createValidContext)
                   .getMessage().contains("illegal attempt to create a context"));
  }

}

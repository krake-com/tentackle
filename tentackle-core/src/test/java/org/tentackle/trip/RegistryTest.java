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

package org.tentackle.trip;

import org.testng.annotations.Test;

import org.tentackle.trip.impl.TestRemoteInterface;
import org.tentackle.trip.impl.TestRemoteObject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@SuppressWarnings("missing-explicit-ctor")
public class RegistryTest {

  @Test
  void testRegistry() {
    Registry registry = TripFactory.getInstance().createRegistry();
    RemoteDelegate<Registry> registryDelegate = registry.lookup(Registry.REGISTRY_ID);
    assertNotNull(registryDelegate);
    assertNotNull(registryDelegate.getTarget());
    assertSame(registryDelegate.getTarget().lookup(Registry.REGISTRY_ID).getTarget(), registry);
    assertNull(registry.lookup("unknown"));
  }

  @Test
  void findRemoteImplementation() {
    Class<TestRemoteInterface> remoteImplementation = TripFactory.getInstance().findRemoteImplementation(TestRemoteInterface.class);
    assertSame(remoteImplementation, TestRemoteObject.class);
  }

  @Test
  void testNamedDelegate() {
    Registry registry = TripFactory.getInstance().createRegistry();
    TestRemoteObject object = new TestRemoteObject();
    RemoteDelegate<TestRemoteObject> delegate = registry.register(object, "test");
    assertNotNull(delegate.getRemoteId());
    assertSame(registry.lookup(delegate.getRemoteId()).getTarget(), object);

    try {
      registry.register(object);
      fail("should fail");
    }
    catch (TripRuntimeException tx) {
      assertTrue(tx.getMessage().startsWith("object already registered"));
    }

    assertEquals(registry.unregister(delegate.getRemoteId()), delegate);;
    assertNull(registry.lookup(delegate.getRemoteId()));
  }

  @Test
  void testUnnamedDelegate() {
    Registry registry = TripFactory.getInstance().createRegistry();
    TestRemoteObject object = new TestRemoteObject();
    RemoteDelegate<TestRemoteObject> delegate = registry.register(object);
    assertNotNull(delegate.getRemoteId());
    assertSame(registry.lookup(delegate.getRemoteId()).getTarget(), object);

    try {
      registry.register(object, "test");
      fail("should fail");
    }
    catch (TripRuntimeException tx) {
      assertTrue(tx.getMessage().startsWith("object already registered"));
    }

    assertEquals(registry.unregister(delegate.getRemoteId()), delegate);;
    assertNull(registry.lookup(delegate.getRemoteId()));
  }

}

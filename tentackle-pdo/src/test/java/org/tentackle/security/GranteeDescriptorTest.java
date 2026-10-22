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

package org.tentackle.security;

import org.testng.annotations.Test;

import org.tentackle.pdo.PersistentObject;
import org.tentackle.security.pdo.Security;

import java.lang.reflect.Proxy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link GranteeDescriptor}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class GranteeDescriptorTest {

  /**
   * Creates a PDO stub answering the class- and object id of its persistence delegate.
   *
   * @param classId the class id
   * @param id the object id
   * @return the stub
   */
  private static Security securityStub(int classId, long id) {
    PersistentObject<?> delegate = (PersistentObject<?>) Proxy.newProxyInstance(
        PersistentObject.class.getClassLoader(), new Class<?>[] { PersistentObject.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "getClassId" -> classId;
          case "getId"      -> id;
          default -> throw new UnsupportedOperationException(method.getName());
        });
    return (Security) Proxy.newProxyInstance(
        Security.class.getClassLoader(), new Class<?>[] { Security.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "getPersistenceDelegate" -> delegate;
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }


  @Test
  public void testAccessors() {
    GranteeDescriptor grantee = new GranteeDescriptor(42, 4711);
    assertEquals(grantee.getGranteeClassId(), 42);
    assertEquals(grantee.getGranteeId(), 4711);
  }

  /**
   * The grantee of all classes and all objects.
   */
  @Test
  public void testAllGrantees() {
    GranteeDescriptor all = new GranteeDescriptor(0, 0);
    assertEquals(all.getGranteeClassId(), 0);
    assertEquals(all.getGranteeId(), 0);
  }

  @Test
  public void testFromPdo() {
    assertEquals(new GranteeDescriptor(securityStub(42, 4711)), new GranteeDescriptor(42, 4711));
  }

  @Test
  public void testToString() {
    assertEquals(new GranteeDescriptor(42, 4711).toString(), "classId=42, id=4711");
  }

  @Test
  public void testEqualsAndHashCode() {
    GranteeDescriptor grantee = new GranteeDescriptor(42, 4711);
    assertEquals(grantee, grantee);
    assertEquals(grantee, new GranteeDescriptor(42, 4711));
    assertEquals(grantee.hashCode(), new GranteeDescriptor(42, 4711).hashCode());
    assertNotEquals(grantee, new GranteeDescriptor(43, 4711));
    assertNotEquals(grantee, new GranteeDescriptor(42, 4712));
    assertNotEquals(grantee, null);
    assertNotEquals(grantee, "classId=42, id=4711");
  }

  /**
   * The class id and the object id must not be interchangeable in the hash.
   */
  @Test
  public void testHashDistinguishesClassIdFromObjectId() {
    assertTrue(new GranteeDescriptor(1, 2).hashCode() != new GranteeDescriptor(2, 1).hashCode());
  }

}

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

import org.tentackle.security.pdo.Security;
import org.tentackle.security.permissions.AllPermission;
import org.tentackle.security.permissions.AllPermissionImpl;
import org.tentackle.security.permissions.EditPermission;
import org.tentackle.security.permissions.EditPermissionImpl;
import org.tentackle.security.permissions.ExecutePermission;
import org.tentackle.security.permissions.ExecutePermissionImpl;
import org.tentackle.security.permissions.ReadPermission;
import org.tentackle.security.permissions.ReadPermissionImpl;
import org.tentackle.security.permissions.ViewPermission;
import org.tentackle.security.permissions.ViewPermissionImpl;
import org.tentackle.security.permissions.WritePermission;
import org.tentackle.security.permissions.WritePermissionImpl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DefaultSecurityFactory}.
 * <p>
 * The factory collects the permissions from the {@link PermissionService} index created by the
 * build, so this module's six predefined permissions are the ones under test.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultSecurityFactoryTest {

  /**
   * A permission interface without an implementation.
   */
  private interface UnconfiguredPermission extends Permission {
  }

  /**
   * Creates a factory of its own so that the tests cannot influence the application's singleton.
   *
   * @return the factory
   */
  private SecurityFactory factory() {
    return new DefaultSecurityFactory();
  }


  @Test
  public void testPredefinedPermissions() {
    SecurityFactory factory = factory();
    assertTrue(factory.getAllPermission() instanceof AllPermissionImpl);
    assertTrue(factory.getReadPermission() instanceof ReadPermissionImpl);
    assertTrue(factory.getViewPermission() instanceof ViewPermissionImpl);
    assertTrue(factory.getWritePermission() instanceof WritePermissionImpl);
    assertTrue(factory.getEditPermission() instanceof EditPermissionImpl);
    assertTrue(factory.getExecutePermission() instanceof ExecutePermissionImpl);
  }

  /**
   * The permissions are singletons: the predefined accessors and the lookup by interface must
   * return the very same instance.
   */
  @Test
  public void testPermissionsAreSingletons() {
    SecurityFactory factory = factory();
    assertSame(factory.getPermission(ReadPermission.class), factory.getReadPermission());
    assertSame(factory.getPermission(EditPermission.class), factory.getEditPermission());
    assertSame(factory.getPermission(ReadPermission.class), factory.getPermission(ReadPermission.class));
  }

  /**
   * Passing the implementation class instead of the interface is tolerated (and logged) because it
   * is an easy mistake to make.
   */
  @Test
  public void testLookupByImplementationClass() {
    SecurityFactory factory = factory();
    assertSame(factory.getPermission(ReadPermissionImpl.class), factory.getReadPermission());
  }

  @Test
  public void testUnconfiguredPermissionRejected() {
    SecurityFactory factory = factory();
    assertThrows(SecurityException.class, () -> factory.getPermission(UnconfiguredPermission.class));
  }

  @Test
  public void testPermissionInterfaceByName() {
    SecurityFactory factory = factory();
    assertEquals(factory.getPermissionInterface(ReadPermission.NAME), ReadPermission.class);
    assertEquals(factory.getPermissionInterface(AllPermission.NAME), AllPermission.class);
    assertNull(factory.getPermissionInterface("NOSUCHPERMISSION"));
  }

  @Test
  public void testAllPermissionsRegistered() {
    SecurityFactory factory = factory();
    assertEquals(Set.copyOf(factory.getPermissionInterfaces()),
                 Set.of(AllPermission.class, ReadPermission.class, ViewPermission.class,
                        WritePermission.class, EditPermission.class, ExecutePermission.class));
    assertEquals(factory.getPermissions().size(), 6);
  }

  /**
   * Only the permissions applicable to the class are offered - an entity cannot be executed, and a
   * plain class cannot be read.
   */
  @Test
  public void testPermissionsApplicableToAClass() {
    SecurityFactory factory = factory();
    assertEquals(Set.copyOf(factory.getPermissionInterfaces(Security.class)),
                 Set.of(AllPermission.class, ReadPermission.class, ViewPermission.class,
                        WritePermission.class, EditPermission.class));
    assertEquals(Set.copyOf(factory.getPermissionInterfaces(String.class)),
                 Set.of(AllPermission.class, ExecutePermission.class));
    assertTrue(factory.getPermissions(Security.class).contains(factory.getEditPermission()));
    assertFalse(factory.getPermissions(Security.class).contains(factory.getExecutePermission()));
  }

  @Test
  public void testStringToPermissionInterfaces() {
    SecurityFactory factory = factory();
    assertEquals(factory.stringToPermissionInterfaces("READ,WRITE"),
                 new Class<?>[] { ReadPermission.class, WritePermission.class });
    assertEquals(factory.stringToPermissionInterfaces(" READ ,\tWRITE\nEXEC\r"),
                 new Class<?>[] { ReadPermission.class, WritePermission.class, ExecutePermission.class },
                 "blanks, tabs and newlines separate as well");
    assertEquals(factory.stringToPermissionInterfaces(""), new Class<?>[0]);
    assertEquals(factory.stringToPermissionInterfaces(null), new Class<?>[0]);
  }

  @Test
  public void testUnknownPermissionNameRejected() {
    SecurityFactory factory = factory();
    assertThrows(SecurityException.class, () -> factory.stringToPermissionInterfaces("READ,NOSUCH"));
  }

  /**
   * The permissions of a rule keep the order they were configured in.
   */
  @Test
  public void testStringToPermissionsKeepsTheOrder() {
    SecurityFactory factory = factory();
    assertEquals(List.copyOf(factory.stringToPermissions("WRITE,READ")),
                 List.of(factory.getWritePermission(), factory.getReadPermission()));
    assertTrue(factory.stringToPermissions(null).isEmpty());
  }

  @Test
  public void testPermissionsToString() {
    SecurityFactory factory = factory();
    assertEquals(factory.permissionsToString(null), "");
    assertEquals(factory.permissionsToString(Set.of()), "");
    Set<Permission> permissions = new LinkedHashSet<>(
        List.of(factory.getWritePermission(), factory.getReadPermission()));
    assertEquals(factory.permissionsToString(permissions), "WRITE,READ");
  }

  @Test
  public void testStringRoundTrip() {
    SecurityFactory factory = factory();
    assertEquals(factory.permissionsToString(factory.stringToPermissions("EDIT,VIEW,EXEC")),
                 "EDIT,VIEW,EXEC");
  }

  @Test
  public void testSecurityManager() {
    SecurityFactory factory = factory();
    assertNotNull(factory.getSecurityManager());
    assertSame(factory.getSecurityManager(), factory.getSecurityManager(), "must be a singleton");
  }

}

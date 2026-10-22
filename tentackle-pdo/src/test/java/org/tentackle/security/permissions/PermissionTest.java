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

package org.tentackle.security.permissions;

import org.testng.annotations.Test;

import org.tentackle.security.Permission;
import org.tentackle.security.pdo.Security;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the predefined permissions.
 * <p>
 * The permission hierarchy is expressed by the interfaces extending each other:
 * {@code EDIT -> WRITE -> READ}, {@code EDIT -> VIEW -> READ} and {@code EXEC}, with {@code ALL} matching
 * everything. {@link Permission#isAllowedBy} and {@link Permission#isDeniedBy} translate that
 * hierarchy into the two directions a rule can be configured in.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PermissionTest {

  @Test
  public void testNames() {
    assertEquals(new AllPermissionImpl().getName(), "ALL");
    assertEquals(new ReadPermissionImpl().getName(), "READ");
    assertEquals(new ViewPermissionImpl().getName(), "VIEW");
    assertEquals(new WritePermissionImpl().getName(), "WRITE");
    assertEquals(new EditPermissionImpl().getName(), "EDIT");
    assertEquals(new ExecutePermissionImpl().getName(), "EXEC");
  }

  @Test
  public void testToStringIsTheName() {
    assertEquals(new ReadPermissionImpl().toString(), ReadPermission.NAME);
  }

  @Test
  public void testNoDescriptionByDefault() {
    assertNull(new ReadPermissionImpl().getDescription());
  }

  /**
   * The permission interface is the one the implementation declares, not any of its super
   * interfaces.
   */
  @Test
  public void testPermissionInterface() {
    assertEquals(new AllPermissionImpl().getPermissionInterface(), AllPermission.class);
    assertEquals(new ReadPermissionImpl().getPermissionInterface(), ReadPermission.class);
    assertEquals(new ViewPermissionImpl().getPermissionInterface(), ViewPermission.class);
    assertEquals(new WritePermissionImpl().getPermissionInterface(), WritePermission.class);
    assertEquals(new EditPermissionImpl().getPermissionInterface(), EditPermission.class);
    assertEquals(new ExecutePermissionImpl().getPermissionInterface(), ExecutePermission.class);
  }

  /**
   * READ, VIEW, WRITE, and EDIT protect entities. EXEC protects everything else and ALL both.
   */
  @Test
  public void testAppliesTo() {
    for (Permission permission : List.of(new ReadPermissionImpl(), new ViewPermissionImpl(),
                                         new WritePermissionImpl(), new EditPermissionImpl())) {
      assertTrue(permission.appliesTo(Security.class), permission + " must apply to a PDO");
      assertFalse(permission.appliesTo(String.class), permission + " must not apply to a non-PDO");
      assertFalse(permission.appliesTo(null), permission + " must not apply to nothing");
    }

    Permission execute = new ExecutePermissionImpl();
    assertFalse(execute.appliesTo(Security.class));
    assertTrue(execute.appliesTo(String.class));
    assertFalse(execute.appliesTo(null));

    Permission all = new AllPermissionImpl();
    assertTrue(all.appliesTo(Security.class));
    assertTrue(all.appliesTo(String.class));
    assertTrue(all.appliesTo(null), "ALL applies to everything");
  }

  /**
   * A permission is allowed by a configured permission that is the same or narrower:
   * granting EDIT grants WRITE and READ as well.
   */
  @Test
  public void testAllowedByNarrowerPermission() {
    assertTrue(new ReadPermissionImpl().isAllowedBy(ReadPermission.class));
    assertTrue(new ReadPermissionImpl().isAllowedBy(ViewPermission.class));
    assertTrue(new ReadPermissionImpl().isAllowedBy(EditPermission.class));
    assertTrue(new WritePermissionImpl().isAllowedBy(EditPermission.class));
    assertFalse(new EditPermissionImpl().isAllowedBy(WritePermission.class),
                "WRITE is wider than EDIT and must not grant it");
    assertFalse(new ViewPermissionImpl().isAllowedBy(WritePermission.class),
                "VIEW and WRITE are unrelated branches");
    assertTrue(new ViewPermissionImpl().isAllowedBy(EditPermission.class),
               "EDIT extends both WRITE and VIEW");
  }

  /**
   * A permission is denied by a configured permission that is the same or wider:
   * denying READ denies VIEW, WRITE, and EDIT as well.
   */
  @Test
  public void testDeniedByWiderPermission() {
    assertTrue(new ViewPermissionImpl().isDeniedBy(ReadPermission.class));
    assertTrue(new EditPermissionImpl().isDeniedBy(ReadPermission.class));
    assertTrue(new EditPermissionImpl().isDeniedBy(WritePermission.class));
    assertFalse(new ReadPermissionImpl().isDeniedBy(ViewPermission.class),
                "denying VIEW must leave READ alone");
    assertFalse(new ExecutePermissionImpl().isDeniedBy(ReadPermission.class),
                "EXEC is an unrelated branch");
  }

  /**
   * A configured ALL grants and denies every requested permission.<br>
   * A requested ALL is denied by any denial, but only granted by ALL.
   */
  @Test
  public void testAllPermission() {
    Permission all = new AllPermissionImpl();
    assertFalse(all.isAllowedBy(ReadPermission.class), "granting READ must not grant ALL");
    assertFalse(all.isAllowedBy(EditPermission.class, ExecutePermission.class), "granting EDIT and EXEC must not grant ALL");
    assertTrue(all.isAllowedBy(ReadPermission.class, AllPermission.class));
    assertTrue(all.isDeniedBy(ExecutePermission.class), "denying anything denies ALL");

    assertTrue(new EditPermissionImpl().isAllowedBy(AllPermission.class));
    assertTrue(new ExecutePermissionImpl().isDeniedBy(AllPermission.class));
  }

  @Test
  public void testNoPermissionsMatchNothing() {
    assertFalse(new ReadPermissionImpl().isAllowedBy());
    assertFalse(new ReadPermissionImpl().isDeniedBy());
  }

  /**
   * Permissions are configured by their interface. Passing an implementation class instead is a
   * programming error and must not silently evaluate to "not allowed".
   */
  @Test
  public void testImplementationClassRejected() {
    assertThrows(org.tentackle.security.SecurityException.class,
                 () -> new ReadPermissionImpl().isAllowedBy(ReadPermissionImpl.class));
    assertThrows(org.tentackle.security.SecurityException.class,
                 () -> new ReadPermissionImpl().isDeniedBy(ReadPermissionImpl.class));
  }

  @Test
  public void testEqualsAndHashCode() {
    assertEquals(new ReadPermissionImpl(), new ReadPermissionImpl());
    assertEquals(new ReadPermissionImpl().hashCode(), new ReadPermissionImpl().hashCode());
    assertNotEquals(new ReadPermissionImpl(), new WritePermissionImpl());
    assertNotEquals(new ReadPermissionImpl(), null);
    assertNotEquals(new ReadPermissionImpl(), ReadPermission.NAME);
  }

  /**
   * Permissions sort by name, which is what the security editor relies on to present them.
   */
  @Test
  public void testComparable() {
    List<Permission> permissions = new ArrayList<>(
        List.of(new WritePermissionImpl(), new ReadPermissionImpl(), new AllPermissionImpl(),
                new ExecutePermissionImpl(), new ViewPermissionImpl(), new EditPermissionImpl()));
    permissions.sort(null);
    assertEquals(permissions.stream().map(Permission::getName).toList(),
                 List.of("ALL", "EDIT", "EXEC", "READ", "VIEW", "WRITE"));
  }

}

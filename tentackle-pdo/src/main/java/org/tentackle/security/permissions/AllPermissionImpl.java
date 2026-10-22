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

import org.tentackle.security.PermissionService;

/**
 * Permission that represents all other permissions.
 *
 * @author harald
 */
@PermissionService(AllPermission.class)
public final class AllPermissionImpl extends AbstractPermission implements AllPermission {

  /**
   * Creates the "all" permission.
   */
  public AllPermissionImpl() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getName() {
    return NAME;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Requesting all permissions is only allowed if ALL is granted.
   */
  @Override
  public boolean isAllowedBy(Class<?>... permissions) {
    return super.isAllowedBy(permissions);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Requesting all permissions is denied by the denial of any permission.
   */
  @Override
  public boolean isDeniedBy(Class<?>... permissions) {
    return true;
  }

  @Override
  public boolean appliesTo(Class<?> clazz) {
    return true;
  }

}

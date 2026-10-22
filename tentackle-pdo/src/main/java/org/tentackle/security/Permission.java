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

/**
 * A security permission.
 * <p>
 * Permissions are interfaces and may extend each other to
 * imply other permissions.
 *
 * @author harald
 */
public interface Permission extends Comparable<Permission> {

  /**
   * Gets the unique name of the permission.<br>
   * The name must conform to the rules of a java identifier,
   * must be unique among all permissions and should be reasonably short.
   *
   * @return the name, never null
   */
  String getName();

  /**
   * Gets the optional internationalized description.<br>
   * May consist of more than one line.<br>
   * Will be displayed in tooltips, for example.
   * Makes sense for application-specific permissions with a non-obvious purpose.
   *
   * @return the description, null or empty if none
   */
  String getDescription();

  /**
   * Gets the permission interface of this implementation.
   *
   * @return the interface
   */
  Class<? extends Permission> getPermissionInterface();

  /**
   * Determines whether this permission applies to given class or instances of it.
   *
   * @param clazz the class
   * @return true if applies
   */
  boolean appliesTo(Class<?> clazz);

  /**
   * Checks whether this requested permission is granted by at least one of the given permissions.<br>
   * This is the case if a given permission is the same as or extends this permission, or is ALL.
   * For example, a requested READ is allowed by a configured EDIT, since EDIT implies READ.
   *
   * @param permissions the configured permission interfaces
   * @return true if matches
   */
  boolean isAllowedBy(Class<?>... permissions);

  /**
   * Checks whether this requested permission is denied by at least one of the given permissions.<br>
   * This is the case if this permission is the same as or extends a given permission, or a given one is ALL.
   * For example, a requested EDIT is denied by a configured READ, since EDIT cannot be granted without READ.
   *
   * @param permissions the configured permission interfaces
   * @return true if matches
   */
  boolean isDeniedBy(Class<?>... permissions);

}

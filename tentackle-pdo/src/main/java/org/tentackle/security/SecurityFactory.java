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

import java.util.Collection;
import java.util.Set;
import org.tentackle.common.ServiceFactory;
import org.tentackle.security.permissions.AllPermission;
import org.tentackle.security.permissions.EditPermission;
import org.tentackle.security.permissions.ExecutePermission;
import org.tentackle.security.permissions.ReadPermission;
import org.tentackle.security.permissions.ViewPermission;
import org.tentackle.security.permissions.WritePermission;


interface SecurityFactoryHolder {
  SecurityFactory INSTANCE = ServiceFactory.createService(SecurityFactory.class);
}


/**
 * Factory for security related objects.
 * <p>
 * The factory also provides methods for the predefined permissions necessary for the framework
 * but creates singletons for any other application-specific permissions as well.
 *
 * @author harald
 */
public interface SecurityFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static SecurityFactory getInstance() {
    return SecurityFactoryHolder.INSTANCE;
  }


  /**
   * Gets the security manager singleton.
   *
   * @return the security manager
   */
  SecurityManager getSecurityManager();

  /**
   * Gets the request all permission singleton.
   *
   * @return the all permission
   */
  AllPermission getAllPermission();

  /**
   * Gets the request read permission singleton.
   *
   * @return the permission
   */
  ReadPermission getReadPermission();

  /**
   * Gets the request write permission singleton.
   *
   * @return the permission
   */
  WritePermission getWritePermission();

  /**
   * Gets the request view permission singleton.
   *
   * @return the permission
   */
  ViewPermission getViewPermission();

  /**
   * Gets the request edit permission singleton.
   *
   * @return the permission
   */
  EditPermission getEditPermission();

  /**
   * Gets the request execute permission singleton.
   *
   * @return the permission
   */
  ExecutePermission getExecutePermission();

  /**
   * Gets all configured permission interfaces.
   *
   * @return the permission interfaces
   */
  Collection<Class<? extends Permission>> getPermissionInterfaces();

  /**
   * Gets all configured permissions.
   *
   * @return the permissions
   */
  Collection<Permission> getPermissions();

  /**
   * Gets all configured permission interfaces applicable for a class or instances of it.
   *
   * @param clazz the class to be protected
   * @return the permissions interfaces applicable
   */
  Collection<Class<? extends Permission>> getPermissionInterfaces(Class<?> clazz);

  /**
   * Gets all configured permission instances applicable for a class or instances of it.
   *
   * @param clazz the class to be protected
   * @return the permissions instances applicable
   */
  Collection<Permission> getPermissions(Class<?> clazz);

  /**
   * Gets a permission interface by its name.
   *
   * @param name the unique name
   * @return the permission, null if no such permission configured
   */
  Class<? extends Permission> getPermissionInterface(String name);

  /**
   * Gets a request permission implementation singleton by its interface.
   *
   * @param <T> the interface type
   * @param iFace the interface
   * @return the permission, never null
   * @throws SecurityException if no instance for permission interface found
   */
  <T extends Permission> T getPermission(Class<T> iFace);

  /**
   * Converts a comma separated string to a set of permission interfaces.
   *
   * @param str the string
   * @return the permission interfaces in the same order as in str, empty array if string is null
   * @throws SecurityException if conversion failed
   */
  Class<?>[] stringToPermissionInterfaces(String str);

  /**
   * Converts a comma separated string to a set of permissions.
   *
   * @param str the string
   * @return the permissions in the same order as in str, empty set if string is null
   * @throws SecurityException if conversion failed
   */
  Set<Permission> stringToPermissions(String str);

  /**
   * Converts a set of permissions to a comma seperated string.
   *
   * @param permissions the permissions
   * @return the string, empty string if permissions is null
   */
  String permissionsToString(Set<Permission> permissions);

}

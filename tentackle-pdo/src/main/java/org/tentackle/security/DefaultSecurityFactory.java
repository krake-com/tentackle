/**
 * Tentackle - http://www.tentackle.org
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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.security.permissions.AllPermission;
import org.tentackle.security.permissions.EditPermission;
import org.tentackle.security.permissions.ExecutePermission;
import org.tentackle.security.permissions.ReadPermission;
import org.tentackle.security.permissions.ViewPermission;
import org.tentackle.security.permissions.WritePermission;


/**
 * Default implementation of a security manager factory.
 *
 * @author harald
 */
@Service(SecurityFactory.class)
public class DefaultSecurityFactory implements SecurityFactory {


  private final SecurityManager securityManager;              // the manager singleton


  private final Map<String,Class<? extends Permission>> permissionInterfacesByName; // permission interfaces by name
  private final Map<Class<? extends Permission>,Permission> permissionsByIface;     // permission implementations by interface
  private final Collection<Permission> permissions;                                 // all permissions

  // permissions essential to the framework
  private AllPermission allPermission;
  private ReadPermission readPermission;
  private ViewPermission viewPermission;
  private WritePermission writePermission;
  private EditPermission editPermission;
  private ExecutePermission executePermission;


  /**
   * Creates the factory.
   */
  public DefaultSecurityFactory() {
    securityManager = createSecurityManager();
    permissionInterfacesByName = new HashMap<>();
    permissionsByIface = new HashMap<>();
    permissions = new ArrayList<>();
    try {
      Map<String,String> permissionMap = ServiceFactory.getServiceFinder().createNameMap(PermissionService.class.getName());
      for (Map.Entry<String,String> entry: permissionMap.entrySet()) {
        @SuppressWarnings("unchecked")
        Class<Permission> permissionInterface = (Class<Permission>) Class.forName(entry.getKey());
        @SuppressWarnings("unchecked")
        Class<Permission> permissionImplementation = (Class<Permission>) Class.forName(entry.getValue());
        if (!permissionInterface.isAssignableFrom(permissionImplementation)) {
          throw new SecurityException("permission implementation " + permissionImplementation.getName() +
                                      " does not implement " + permissionInterface.getName());
        }
        Permission permission = permissionImplementation.getDeclaredConstructor().newInstance();
        if (permission.getClass().getInterfaces()[0] != permissionInterface) {
          throw new SecurityException("permission interface of " + permissionImplementation.getName() +
                                      " is not a " + permissionInterface.getName());
        }
        String name = permission.getName();
        if (!StringHelper.isValidJavaIdentifier(name)) {
          throw new SecurityException("invalid name '" + name + "' in " + permissionImplementation.getName());
        }
        Class<? extends Permission> oldPermission = permissionInterfacesByName.put(name, permissionInterface);
        if (oldPermission != null) {
          throw new SecurityException("permission name '" + name + "' of " + permissionImplementation.getName() +
                  " already used by " + oldPermission.getClass().getName());
        }
        permissionsByIface.put(permissionInterface, permission);
        permissions.add(permission);

        if (permission instanceof EditPermission) {
          if (editPermission == null ||
              permission.getPermissionInterface().isAssignableFrom(editPermission.getPermissionInterface())) {
            // new or narrower type
            editPermission = (EditPermission) permission;
          }
        }
        if (permission instanceof WritePermission) {
          if (writePermission == null ||
              permission.getPermissionInterface().isAssignableFrom(writePermission.getPermissionInterface())) {
            writePermission = (WritePermission) permission;
          }
        }
        if (permission instanceof ViewPermission) {
          if (viewPermission == null ||
              permission.getPermissionInterface().isAssignableFrom(viewPermission.getPermissionInterface())) {
            viewPermission = (ViewPermission) permission;
          }
        }
        if (permission instanceof ReadPermission) {
          if (readPermission == null ||
              permission.getPermissionInterface().isAssignableFrom(readPermission.getPermissionInterface())) {
            readPermission = (ReadPermission) permission;
          }
        }
        if (permission instanceof ExecutePermission) {
          if (executePermission == null ||
              permission.getPermissionInterface().isAssignableFrom(executePermission.getPermissionInterface())) {
            executePermission = (ExecutePermission) permission;
          }
        }
        if (permission instanceof AllPermission) {
          if (allPermission == null ||
              permission.getPermissionInterface().isAssignableFrom(allPermission.getPermissionInterface())) {
            allPermission = (AllPermission) permission;
          }
        }
      }

      // check that all essential permissions were found
      if (readPermission == null) {
        throw new SecurityException("no ReadPermission configured");
      }
      if (viewPermission == null) {
        throw new SecurityException("no ViewPermission configured");
      }
      if (writePermission == null) {
        throw new SecurityException("no WritePermission configured");
      }
      if (editPermission == null) {
        throw new SecurityException("no EditPermission configured");
      }
      if (executePermission == null) {
        throw new SecurityException("no ExecutePermission configured");
      }
      if (allPermission == null) {
        throw new SecurityException("no AllPermission configured");
      }
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
           InvocationTargetException | NoSuchMethodException nfe) {
      throw new SecurityException("supported permissions could not be determined", nfe);
    }
  }


  /**
   * Creates the security manager.
   *
   * @return the security manager
   */
  protected SecurityManager createSecurityManager() {
    return new DefaultSecurityManager();
  }


  @Override
  public SecurityManager getSecurityManager() {
    return securityManager;
  }

  @Override
  public Collection<Class<? extends Permission>> getPermissionInterfaces() {
    return permissionInterfacesByName.values();
  }

  @Override
  public Collection<Permission> getPermissions() {
    return permissions;
  }

  @Override
  public Collection<Permission> getPermissions(Class<?> clazz) {
    Collection<Permission> perms = new ArrayList<>();
    for (Class<? extends Permission> permissionIface: getPermissionInterfaces()) {
      Permission permission = getPermission(permissionIface);
      if (permission.appliesTo(clazz)) {
        perms.add(permission);
      }
    }
    return perms;
  }

  @Override
  public Collection<Class<? extends Permission>> getPermissionInterfaces(Class<?> clazz) {
    Collection<Class<? extends Permission>> perms = new ArrayList<>();
    for (Permission permission: getPermissions(clazz)) {
      if (permission.appliesTo(clazz)) {
        perms.add(permission.getPermissionInterface());
      }
    }
    return perms;
  }

  @Override
  public Class<? extends Permission> getPermissionInterface(String name) {
    return permissionInterfacesByName.get(name);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Permission> T getPermission(Class<T> iFace) {
    T permission = (T) permissionsByIface.get(iFace);
    if (permission == null) {
      throw new SecurityException("no permission instance for " + iFace);
    }
    return permission;
  }

  @Override
  public AllPermission getAllPermission() {
    return allPermission;
  }

  @Override
  public ReadPermission getReadPermission() {
    return readPermission;
  }

  @Override
  public ViewPermission getViewPermission() {
    return viewPermission;
  }

  @Override
  public WritePermission getWritePermission() {
    return writePermission;
  }

  @Override
  public EditPermission getEditPermission() {
    return editPermission;
  }

  @Override
  public ExecutePermission getExecutePermission() {
    return executePermission;
  }

  @Override
  public Class<?>[] stringToPermissionInterfaces(String str) {
    if (str != null) {
      StringTokenizer stok = new StringTokenizer(str, " ,\n\r\t");
      int size = stok.countTokens();
      Class<?>[] perms = new Class<?>[size];
      int ndx = 0;
      while (stok.hasMoreTokens()) {
        String name = stok.nextToken();
        Class<? extends Permission> permissionClass = getPermissionInterface(name);
        if (permissionClass == null) {
          throw new SecurityException("no such permission: '" + name + "'");
        }
        perms[ndx++] = permissionClass;
      }
      return perms;
    }
    return new Class<?>[0];
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<Permission> stringToPermissions(String str) {
    Class<?>[] permIfaces = stringToPermissionInterfaces(str);
    Set<Permission> perms = new LinkedHashSet<>();
    for (Class<?> permIface: permIfaces) {
      perms.add(getPermission((Class<Permission>) permIface));
    }
    return perms;
  }

  @Override
  public String permissionsToString(Set<Permission> permissions) {
    if (permissions != null) {
      StringBuilder buf = new StringBuilder();
      for (Permission permission: permissions) {
        if (buf.length() > 0) {
          buf.append(',');
        }
        buf.append(permission.getName());
      }
      return buf.toString();
    }
    return "";
  }

}

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

import java.util.Objects;
import org.tentackle.security.Permission;

/**
 * Abstract permission.
 *
 * @author harald
 */
public abstract class AbstractPermission implements Permission, Comparable<Permission> {

  // the extracted permission interface
  private Class<?> iFace;

  @Override
  @SuppressWarnings("unchecked")
  public Class<? extends Permission> getPermissionInterface() {
    if (iFace == null) {
      iFace = getClass().getInterfaces()[0];
    }
    if (!Permission.class.isAssignableFrom(iFace)) {
      throw new SecurityException(iFace + " is not a permission interface");
    }
    return (Class<? extends Permission>) iFace;
  }

  @Override
  public boolean isAllowedBy(Class<?>... permissions) {
    for (Class<?> permission: permissions) {
      if (!permission.isInterface()) {
        throw new SecurityException(permission + " is not an interface");
      }
      if (AllPermission.class.isAssignableFrom(permission) ||
          getPermissionInterface().isAssignableFrom(permission)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isDeniedBy(Class<?>... permissions) {
    for (Class<?> permission: permissions) {
      if (!permission.isInterface()) {
        throw new SecurityException(permission + " is not an interface");
      }
      if (permission.isAssignableFrom(AllPermission.class) ||
          permission.isAssignableFrom(getPermissionInterface())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 89 * hash + Objects.hashCode(getName());
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final AbstractPermission other = (AbstractPermission) obj;
    return Objects.equals(getName(), other.getName());
  }

  @Override
  public int compareTo(Permission o) {
    return getName().compareTo(o.getName());
  }

}

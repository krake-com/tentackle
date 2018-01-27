/*
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

package org.tentackle.common;

import java.util.Objects;

/**
 * Key to map service finders.
 *
 * @author harald
 */
public class ServiceFinderKey {

  private final ClassLoader classLoader;
  private final String servicePath;

  /**
   * Creates a finder key.
   *
   * @param classLoader the classloader
   * @param servicePath the service path
   */
  public ServiceFinderKey(ClassLoader classLoader, String servicePath) {
    if (classLoader == null) {
      throw new IllegalArgumentException("classloader must not be null");
    }
    if (servicePath == null) {
      throw new IllegalArgumentException("service path must not be null");
    }
    this.classLoader = classLoader;
    this.servicePath = servicePath;
  }

  /**
   * Gets the classloader.
   *
   * @return the classloader
   */
  public ClassLoader getClassLoader() {
    return classLoader;
  }

  /**
   * Gets the service path.
   *
   * @return the service path in META-INF
   */
  public String getServicePath() {
    return servicePath;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 19 * hash + Objects.hashCode(this.classLoader);
    hash = 19 * hash + Objects.hashCode(this.servicePath);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ServiceFinderKey other = (ServiceFinderKey) obj;
    if (!Objects.equals(this.servicePath, other.servicePath)) {
      return false;
    }
    if (!Objects.equals(this.classLoader, other.classLoader)) {
      return false;
    }
    return true;
  }

}

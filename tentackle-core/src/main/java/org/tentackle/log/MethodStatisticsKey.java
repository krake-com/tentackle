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

package org.tentackle.log;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Method key for collecting method statistics.
 *
 * @author harald
 */
public class MethodStatisticsKey {

  private final Method method;
  private final Class<?> servicedClass;

  /**
   * Creates a key for given method and optional class.
   *
   * @param method the method
   * @param servicedClass the serviced class, null if {@code method.getDeclaringClass()}
   */
  public MethodStatisticsKey(Method method, Class<?> servicedClass) {
    this.method = method;
    this.servicedClass = servicedClass == null ? method.getDeclaringClass() : servicedClass;
  }

  /**
   * Creates a method key.
   *
   * @param method the method
   */
  public MethodStatisticsKey(Method method) {
    this(method, null);
  }

  /**
   * Gets the method.
   *
   * @return the method
   */
  public Method getMethod() {
    return method;
  }

  /**
   * Gets the serviced class.
   *
   * @return the class
   */
  public Class<?> getServicedClass() {
    return servicedClass;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 23 * hash + Objects.hashCode(this.method);
    hash = 23 * hash + Objects.hashCode(this.servicedClass);
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
    final MethodStatisticsKey other = (MethodStatisticsKey) obj;
    if (!Objects.equals(this.method, other.method)) {
      return false;
    }
    return Objects.equals(this.servicedClass, other.servicedClass);
  }

}

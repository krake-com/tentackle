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
package org.tentackle.pdo;

import org.tentackle.reflect.Interceptable;

/**
 * Cache info provided for the {@link OperationMethodCache} to create method entries.
 */
public class OperationMethodCacheInfo {

  private final OperationInvocation invocation;
  private final Class<? extends Interceptable> delegateClass;

  /**
   * Creates a cache info.
   *
   * @param invocation the operation invocation
   * @param delegateClass the delegate
   */
  public OperationMethodCacheInfo(OperationInvocation invocation, Class<? extends Interceptable> delegateClass) {
    this.invocation = invocation;
    this.delegateClass = delegateClass;
  }

  /**
   * Gets the operation invocation.
   *
   * @return the invocation
   */
  public OperationInvocation getInvocation() {
    return invocation;
  }

  /**
   * Gets the delegate class.
   *
   * @return the implementing class
   */
  public Class<? extends Interceptable> getDelegateClass() {
    return delegateClass;
  }

}

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

import org.tentackle.reflect.InterceptableMethod;

/**
 * Method reference for operation related methods.
 */
@FunctionalInterface
public interface OperationInvocation {

  /**
   * Invokes an operation related method.
   *
   * @param invocationHandler the invocation handler
   * @param method the interceptable method
   * @param args the method args
   * @return the method's return value
   * @throws Throwable if invocation fails
   */
  Object invoke(OperationInvocationHandler invocationHandler, InterceptableMethod method, Object[] args) throws Throwable;

}

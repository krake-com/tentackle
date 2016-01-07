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

package org.tentackle.reflect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A method wrapper to optionally invoke the interceptors around a method.
 *
 * @author harald
 */
public class InterceptableMethod {

  private final Method method;                // the method
  private final Interceptor interceptor;      // the head of interceptors, null if none

  /**
   * Creates a delegate method.
   *
   * @param method the method
   * @param interceptor the head of interceptors, null if none
   */
  public InterceptableMethod(Method method, Interceptor interceptor) {
    if (method == null) {
      throw new NullPointerException("method must not be null");
    }
    this.method = method;
    this.interceptor = interceptor;
  }

  /**
   * Invokes a method.<br>
   * If intercepted the head of interceptors will be invoked, else the method.
   *
   * @param delegate the delegate to invoke the method on, null if static
   * @param args the method arguments
   * @return the method's return value
   * @throws Throwable if invocation failed
   */
  public Object invoke(Object delegate, Object[] args) throws Throwable {
    try {
      if (interceptor != null) {
        return interceptor.invoke(delegate, method, args);
      }
      else {
        return method.invoke(delegate, args);
      }
    }
    catch (InvocationTargetException ix) {
      throw ix.getCause();
    }
  }

}

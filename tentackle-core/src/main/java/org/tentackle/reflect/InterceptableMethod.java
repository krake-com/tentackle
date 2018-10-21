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

package org.tentackle.reflect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A method wrapper to optionally invoke the interceptors around a delegate's method.
 *
 * @author harald
 */
public class InterceptableMethod {

  private final Method method;                // the orginal method of the interface
  private final Method delegateMethod;        // the delegate method
  private final Interceptor interceptor;      // the head of interceptors, null if none

  /**
   * Creates a interceptable method.
   *
   * @param method the orginal method of the interface
   * @param delegateMethod the delegate's method
   * @param interceptor the head of interceptors, null if none
   */
  public InterceptableMethod(Method method,  Method delegateMethod, Interceptor interceptor) {
    this.method = method;
    this.delegateMethod = delegateMethod;
    this.interceptor = interceptor;
  }

  /**
   * Gets the original method.
   *
   * @return the method of the interface
   */
  public Method getMethod() {
    return method;
  }

  /**
   * Gets the delegate's method.
   *
   * @return the method to invoke
   */
  public Method getDelegateMethod() {
    return delegateMethod;
  }

  /**
   * Gets the interceptor.
   *
   * @return the interceptor, null if none
   */
  public Interceptor getInterceptor() {
    return interceptor;
  }

  /**
   * Invokes a delegateMethod.<br>
   * If intercepted the head of interceptors will be invoked, else the delegateMethod.
   *
   * @param delegate the delegate to invoke the delegateMethod on, null if static
   * @param args the delegateMethod arguments
   * @return the delegateMethod's return value
   * @throws Throwable if invocation failed
   */
  public Object invoke(Object delegate, Object[] args) throws Throwable {
    try {
      if (interceptor != null) {
        return interceptor.invoke(delegate, delegateMethod, args);
      }
      else {
        return delegateMethod.invoke(delegate, args);
      }
    }
    catch (InvocationTargetException ix) {
      throw ix.getCause();
    }
  }

}

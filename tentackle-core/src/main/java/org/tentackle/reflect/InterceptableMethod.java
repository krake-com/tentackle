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

  // see note below...
  // private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

  private final Method method;                // the original method of the interface
  private final Method delegateMethod;        // the delegate method
  private final Interceptor interceptor;      // the head of interceptors, null if none
  // private final MethodHandle delegateHandle;  // the delegate method handle

  /**
   * Creates an interceptable method.
   *
   * @param method the original method of the interface
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
   * Invokes the delegate method.<br>
   * If intercepted, the head of the interceptors will be invoked, else the delegate method.
   *
   * @param delegate the delegate to invoke the delegate method on, null if static
   * @param args the delegate method's arguments
   * @return the delegate method's return value
   * @throws Throwable if invocation failed, the cause of an {@link InvocationTargetException}
   */
  public Object invoke(Object delegate, Object[] args) throws Throwable {
    try {
      if (interceptor != null) {
        return interceptor.invoke(delegate, delegateMethod, args);
      }
      else {
        // we stick to old reflection because the new MethodHandles are a lot slower, at least when using invokeWithArguments.
        // The opposite should be true, since the security is checked only once when creating the method handle,
        // but in reality it shows a significant performance degradation -- for whatever reason.
        //
        //        return delegateHandle.bindTo(delegate).invokeWithArguments(args);
        //
        return delegateMethod.invoke(delegate, args);
      }
    }
    catch (InvocationTargetException ix) {
      throw ix.getCause();
    }
  }

}

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

import org.tentackle.log.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A method wrapper to optionally invoke the interceptors around a delegate's method.
 *
 * @author harald
 */
public class InterceptableMethod {

  private static final Logger LOGGER = Logger.get(InterceptableMethod.class);

  /**
   * Whether non-intercepted methods are invoked via method handles instead of reflection.<br>
   * Defaults to true, set the system property {@code tentackle.reflect.methodHandles} to false to use reflection.
   */
  public static final boolean METHOD_HANDLES = Boolean.parseBoolean(System.getProperty("tentackle.reflect.methodHandles", "true"));

  private final Method method;                // the original method of the interface
  private final Method delegateMethod;        // the delegate method
  private final Interceptor interceptor;      // the head of interceptors, null if none
  private final MethodHandle delegateHandle;  // typed (Object,Object[])Object, null if intercepted or not bindable

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
    delegateHandle = METHOD_HANDLES && interceptor == null ? bind(delegateMethod) : null;
  }

  /**
   * Binds the delegate method to a method handle.
   *
   * @param method the delegate method
   * @return the handle, null if the method cannot be bound (reflection is used instead)
   */
  private static MethodHandle bind(Method method) {
    if (Modifier.isStatic(method.getModifiers())) {
      return null;
    }
    Module module = InterceptableMethod.class.getModule();
    Module targetModule = method.getDeclaringClass().getModule();
    if (!module.canRead(targetModule)) {
      // MethodHandles.lookup() is caller-sensitive: make the method's module readable by this module
      module.addReads(targetModule);
    }
    try {
      int paramCount = method.getParameterCount();
      // invokeExact on a handle avoids the caller-sensitive access check and the argument checks of Method#invoke
      // asFixedArity: varargs already arrive packed in args, a variable arity handle would wrap them again
      return MethodHandles.lookup().unreflect(method)
                          .asFixedArity()
                          .asType(MethodType.genericMethodType(paramCount + 1))
                          .asSpreader(Object[].class, paramCount);
    }
    catch (IllegalAccessException | RuntimeException e) {
      // not accessible from here: fall back to reflection
      LOGGER.fine("cannot bind " + method + ", falling back to reflection", e);
      return null;
    }
  }

  /**
   * Returns whether the delegate method is invoked via a method handle.
   *
   * @return true if method handle, false if reflection or intercepted
   */
  public boolean isMethodHandleBound() {
    return delegateHandle != null;
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
    if (delegateHandle != null) {
      return delegateHandle.invokeExact(delegate, args);
    }
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

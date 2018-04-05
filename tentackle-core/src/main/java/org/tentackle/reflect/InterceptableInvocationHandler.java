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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class mapping invocation handler.<br>
 *
 * @param <T> the interceptable type
 * @author harald
 */
public class InterceptableInvocationHandler<T extends Interceptable> implements InvocationHandler {

  /**
   * The method cache.
   * Speeds up method invocations.
   */
  private static final ConcurrentHashMap<Method,InterceptableMethod> METHOD_CACHE = new ConcurrentHashMap<>();


  // preloaded methods of EffectiveClassProvider
  private static final Method GETEFFECTIVECLASS_METHOD;

  static {
    try {
      GETEFFECTIVECLASS_METHOD = EffectiveClassProvider.class.getMethod("getEffectiveClass");
    }
    catch (NoSuchMethodException e) {
      throw new NoSuchMethodError(e.getMessage());
    }
  }


  private final Class<T> clazz;     // the handled class
  private final Mixin<T,T> mixin;   // the mixin



  /**
   * Creates an invocation handler.
   *
   * @param mapper the classmapper
   * @param clazz the PDO declaring interface
   *
   * @throws ClassNotFoundException if no implementation found
   */
  public InterceptableInvocationHandler(ClassMapper mapper, Class<T> clazz) throws ClassNotFoundException {
    this.clazz = clazz;
    mixin = new Mixin<>(mapper, clazz, Interceptable.class);
  }



  /**
   * Creates the delegates for the given proxy instance and the domain context.
   *
   * @param proxy the dynamic proxy instance
   * @param clazz the class to implement
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegate(T proxy, Class<T> clazz)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    mixin.createDelegate(new Class<?>[] { clazz }, new Object[] { proxy });
  }


  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    Class<?> delegateClass = method.getDeclaringClass();

    if (delegateClass == EffectiveClassProvider.class && method.equals(GETEFFECTIVECLASS_METHOD)) {
      return clazz;
    }

    InterceptableMethod delegateMethod = METHOD_CACHE.get(method);
    if (delegateMethod == null) {
      Method implMethod = delegateClass.getMethod(method.getName(), method.getParameterTypes());
      Interceptor interceptor = InterceptionUtilities.getInstance().findInterceptors(clazz, method, implMethod);
      delegateMethod = new InterceptableMethod(method, interceptor);
      METHOD_CACHE.put(method, delegateMethod);
    }
    
    // invoke the method
    return delegateMethod.invoke(mixin.getDelegate(), args);
  }

}

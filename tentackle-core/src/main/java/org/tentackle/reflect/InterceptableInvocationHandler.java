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

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Invocation handler for {@link Interceptable}s.
 * <p>
 * This invocation handler is for dynamic proxies serving only one interface.
 * The method invocations are delegated to the implementation mapped via &#64;{@link InterceptionService}
 * and its interceptors, if any. The methods of {@link EffectiveClassProvider} are handled by the invocation handler itself.
 *
 * @param <T> the interceptable type
 * @author harald
 */
public class InterceptableInvocationHandler<T extends Interceptable & Serializable> implements InvocationHandler, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * The method invoker.<br>
   * Performs the method invocations and provides logging and statistics.
   */
  public static final InterceptableMethodInvoker INVOKER = new InterceptableMethodInvoker("Interceptable");


  // preloaded methods of EffectiveClassProvider
  private static final Method GETEFFECTIVECLASS_METHOD;
  private static final Method GETEFFECTIVESUPERCLASSES_METHOD;

  static {
    try {
      GETEFFECTIVECLASS_METHOD = EffectiveClassProvider.class.getDeclaredMethod("getEffectiveClass");
      GETEFFECTIVESUPERCLASSES_METHOD = EffectiveClassProvider.class.getDeclaredMethod("getEffectiveSuperClasses");
    }
    catch (NoSuchMethodException e) {
      throw new NoSuchMethodError(e.getMessage());
    }
  }



  private final Class<T> clazz;                           // the handled class
  private final transient Mixin<T,T> mixin;               // the shared mixin (only needed to create the delegate)
  private T delegate;                                     // the delegate
  private transient InterceptableMethodCache methodCache; // the method cache for given class


  /**
   * Creates an invocation handler.
   *
   * @param mapper the classmapper
   * @param clazz the declaring interface
   *
   * @throws ClassNotFoundException if no implementation found
   */
  public InterceptableInvocationHandler(ClassMapper mapper, Class<T> clazz) throws ClassNotFoundException {
    this.clazz = clazz;
    mixin = Mixin.get(mapper, clazz, Interceptable.class);
  }


  /**
   * Creates the delegate for the given proxy instance.<br>
   * The delegate's constructor must accept the proxy instance.
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

    delegate = mixin.createDelegate(new Class<?>[] { clazz }, new Object[] { proxy });
  }


  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    if (method.equals(GETEFFECTIVECLASS_METHOD)) {
      return clazz;
    }
    else if (method.equals(GETEFFECTIVESUPERCLASSES_METHOD)) {
      List<Class<?>> list = new ArrayList<>();
      for (Class<?> ifClass: clazz.getInterfaces()) {
        if (Interceptable.class.isAssignableFrom(ifClass)) {
          list.add(ifClass);
        }
      }
      return list;
    }

    return getMethodCache().invoke(INVOKER, delegate, method, args);
  }


  private InterceptableMethodCache getMethodCache() {
    if (methodCache == null) {
      methodCache = InterceptableMethodCache.getCache(clazz);
    }
    return methodCache;
  }

}

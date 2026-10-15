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

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A method cache for {@link InterceptableInvocationHandler}s.
 *
 * @author harald
 */
public class InterceptableMethodCache {

  /**
   * Maps interceptable classes to caches.
   */
  private static final ConcurrentHashMap<Class<? extends Interceptable>, InterceptableMethodCache> CACHE_MAP = new ConcurrentHashMap<>();


  /**
   * Gets the cache for given class.
   *
   * @param effectiveClass the interceptable class
   * @return the method cache
   */
  public static InterceptableMethodCache getCache(Class<? extends Interceptable> effectiveClass) {
    return CACHE_MAP.computeIfAbsent(effectiveClass, InterceptableMethodCache::new);
  }


  private final Class<? extends Interceptable> clazz;                           // the topmost interceptable's interface
  private final ConcurrentHashMap<Method,InterceptableMethod> methodCache;      // the method cache


  /**
   * Creates a method cache.
   *
   * @param clazz the interceptable class
   */
  public InterceptableMethodCache(Class<? extends Interceptable> clazz) {
    this.clazz = clazz;
    methodCache = new ConcurrentHashMap<>();
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + clazz.getSimpleName() + ")";
  }

  /**
   * Performs the method invocation.<br>
   * The methods are converted to an {@link InterceptableMethod} and cached.
   *
   * @param invoker the method invoker
   * @param delegate the delegate to invoke the method on
   * @param method the method to invoke
   * @param args the method's arguments
   * @return the method's return value
   * @throws Throwable if invocation failed
   */
  public Object invoke(InterceptableMethodInvoker invoker, Interceptable delegate, Method method, Object[] args) throws Throwable {
    // get() first: the lambda below captures the delegate and would be allocated on every invocation,
    // although the method is missing only on its first invocation
    InterceptableMethod delegateMethod = methodCache.get(method);
    if (delegateMethod == null) {
      delegateMethod = methodCache.computeIfAbsent(method,
          m -> InterceptionUtilities.getInstance().createInterceptableMethod(clazz, delegate.getClass(), m));
    }
    return invoker.invoke(delegate, delegateMethod, args);
  }

}

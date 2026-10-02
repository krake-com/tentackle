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
import org.tentackle.reflect.InterceptableMethod;
import org.tentackle.reflect.InterceptionUtilities;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Method-cache for operations.<br>
 * Each operation class provides its own cache.
 *
 * @param <T> the operation type
 */
public class OperationMethodCache<T extends Operation<T>> {

  /**
   * Maps operation classes to caches.
   */
  @SuppressWarnings("rawtypes")
  private static final ConcurrentHashMap<Class<? extends Operation>, OperationMethodCache> CACHE_MAP = new ConcurrentHashMap<>();


  /**
   * Gets the cache for given class.
   *
   * @param <T> the operation type
   * @param effectiveClass the interceptable class
   * @return the method cache
   */
  @SuppressWarnings("unchecked")
  public static <T extends Operation<T>> OperationMethodCache<T> getCache(Class<T> effectiveClass) {
    return CACHE_MAP.computeIfAbsent(effectiveClass, OperationMethodCache::new);
  }

  /**
   * VO holding the method and its invocation.
   */
  private record OperationMethod(InterceptableMethod method, OperationInvocation invocation) {}


  private final Class<? extends Interceptable> clazz;       // the topmost interceptable's interface
  private final Map<Method, OperationMethod> methodCache;   // the method cache

  /**
   * Creates a method cache.
   *
   * @param clazz the interceptable class
   */
  public OperationMethodCache(Class<T> clazz) {
    this.clazz = clazz;
    this.methodCache = new ConcurrentHashMap<>();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + clazz.getSimpleName() + ")";
  }

  /**
   * Invokes a method on a delegate.
   *
   * @param invocationHandler the invocation handler
   * @param method the original method of the interface
   * @param args the method's args
   * @return the method's return value
   * @throws Throwable if invocation fails
   */
  public Object invoke(OperationInvocationHandler<T> invocationHandler, Method method, Object[] args) throws Throwable {
    OperationMethod operationMethod = methodCache.computeIfAbsent(method, m -> {
      OperationMethodCacheInfo info = invocationHandler.determineInvocation(method);
      InterceptableMethod interceptableMethod = info.delegateClass() == null ? null :
                                                InterceptionUtilities.getInstance().createInterceptableMethod(clazz, info.delegateClass(), method);
      return new OperationMethod(interceptableMethod, info.invocation());
    });
    return operationMethod.invocation.invoke(invocationHandler, operationMethod.method, args);
  }

}

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
 * Method-cache for PDOs.<br>
 * Each PDO class provides its own cache.
 *
 * @param <T> the PDO type
 */
public class PdoMethodCache<T extends PersistentDomainObject<T>> {

  /**
   * VO holding the method and its invocation.
   */
  private record PdoMethod(InterceptableMethod method, PdoInvocation invocation) {}


  private final Class<? extends Interceptable> clazz;       // the topmost interceptable's interface
  private final Map<Method, PdoMethod> methodCache;         // the method cache

  /**
   * Creates a method cache.
   *
   * @param clazz the interceptable class
   */
  public PdoMethodCache(Class<T> clazz) {
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
  public Object invoke(PdoInvocationHandler<T> invocationHandler, Method method, Object[] args) throws Throwable {
    // get() first: the lambda below captures the invocation handler and would be allocated on every invocation,
    // although the method is missing only on its first invocation
    PdoMethod pdoMethod = methodCache.get(method);
    if (pdoMethod == null) {
      pdoMethod = methodCache.computeIfAbsent(method, m -> {
        PdoMethodCacheInfo info = invocationHandler.determineInvocation(m);
        InterceptableMethod interceptableMethod = InterceptionUtilities.getInstance().createInterceptableMethod(clazz, info.delegateClass(), m);
        return new PdoMethod(interceptableMethod, info.invocation());
      });
    }
    return pdoMethod.invocation.invoke(invocationHandler, pdoMethod.method, args);
  }

}

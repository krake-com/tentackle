/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.common.TentackleRuntimeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * An abstract interceptor.
 * <p>
 * Concrete interceptors implement {@link #proceed}. If the interceptor is chained, {@code proceed} receives
 * the {@code invoke} method of the next interceptor in the chain. Otherwise, it receives the intercepted method itself.
 * In both cases, the interceptor continues by invoking {@code method.invoke(proxy, args)}.
 *
 * @author harald
 */
public abstract class AbstractInterceptor implements Interceptor {

  private Interceptor chainedInterceptor;   // the chained interceptor, null if end of chain
  private Method chainedMethod;             // the proceed method of the chained interceptor
  private Annotation annotation;            // the optional annotation


  /**
   * Creates an interceptor.
   */
  public AbstractInterceptor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    this.annotation = annotation;
  }

  @Override
  public Annotation getAnnotation() {
    return annotation;
  }

  /**
   * {@inheritDoc}
   *
   * @throws TentackleRuntimeException if the {@code invoke} method of the chained interceptor cannot be determined
   */
  @Override
  public void setChainedInterceptor(Interceptor chainedInterceptor) {
    this.chainedInterceptor = chainedInterceptor;
    try {
      chainedMethod = chainedInterceptor.getClass().getMethod("invoke", Object.class, Method.class, Object[].class);
    }
    catch (NoSuchMethodException e) {
      throw new TentackleRuntimeException("cannot determine the 'invoke' method of the chained interceptor " + chainedInterceptor.getClass().getName(), e);
    }
  }

  @Override
  public Interceptor getChainedInterceptor() {
    return chainedInterceptor;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      if (chainedMethod != null) {
        return proceed(chainedInterceptor, chainedMethod, new Object[]{proxy, method, args}, proxy, method, args);
      }
      return proceed(proxy, method, args, proxy, method, args);
    }
    catch (InvocationTargetException ix) {
      throw ix.getCause();
    }
  }

  /**
   * The interceptor implementation.
   *
   * @param proxy the object to invoke the method on, the chained interceptor if chained, null if static
   * @param method the method to invoke, the chained interceptor's {@code invoke} method if chained
   * @param args the method's arguments, the original {@code proxy}, {@code method} and {@code args} if chained
   * @param orgProxy the original proxy object, same as proxy if not chained
   * @param orgMethod the original method, same as method if not chained
   * @param orgArgs the original arguments, same as args if not chained
   * @return the method's return value
   * @throws Throwable if invoked method failed
   */
  public abstract Object proceed(Object proxy, Method method, Object[] args,
                                 Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable;

}

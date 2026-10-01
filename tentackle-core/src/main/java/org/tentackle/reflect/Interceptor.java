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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;

/**
 * A method interceptor.
 * <p>
 * Interceptors may be chained. Only one interceptor should invoke
 * the intercepted method.
 * <p>
 * IMPORTANT: interceptors are cached and created only once per class and method.
 * Therefore, interceptors MUST NOT maintain any state!
 *
 * @see Interception
 * @see AbstractInterceptor
 */
public interface Interceptor extends InvocationHandler {

  /**
   * Sets the interceptor annotation.
   *
   * @param annotation the annotation, null if none
   */
  void setAnnotation(Annotation annotation);

  /**
   * Gets the interceptor annotation.
   *
   * @return the annotation, null if none
   */
  Annotation getAnnotation();

  /**
   * Sets the chained interceptor.<br>
   * The chained interceptor is invoked instead of the intercepted method.
   *
   * @param chainedInterceptor the chained interceptor
   */
  void setChainedInterceptor(Interceptor chainedInterceptor);

  /**
   * Gets the chained interceptor.
   *
   * @return the interceptor, null if end of chain
   */
  Interceptor getChainedInterceptor();

}

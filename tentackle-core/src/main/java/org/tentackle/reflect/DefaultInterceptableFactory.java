/*
 * Tentackle - http://www.tentackle.org.
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

import java.lang.reflect.Proxy;
import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;

/**
 * Default implementation of an InterceptableFactory.
 *
 * @author harald
 */
@Service(InterceptableFactory.class)
public class DefaultInterceptableFactory implements InterceptableFactory {

  private final ClassMapper classMapper;

  /**
   * Creates the factory.
   */
  public DefaultInterceptableFactory() {
    classMapper = new DefaultClassMapper(
            "interception",
            ServiceFactory.getClassLoader(Constants.MAPPED_SERVICE_PATH, Interceptable.class.getName()),
            ServiceFactory.getServiceFinder().createNameMap(Interceptable.class.getName()), null);
  }

  @Override
  public <T extends Interceptable> T createInterceptable(Class<T> clazz) {
    try {
      InterceptableInvocationHandler<T> handler = new InterceptableInvocationHandler<>(classMapper, clazz);
      @SuppressWarnings("unchecked")
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegate(instance, clazz);
      return instance;
    }
    catch (Exception e) {
      throw new TentackleRuntimeException("cannot create interceptable for class " + clazz, e);
    }
  }

}

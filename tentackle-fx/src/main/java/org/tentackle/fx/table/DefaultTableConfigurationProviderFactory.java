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

package org.tentackle.fx.table;

import org.tentackle.common.Service;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.reflect.ClassMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default table configuration provider factory.
 *
 * @author harald
 */
@Service(TableConfigurationProviderFactory.class)
public class DefaultTableConfigurationProviderFactory implements TableConfigurationProviderFactory {

  /**
   * maps classes to the constructors of the provider classes.
   */
  @SuppressWarnings("rawtypes")
  private final ConcurrentHashMap<Class, Constructor> serviceMap = new ConcurrentHashMap<>();

  /**
   * maps classes to providers.
   */
  private final ClassMapper classMapper;

  /**
   * Value for no co provider found.
   */
  private final Constructor noProvider;

  /**
   * Creates the factory.
   */
  public DefaultTableConfigurationProviderFactory() {
    classMapper = ClassMapper.create("FX table-config-provider", TableConfigurationProvider.class);
    noProvider = getClass().getConstructors()[0];
  }


  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public <T> TableConfigurationProvider<T> createTableConfigurationProvider(Class<T> clazz) {
    Constructor<TableConfigurationProvider<T>> constructor = serviceMap.get(clazz);
    if (constructor == null) {
      // no harm if replaced by concurrent threads...
      try {
        Class serviceClass = classMapper.mapLenient(clazz);
        constructor = serviceClass.getConstructor();
      }
      catch (NoSuchMethodException nx) {
        throw new FxRuntimeException("missing default constructor in table configuration service class for " + clazz.getName(), nx);
      }
      catch (ClassNotFoundException ex) {
        constructor = noProvider;
      }
      serviceMap.put(clazz, constructor);
    }

    TableConfigurationProvider<T> provider = null;

    if (constructor != noProvider) {
      try {
        provider = constructor.newInstance();
      }
      catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
        throw new FxRuntimeException("cannot instantiate table configuration service object for " + clazz.getName(), e);
      }
    }

    return provider;
  }

}

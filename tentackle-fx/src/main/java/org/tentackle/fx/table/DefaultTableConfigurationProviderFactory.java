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
   * Maps classes to the providers.<br>
   * Notice that providers are per class!
   */
  @SuppressWarnings("rawtypes")
  private final ConcurrentHashMap<Class, TableConfigurationProvider> serviceMap = new ConcurrentHashMap<>();

  /**
   * Maps classes to provider classes.
   */
  private final ClassMapper classMapper;

  /**
   * Marker value if no provider found.
   */
  @SuppressWarnings("rawtypes")
  private final TableConfigurationProvider noProvider;

  /**
   * Creates the factory.
   */
  @SuppressWarnings("rawtypes")
  public DefaultTableConfigurationProviderFactory() {
    classMapper = ClassMapper.create("FX table-config-provider", TableConfigurationProvider.class);
    noProvider = new DefaultTableConfigurationProvider();
  }


  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public <T> TableConfigurationProvider<T> createTableConfigurationProvider(Class<T> clazz) {
    TableConfigurationProvider<T> provider = serviceMap.get(clazz);
    if (provider == null) {
      // no harm if replaced by concurrent threads...
      try {
        Class serviceClass = classMapper.mapLenient(clazz);
        // find matching constructor
        for (Constructor<TableConfigurationProvider<T>> con : serviceClass.getConstructors()) {
          if (con.getParameterTypes().length == 0) {
            provider = con.newInstance();
            break;
          }
        }
        if (provider == null) {
          throw new FxRuntimeException("no default constructor found for " + serviceClass.getName());
        }
      }
      catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
        throw new FxRuntimeException("cannot instantiate table configuration service object for " + clazz.getName(), e);
      }
      catch (ClassNotFoundException ex) {
        provider = noProvider;
      }
      serviceMap.put(clazz, provider);
    }

    return provider == noProvider ? null : provider;
  }

}

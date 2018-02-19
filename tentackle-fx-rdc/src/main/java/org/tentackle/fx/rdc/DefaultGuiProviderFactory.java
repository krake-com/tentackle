/**
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

package org.tentackle.fx.rdc;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.common.Service;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ClassMapper;

/**
 * The default GUI provider factory.
 *
 * @author harald
 */
@Service(GuiProviderFactory.class)
public class DefaultGuiProviderFactory implements GuiProviderFactory {

  /**
   * maps PDO-classes to the constructors of the provider classes.
   */
  @SuppressWarnings("rawtypes")
  private final ConcurrentHashMap<Class, Constructor> serviceMap = new ConcurrentHashMap<>();

  /**
   * maps PDO-classes to providers.
   */
  private final ClassMapper guiClassMapper;

  /**
   * Creates the factory.
   */
  public DefaultGuiProviderFactory() {
    guiClassMapper = ClassMapper.create("FX GUI-provider", GuiProvider.class);
  }


  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public <T extends PersistentDomainObject<T>> GuiProvider<T> createGuiProvider(T pdo) {
    Class<T> pdoClass = pdo.getEffectiveClass();
    Constructor<T> constructor = serviceMap.get(pdoClass);
    if (constructor == null) {
      // no harm if replaced by concurrent threads...
      try {
        Class serviceClass = guiClassMapper.mapLenient(pdoClass);
        // find matching constructor
        for (Constructor<T> con : serviceClass.getConstructors()) {
          Class<?>[] params = con.getParameterTypes();
          if (params.length == 1 && pdoClass.isAssignableFrom(params[0])) {
            constructor = con;
            serviceMap.put(pdoClass, constructor);
            break;
          }
        }
        if (constructor == null) {
          throw new PdoRuntimeException("no matching constructor found for " + serviceClass.getName() +
                                        "(" + pdoClass.getName() + ")");
        }
      }
      catch (ClassNotFoundException ex) {
        throw new PdoRuntimeException("cannot load GUI service class for " + pdoClass.getName(), ex);
      }
    }

    try {
      return (GuiProvider<T>) constructor.newInstance(pdo);
    }
    catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
      throw new PdoRuntimeException("cannot instantiate GUI service object for " + pdoClass.getName(), e);
    }
  }

}

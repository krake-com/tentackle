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
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.DefaultClassMapper;

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
    guiClassMapper = new DefaultClassMapper(
            "FX GUI-provider",
            ServiceFactory.getClassLoader(Constants.MAPPED_SERVICE_PATH, GuiProvider.class.getName()),
            ServiceFactory.getServiceFinder().createNameMap(GuiProvider.class.getName()), null);
  }


  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public <T extends PersistentDomainObject<T>> GuiProvider<T> createGuiProvider(T pdo) {
    Class<T> pdoClass = pdo.getEffectiveClass();
    Constructor<T> con = serviceMap.get(pdoClass);
    if (con == null) {
      // no harm if replaced by concurrent threads...
      try {
        Class serviceClass = guiClassMapper.mapLenient(pdoClass);

        // find matching constructor
        Constructor<T>[] cons = serviceClass.getConstructors();
        for (int i=0; i < cons.length; i++) {
          con = cons[i];
          Class<?>[] params = con.getParameterTypes();
          if (params.length == 1 &&
              pdoClass.isAssignableFrom(params[0])) {
            serviceMap.put(pdoClass, con);
            break;
          }
        }
        if (con == null) {
          throw new PdoRuntimeException("no matching constructor found for " + serviceClass.getName() +
                                        "(" + pdoClass.getName() + ")");
        }
      }
      catch (ClassNotFoundException ex) {
        throw new PdoRuntimeException("cannot load GUI service class for " + pdoClass.getName(), ex);
      }
    }

    try {
      return (GuiProvider<T>) con.newInstance(pdo);
    }
    catch (Exception e) {
      throw new PdoRuntimeException("cannot instantiate GUI service object for " + pdoClass.getName(), e);
    }
  }

}

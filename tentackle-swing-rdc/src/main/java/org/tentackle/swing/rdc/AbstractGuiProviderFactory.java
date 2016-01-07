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


package org.tentackle.swing.rdc;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ClassMapper;

/**
 * Base implementation of a gui provider factory.
 *
 * @author harald
 */
public abstract class AbstractGuiProviderFactory implements GuiProviderFactory {


  // a map for PDO-classes to the constructors of GUI-Service classes
  private final ConcurrentHashMap<Class, Constructor> serviceMap = new ConcurrentHashMap<>();


  /**
   * Gets the GUI-service class mapper associated to this factory.<br>
   *
   * @return the mapper for the gui service classes
   */
  public abstract ClassMapper getGuiMapper();



  @Override
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> GuiProvider<T> createGuiProvider(T pdo) {
    Class<T> pdoClass = pdo.getEffectiveClass();
    Constructor<T> con = serviceMap.get(pdoClass);
    if (con == null) {
      // no harm if replaced by concurrent threads...
      try {
        Class serviceClass = getGuiMapper().mapLenient(pdoClass);

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
          throw new PdoRuntimeException("no matching constructor found for " + serviceClass.getName() + "(" + pdoClass.getName() + ")");
        }
      }
      catch (ClassNotFoundException ex) {
        throw new PdoRuntimeException("cannot load GUI service class for " + pdoClass.getName());
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

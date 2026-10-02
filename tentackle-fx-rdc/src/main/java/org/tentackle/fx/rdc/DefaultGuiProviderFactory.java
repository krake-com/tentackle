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

package org.tentackle.fx.rdc;

import org.tentackle.common.Service;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ClassMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default GUI provider factory.<br>
 * Maps the PDO's effective class to the provider class registered via {@link GuiProviderService},
 * caches the provider's constructor, and creates a new provider instance per invocation.
 *
 * @author harald
 */
@Service(GuiProviderFactory.class)
public class DefaultGuiProviderFactory implements GuiProviderFactory {

  /**
   * Maps PDO classes to the constructors of the provider classes.
   */
  @SuppressWarnings("rawtypes")
  private final ConcurrentHashMap<Class, Optional<Constructor>> serviceMap = new ConcurrentHashMap<>();

  /**
   * Maps PDO classes to provider classes.
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
    Optional<Constructor> constructor = serviceMap.computeIfAbsent(pdoClass, this::getConstructor);
    if (constructor.isPresent()) {
      try {
        return (GuiProvider<T>) constructor.get().newInstance(pdo);
      }
      catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
        throw new RdcRuntimeException("cannot instantiate GUI service object for " + pdoClass.getName(), e);
      }
    }
    throw new RdcRuntimeException("no @GuiProviderService for " + pdoClass.getName());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> boolean isGuiProviderAvailable(Class<T> pdoClass) {
    return serviceMap.computeIfAbsent(pdoClass, this::getConstructor).isPresent();
  }

  /**
   * Gets the {@link GuiProvider}'s constructor for a PDO class.
   * <p>
   * Throws {@link RdcRuntimeException} if no matching constructor was found.
   *
   * @param pdoClass the PDO class
   * @param <T> the PDO type
   * @return the constructor's optional, empty if no {@link GuiProviderService} was found at all
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected <T extends PersistentDomainObject<T>> Optional<Constructor> getConstructor(Class<T> pdoClass) {
    try {
      Class serviceClass = guiClassMapper.mapLenient(pdoClass);
      for (Constructor<GuiProvider<T>> con : serviceClass.getConstructors()) {
        Class<?>[] params = con.getParameterTypes();
        if (params.length == 1 && pdoClass.isAssignableFrom(params[0])) {
          return Optional.of(con);
        }
      }
      throw new RdcRuntimeException("no matching constructor found for " + serviceClass.getName() +
                                    "(" + pdoClass.getName() + ")");
    }
    catch (ClassNotFoundException ex) {
      return Optional.empty();
    }
  }

}

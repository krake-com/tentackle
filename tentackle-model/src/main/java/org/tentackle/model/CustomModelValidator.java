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

package org.tentackle.model;

import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Custom model validator.<br>
 * Applications may enforce extra rules on the model.
 * The validators are invoked at the end of {@link Model#loadFromDirectory(String, boolean)},
 * whenever the directory was (re)loaded.<br>
 * The validators must
 * be annotated with the {@link org.tentackle.common.Service}-annotation as follows:
 * <pre>
 *    &#64;Service(CustomModelValidator.class)
 *    public class MyModelValidator implements CustomModelValidator {
 *
 *      &#64;Override
 *      public void validate(Model model) throws ModelException {
 *        ...
 *      }
 *    }
 * </pre>
 */
public interface CustomModelValidator {

  /**
   * Validates the model.
   *
   * @param model the model
   * @throws ModelException if validation failed
   */
  void validate(Model model) throws ModelException;


  /**
   * Gets all custom model validators.
   *
   * @return the validators, never null
   */
  static Collection<CustomModelValidator> loadValidators() {
    try {
      Collection<CustomModelValidator> validators = new ArrayList<>();
      for (Class<CustomModelValidator> clazz : ServiceFactory.getServiceFinder().findServiceProviders(CustomModelValidator.class)) {
        validators.add(clazz.getDeclaredConstructor().newInstance());
      }
      return validators;
    }
    catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
      throw new TentackleRuntimeException("loading custom model validators failed", ex);
    }
  }
}

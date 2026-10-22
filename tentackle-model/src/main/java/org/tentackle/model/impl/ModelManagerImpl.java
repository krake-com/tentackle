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

package org.tentackle.model.impl;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.model.Model;
import org.tentackle.model.ModelManager;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link ModelManager}.
 */
@Service(ModelManager.class)
public class ModelManagerImpl implements ModelManager {


  private final Map<String, Model> modelMap;

  /**
   * Creates the model manager.
   */
  public ModelManagerImpl() {
    modelMap = new ConcurrentHashMap<>();
  }

  @Override
  public Model getModel(String name) {
    return modelMap.computeIfAbsent(name, n -> {
      try {
        return ServiceFactory.createServiceClass(Model.class).getDeclaredConstructor(String.class).newInstance(n);
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new TentackleRuntimeException("cannot create model: " + n, e);
      }
    });
  }

  @Override
  public void removeModel(Model model) {
    if (DEFAULT_MODEL_NAME.equals(model.getName())) {
      throw new TentackleRuntimeException("the default model cannot be removed");
    }
    Model removedModel = modelMap.remove(model.getName());
    if (removedModel == null) {
      throw new TentackleRuntimeException("no such model: " + model.getName());
    }
  }

}

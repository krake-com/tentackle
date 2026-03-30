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


interface ModelManagerHolder {
  ModelManager INSTANCE = ServiceFactory.createService(ModelManager.class);
}


/**
 * Creates and maintains models.<br>
 *
 * While there is usually only one model instance per build process (the default model),
 * it is possible to create multiple models for different purposes, for example,
 * to create PDOs from the model of another project.<br>
 * Models are distinguished by their unique name.<br>
 * The default model is always called "default".
 */
public interface ModelManager {

  /**
   * The default model name.
   */
  String DEFAULT_MODEL_NAME = "default";

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static ModelManager getInstance() {
    return ModelManagerHolder.INSTANCE;
  }



  /**
   * Gets the model with the given name.<br>
   * If the model does not exist, it is created.
   *
   * @param name the model name
   * @return the model
   */
  Model getModel(String name);

  /**
   * Removes the given model.
   * The default model cannot be removed.
   * If the model is the default model or unknown to the ModelManager,
   * a {@link org.tentackle.common.TentackleRuntimeException} is thrown.
   *
   * @param model the model to remove
   */
  void removeModel(Model model);

}

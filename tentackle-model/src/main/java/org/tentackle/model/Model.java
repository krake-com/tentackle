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

package org.tentackle.model;

import java.util.Collection;
import org.tentackle.common.ServiceFactory;


interface Model$Singleton {
  static Model INSTANCE = ServiceFactory.createService(Model.class);
}


/**
 * Model singleton.
 *
 * @author harald
 */
public interface Model {


  /**
   * The singleton.
   *
   * @return the singleton
   */
  static Model getInstance() {
    return Model$Singleton.INSTANCE;
  }



  /**
   * Gets the entity factory singleton.
   *
   * @return the entity model factory
   */
  EntityFactory getEntityFactory();

  /**
   * Loads the whole model from the model directory if not yet done.
   *
   * @param modelDir the directory containing the model files
   * @param defaults model defaults, null if none
   * @throws ModelException
   */
  void loadModel(String modelDir, ModelDefaults defaults) throws ModelException;

  /**
   * Clears the model.
   */
  void clearModel();

  /**
   * Sets whether schema names should be mapped to the leading part of tablenames.
   *
   * @param mapSchemas true if map
   */
  void setSchemaNameMapped(boolean mapSchemas);

  /**
   * Returns whether schema names are mapped.
   *
   * @return true if mapped
   */
  boolean isSchemaNameMapped();

  /**
   * Refreshes the model.<br>
   * Checks for changes and reloads if necessary.
   */
  void refreshModel() throws ModelException;

  /**
   * Gets all cached entities.
   *
   * @return all entities loaded so far
   */
  Collection<Entity> getAllEntitites() throws ModelException;

  /**
   * Gets the entity by the mapfile name.
   *
   * @param fileName the name of the mapfile
   * @return the entity, null if no such entity
   */
  Entity getByFileName(String fileName) throws ModelException;

  /**
   * Gets the entity by its name.
   *
   * @param entityName the name
   * @return the entity, null if no such entity
   */
  Entity getByEntityName(String entityName) throws ModelException;

  /**
   * Gets the entity by its id.
   *
   * @param classId the class id
   * @return the entity, null if no such entity
   */
  Entity getByClassId(int classId) throws ModelException;

  /**
   * Loads an entity for a given mapfile name.<br>
   * Entities are cached so they are loaded and parsed only once.
   *
   * @param defaults the model defaults
   * @param fileName the name of the mapfile
   * @return the entity
   * @see WurbUtil#openReader(java.lang.String)
   * @throws ModelException if no such mapfile or parsing error
   */
  Entity loadByFileName(ModelDefaults defaults, String fileName) throws ModelException;

  /**
   * Gets all foreign keys.
   *
   * @return the foreign keys
   * @throws ModelException if model is inconsistent
   */
  Collection<ForeignKey> getForeignKeys() throws ModelException;

}

/*
 * Tentackle - https://tentackle.org.
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

import java.io.File;
import java.net.URL;
import java.util.Collection;


interface ModelHolder {
  Model INSTANCE = ModelManager.getInstance().getModel(ModelManager.DEFAULT_MODEL_NAME);
}


/**
 * A model, i.e. a named set of entities.
 * <p>
 * The default model is available via {@link #getInstance()}, other models via {@link ModelManager#getModel(String)}.
 *
 * @author harald
 */
public interface Model {

  /**
   * Default name of the index file holding the model names within a jar or classpath.<br>
   * Resides in {@code META-INF}.
   */
  String DEFAULT_INDEX_NAME = "MODEL-INDEX.LIST";


  /**
   * Gets the default model singleton.
   *
   * @return the model singleton
   */
  static Model getInstance() {
    return ModelHolder.INSTANCE;
  }


  /**
   * Gets the model name.<br>
   * The default model is named {@link ModelManager#DEFAULT_MODEL_NAME}.
   *
   * @return the model name
   */
  String getName();

  /**
   * Gets the entity factory of this model.
   *
   * @return the entity factory
   */
  EntityFactory getEntityFactory();

  /**
   * Sets the model defaults to apply when loading the model sources.
   *
   * @param modelDefaults the model defaults, null if none
   */
  void setModelDefaults(ModelDefaults modelDefaults);

  /**
   * Gets the model defaults.
   *
   * @return the model defaults, null if none
   */
  ModelDefaults getModelDefaults();

  /**
   * Sets the entity aliases.<br>
   * Aliases are alternative entity names used in relations or inheritance declarations.
   *
   * @param entityAliases the aliases, null if none
   */
  void setEntityAliases(EntityAliases entityAliases);

  /**
   * Gets the entity aliases.
   *
   * @return the aliases, null if none
   */
  EntityAliases getEntityAliases();

  /**
   * Sets the name of the model index list.<br>
   * The default is {@link #DEFAULT_INDEX_NAME}.
   *
   * @param indexName the name of the list containing the entity model resource names
   */
  void setIndexName(String indexName);

  /**
   * Gets the name of the model index list.
   *
   * @return the name of the list containing the entity model resource names
   */
  String getIndexName();

  /**
   * Loads the whole model from the model directory if not yet done.<br>
   * The directory is reloaded if files were added or removed.
   * Finally, the {@link CustomModelValidator}s are invoked, if the directory was (re)loaded.
   *
   * @param modelDir the name of the directory containing the model files
   * @param updateRelations true if update the relations, false if the model is still incomplete
   * @return the entity infos of the loaded model files
   * @throws ModelException if the model is inconsistent
   * @see #updateRelations()
   */
  Collection<EntityInfo> loadFromDirectory(String modelDir, boolean updateRelations) throws ModelException;

  /**
   * Loads an entity from a given URL.<br>
   * Entities are cached, so they are loaded and parsed only once.
   *
   * @param url the URL of the model file
   * @param updateRelations true if update the relations, false if the model is still incomplete
   * @return the entity info
   * @throws ModelException if the model is inconsistent
   * @see #updateRelations()
   */
  EntityInfo loadFromURL(URL url, boolean updateRelations) throws ModelException;

  /**
   * Loads the model from a jar file.<br>
   * Reads the model files listed in {@code META-INF/<indexName>}.
   *
   * @param file the jar file
   * @param updateRelations true if update the relations, false if the model is still incomplete
   * @return the entity infos of the loaded model files
   * @throws ModelException if the model is inconsistent
   * @see #getIndexName()
   * @see #updateRelations()
   */
  Collection<EntityInfo> loadFromJar(File file, boolean updateRelations) throws ModelException;

  /**
   * Loads the model from resources.<br>
   * Locates all {@code META-INF/<indexName>} resources and loads the model files listed therein.
   *
   * @param updateRelations true if update the relations, false if the model is still incomplete
   * @return the entity infos of the loaded model files
   * @throws ModelException if the model is inconsistent
   * @see #getIndexName()
   * @see #updateRelations()
   */
  Collection<EntityInfo> loadFromResources(boolean updateRelations) throws ModelException;

  /**
   * Updates the relations if not yet done while loading the model.
   *
   * @throws ModelException if the model is inconsistent
   * @see #loadFromURL(URL, boolean)
   * @see #loadFromDirectory(String, boolean)
   * @see #loadFromJar(File, boolean)
   * @see #loadFromResources(boolean)
   */
  void updateRelations() throws ModelException;

  /**
   * Clears the model.
   */
  void clearModel();

  /**
   * Sets whether schema names should be mapped to the leading part of table names.<br>
   * Used for backends that don't support schemas.
   * The schema-qualified table names are then folded into flat table names
   * ({@code schema.table} becomes {@code schema_table}).
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
   * Marks all model directories dirty, reloads them and updates the relations.
   *
   * @throws ModelException if the model is inconsistent
   */
  void refreshModel() throws ModelException;

  /**
   * Gets all cached entities.
   *
   * @return all entities loaded so far
   * @throws ModelException if the model is inconsistent
   */
  Collection<Entity> getAllEntities() throws ModelException;

  /**
   * Gets the entity by the URL of its model source.
   *
   * @param url the URL of the model source
   * @return the entity, null if no such entity
   * @throws ModelException if the model is inconsistent
   */
  Entity getByURL(URL url) throws ModelException;

  /**
   * Gets the entity by its table name (case-insensitive).
   *
   * @param tableName the table name (optional leading schema will be ignored)
   * @return the entity, null if no such entity
   * @throws ModelException if the model is inconsistent
   */
  Entity getByTableName(String tableName) throws ModelException;

  /**
   * Gets the entity by its name (case-insensitive).
   *
   * @param entityName the name
   * @return the entity, null if no such entity
   * @throws ModelException if the model is inconsistent
   */
  Entity getByEntityName(String entityName) throws ModelException;

  /**
   * Gets the entity by its class id.
   *
   * @param classId the class id
   * @return the entity, null if no such entity
   * @throws ModelException if the model is inconsistent
   */
  Entity getByClassId(int classId) throws ModelException;

  /**
   * Gets the model information for the given entity.
   *
   * @param entity the entity
   * @return the model info, never null
   * @throws ModelException if the model is inconsistent or no such entity
   */
  EntityInfo getEntityInfo(Entity entity) throws ModelException;

  /**
   * Gets all foreign keys.
   *
   * @return the foreign keys
   * @throws ModelException if the model is inconsistent
   */
  Collection<ForeignKey> getForeignKeys() throws ModelException;

}

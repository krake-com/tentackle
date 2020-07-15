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

package org.tentackle.model.impl;

import org.tentackle.common.Constants;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.Service;
import org.tentackle.common.Settings;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.CustomModelValidator;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.EntityFactory;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Index;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Integrity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelError;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.TrackType;
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlNameType;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Model implementation.
 *
 * @author harald
 */
@Service(Model.class)
public class ModelImpl implements Model {

  /** cached entities mapped by filepath. */
  private final Map<String,ModelEntity> mapByFilePath;

  /** cached entities mapped by entity name. */
  private final Map<String,ModelEntity> mapByEntityName;

  /** cached entities mapped by the tablename (for entities associated with a table). */
  private final Map<String,ModelEntity> mapByTableName;

  /** cached entities mapped by entity id (for entities associated with a class id). */
  private final Map<Integer,ModelEntity> mapByClassId;

  /** model dirs processed so far. */
  private final Map<String,ModelDirectory> modelDirs;   // <dirname,modeldir>

  /** the entity factory. */
  private final EntityFactory entityFactory;

  /** application specific model validators. */
  private final Collection<CustomModelValidator> customModelValidators;

  /** the foreign keys. */
  private Collection<ForeignKey> foreignKeys;

  /** map schema names. */
  private boolean mapSchemas;



  /**
   * Creates a model.
   */
  public ModelImpl() {
    entityFactory = createEntityFactory();
    mapByEntityName = new TreeMap<>();    // tree map for implicit sort by name
    mapByTableName = new HashMap<>();
    mapByClassId = new HashMap<>();
    mapByFilePath = new HashMap<>();
    modelDirs = new HashMap<>();
    customModelValidators = CustomModelValidator.loadValidators();
  }

  @Override
  public void setSchemaNameMapped(boolean mapSchemas) {
    this.mapSchemas = mapSchemas;
  }

  @Override
  public boolean isSchemaNameMapped() {
    return mapSchemas;
  }

  @Override
  public EntityFactory getEntityFactory() {
    return entityFactory;
  }

  @Override
  public synchronized void loadModel(String modelDir, ModelDefaults defaults, EntityAliases aliases) throws ModelException {

    ModelDirectory dir = modelDirs.get(modelDir);
    if (dir == null || dir.hasChanged()) {
      dir = createModelDirectory(modelDir, defaults, aliases);
      modelDirs.put(modelDir, dir);
      for (String filePath : dir.getFilePaths()) {
        loadByFilePath(filePath, defaults, aliases, false);
      }
      updateRelations(defaults, aliases);
    }

    if (defaults != null) {
      boolean updateRelations = false;
      if (defaults.getTrackType() != null) {
        for (ModelEntity modelEntity: mapByEntityName.values()) {
          Entity entity = modelEntity.getEntity();
          if (!entity.getOptions().noModelDefaults() &&
              defaults.getTrackType().ordinal() > entity.getOptions().getTrackType().ordinal()) {
            ((EntityImpl) entity).getOptions().setTrackType(defaults.getTrackType());
            updateRelations = true;
          }
        }
      }
      if (defaults.getDeletionCascaded() != null && defaults.getDeletionCascaded()) {
        for (ModelEntity modelEntity: mapByEntityName.values()) {
          Entity entity = modelEntity.getEntity();
          if (!entity.getOptions().noModelDefaults()) {
            for (Relation relation: entity.getRelations()) {
              if (relation.isComposite() && !relation.isDeletionCascaded()) {
                ((RelationImpl) relation).setDeletionCascaded(defaults.getDeletionCascaded());
                updateRelations = true;
              }
            }
          }
        }
      }
      if (updateRelations) {
        updateRelations(defaults, aliases);
      }
    }

    // indexes are parsed when the model is complete
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      EntityImpl entity = (EntityImpl) modelEntity.getEntity();
      for (Index idx: entity.getIndexes()) {
        IndexImpl index = (IndexImpl) idx;
        if (!index.isParsed()) {
          index.parse(entity);
        }
      }
      entity.validate();  // validate again
    }

    // run the application-specific validators
    for (CustomModelValidator validator: customModelValidators) {
      validator.validate(this);
    }
  }


  @Override
  public synchronized void clearModel() {
    mapByEntityName.clear();
    mapByTableName.clear();
    mapByClassId.clear();
    mapByFilePath.clear();
    modelDirs.clear();
  }

  @Override
  public void refreshModel() throws ModelException {
    for (ModelDirectory modelDir: modelDirs.values()) {
      modelDir.markDirty();
      loadModel(modelDir.getPath(), modelDir.getModelDefaults(), modelDir.getEntityAliases());
    }
  }

  @Override
  public synchronized Collection<Entity> getAllEntitites() throws ModelException {
    List<Entity> list = new ArrayList<>();

    boolean refreshed = false;
    do {
      for (ModelEntity modelEntity: mapByEntityName.values()) {
        if (modelEntity.hasChanged()) {
          refreshModel();
          list.clear();
          refreshed = true;
          break;
        }
        list.add(modelEntity.getEntity());
      }
    } while (refreshed);

    return list;
  }


  @Override
  public synchronized Entity getByFilePath(String filePath) throws ModelException {
    ModelEntity modelEntity = mapByFilePath.get(filePath);
    if (modelEntity != null && modelEntity.hasChanged()) {
      refreshModel();
      modelEntity = mapByFilePath.get(filePath);
    }
    return modelEntity == null ? null : modelEntity.getEntity();
  }


  @Override
  public synchronized Entity getByEntityName(String entityName) throws ModelException {
    String name = entityName.toLowerCase();
    ModelEntity modelEntity = mapByEntityName.get(name);
    if (modelEntity != null && modelEntity.hasChanged()) {
      refreshModel();
      modelEntity = mapByEntityName.get(name);
    }
    return modelEntity == null ? null : modelEntity.getEntity();
  }

  @Override
  public synchronized Entity getByTableName(String tableName) throws ModelException {
    tableName = tableName.toLowerCase();
    int ndx = tableName.indexOf('.');
    if (ndx >= 0) {
      tableName = tableName.substring(ndx + 1); // skip schema
    }
    ModelEntity modelEntity = mapByTableName.get(tableName);
    if (modelEntity != null && modelEntity.hasChanged()) {
      refreshModel();
      modelEntity = mapByTableName.get(tableName);
    }
    return modelEntity == null ? null : modelEntity.getEntity();
  }

  @Override
  public synchronized Entity getByClassId(int classId) throws ModelException {
    ModelEntity modelEntity = mapByClassId.get(classId);
    if (modelEntity != null && modelEntity.hasChanged()) {
      refreshModel();
      modelEntity = mapByClassId.get(classId);
    }
    return modelEntity == null ? null : modelEntity.getEntity();
  }


  @Override
  public Entity loadByFilePath(String filePath, ModelDefaults defaults, EntityAliases aliases) throws ModelException {
    return loadByFilePath(filePath, defaults, aliases, true);
  }


  /**
   * Loads an entity for a given mapfile path.<br>
   * Entities are cached so they are loaded and parsed only once.
   *
   * @param filePath the path of the mapfile
   * @param defaults optional model defaults, null if none
   * @param aliases optional entity aliases, null if none
   * @param updateRelations true if update the related entites in the model
   * @return the entity
   * @throws ModelException if no such mapfile or parsing error
   */
  protected synchronized Entity loadByFilePath(String filePath, ModelDefaults defaults, EntityAliases aliases,
                                               boolean updateRelations)
            throws ModelException {

    ModelEntity modelEntity = mapByFilePath.get(filePath);

    if (modelEntity != null && modelEntity.hasChanged()) {
      // check modification time
      modelEntity = null;    // force reload
    }

    if (modelEntity == null) {

      // load from file
      try {
        Entity entity = parseEntity(defaults, filePath);
        modelEntity = createModelEntity(entity, filePath);
      }
      catch (ModelException mex) {
        String msg = "parsing '" + filePath + "' failed:\n    " + ExceptionHelper.concatenateMessages(mex);
        throw new ModelException(msg, mex.getElement(), mex);
      }

      addModelEntity(modelEntity);

      foreignKeys = null;

      if (updateRelations) {
        updateRelations(defaults, aliases, modelEntity);
      }
    }

    return modelEntity.getEntity();
  }


  /**
   * Parses the given file and returns the created entity.
   *
   * @param defaults the model defaults, null if none
   * @param fileName the name of the file holding the model
   * @return the entity
   * @throws ModelException if parsing failed
   */
  protected Entity parseEntity(ModelDefaults defaults, String fileName) throws ModelException {
    StringBuilder modelSource = new StringBuilder();
    try (Reader reader = createReader(fileName)) {
      char[] buf = new char[1024];
      int len;
      while ((len = reader.read(buf)) != -1)  {
        modelSource.append(buf, 0, len);
      }
    }
    catch (IOException ex) {
      throw new ModelException("reading model '" + fileName + "' failed", ex);
    }
    return getEntityFactory().createEntity(modelSource.toString(), defaults);
  }


  /**
   * Adds an entity to the model.
   *
   * @param modelEntity the model entity
   * @throws ModelException if duplicate entities detected
   */
  protected void addModelEntity(ModelEntity modelEntity) throws ModelException {
    if (mapSchemas) {
      String tableName = modelEntity.getEntity().getTableName();
      if (tableName != null) {
        ((EntityImpl) modelEntity.getEntity()).setTableName(tableName.replace('.', '_'));
      }
    }

    String filePath = modelEntity.getFilePath();

    // append to maps
    mapByFilePath.put(filePath, modelEntity);    // must be new...
    ModelEntity oldModelEntity = mapByEntityName.put(modelEntity.getEntity().getName().toLowerCase(), modelEntity);
    if (modelEntity.getEntity().getClassId() != 0) {
      if (oldModelEntity != null && oldModelEntity.getEntity().getClassId() != modelEntity.getEntity().getClassId()) {
        throw new ModelException("duplicate entity " + modelEntity.getEntity().getName() + " in file " + filePath, modelEntity.getEntity());
      }
      oldModelEntity = mapByClassId.put(modelEntity.getEntity().getClassId(), modelEntity);
      if (oldModelEntity != null && !oldModelEntity.getEntity().getName().equals(modelEntity.getEntity().getName())) {
        throw new ModelException("duplicate entity id " + modelEntity.getEntity().getClassId() + " for " + modelEntity.getEntity().getName() +
                                 ", already assigned to " + oldModelEntity.getEntity().getName(), modelEntity.getEntity());
      }
    }

    String tableName = modelEntity.getEntity().getTableNameWithoutSchema();
    if (tableName != null) {
      oldModelEntity = mapByTableName.put(tableName, modelEntity);
      if (oldModelEntity != null && !oldModelEntity.getEntity().getName().equals(modelEntity.getEntity().getName())) {
        throw new ModelException("duplicate table name '" + modelEntity.getEntity().getTableNameWithoutSchema() + "' for " + modelEntity.getEntity().getName() +
                                 ", already assigned to " + oldModelEntity.getEntity().getName(), modelEntity.getEntity());
      }
    }
  }


  /**
   * Updates the inheritance links and relations of all entities in the model.
   *
   * @param defaults the optional model defaults
   * @param aliases the optional entity aliases
   * @throws ModelException if relations or inheritance misconfigured
   */
  protected void updateRelations(ModelDefaults defaults, EntityAliases aliases) throws ModelException {
    updateRelations(defaults, aliases, null);
  }

  /**
   * Updates all relations.
   *
   * @param defaults the optional model defaults
   * @param aliases the optional entity aliases
   * @param loadedModelEntity the entity that caused the update, null if none
   * @throws ModelException if relations or inheritance misconfigured
   */
  protected void updateRelations(ModelDefaults defaults, EntityAliases aliases, ModelEntity loadedModelEntity)
            throws ModelException {

    Set<ModelError> errors = new LinkedHashSet<>();
    Entity loadedEntity = loadedModelEntity == null ? null : loadedModelEntity.getEntity();

    // set inheritance entities links

    for (ModelEntity modelEntity: mapByEntityName.values()) {
      modelEntity.getEntity().getSubEntities().clear();
    }

    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      if (entity.getSuperEntityName() != null) {
        Entity superEntity = getByEntityName(translateAlias(aliases, entity.getSuperEntityName()));
        if (superEntity == null) {
          if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
            errors.add(new ModelError(entity,
                    "no such super entity '" + entity.getSuperEntityName() +
                    "' to be extended by '" + entity + "'"));
          }
        }
        else  {
          ((EntityImpl) entity).setSuperEntity(superEntity);
          if (superEntity.getInheritanceType() == InheritanceType.NONE &&
              (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
            errors.add(new ModelError(entity,
                    "super entity '" + superEntity + "' extended by '" + entity +
                    "' is not extensible (missing inheritance := ... ?)"));
          }
          superEntity.getSubEntities().add(entity);
        }
      }
    }

    // check for normtext provided
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      boolean normTextProvided = entity.getOptions().isNormTextProvided();
      if (!normTextProvided) {
        for (Attribute attribute: entity.getAttributes()) {
          if (attribute.getOptions().isPartOfNormText()) {
            normTextProvided = true;
            break;
          }
        }
        if (!normTextProvided) {
          for (Relation relation: entity.getRelations()) {
            if (relation.isPartOfNormText()) {
              normTextProvided = true;
              break;
            }
          }
        }

        if (normTextProvided) {
          // find topmost super entity not providing normtext
          Entity superEntity = entity.getSuperEntity();
          while (superEntity != null) {
            entity = superEntity;
            if (superEntity.getOptions().isNormTextProvided()) {
              normTextProvided = false;
              break;
            }
            superEntity = superEntity.getSuperEntity();
          }
          if (normTextProvided) {
            ((EntityOptionsImpl) entity.getOptions()).setNormTextProvided(true);
          }
        }
      }
    }

    // update relations
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      entity.getReferencingRelations().clear();
      entity.getCompositePaths().clear();
      entity.getDeepReferences().clear();
      entity.getDeepReferencesToComponents().clear();
      ((EntityImpl) entity).setDeeplyReferenced(false);

      for (Relation relation: entity.getRelations()) {

        ((RelationImpl) relation).setForeignRelation(null);

        Entity foreignEntity = getByEntityName(translateAlias(aliases, relation.getClassName()));
        if (foreignEntity == null) {
          if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
            // log error only if class is associated to the updated one
            errors.add(new ModelError(relation,
                    "no such entity: " + relation.getClassName()));
          }
        }
        else  {
          if (entity.getOptions().isProvided() && !foreignEntity.getOptions().isProvided()) {
            errors.add(new ModelError(relation,"provided entities cannot have relations to managed entities"));
          }
          ((RelationImpl) relation).setForeignEntity(foreignEntity);
          if (relation.getRelationType() == RelationType.LIST) {
            String attributeName;
            if (relation.getMethodName() != null) {
              attributeName = relation.getMethodName();     // selectBy<MethodName>
            }
            else {
              attributeName = relation.getEntity().getName() + "Id";
            }
            attributeName = StringHelper.firstToLower(attributeName);
            Attribute foreignAttribute = foreignEntity.getAttributeByJavaName(attributeName, true);
            if (foreignAttribute == null) {
              for (Relation fRel: foreignEntity.getRelations()) {
                if (fRel.getRelationType() == RelationType.OBJECT &&
                    relation.getEntity().getName().equals(fRel.getClassName())) {
                  // explicit backward relation given: try <namd>Id
                  attributeName = StringHelper.firstToLower(fRel.getName() + "Id");
                  foreignAttribute = foreignEntity.getAttributeByJavaName(attributeName, true);
                  break;
                }
              }
              if (foreignAttribute == null &&
                  relation.getMethodArgs().isEmpty() &&   // not an error if args given (means no simple link via id)
                   (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
                errors.add(new ModelError(relation, "no such attribute: " + attributeName + " in " + foreignEntity +
                                                    " referenced by " + entity));
              }
            }
            ((RelationImpl) relation).setForeignAttribute(foreignAttribute);

            if (relation.getNmName() != null) {
              Relation nmRelation = foreignEntity.getRelation(relation.getNmName(), false);
              if (nmRelation == null) {
                if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
                  errors.add(new ModelError(relation,
                       "no such nm-relation: " + relation.getNmName() + " in " + foreignEntity));
                }
              }
              else  {
                if ((nmRelation.isComposite() || nmRelation.getRelationType() == RelationType.LIST) &&
                    (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
                  errors.add(new ModelError(nmRelation,
                          "nm-relation " + nmRelation.getName() + " in " + foreignEntity +
                          " must be non-composite object"));
                }
                if (translateAlias(aliases, nmRelation.getClassName()).equals(entity.getName()) &&
                    (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
                  errors.add(new ModelError(nmRelation,
                          "nm-relation " + nmRelation.getName() + " in " + foreignEntity +
                          " points back to " + entity));
                }
                if (nmRelation.getSelectionType() == SelectionType.LAZY && !nmRelation.isSerialized()) {
                  // PdoRelations-Wurblet will generate --join which makes only sense if pdos are transferred via network
                  ((RelationImpl) nmRelation).setSerialized(true);      // retrieve from server
                  ((RelationImpl) nmRelation).setClearOnRemoteSave(true); // but don't send back to server
                }
                ((RelationImpl) relation).setNmRelation(nmRelation);
                ((RelationImpl) nmRelation).setDefiningNmRelation(relation);
              }
            }
          }
        }
      }
    }

    // update referencing relations
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      for (ModelEntity otherModelEntity: mapByEntityName.values()) {
        Entity otherEntity = otherModelEntity.getEntity();
        for (Relation relation: otherEntity.getRelations()) {
          Entity foreignEntity = relation.getForeignEntity();
          if (foreignEntity != null && foreignEntity.equals(entity)) {
            entity.getReferencingRelations().add(relation);
          }
        }
      }
    }

    // update foreign relations
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      for (Relation relation: entity.getReferencingRelations()) {
        if (relation.getForeignRelation() == null) {
          // find a relation with opposite attributes
          for (Relation otherRelation: relation.getForeignEntity().getTableRelations()) {
            if (Objects.equals(relation.getEntity(), otherRelation.getForeignEntity()) &&
                Objects.equals(relation.getAttribute(), otherRelation.getForeignAttribute())) {
              ((RelationImpl) relation).setForeignRelation(otherRelation);
              ((RelationImpl) otherRelation).setForeignRelation(relation);
              break;
            }
          }
        }
      }
    }

    // update composite paths and inspect model for root, rootid and rootclassid
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      boolean isRootEntity = true;
      for (Relation relation: entity.getAllReferencingRelations()) {
        if (relation.isComposite() && !entity.equals(relation.getEntity())) {
          isRootEntity = false;
          break;
        }
      }
      ((EntityImpl) entity).setRootEntityAccordingToModel(isRootEntity);
      if (isRootEntity) {
        for (Relation relation: entity.getAllRelations()) {
          if (relation.isComposite() && relation.getForeignEntity() != null) {
            List<Relation> compositePath = new ArrayList<>();
            compositePath.add(relation);
            updateCompositePath(compositePath, relation.getForeignEntity());
          }
        }
      }
    }

    // determine deep references and set rootid,rootclassid options
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      /*
       * The entity should provide a root class id if it is a composite of more than one root entities
       * and holds exactly one root attribute column (2 means N:M relation, for example).
       */
      Set<Entity> rootEntities = entity.getRootEntities();
      if (rootEntities.size() > 1 && entity.getRootAttribute() != null) {
        ((EntityImpl) entity).setProvidingRootClassIdAccordingToModel(true);
      }
      // or there is a non-composite reference from an entity which is not one of its root entities (deep reference)
      // and the entity does not provide a fixed rootClassId.
      // deep references would otherwise be rejected by the security manager because the
      // rootId/rootClassId would not match with the domain context's root entity.
      if (!rootEntities.isEmpty()) {
        // is a component of at least one root entity
        for (Relation relation: entity.getReferencingRelationsIncludingSubEntities()) {
          if (!relation.isComposite() &&
              (relation.getForeignRelation() == null || !relation.getForeignRelation().isComposite()) &&
              !rootEntities.containsAll(relation.getEntity().getRootEntities())) {
            // this is a deep reference
            ((RelationImpl) relation).setDeepReference(true);
            ((EntityImpl) entity).setDeeplyReferenced(true);
            entity.getDeepReferences().add(relation);
            for (List<Relation> compositePath: entity.getCompositePaths()) {
              for (Relation compositeRelation: compositePath) {
                ((EntityImpl) compositeRelation.getEntity()).setDeeplyReferenced(true);
              }
            }
            for (Entity rootEntity: rootEntities) {
              rootEntity.getDeepReferencesToComponents().add(relation);
            }
            if (entity.getRootEntity() == null) {
              // need rootclassid column
              ((EntityImpl) entity).setProvidingRootClassIdAccordingToModel(true);
            }
          }
        }
      }

      // The entity should provide a rootId if it is a composite with a relation path of more than one hops
      // or an OBJECT-relation
      for (List<Relation> compositePath: entity.getCompositePaths()) {
        if (compositePath.size() > 1 ||
            compositePath.get(compositePath.size() - 1).getRelationType() == RelationType.OBJECT) {
          ((EntityImpl) entity).setProvidingRootIdAccordingToModel(true);
          break;
        }
      }
    }

    // applies model defaults
    if (defaults != null) {

      for (ModelEntity modelEntity : mapByEntityName.values()) {
        Entity entity = modelEntity.getEntity();
        if (!entity.getOptions().noModelDefaults()) {
          if (defaults.getTrackType() != null &&
              defaults.getTrackType().ordinal() > entity.getOptions().getTrackType().ordinal()) {
            ((EntityOptionsImpl) entity.getOptions()).setTrackType(defaults.getTrackType());
          }
          if (Boolean.TRUE.equals(defaults.getRoot()) &&
              entity.isRootEntityAccordingToModel()) {
            ((EntityImpl) entity.getTopSuperEntity()).getOptions().setRootEntity(true);
          }
          if (Boolean.TRUE.equals(defaults.getRootClassId()) &&
              entity.isProvidingRootClassIdAccordingToModel()) {
              ((EntityImpl) entity.getTopSuperEntity()).getOptions().setRootClassIdProvided(true);
          }
          if (Boolean.TRUE.equals(defaults.getRootId()) &&
              entity.isProvidingRootIdAccordingToModel()) {
            ((EntityImpl) entity.getTopSuperEntity()).getOptions().setRootIdProvided(true);
          }
          if (defaults.getRemote() != null) {
            ((EntityOptionsImpl) entity.getOptions()).setRemote(defaults.getRemote());
          }
        }
      }
    }


    // add/remove rootId, rootClassId to/from model
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();

      if (entity.getOptions().isRootClassIdProvided()) {
        if (entity.getAttributeByColumnName(Constants.CN_ROOTCLASSID, true) == null) {
          ((EntityImpl) entity).getOptions().applyOption(EntityOptionsImpl.OPTION_ROOTCLASSID, Boolean.TRUE);
        }
      }
      else {
        Attribute rootClassAttribute = entity.getAttributeByColumnName(Constants.CN_ROOTCLASSID, false);
        if (rootClassAttribute != null) {
          entity.getAttributes().remove(rootClassAttribute);
        }
      }

      if (entity.getOptions().isRootIdProvided()) {
        if (entity.getAttributeByColumnName(Constants.CN_ROOTID, true) == null) {
          ((EntityImpl) entity).getOptions().applyOption(EntityOptionsImpl.OPTION_ROOTID, Boolean.TRUE);
        }
      }
      else  {
        Attribute rootAttribute = entity.getAttributeByColumnName(Constants.CN_ROOTID, false);
        if (rootAttribute != null) {
          entity.getAttributes().remove(rootAttribute);
        }
      }
    }

    // do some automatic settings and verifications
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      if (entity.getOptions().isCached() && !entity.isRootEntityAccordingToModel()) {
        errors.add(new ModelError(entity,"component " + entity + " cannot be cached"));
      }

      for (Relation relation: entity.getRelations()) {
        if (relation.getEntity().getOptions().getTrackType().isTracked()) {
          ((RelationImpl) relation).setTracked(true);
        }
        if (relation.getForeignEntity() != null) {
          if (relation.isComposite()) {
            if (relation.getRelationType() == RelationType.LIST) {
              // check if there is a lazy object relation back to me
              Relation parentRelation = relation.getForeignEntity().getRelation(entity.getName(), true);
              if (parentRelation == null) {
                // check by entity instead of name (in case relation name was changed)
                List<Relation> parentRelations = relation.getForeignEntity().getRelations(entity, true);
                if (parentRelations.size() == 1) {
                  parentRelation = parentRelations.get(0);
                }
              }
              if (parentRelation != null && parentRelation.getRelationType() == RelationType.OBJECT &&
                  parentRelation.getSelectionType() == SelectionType.LAZY) {
                // 1:N composite lazy relations without a linkMethod don't make sense
                ((RelationImpl) relation).setLinkMethodName("set" + StringHelper.firstToUpper(parentRelation.getName()));
                if (parentRelation.getEntity().getOptions().isRemote()) {
                  ((RelationImpl) parentRelation).setSerialized(true);
                }
              }
            }
            if (relation.getLinkMethodName() != null) {
              // if a link is defined for a composite (even for OBJECT), it will be referenced
              ((RelationImpl) relation).setReferenced(true);
            }
            if (!relation.isTracked() && entity.getIntegrity().isCompositesCheckedByDatabase()) {
              errors.add(new ModelError(relation,
                                        "untracked relation " + relation + " not allowed in conjunction with " +
                                        entity.getIntegrity() + "-integrity in " + entity));
            }
          }
          else if (relation.getRelationType() == RelationType.OBJECT &&
                   relation.getForeignEntity().getOptions().isCached() && !relation.isSerialized() &&
                   ((RelationImpl) relation).isSelectionTypeDetermined()) {
            ((RelationImpl) relation).setSelectionType(SelectionType.ALWAYS);
            ((RelationImpl) relation).setSelectionCached(true);
          }
        }
      }
    }


    // validate configuration depending on other entities
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      try {
        ((EntityImpl) entity).validateRelated();
      }
      catch (ModelException mex) {
        if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
          errors.add(new ModelError(entity, mex.getMessage() + " in " + entity));
        }
      }
    }

    // verify that columns are defined only once in the inheritance hierarchy
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      if (entity.getSuperEntity() == null && !entity.getInheritanceType().isMappingToNoTable()) {
        // check
        Map<String,Attribute> columnMap = new HashMap<>();  // map by column name
        Map<String,Attribute> nameMap = new HashMap<>();    // map by java name
        List<Attribute> allAttributes = new ArrayList<>(entity.getAttributes());
        for (Entity child: entity.getAllSubEntities()) {
          allAttributes.addAll(child.getAttributes());
        }
        for (Attribute attrib: allAttributes) {
          AttributeImpl attribute = (AttributeImpl) attrib;
          AttributeImpl oldAttribute = (AttributeImpl) nameMap.put(attribute.getName().toLowerCase(), attribute);
          if (oldAttribute != null && (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
            StringBuilder exMsg = new StringBuilder();
            exMsg.append("attribute '").append(attribute.getName()).append("' in ").
                  append(attribute.getEntity());
            exMsg.append(" already defined in ").append(oldAttribute.getEntity());
            if (oldAttribute.getSourceLine() != null) {
              exMsg.append("(").append(oldAttribute.getSourceLine()).append(")");
            }
            errors.add(new ModelError(attribute, exMsg.toString()));
          }
          AttributeImpl oldColumn = (AttributeImpl) columnMap.put(attribute.getColumnName().toLowerCase(), attribute);
          if (oldAttribute == null && // log same attrib only once
              oldColumn != null && (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
            StringBuilder exMsg = new StringBuilder();
            exMsg.append("column '").append(attribute.getColumnName()).append("' in ").
                  append(attribute.getEntity());
            exMsg.append(" already defined in ").append(oldColumn.getEntity());
            if (oldColumn.getSourceLine() != null) {
              exMsg.append("(").append(oldColumn.getSourceLine()).append(")");
            }
            exMsg.append(" as attribute '").append(attribute).append("'");
            errors.add(new ModelError(attribute, exMsg.toString()));
          }
        }
      }
    }

    // generate the table aliases.
    Map<String,Entity> aliasMap = new HashMap<>();
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      if (entity.getTableAlias() != null) {
        Entity dupAliasEntity = aliasMap.get(entity.getTableAlias());
        if (dupAliasEntity != null) {
          errors.add(new ModelError(dupAliasEntity,
                  "table alias '" + entity.getTableAlias() + "' defined more than once in " +
                  entity + " and " + dupAliasEntity));
        }
        else  {
          aliasMap.put(entity.getTableAlias(), entity);
        }
      }
    }

    // generate automatic alias if not specified in model
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      if (entity.getTableName() != null && entity.getTableAlias() == null) {
        // determine the meaningful letters: Start and end of camel case words
        StringBuilder letters = new StringBuilder();
        char lastChar = '_';
        boolean lastCharAppended = false;
        for (int i = 0; i < entity.getName().length(); i++) {
          char currentChar = entity.getName().charAt(i);
          // each uppercase letter and digit (2=to, 4=for, etc...)
          if (Character.isUpperCase(currentChar) || Character.isDigit(currentChar)) {
            letters.append(currentChar);
          }
          lastChar = currentChar;
        }
        if (letters.length() <= 1) {
          // append the last character, if only one Uppercase letter (e.g. Shipment -> st)
          letters.append(lastChar);
          lastCharAppended = true;
        }

        if (!applyTableAlias(aliasMap, entity, letters.toString().toLowerCase())) {
          // alias already exists

          if (!lastCharAppended) {
            // try with appended last character
            letters.append(lastChar);
            if (applyTableAlias(aliasMap, entity, letters.toString().toLowerCase())) {
              continue;   // yep! -> next entity
            }
          }

          // If lastchar was added (Shipment -> st), try the first 3 chars, then the first 4, finally 5 non-vocal chars
          letters.setLength(1);
          boolean nonVocalNameFound = false;
          for (int i = 1; i < entity.getName().length() && i < 6; i++) {
            char c = Character.toLowerCase(entity.getName().charAt(i));
            if (c != 'a' && c != 'e' && c != 'i' && c != 'o' && c != 'u') {
              letters.append(c);
              if (letters.length() >= 3) {
                if (applyTableAlias(aliasMap, entity, letters.toString().toLowerCase())) {
                  nonVocalNameFound = true;
                  break;
                }
              }
            }
          }

          if (!nonVocalNameFound) {
            // just take the first letter and count. This will always work
            String aliasPrefix = entity.getName().substring(0, 1).toLowerCase();
            int count = 1;
            while (!applyTableAlias(aliasMap, entity, aliasPrefix + count++)) ;
          }
        }
      }
    }

    // sets the inheritance level (ordinal)
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      int level = 0;
      Entity superEntity = entity.getSuperEntity();
      while (superEntity != null) {
        level++;
        superEntity = superEntity.getSuperEntity();
      }
      ((EntityImpl) entity).setOrdinal(level);
    }

    // sets the ordinals of attributes, relations and indexes
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      if (!entity.isAbstract()) {   // only leafs
        Entity[] entities = new Entity[modelEntity.getEntity().getOrdinal() + 1];
        List<Entity> superEntities = entity.getSuperEntities();
        // reverse the path in the array
        int superNdx = superEntities.size() - 1;
        for (int i = 0; i < entities.length; i++) {
          if (i < superEntities.size()) {
            entities[i] = superEntities.get(superNdx--);
          }
          else {
            entities[i] = entity;
          }
        }

        int attributeOrdinal = 0;
        int relationOrdinal = 0;
        int indexOrdinal = 0;
        for (Entity nextEntity : entities) {
          for (Attribute attribute : nextEntity.getAttributes()) {
            ((AttributeImpl) attribute).setOrdinal(attributeOrdinal++);
          }
          for (Relation relation : nextEntity.getRelations()) {
            ((RelationImpl) relation).setOrdinal(relationOrdinal++);
          }
          for (Index index : nextEntity.getIndexes()) {
            ((IndexImpl) index).setOrdinal(indexOrdinal++);
          }
        }
      }
    }

    // check hierarchies
    if (loadedEntity != null) {
      // check the hierarchy the loaded entity belongs to
      Entity topEntity = loadedEntity.getTopSuperEntity();
      validateInheritanceHierarchy(topEntity, topEntity.getInheritanceType(), topEntity.getOptions().getTrackType(),
                                   topEntity.getIntegrity(), errors);
      validateComponents(topEntity, topEntity.getIntegrity(), errors);
    }
    else  {
      // check that within each class hierarchy
      for (ModelEntity modelEntity: mapByEntityName.values()) {
        Entity entity = modelEntity.getEntity();
        validateInheritanceHierarchy(entity, entity.getInheritanceType(), entity.getOptions().getTrackType(),
                                     entity.getIntegrity(), errors);
        validateComponents(entity, entity.getIntegrity(), errors);
      }
    }

    if (!errors.isEmpty()) {
      throw new ModelException(errors);
    }
  }

  /**
   * Applies the table alias if unique and valid.
   *
   * @param aliasMap the map holding the aliases mapped to entities
   * @param entity the entity to apply the alias to
   * @param alias the alias
   * @return true if applied, false if alias already exists or is not valid for at least one backend
   */
  protected boolean applyTableAlias(Map<String,Entity> aliasMap, Entity entity, String alias) {
    if (aliasMap.get(alias) == null && isValidTableAlias(alias)) {
      // found a new alias
      ((EntityImpl) entity).setTableAlias(alias);
      aliasMap.put(alias, entity);
      return true;
    }
    return false;
  }

  /**
   * Checks if a table alias is a valid name for all backends.
   *
   * @param alias the table alias
   * @return true if ok
   */
  protected boolean isValidTableAlias(String alias) {
    boolean valid = true;

    for (Backend backend: getEntityFactory().getBackends()) {
      try {
        backend.assertValidName(SqlNameType.TABLE_ALIAS, alias);
      }
      catch (RuntimeException rex) {
        valid = false;
        break;
      }
    }

    return valid;
  }

  /**
   * Updates the composite path.
   *
   * @param compositePath the path that leads to given entity so far
   * @param entity the entity
   */
  protected void updateCompositePath(List<Relation> compositePath, Entity entity) {
    Relation lastRelation = compositePath.get(compositePath.size() - 1);
    if (lastRelation.getForeignAttribute() != null) {
      // the attribute actually may belong to a superclass
      entity = lastRelation.getForeignAttribute().getEntity();
    }
    entity.getCompositePaths().add(compositePath);
    for (Relation relation: entity.getAllRelations()) {
      if (relation.isComposite()) {
        Entity foreignEntity = relation.getForeignEntity();
        if (foreignEntity != null && !foreignEntity.equals(entity)) {
          List<Relation> newPath = new ArrayList<>(compositePath);
          newPath.add(relation);
          updateCompositePath(newPath, foreignEntity);
        }
      }
    }
  }

  @Override
  public synchronized Collection<ForeignKey> getForeignKeys() throws ModelException {

    if (foreignKeys == null) {
      Map<ForeignKey, ForeignKey> foreignKeyMap = new TreeMap<>(); // map to update composite flag and sort
      Set<ModelError> errors = new LinkedHashSet<>();

      for (ModelEntity modelEntity : mapByEntityName.values()) {
        Entity entity = modelEntity.getEntity();

        if (entity.getInheritanceType().isMappingToOwnTable()) {

          for (Relation relation : entity.getTableRelations()) {

            Attribute referencingAttribute = null;
            Entity referencingEntity = null;
            Attribute referencedAttribute = null;
            Entity referencedEntity = null;
            boolean composite = relation.isComposite();   // valid for OBJECT relations
            if (!composite) {
              Relation foreignRelation = relation.getForeignRelation();
              if (foreignRelation != null && foreignRelation.getRelationType() == RelationType.LIST &&
                  foreignRelation.isComposite()) {
                // the list relation is composite
                composite = true;
              }
            }

            if (relation.getAttribute() != null) {
              // object reference
              if (relation.getForeignEntity() == null) {
                errors.add(new ModelError(relation, "no foreign entity for " + relation));
              }
              else {
                referencingEntity = relation.getEntity();
                referencingAttribute = relation.getAttribute();
                referencedEntity = relation.getForeignEntity();
                referencedAttribute = referencedEntity.getAttributeByJavaName(Constants.AN_ID, true);
              }
            }
            else if (relation.getForeignAttribute() != null) {
              // list reference
              referencingEntity = relation.getForeignEntity();
              referencingAttribute = relation.getForeignAttribute();
              referencedEntity = entity;
              referencedAttribute = entity.getAttributeByJavaName(Constants.AN_ID, true);
            }
            else {
              if (!relation.getMethodArgs().isEmpty()) {
                continue;   // complex relation, classname + id, whatever -> no foreign key possible
              }
              errors.add(new ModelError(relation,
                      "broken foreign key relation " + relation + " in " + entity));
            }

            List<Entity> referencingEntities;
            if (referencingEntity != null && referencingEntity.getTableProvidingEntity() == null) {
              // PLAIN
              referencingEntities = referencingEntity.getLeafEntities();
            }
            else  {
              referencingEntities = new ArrayList<>();
              if (referencedEntity != null) {
                referencingEntities.add(referencingEntity);
              }
            }

            for (Entity refingEnt: referencingEntities) {
              if (!refingEnt.getOptions().isProvided() && referencingAttribute != null && referencedAttribute != null &&
                  (!composite && referencedAttribute.getEntity().getIntegrity().isRelatedCheckedByDatabase() ||
                   composite && referencedAttribute.getEntity().getIntegrity().isCompositesCheckedByDatabase())) {
                ForeignKey foreignKey = new ForeignKeyImpl(refingEnt, referencingAttribute, referencedEntity,
                                                           referencedAttribute, composite);
                ForeignKey oppositeForeignKey = foreignKeyMap.get(foreignKey);
                if (oppositeForeignKey != null) {
                  if (composite && !oppositeForeignKey.isComposite()) {
                    // upgrade to composite
                    ((ForeignKeyImpl) oppositeForeignKey).setComposite(true);
                  }
                }
                else {
                  // new
                  foreignKeyMap.put(foreignKey, foreignKey);
                }
              }
            }
          }

          // add constraints for MULTI table inheritance
          if (!entity.getOptions().isProvided() && entity.getSuperEntity() != null && entity.getHierarchyInheritanceType().isMappingToOwnTable()) {
            // is a subclass
            Entity topEntity = entity.getTopSuperEntity();
            Attribute idAttribute = topEntity.getAttributeByJavaName(Constants.AN_ID, false);
            ForeignKey foreignKey = new ForeignKeyImpl(entity, idAttribute, topEntity, idAttribute, false);
            foreignKeyMap.put(foreignKey, foreignKey);
          }
        }
      }

      if (!errors.isEmpty()) {
        throw new ModelException(errors);
      }

      foreignKeys = foreignKeyMap.values();
    }

    return foreignKeys;
  }


  /**
   * Creates the entity factory for this model.<br>
   * The method is invoked once from within the Model constructor.
   *
   * @return the entity factory
   */
  protected EntityFactory createEntityFactory() {
    return new EntityFactoryImpl();
  }

  /**
   * Creates a model directory object.
   *
   * @param modelDir the directory name
   * @param defaults optional model defaults, null if none
   * @param aliases optional entity aliases, null if none
   * @return the model directory
   * @throws ModelException if directory does not exist or is not a directory
   */
  protected ModelDirectory createModelDirectory(String modelDir, ModelDefaults defaults, EntityAliases aliases)
            throws ModelException {
    return new ModelDirectoryImpl(modelDir, defaults, aliases);
  }

  /**
   * Creates a model entity wrapper.
   *
   * @param entity the entity
   * @param fileName the model file name
   * @return the model entity
   * @throws ModelException if creation failed
   */
  protected ModelEntity createModelEntity(Entity entity, String fileName) throws ModelException {
    return new ModelEntityImpl(entity, fileName);
  }

  /**
   * Creates a reader for given name.
   *
   * @param name the file- or resource name
   * @return the reader
   * @throws ModelException if creating the reader failed (because file not found)
   */
  protected Reader createReader(String name) throws ModelException {
    try {
      return new BufferedReader(new InputStreamReader(new FileInputStream(name), Settings.getEncodingCharset()));
    }
    catch (IOException ex) {
      throw new ModelException("cannot open reader for " + name, ex);
    }
  }


  /**
   * Verifies that the inheritance, integrity and track type is the same within a given interitance hierarchy.<br>
   * Notice that concrete classes (the leafs of the hierarchy) must have InheritanceType.NONE defined!
   *
   * @param entity the entity to check
   * @param inheritanceType the inheritance type
   * @param trackType the tracking type
   * @param integrity the integrity type
   * @param errors the collected model errors
   */
  protected void validateInheritanceHierarchy(Entity entity, InheritanceType inheritanceType, TrackType trackType,
                                              Integrity integrity, Set<ModelError> errors) {

    if (entity.getOptions().getTrackType() != trackType) {
      errors.add(new ModelError(entity,
              entity + " has wrong track type " + entity.getOptions().getTrackType() +
              ", expected " + trackType));
    }

    if (entity.getIntegrity() != integrity) {
      errors.add(new ModelError(entity,
              entity + " has wrong integrity type " + entity.getIntegrity() +
              ", expected " + integrity));
    }

    if (!entity.getSubEntities().isEmpty()) {
      if (entity.getInheritanceType() != inheritanceType) {
        errors.add(new ModelError(entity,
                entity + " has wrong inheritance type " + entity.getInheritanceType() +
                ", expected " + inheritanceType));
      }
      for (Entity child: entity.getSubEntities()) {
        validateInheritanceHierarchy(child, inheritanceType, trackType, integrity, errors);
      }
    }
    // else: no sub entities. We don't check for InheritanceType.NONE because we may be in the middle
    // of development. We could issue warnings though, but there is no logger...
  }


  /**
   * Verifies that the integrity is the same within a given composite hierarchy.<br>
   *
   * @param entity the entity to check
   * @param integrity the integrity type
   * @param errors the collected model errors
   */
  protected void validateComponents(Entity entity, Integrity integrity, Set<ModelError> errors) {

    if (entity.getIntegrity() != integrity) {
      errors.add(new ModelError(entity,
              entity + " has wrong integrity type " + entity.getIntegrity() +
              ", expected " + integrity));
    }

    for (Relation relation: entity.getRelations()) {
      if (relation.isComposite()) {
        Entity foreignEntity = relation.getForeignEntity();
        if (foreignEntity != null && !foreignEntity.equals(entity)) {
          validateComponents(foreignEntity, integrity, errors);
        }
      }
    }
  }


  /**
   * Translates an entity name if it is an alias.
   *
   * @param aliases the entity aliases
   * @param name the original name
   * @return the possibly translated name
   */
  protected String translateAlias(EntityAliases aliases, String name) {
    if (aliases != null) {
      String mappedName = aliases.get(name);
      if (mappedName != null) {
        name = mappedName;
      }
    }
    return name;
  }

}

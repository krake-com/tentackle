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

package org.tentackle.model.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.tentackle.buildsupport.Settings;
import org.tentackle.common.BasicStringHelper;
import org.tentackle.common.Constants;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.Service;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityFactory;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Integrity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.TrackType;
import org.tentackle.sql.Backend;

/**
 * Model implementation.
 *
 * @author harald
 */
@Service(Model.class)
public class ModelImpl implements Model {

  /** cached entities mapped by filename. */
  private final Map<String,ModelEntity> mapByFileName;

  /** cached entities mapped by entity name. */
  private final Map<String,ModelEntity> mapByEntityName;

  /** cached entities mapped by entity id. */
  private final Map<Integer,ModelEntity> mapByClassId;

  /** model dirs processed so far. */
  private final Map<String,ModelDirectory> modelDirs;   // <dirname,modeldir>

  /** the entity factory. */
  private final EntityFactory entityFactory;

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
    mapByClassId = new HashMap<>();
    mapByFileName = new HashMap<>();
    modelDirs = new HashMap<>();
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
  public synchronized void loadModel(String modelDir, ModelDefaults defaults) throws ModelException {

    ModelDirectory dir = modelDirs.get(modelDir);
    if (dir == null || dir.hasChanged()) {
      dir = new ModelDirectory(modelDir, defaults);
      modelDirs.put(modelDir, dir);
      File[] files = dir.getFile().listFiles();
      if (files != null) {
        for (File file : files) {
          if (!file.isHidden()) {
            loadByFileName(defaults, file.getPath(), false);
          }
        }
      }
      updateRelations(defaults);
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
        updateRelations(defaults);
      }
    }
  }


  @Override
  public synchronized void clearModel() {
    mapByEntityName.clear();
    mapByClassId.clear();
    mapByFileName.clear();
    modelDirs.clear();
  }

  @Override
  public void refreshModel() throws ModelException {
    for (ModelDirectory modelDir: modelDirs.values()) {
      modelDir.markDirty();
      loadModel(modelDir.getFile().getAbsolutePath(), modelDir.getModelDefaults());
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
  public synchronized Entity getByFileName(String fileName) throws ModelException {
    ModelEntity modelEntity = mapByFileName.get(fileName);
    if (modelEntity != null && modelEntity.hasChanged()) {
      refreshModel();
      modelEntity = mapByFileName.get(fileName);
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
  public synchronized Entity getByClassId(int classId) throws ModelException {
    ModelEntity modelEntity = mapByClassId.get(classId);
    if (modelEntity != null && modelEntity.hasChanged()) {
      refreshModel();
      modelEntity = mapByClassId.get(classId);
    }
    return modelEntity == null ? null : modelEntity.getEntity();
  }


  @Override
  public Entity loadByFileName(ModelDefaults defaults, String fileName) throws ModelException {
    return loadByFileName(defaults, fileName, true);
  }


  /**
   * Loads an entity for a given mapfile name.<br>
   * Entities are cached so they are loaded and parsed only once.
   *
   * @param defaults the model defaults
   * @param fileName the name of the mapfile
   * @param updateRelations true if update the related entites in the model
   * @return the entity
   * @throws ModelException if no such mapfile or parsing error
   */
  protected synchronized Entity loadByFileName(ModelDefaults defaults, String fileName, boolean updateRelations)
            throws ModelException {

    ModelEntity modelEntity = mapByFileName.get(fileName);

    if (modelEntity != null) {
      // check modification time
      if (modelEntity.hasChanged()) {
        modelEntity = null;    // force reload
      }
    }

    if (modelEntity == null) {

      // load from file
      StringBuilder modelSource = new StringBuilder();

      try (Reader reader = createFileReader(fileName)) {
        char[] buf = new char[1024];
        int len;
        while ((len = reader.read(buf)) != -1)  {
          modelSource.append(buf, 0, len);
        }
      }
      catch (IOException ex) {
        throw new ModelException("reading model '" + fileName + "' failed", ex);
      }

      // parse the model source
      try {
        Entity entity = getEntityFactory().createEntity(modelSource.toString(), defaults);
        modelEntity = new ModelEntity(entity, fileName);
      }
      catch (ModelException mex) {
        String msg = "parsing '" + fileName + "' failed:\n    " + ExceptionHelper.concatenateMessages(mex);
        throw new ModelException(msg, mex);
      }

      addModelEntity(modelEntity);

      foreignKeys = null;

      if (updateRelations) {
        updateRelations(defaults, modelEntity);
      }
    }

    return modelEntity.getEntity();
  }


  /**
   * Adds an entity to the model.
   *
   * @param modelEntity the model entity
   */
  protected void addModelEntity(ModelEntity modelEntity) throws ModelException {
    if (mapSchemas) {
      String tableName = modelEntity.getEntity().getTableName();
      if (tableName != null) {
        ((EntityImpl) modelEntity.getEntity()).setTableName(tableName.replace('.', '_'));
      }
    }

    String fileName = modelEntity.getFileName();

    // append to maps
    mapByFileName.put(fileName, modelEntity);    // must be new...
    ModelEntity oldModelEntity = mapByEntityName.put(modelEntity.getEntity().getName().toLowerCase(), modelEntity);
    if (modelEntity.getEntity().getClassId() != 0) {
      if (oldModelEntity != null && oldModelEntity.getEntity().getClassId() != modelEntity.getEntity().getClassId()) {
        throw new ModelException("duplicate entity " + modelEntity.getEntity().getName() + " in file " + fileName, modelEntity.getEntity());
      }
      oldModelEntity = mapByClassId.put(modelEntity.getEntity().getClassId(), modelEntity);
      if (oldModelEntity != null && !oldModelEntity.getEntity().getName().equals(modelEntity.getEntity().getName())) {
        throw new ModelException("duplicate entity id " + modelEntity.getEntity().getClassId() + " for " + modelEntity.getEntity().getName() +
                                 ", already assigned to " + oldModelEntity.getEntity().getName(), modelEntity.getEntity());
      }
    }
  }


  /**
   * Updates the inheritance links and relations of all entities in the model.
   *
   * @param defaults the model defaults
   * @throws ModelException if relations or inheritance misconfigured
   */
  protected void updateRelations(ModelDefaults defaults) throws ModelException {
    updateRelations(defaults, null);
  }

  /**
   * Updates all relations.
   *
   * @param defaults the model defaults
   * @param loadedModelEntity the entity that caused the update, null if none
   * @throws ModelException if relations or inheritance misconfigured
   */
  protected void updateRelations(ModelDefaults defaults, ModelEntity loadedModelEntity) throws ModelException {

    StringBuilder exMsg = new StringBuilder();
    Set<Entity> exEntities = new HashSet<>();
    Entity loadedEntity = loadedModelEntity == null ? null : loadedModelEntity.getEntity();

    // set inheritance entities links

    for (ModelEntity modelEntity: mapByEntityName.values()) {
      modelEntity.getEntity().getSubEntities().clear();
    }

    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      if (entity.getSuperEntityName() != null) {
        Entity superEntity = getByEntityName(entity.getSuperEntityName());
        if (superEntity == null) {
          if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
            if (exMsg.length() > 0) {
              exMsg.append('\n');
            }
            exMsg.append("no such super entity '");
            exMsg.append(entity.getSuperEntityName());
            exMsg.append("' to be extended by '");
            exMsg.append(entity);
            exMsg.append("'");
            exEntities.add(entity);
          }
        }
        else  {
          ((EntityImpl) entity).setSuperEntity(superEntity);
          if (superEntity.getInheritanceType() == InheritanceType.NONE) {
            if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
              if (exMsg.length() > 0) {
                exMsg.append('\n');
              }
              exMsg.append("super entity '");
              exMsg.append(superEntity);
              exMsg.append("' extended by '");
              exMsg.append(entity);
              exMsg.append("' is not extensible (missing inheritance := ... ?)");
              exEntities.add(entity);
              exEntities.add(superEntity);
            }
          }
          superEntity.getSubEntities().add(entity);
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

        Entity foreignEntity = getByEntityName(relation.getClassName());
        if (foreignEntity == null) {
          if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
            // log error only if class is associated to the updated one
            if (exMsg.length() > 0) {
              exMsg.append('\n');
            }
            exMsg.append("no such entity: ");
            exMsg.append(relation.getClassName());
            exEntities.add(entity);
          }
        }
        else  {
          ((RelationImpl) relation).setForeignEntity(foreignEntity);
          if (relation.getRelationType() == RelationType.LIST) {
            String attributeName;
            if (relation.getMethodName() != null) {
              attributeName = relation.getMethodName();
            }
            else {
              attributeName = relation.getEntity().getName() + "Id";
            }
            attributeName = BasicStringHelper.firstToLower(attributeName);
            Attribute foreignAttribute = foreignEntity.getAttributeByJavaName(attributeName, true);
            if (foreignAttribute == null) {
              if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
                if (exMsg.length() > 0) {
                  exMsg.append('\n');
                }
                exMsg.append("no such attribute: ");
                exMsg.append(attributeName);
                exMsg.append(" in ");
                exMsg.append(foreignEntity);
                exEntities.add(entity);
                exEntities.add(foreignEntity);
              }
            }
            else  {
              ((RelationImpl) relation).setForeignAttribute(foreignAttribute);
            }

            if (relation.getNmName() != null) {
              Relation nmRelation = foreignEntity.getRelation(relation.getNmName(), false);
              if (nmRelation == null) {
                if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
                  if (exMsg.length() > 0) {
                    exMsg.append('\n');
                  }
                  exMsg.append("no such nm-relation: ");
                  exMsg.append(relation.getNmName());
                  exMsg.append(" in ");
                  exMsg.append(foreignEntity);
                  exEntities.add(entity);
                  exEntities.add(foreignEntity);
                }
              }
              else  {
                if (nmRelation.isComposite() || nmRelation.getRelationType() == RelationType.LIST) {
                  if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
                    if (exMsg.length() > 0) {
                      exMsg.append('\n');
                    }
                    exMsg.append("nm-relation ");
                    exMsg.append(nmRelation.getName());
                    exMsg.append(" in ");
                    exMsg.append(foreignEntity);
                    exMsg.append(" must be non-composite object");
                    exEntities.add(entity);
                    exEntities.add(foreignEntity);
                  }
                }
                if (nmRelation.getClassName().equals(entity.getName())) {
                  if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
                    if (exMsg.length() > 0) {
                      exMsg.append('\n');
                    }
                    exMsg.append("nm-relation ");
                    exMsg.append(nmRelation.getName());
                    exMsg.append(" in ");
                    exMsg.append(foreignEntity);
                    exMsg.append(" points back to ");
                    exMsg.append(entity);
                    exEntities.add(entity);
                    exEntities.add(foreignEntity);
                  }
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
          if (foreignEntity != null) {
            if (foreignEntity.equals(entity)) {
              entity.getReferencingRelations().add(relation);
            }
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
        if (relation.isComposite()) {
          isRootEntity = false;
          break;
        }
      }
      ((EntityImpl) entity).setRootEntityAccordingToModel(isRootEntity);
      if (isRootEntity) {
        for (Relation relation: entity.getAllRelations()) {
          if (relation.isComposite()) {
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
      /**
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

    // do some automatic settings
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      for (Relation relation: entity.getRelations()) {
        if (relation.getEntity().getOptions().getTrackType().isTracked()) {
          ((RelationImpl) relation).setTracked(true);
        }
        if (relation.isComposite()) {
          if (relation.getRelationType() == RelationType.LIST && relation.getLinkMethodName() == null) {
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
              ((RelationImpl) relation).setLinkMethodName("set" + BasicStringHelper.firstToUpper(parentRelation.getName()));
            }
          }
          if (relation.getLinkMethodName() != null) {
            // if a link is defined for a composite (even for OBJECT), it will be referenced
            ((RelationImpl) relation).setReferenced(true);
          }
          if (!relation.isTracked() && entity.getIntegrity().isCompositesCheckedByDatabase()) {
            if (exMsg.length() > 0) {
              exMsg.append('\n');
            }
            exMsg.append("untracked relation ").append(relation).append(" not allowed in conjunction with ").
                    append(entity.getIntegrity()).append("-integrity in ");
            exMsg.append(entity);
            exEntities.add(entity);
            exEntities.add(relation.getForeignEntity());
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
          if (exMsg.length() > 0) {
            exMsg.append('\n');
          }
          exMsg.append(mex.getMessage());
          exMsg.append(" in ");
          exMsg.append(entity);
          exEntities.add(entity);
          exEntities.addAll(mex.getEntities());
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
        List<Attribute> allAttributes = new ArrayList<>();
        allAttributes.addAll(entity.getAttributes());
        for (Entity child: entity.getAllSubEntities()) {
          allAttributes.addAll(child.getAttributes());
        }
        for (Attribute attribute: allAttributes) {
          Attribute oldAttribute = nameMap.put(attribute.getJavaName().toLowerCase(), attribute);
          if (oldAttribute != null && (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
            if (exMsg.length() > 0) {
              exMsg.append('\n');
            }
            exMsg.append("attribute '").append(attribute.getJavaName()).append("' in ").
                  append(attribute.getEntity());
            if (attribute.getSourceLine() != null) {
              exMsg.append("(").append(attribute.getSourceLine()).append(")");
            }
            exMsg.append(" already defined in ").append(oldAttribute.getEntity());
            if (oldAttribute.getSourceLine() != null) {
              exMsg.append("(").append(oldAttribute.getSourceLine()).append(")");
            }
            exEntities.add(entity);
            exEntities.add(attribute.getEntity());
            exEntities.add(oldAttribute.getEntity());
          }
          Attribute oldColumn = columnMap.put(attribute.getColumnName().toLowerCase(), attribute);
          if (oldAttribute == null && // log same attrib only once
              oldColumn != null && (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
            if (exMsg.length() > 0) {
              exMsg.append('\n');
            }
            exMsg.append("column '").append(attribute.getColumnName()).append("' in ").
                  append(attribute.getEntity());
            if (attribute.getSourceLine() != null) {
              exMsg.append("(").append(attribute.getSourceLine()).append(")");
            }
            exMsg.append(" already defined in ").append(oldColumn.getEntity());
            if (oldColumn.getSourceLine() != null) {
              exMsg.append("(").append(oldColumn.getSourceLine()).append(")");
            }
            exMsg.append(" as attribute '").append(attribute).append("'");
            exEntities.add(entity);
            exEntities.add(attribute.getEntity());
            exEntities.add(oldColumn.getEntity());
          }
        }
      }
    }

    /**
     * generate the table aliases.
     */
    Map<String,Entity> aliasMap = new HashMap<>();
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      if (entity.getTableAlias() != null) {
        Entity dupAliasEntity = aliasMap.get(entity.getTableAlias());
        if (dupAliasEntity != null) {
          if (exMsg.length() > 0) {
            exMsg.append('\n');
          }
          exMsg.append("table alias '").append(entity.getTableAlias()).append("' defined more than once in ");
          exMsg.append(entity).append(" and ").append(dupAliasEntity);
          exEntities.add(entity);
          exEntities.add(dupAliasEntity);
        }
        else  {
          aliasMap.put(entity.getTableAlias(), entity);
        }
      }
    }

    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      if (entity.getTableName() != null && entity.getTableAlias() == null) {
        // determine the meaningful letters: Start and end of camel case words
        StringBuilder letters = new StringBuilder();
        char lastChar = '_';
        for (int i=0; i < entity.getName().length(); i++) {
          char currentChar = entity.getName().charAt(i);
          if (Character.isUpperCase(currentChar)) {
            letters.append(currentChar);
          }
          lastChar = currentChar;
        }
        if (letters.length() <= 1) {
          letters.append(lastChar);
        }
        String alias = letters.toString().toLowerCase();

        if (aliasMap.get(alias) == null) {
          // found a new alias: check agains reserved word
          boolean aliasValid = true;
          for (Backend backend: getEntityFactory().getBackends()) {
            try {
              backend.assertValidName("table alias", alias);
            }
            catch (RuntimeException rex) {
              aliasValid = false;
              break;
            }
          }
          if (aliasValid) {
            ((EntityImpl) entity).setTableAlias(alias);
            aliasMap.put(alias, entity);
            continue;
          }
        }

        // just take the first letter and count. This will always work
        for (int count=1; ; count++) {
          String numberedAlias = alias.substring(0, 1) + count;
          if (aliasMap.get(numberedAlias) == null) {
            // found a new alias
            ((EntityImpl) entity).setTableAlias(numberedAlias);
            aliasMap.put(numberedAlias, entity);
            break;
          }
        }
      }
    }

    // sets the inheritance level
    for (ModelEntity modelEntity: mapByEntityName.values()) {
      Entity entity = modelEntity.getEntity();
      int level = 0;
      Entity superEntity = entity.getSuperEntity();
      while (superEntity != null) {
        level++;
        superEntity = superEntity.getSuperEntity();
      }
      ((EntityImpl) entity).setInheritanceLevel(level);
    }

    // check hierarchies
    if (loadedEntity != null) {
      // check the hierarchy the loaded entity belongs to
      Entity topEntity = loadedEntity.getTopSuperEntity();
      validateInheritanceHierarchy(topEntity, topEntity.getInheritanceType(), topEntity.getOptions().getTrackType(),
                                   topEntity.getIntegrity(), exMsg, exEntities);
      validateComponents(topEntity, topEntity.getIntegrity(), exMsg, exEntities);
    }
    else  {
      // check that within each class hierarchy
      for (ModelEntity modelEntity: mapByEntityName.values()) {
        Entity entity = modelEntity.getEntity();
        validateInheritanceHierarchy(entity, entity.getInheritanceType(), entity.getOptions().getTrackType(),
                                     entity.getIntegrity(), exMsg, exEntities);
        validateComponents(entity, entity.getIntegrity(), exMsg, exEntities);
      }
    }

    if (exMsg.length() > 0) {
      ModelException ex = new ModelException(exMsg.toString());
      ex.getEntities().addAll(exEntities);
      throw ex;
    }
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
        List<Relation> newPath = new ArrayList<>(compositePath);
        newPath.add(relation);
        updateCompositePath(newPath, relation.getForeignEntity());
      }
    }
  }

  @Override
  public synchronized Collection<ForeignKey> getForeignKeys() throws ModelException {

    if (foreignKeys == null) {
      Map<ForeignKey, ForeignKey> foreignKeyMap = new TreeMap<>(); // map to update composite flag and sort
      StringBuilder exMsg = new StringBuilder();
      Set<Entity> exEntities = new HashSet<>();

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
                if (exMsg.length() > 0) {
                  exMsg.append('\n');
                }
                exMsg.append("no foreign entity for ");
                exMsg.append(relation);
                exEntities.add(entity);
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
              if (exMsg.length() > 0) {
                exMsg.append('\n');
              }
              exMsg.append("broken relation ");
              exMsg.append(relation);
              exMsg.append(" in ");
              exMsg.append(entity);
              exEntities.add(entity);
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
              if (referencingAttribute != null && referencedAttribute != null &&
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
          if (entity.getSuperEntity() != null && entity.getHierarchyInheritanceType().isMappingToOwnTable()) {
            // is a subclass
            Entity topEntity = entity.getTopSuperEntity();
            Attribute idAttribute = topEntity.getAttributeByJavaName(Constants.AN_ID, false);
            ForeignKey foreignKey = new ForeignKeyImpl(entity, idAttribute, topEntity, idAttribute, false);
            foreignKeyMap.put(foreignKey, foreignKey);
          }
        }
      }

      if (exMsg.length() > 0) {
        ModelException ex = new ModelException(exMsg.toString());
        ex.getEntities().addAll(exEntities);
        throw ex;
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
   * Creates a reader for given filename.
   *
   * @param fileName the filename
   * @return the reader
   * @throws ModelException if creating the reader failed (because file not found)
   */
  protected Reader createFileReader(String fileName) throws ModelException {
    try {
      return new BufferedReader(new InputStreamReader(new FileInputStream(fileName), Settings.encodingCharset));
    }
    catch (IOException ex) {
      throw new ModelException("cannot open reader for " + fileName, ex);
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
   * @param exMsg execption message buffer
   * @param exEntities the entities related to the exception message
   * @return the error string, empty if all is ok
   */
  protected String validateInheritanceHierarchy(Entity entity, InheritanceType inheritanceType, TrackType trackType,
                                                Integrity integrity, StringBuilder exMsg, Set<Entity> exEntities) {

    if (entity.getOptions().getTrackType() != trackType) {
      if (exMsg.length() > 0) {
        exMsg.append('\n');
      }
      exMsg.append(entity);
      exMsg.append(" has wrong track type ");
      exMsg.append(entity.getOptions().getTrackType());
      exMsg.append(", expected ");
      exMsg.append(trackType);
      exEntities.add(entity);
    }
    if (entity.getIntegrity() != integrity) {
      if (exMsg.length() > 0) {
        exMsg.append('\n');
      }
      exMsg.append(entity);
      exMsg.append(" has wrong integrity type ");
      exMsg.append(entity.getIntegrity());
      exMsg.append(", expected ");
      exMsg.append(integrity);
      exEntities.add(entity);
    }

    if (entity.getSubEntities().isEmpty()) {
      if (entity.getInheritanceType() != InheritanceType.NONE) {
        if (exMsg.length() > 0) {
          exMsg.append('\n');
        }
        exMsg.append(entity);
        exMsg.append(" without child entities must have an inheritance type of NONE");
        exEntities.add(entity);
      }
    }
    else  {
      if (entity.getInheritanceType() != inheritanceType) {
        if (exMsg.length() > 0) {
          exMsg.append('\n');
        }
        exMsg.append(entity);
        exMsg.append(" has wrong inheritance type ");
        exMsg.append(entity.getInheritanceType());
        exMsg.append(", expected ");
        exMsg.append(inheritanceType);
        exEntities.add(entity);
      }
      for (Entity child: entity.getSubEntities()) {
        validateInheritanceHierarchy(child, inheritanceType, trackType, integrity, exMsg, exEntities);
      }
    }

    return exMsg.toString();
  }


  /**
   * Verifies that the integrity is the same within a given composite hierarchy.<br>
   *
   * @param entity the entity to check
   * @param integrity the integrity type
   * @param exMsg execption message buffer
   * @param exEntities the entities related to the exception message
   * @return the error string, empty if all is ok
   */
  protected String validateComponents(Entity entity, Integrity integrity, StringBuilder exMsg, Set<Entity> exEntities) {

    if (entity.getIntegrity() != integrity) {
      if (exMsg.length() > 0) {
        exMsg.append('\n');
      }
      exMsg.append(entity);
      exMsg.append(" has wrong integrity type ");
      exMsg.append(entity.getIntegrity());
      exMsg.append(", expected ");
      exMsg.append(integrity);
      exEntities.add(entity);
    }

    for (Relation relation: entity.getRelations()) {
      if (relation.isComposite()) {
        validateComponents(relation.getForeignEntity(), integrity, exMsg, exEntities);
      }
    }

    return exMsg.toString();
  }

}

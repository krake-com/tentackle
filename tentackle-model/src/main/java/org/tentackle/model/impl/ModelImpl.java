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
import org.tentackle.common.FileHelper;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.Settings;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.CustomModelValidator;
import org.tentackle.model.Deferrability;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.EntityFactory;
import org.tentackle.model.EntityInfo;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Index;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Integrity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelDirectory;
import org.tentackle.model.ModelError;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelUtilities;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.TrackType;
import org.tentackle.model.parse.Document;
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlNameType;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Model implementation.
 *
 * @author harald
 */
@Service(Model.class)
public class ModelImpl implements Model {

  private static final String PROPS_PREFIX = "tentackle.";     // system properties prefix
  private static final String PROPS_MAP_SCHEMAS = PROPS_PREFIX + "mapSchemas";
  private static final String PROPS_MODEL_DEFAULTS = PROPS_PREFIX + "modelDefaults";
  private static final String PROPS_ENTITY_ALIASES = PROPS_PREFIX + "entityAliases";
  private static final String PROPS_INDEX_NAME = PROPS_PREFIX + "indexName";


  /** the model name. */
  private final String name;

  /** cached entities mapped by URL. */
  private final Map<String, EntityInfo> urlToEntityInfoMap;

  /** source infos mapped by entity (to detect entities defined in more than one file). */
  private final Map<Entity, SourceInfo> entityToSourceInfoMap;

  /** cached entities mapped by entity name. */
  private final Map<String, EntityInfo> nameToEntityInfoMap;

  /** cached entities mapped by the table name (for entities associated with a table). */
  private final Map<String, EntityInfo> tableToEntityInfoMap;

  /** cached entities mapped by class id (for entities associated with a class id). */
  private final Map<Integer, EntityInfo> classidToEntityInfoMap;

  /** model dirs processed so far. */
  private final Map<String, ModelDirectory> modelDirs;   // <dirname, modeldir>

  /** the entity factory. */
  private final EntityFactory entityFactory;

  /** application-specific model validators. */
  private final Collection<CustomModelValidator> customModelValidators;

  /** the foreign keys. */
  private Collection<ForeignKey> foreignKeys;

  /** map schema names. */
  private boolean mapSchemas;

  /** the optional model defaults. */
  private ModelDefaults modelDefaults;

  /** the optional entity aliases. */
  private EntityAliases entityAliases;

  /** the name of the model index list. */
  private String indexName = DEFAULT_INDEX_NAME;


  /**
   * Creates a model.
   *
   * @param name the model name
   * @throws ModelException if the model defaults or entity aliases given as system properties are malformed
   */
  public ModelImpl(String name) throws ModelException {
    this.name = name;
    entityFactory = createEntityFactory();
    nameToEntityInfoMap = new TreeMap<>();    // tree map for implicit sort by name
    tableToEntityInfoMap = new HashMap<>();
    classidToEntityInfoMap = new HashMap<>();
    urlToEntityInfoMap = new HashMap<>();
    entityToSourceInfoMap = new HashMap<>();
    modelDirs = new HashMap<>();
    customModelValidators = CustomModelValidator.loadValidators();

    /*
     * Presets model configuration from the runtime properties.
     * This is provided to easily configure unit tests as an alternative
     * to extending DbTestUtilities in tentackle-test-pdo.
     * Example:
     *
       <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>3.1.2</version>
          <configuration>
            <argLine>-Dtentackle.mapSchemas=true</argLine>
          </configuration>
        </plugin>
     */
    String prop = System.getProperty(PROPS_MAP_SCHEMAS);
    if (prop != null) {
      mapSchemas = Boolean.parseBoolean(prop);
    }
    prop = System.getProperty(PROPS_MODEL_DEFAULTS);
    if (prop != null) {
      modelDefaults = new ModelDefaults(prop);
    }
    prop = System.getProperty(PROPS_ENTITY_ALIASES);
    if (prop != null) {
      entityAliases = new EntityAliases(prop);
    }
    prop = System.getProperty(PROPS_INDEX_NAME);
    if (prop != null) {
      indexName = prop;
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return getName();
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
  public ModelDefaults getModelDefaults() {
    return modelDefaults;
  }

  @Override
  public void setModelDefaults(ModelDefaults modelDefaults) {
    this.modelDefaults = modelDefaults;
  }

  @Override
  public EntityAliases getEntityAliases() {
    return entityAliases;
  }

  @Override
  public void setEntityAliases(EntityAliases entityAliases) {
    this.entityAliases = entityAliases;
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public void setIndexName(String indexName) {
    this.indexName = indexName;
  }

  @Override
  public EntityFactory getEntityFactory() {
    return entityFactory;
  }

  @Override
  public synchronized Collection<EntityInfo> loadFromDirectory(String modelDir, boolean updateRelations) throws ModelException {

    Collection<EntityInfo> entityInfos = new ArrayList<>();
    boolean modelChanged = false;

    ModelDirectory dir = modelDirs.get(modelDir);
    if (dir == null || dir.hasChanged()) {
      modelChanged = true;
      dir = createModelDirectory(modelDir);
      modelDirs.put(modelDir, dir);
      for (URL url : dir.getURLs()) {
        entityInfos.add(loadFromURL(url, false));
      }
      if (updateRelations) {
        updateRelations();
      }
    }

    // update track type and cascaded delete according to model defaults
    // (the track type is only raised, never lowered by the defaults)
    boolean updateRelationsNecessary = false;
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      ModelDefaults defaults = entityInfo.getModelDefaults();
      Entity entity = entityInfo.getEntity();
      if (defaults != null && !entity.getOptions().noModelDefaults()) {
        if (defaults.getTrackType() != null &&
            defaults.getTrackType().ordinal() > entity.getOptions().getTrackType().ordinal()) {
          ((EntityImpl) entity).getOptions().setTrackType(defaults.getTrackType());
          updateRelationsNecessary = true;
        }

        if (Boolean.TRUE.equals(defaults.getDeletionCascaded())) {
          for (Relation relation : entity.getRelations()) {
            // object relations are always deleted cascaded and must not be marked explicitly
            if (relation.isComposite() && relation.getRelationType() == RelationType.LIST &&
                !relation.isDeletionCascaded()) {
              ((RelationImpl) relation).setDeletionCascaded(true);
              updateRelationsNecessary = true;
            }
          }
        }
      }
    }

    if (updateRelationsNecessary && updateRelations) {
      updateRelations();
    }

    if (modelChanged) {
      // run the application-specific validators
      for (CustomModelValidator validator : customModelValidators) {
        validator.validate(this);
      }
    }

    return entityInfos;
  }

  @Override
  public synchronized EntityInfo loadFromURL(URL url, boolean updateRelations) throws ModelException {

    EntityInfo entityInfo = urlToEntityInfoMap.get(url.toString());

    if (entityInfo != null && entityInfo.hasChanged()) {
      // check modification time
      entityInfo = null;    // force reload
    }

    if (entityInfo == null) {
      // load from URL
      try {
        entityInfo = createEntityInfo(modelDefaults, url);
      }
      catch (ModelException mex) {
        String msg = "parsing '" + url + "' failed:\n    " + ExceptionHelper.concatenateMessages(mex);
        throw new ModelException(msg, mex.getElement(), mex);
      }

      addEntityInfo(entityInfo);

      foreignKeys = null;

      if (updateRelations) {
        updateRelations(entityInfo);
      }
    }

    return entityInfo;
  }

  @Override
  public Collection<EntityInfo> loadFromJar(File file, boolean updateRelations) throws ModelException {
    Collection<EntityInfo> entityInfos = new ArrayList<>();
    String metaInfIndexName = Constants.META_INF + getIndexName();
    try (JarFile jarFile = new JarFile(file)) {
      JarEntry indexEntry = jarFile.getJarEntry(metaInfIndexName);
      if (indexEntry != null) {
        String urlLead = "jar:file:" + file.getCanonicalPath() + "!/";
        try (BufferedReader indexReader = new BufferedReader(new InputStreamReader(jarFile.getInputStream(indexEntry), Settings.getEncodingCharset()))) {
          String line;
          while ((line = indexReader.readLine()) != null) {
            JarEntry modelEntry = jarFile.getJarEntry(line);
            if (modelEntry != null) {
              URL url = new URI(urlLead + modelEntry.getName()).toURL();
              entityInfos.add(loadFromURL(url, false));
            }
            else {
              throw new ModelException("no such model resource '" + line + "' in " + file);
            }
          }
        }
      }
      else {
        throw new ModelException("missing " + metaInfIndexName + " in " + file);
      }
    }
    catch (URISyntaxException | IOException e) {
      throw new ModelException("cannot load model from jar " + file, e);
    }

    if (updateRelations) {
      updateRelations();
    }

    return entityInfos;
  }

  @Override
  public Collection<EntityInfo> loadFromResources(boolean updateRelations) throws ModelException {
    Collection<EntityInfo> entityInfos = new ArrayList<>();
    for (Map.Entry<String, URL> entry : ServiceFactory.getServiceFinder(Constants.META_INF).findServiceConfigurations(getIndexName()).entrySet()) {
      // URL forms:
      // from jimage:       jrt:/com.example.myapp.client/META-INF/MODEL-INDEX.LIST
      // from artifact:     jar:file:/home/harald/.m2/repository/com/example/myapp-client/1.0/myapp-client-1.0.jar!/META-INF/MODEL-INDEX.LIST
      // from classfiles:   file:/applic/src/myapp/client/target/classes/META-INF/MODEL-INDEX.LIST
      String urlName = entry.getValue().toString();
      int ndx = urlName.indexOf(Constants.META_INF);
      if (ndx >= 0) {
        urlName = urlName.substring(0, ndx) + entry.getKey();   // "xxx:..../" + "META-INF/model/blah.map"
        try {
          URL url = new URI(urlName).toURL();
          // order is not important, since we don't update the relations for each loaded entity
          entityInfos.add(loadFromURL(url, false));
        }
        catch (URISyntaxException | MalformedURLException e) {
          throw new ModelException("cannot create model URL for " + entry.getKey() + " in " + entry.getValue(), e);
        }
      }
      else {
        throw new ModelException("unexpected url: " + urlName);
      }
    }
    if (updateRelations) {
      updateRelations();
    }
    return entityInfos;
  }

  @Override
  public void updateRelations() throws ModelException {
    updateRelations(null);

    ModelDefaults defaults = getModelDefaults();
    boolean setDeferrability = defaults != null && defaults.getDeferrability() != null &&
                               defaults.getDeferrability() != Deferrability.NOT_DEFERRABLE;

    // indexes are parsed when the model is complete
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      EntityImpl entity = (EntityImpl) entityInfo.getEntity();
      for (Index idx : entity.getIndexes()) {
        IndexImpl index = (IndexImpl) idx;
        if (!index.isParsed()) {
          index.parse(entity);
          // only unique indexes can be deferrable, and neither filtered nor function-based ones
          if (setDeferrability && index.isUnique() && index.getFilterCondition() == null && !index.isFunctionBased()) {
            index.applyDefaultDeferrability(Deferrability.DEFERRABLE_IMMEDIATE);
          }
        }
      }
      entity.validate();  // validate again
    }
  }

  @Override
  public synchronized void clearModel() {
    nameToEntityInfoMap.clear();
    tableToEntityInfoMap.clear();
    classidToEntityInfoMap.clear();
    urlToEntityInfoMap.clear();
    entityToSourceInfoMap.clear();
    modelDirs.clear();
    foreignKeys = null;
  }

  @Override
  public void refreshModel() throws ModelException {
    for (ModelDirectory modelDir: modelDirs.values()) {
      modelDir.markDirty();
      loadFromDirectory(modelDir.getPath(), false);
    }
    updateRelations();
  }

  @Override
  public synchronized Collection<Entity> getAllEntities() throws ModelException {
    List<Entity> list = new ArrayList<>();

    boolean refreshed;
    do {
      refreshed = false;    // must be cleared for each attempt, else the loop would never terminate
      for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
        if (entityInfo.hasChanged()) {
          refreshModel();
          list.clear();
          refreshed = true;
          break;
        }
        list.add(entityInfo.getEntity());
      }
    } while (refreshed);

    return list;
  }

  @Override
  public synchronized Entity getByURL(URL url) throws ModelException {
    String urlStr = url.toString();
    EntityInfo entityInfo = urlToEntityInfoMap.get(urlStr);
    if (entityInfo != null && entityInfo.hasChanged()) {
      refreshModel();
      entityInfo = urlToEntityInfoMap.get(urlStr);
    }
    return entityInfo == null ? null : entityInfo.getEntity();
  }

  @Override
  public synchronized Entity getByEntityName(String entityName) throws ModelException {
    String name = entityName.toLowerCase(Locale.ROOT);
    EntityInfo entityInfo = nameToEntityInfoMap.get(name);
    if (entityInfo != null && entityInfo.hasChanged()) {
      refreshModel();
      entityInfo = nameToEntityInfoMap.get(name);
    }
    return entityInfo == null ? null : entityInfo.getEntity();
  }

  @Override
  public synchronized Entity getByTableName(String tableName) throws ModelException {
    tableName = tableName.toLowerCase(Locale.ROOT);
    int ndx = tableName.indexOf('.');
    if (ndx >= 0) {
      tableName = tableName.substring(ndx + 1); // skip schema
    }
    EntityInfo entityInfo = tableToEntityInfoMap.get(tableName);
    if (entityInfo != null && entityInfo.hasChanged()) {
      refreshModel();
      entityInfo = tableToEntityInfoMap.get(tableName);
    }
    return entityInfo == null ? null : entityInfo.getEntity();
  }

  @Override
  public synchronized Entity getByClassId(int classId) throws ModelException {
    EntityInfo entityInfo = classidToEntityInfoMap.get(classId);
    if (entityInfo != null && entityInfo.hasChanged()) {
      refreshModel();
      entityInfo = classidToEntityInfoMap.get(classId);
    }
    return entityInfo == null ? null : entityInfo.getEntity();
  }

  @Override
  public EntityInfo getEntityInfo(Entity entity) throws ModelException {
    String name = entity.getName().toLowerCase(Locale.ROOT);
    EntityInfo entityInfo = nameToEntityInfoMap.get(name);
    if (entityInfo != null && entityInfo.hasChanged()) {
      refreshModel();
      entityInfo = nameToEntityInfoMap.get(name);
    }
    if (entityInfo == null) {
      throw new ModelException("no such entity " + entity);
    }
    return entityInfo;
  }


  @Override
  public synchronized Collection<ForeignKey> getForeignKeys() throws ModelException {

    if (foreignKeys == null) {
      Map<ForeignKey, ForeignKey> foreignKeyMap = new TreeMap<>(); // map to update composite flag and sort
      Set<ModelError> errors = new LinkedHashSet<>();

      for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
        Entity entity = entityInfo.getEntity();

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
                if (relation.getEmbeddingEntity() != null) {
                  referencingEntity = relation.getEmbeddingEntity();
                  if (relation.getMethodArgs().isEmpty()) {
                    errors.add(new ModelError(relation, "broken foreign key relation " + relation + " in embedded " + entity));
                  }
                  referencingAttribute = relation.getMethodArgs().getFirst().getAttribute();
                }
                else {
                  referencingEntity = relation.getEntity();
                  referencingAttribute = relation.getAttribute();
                }
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
              errors.add(new ModelError(relation, "broken foreign key relation " + relation + " in " + entity));
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

      // apply the model defaults' deferrability: foreign keys are not user-declared,
      // so this is the only way to set their deferrability, always as DEFERRABLE_IMMEDIATE
      // (deferred is never propagated automatically, see ModelDefaults#getDeferrability())
      for (ForeignKey foreignKey : foreignKeys) {
        Entity referencingEntity = foreignKey.getReferencingEntity();
        if (!referencingEntity.getOptions().noModelDefaults()) {
          EntityInfo referencingEntityInfo = nameToEntityInfoMap.get(referencingEntity.getName().toLowerCase(Locale.ROOT));
          ModelDefaults defaults = referencingEntityInfo != null ? referencingEntityInfo.getModelDefaults() : null;
          if (defaults != null && defaults.getDeferrability() != null && defaults.getDeferrability() != Deferrability.NOT_DEFERRABLE) {
            ((ForeignKeyImpl) foreignKey).setDeferrability(Deferrability.DEFERRABLE_IMMEDIATE);
          }
        }
      }
    }

    return foreignKeys;
  }


  /**
   * Adds an entity to the model.<br>
   * If schema names are mapped, the schema-qualified table name is folded into a flat table name
   * ({@code schema.table} becomes {@code schema_table}).
   *
   * @param entityInfo the entity info
   * @throws ModelException if duplicate entities detected
   */
  protected void addEntityInfo(EntityInfo entityInfo) throws ModelException {
    if (mapSchemas) {
      String tableName = entityInfo.getEntity().getTableName();
      if (tableName != null) {
        ((EntityImpl) entityInfo.getEntity()).setTableName(tableName.replace('.', '_'));
      }
    }

    URL url = entityInfo.getURL();

    // append to maps
    urlToEntityInfoMap.put(url.toString(), entityInfo);    // must be new...

    SourceInfo sourceInfo = entityInfo.getEntity().getSourceInfo();
    if (sourceInfo != null) {
      SourceInfo otherSourceInfo = entityToSourceInfoMap.put(entityInfo.getEntity(), sourceInfo);
      if (otherSourceInfo != null && !otherSourceInfo.equals(sourceInfo)) {
        throw new ModelException("entity " + entityInfo.getEntity() + " defined from more than one model file:\n" +
                                 otherSourceInfo + "\n" + sourceInfo);
      }
    }

    EntityInfo oldEntityInfo = nameToEntityInfoMap.put(entityInfo.getEntity().getName().toLowerCase(Locale.ROOT), entityInfo);
    if (entityInfo.getEntity().getClassId() != 0) {
      if (oldEntityInfo != null && oldEntityInfo.getEntity().getClassId() != entityInfo.getEntity().getClassId()) {
        throw new ModelException("duplicate entity " + entityInfo.getEntity().getName() + " in " + url, entityInfo.getEntity());
      }
      oldEntityInfo = classidToEntityInfoMap.put(entityInfo.getEntity().getClassId(), entityInfo);
      if (oldEntityInfo != null && !oldEntityInfo.getEntity().getName().equals(entityInfo.getEntity().getName())) {
        throw new ModelException("duplicate entity id " + entityInfo.getEntity().getClassId() + " for " + entityInfo.getEntity().getName() +
                                 ", already assigned to " + oldEntityInfo.getEntity().getName(), entityInfo.getEntity());
      }
    }

    String tableName = entityInfo.getEntity().getTableNameWithoutSchema();
    if (tableName != null) {
      oldEntityInfo = tableToEntityInfoMap.put(tableName, entityInfo);
      if (oldEntityInfo != null && !oldEntityInfo.getEntity().getName().equals(entityInfo.getEntity().getName())) {
        throw new ModelException("duplicate table name '" + entityInfo.getEntity().getTableNameWithoutSchema() + "' for " + entityInfo.getEntity().getName() +
                                 ", already assigned to " + oldEntityInfo.getEntity().getName(), entityInfo.getEntity());
      }
    }
  }

  /**
   * Updates all relations.
   *
   * @param loadedEntityInfo the entity that caused the update, null if none
   * @throws ModelException if the relations or inheritance is misconfigured
   */
  protected void updateRelations(EntityInfo loadedEntityInfo) throws ModelException {

    Set<ModelError> errors = new LinkedHashSet<>();
    Entity loadedEntity = loadedEntityInfo == null ? null : loadedEntityInfo.getEntity();

    // rebuild the super/sub entity links

    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      EntityImpl entity = (EntityImpl) entityInfo.getEntity();
      entity.getSubEntities().clear();
      entity.setSuperEntity(null);   // links are rebuilt below, don't keep stale ones
    }

    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      if (entity.getSuperEntityName() != null) {
        Entity superEntity;
        try {
          superEntity = getByEntityName(translateAlias(entity.getSuperEntityName()));
        }
        catch (ModelException mx) {
          errors.add(new ModelError(mx));
          continue;
        }
        if (superEntity == null) {
          if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
            errors.add(new ModelError(entity,
                    "no such super entity '" + entity.getSuperEntityName() +
                    "' to be extended by '" + entity + "'"));
          }
        }
        else if (createsInheritanceCycle(entity, superEntity)) {
          // never link a cycle: walking up the hierarchy would not terminate
          errors.add(new ModelError(entity,
                  "cyclic inheritance: '" + entity + "' extends '" + superEntity +
                  "', which already extends '" + entity + "'"));
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
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      boolean normTextProvided = entity.getOptions().isNormTextProvided();
      if (!normTextProvided) {
        for (Attribute attribute: entity.getAttributes()) {
          if (attribute.getOptions().isPartOfNormText() && !entity.isEmbedded()) {
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
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      entity.getReferencingRelations().clear();
      entity.getCompositePaths().clear();
      entity.getDeepReferences().clear();
      entity.getDeepReferencesToComponents().clear();
      ((EntityImpl) entity).setDeeplyReferenced(false);

      for (Relation relation: entity.getRelations()) {

        ((RelationImpl) relation).setForeignRelation(null);
        Entity foreignEntity;
        try {
          foreignEntity = getByEntityName(translateAlias(relation.getClassName()));
        }
        catch (ModelException mx) {
          errors.add(new ModelError(mx));
          continue;
        }
        if (foreignEntity == null) {
          if (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity)) {
            // log error only if class is associated with the updated one
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
                  // explicit backward relation given: try <name>Id
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
                if (translateAlias(nmRelation.getClassName()).equals(entity.getName()) &&
                    (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
                  errors.add(new ModelError(nmRelation,
                          "nm-relation " + nmRelation.getName() + " in " + foreignEntity +
                          " points back to " + entity));
                }
                if (nmRelation.getSelectionType() == SelectionType.LAZY && !nmRelation.isSerialized()) {
                  // the PdoRelations wurblet will generate a join
                  // which only makes sense if the PDOs are transferred via network
                  ((RelationImpl) nmRelation).setSerialized(true);      // retrieve from server
                  ((RelationImpl) nmRelation).setClearOnRemoteSave(true); // but don't send back to server
                }
                ((RelationImpl) relation).setNmRelation(nmRelation);
                ((RelationImpl) nmRelation).setDefiningNmRelation(relation);
              }
            }
          }
          else if (relation.getForeignEntity() != null && relation.getForeignEntity().isEmbedded()) {
            // embedded relations often don't explicitly mention "select = embedded" in their model
            // -> set it here asap to enable construction of composite paths
            ((RelationImpl) relation).setComposite(true);
            ((RelationImpl) relation).setSelectionType(SelectionType.EMBEDDED);
            ((RelationImpl) relation).setSelectionCached(false);
            ((RelationImpl) relation).setSelectionTypeDetermined(false);    // don't switch to cached if entity is cached
          }
        }
      }
    }

    // update referencing relations
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      for (EntityInfo otherEntityInfo : nameToEntityInfoMap.values()) {
        Entity otherEntity = otherEntityInfo.getEntity();
        for (Relation relation: otherEntity.getRelations()) {
          Entity foreignEntity = relation.getForeignEntity();
          if (foreignEntity != null && foreignEntity.equals(entity)) {
            entity.getReferencingRelations().add(relation);
          }
        }
      }
    }

    // update foreign relations
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      for (Relation relation: entity.getReferencingRelations()) {
        if (relation.getForeignRelation() == null) {
          // find a relation with opposite attributes
          for (Relation otherRelation: relation.getForeignEntity().getTableRelations()) {
            if (Objects.equals(relation.getEntity(), otherRelation.getForeignEntity()) &&
                (otherRelation.getForeignAttribute() == null && otherRelation.getRelationType() == RelationType.OBJECT || // referred to ID
                 Objects.equals(relation.getAttribute(), otherRelation.getForeignAttribute()))) {
              ((RelationImpl) relation).setForeignRelation(otherRelation);
              ((RelationImpl) otherRelation).setForeignRelation(relation);
              break;
            }
          }
        }
      }
    }

    // update composite paths and inspect model for root, rootid and rootclassid
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      boolean isRootEntity;
      if (entity.isEmbedded()) {
        isRootEntity = false;
      }
      else {
        isRootEntity = true;
        for (Relation relation : entity.getAllReferencingRelations()) {
          if (relation.isComposite() && !entity.equals(relation.getEntity())) {
            isRootEntity = false;
            break;
          }
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

    // determine deep references and set rootId and rootClassId options
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      /*
       * The entity should provide a root class id if it is a composite of more than one root entity
       * and holds exactly one root attribute column (2 means N:M relation, for example).
       */
      Set<Entity> rootEntities = entity.getRootEntities();
      if (!entity.isEmbedded() && rootEntities.size() > 1 && entity.getRootAttribute() != null) {
        ((EntityImpl) entity).setProvidingRootClassIdAccordingToModel(true);
      }
      // or there is a non-composite reference from an entity which is not one of its root entities (a deep reference),
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
            if (entity.getRootEntity() == null && !entity.isEmbedded()) {
              // need rootclassid column
              ((EntityImpl) entity).setProvidingRootClassIdAccordingToModel(true);
            }
          }
        }
      }

      // The entity should provide a rootId if it is a composite with a relation path of more than one hop
      // or an OBJECT-relation
      if (!entity.isEmbedded()) {
        for (List<Relation> compositePath : entity.getCompositePaths()) {
          if (compositePath.size() > 1 ||
              compositePath.getLast().getRelationType() == RelationType.OBJECT) {
            ((EntityImpl) entity).setProvidingRootIdAccordingToModel(true);
            break;
          }
        }
      }
    }

    // apply the model defaults:
    // the track type is only raised, ROOT, ROOTID and ROOTCLASSID go to the top super entity, REMOTE is forced
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      ModelDefaults defaults = entityInfo.getModelDefaults();
      Entity entity = entityInfo.getEntity();
      if (defaults != null && !entity.getOptions().noModelDefaults()) {
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


    // add/remove rootId, rootClassId to/from model
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();

      if (entity.getOptions().isRootClassIdProvided()) {
        if (entity.getAttributeByColumnName(Constants.CN_ROOTCLASSID, true) == null) {
          try {
            ((EntityImpl) entity).getOptions().applyOption(EntityOptionsImpl.OPTION_ROOTCLASSID, Boolean.TRUE);
          }
          catch (ModelException mx) {
            errors.add(new ModelError(mx));
            continue;
          }
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
          try {
            ((EntityImpl) entity).getOptions().applyOption(EntityOptionsImpl.OPTION_ROOTID, Boolean.TRUE);
          }
          catch (ModelException mx) {
            errors.add(new ModelError(mx));
          }
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
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      if (entity.getOptions().isCached() && !entity.isRootEntityAccordingToModel()) {
        errors.add(new ModelError(entity,"component " + entity + " cannot be cached"));
      }

      for (Relation relation: entity.getRelations()) {
        if (relation.getEntity().getOptions().getTrackType().isTracked()) {
          ((RelationImpl) relation).setTracked(true);
        }
        if (relation.getForeignEntity() != null) {
          if (relation.isComposite()) {
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
                (parentRelation.getSelectionType() == SelectionType.LAZY || relation.getForeignEntity().isEmbedded())) {
              // composite lazy relations without a linkMethod don't make sense
              if (relation.getLinkMethodName() == null || "set".equals(relation.getLinkMethodName())) {
                ((RelationImpl) relation).setLinkMethodName("set" + StringHelper.firstToUpper(parentRelation.getName()));
              }
              if (parentRelation.getEntity().getOptions().isRemote()) {
                ((RelationImpl) parentRelation).setSerialized(true);
              }
              ((RelationImpl) parentRelation).setSelectionCached(false);            // even if object is cached
              ((RelationImpl) parentRelation).setSelectionTypeDetermined(false);    // don't switch to cached (see below)
              if (relation.getForeignEntity().isEmbedded()) {
                ((RelationImpl) parentRelation).setSelectionType(SelectionType.LAZY);   // set back
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
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
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
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      if (entity.getSuperEntity() == null && !entity.getInheritanceType().isMappingToNoTable()) {
        // check
        Map<String,Attribute> columnMap = new HashMap<>();  // map by column name
        Map<String,Attribute> nameMap = new HashMap<>();    // map by java name
        List<Attribute> allAttributes;
        try {
          allAttributes = entity.getTableAttributes();
        }
        catch (ModelException mx) {
          errors.add(new ModelError(mx));
          continue;
        }
        for (Attribute attrib: allAttributes) {
          AttributeImpl attribute = (AttributeImpl) attrib;
          AttributeImpl oldAttribute = (AttributeImpl) nameMap.put(attribute.getPathName().toLowerCase(Locale.ROOT), attribute);
          if (oldAttribute != null && (loadedEntity == null || loadedEntity.getAssociatedEntities().contains(entity))) {
            StringBuilder exMsg = new StringBuilder();
            exMsg.append("attribute '").append(attribute.getPathName()).append("' in ").
            append(attribute.getEntity());
            exMsg.append(" already defined in ").append(oldAttribute.getEntity());
            if (oldAttribute.getSourceLine() != null) {
              exMsg.append("(").append(oldAttribute.getSourceLine()).append(")");
            }
            errors.add(new ModelError(attribute, exMsg.toString()));
          }
          AttributeImpl oldColumn = (AttributeImpl) columnMap.put(attribute.getColumnName().toLowerCase(Locale.ROOT), attribute);
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

    // collect the explicitly defined table aliases and detect duplicates
    Map<String,Entity> aliasMap = new HashMap<>();
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
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

    // prio 1: apply camelcase letters and digits if possible
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      if (entity.getTableName() != null && entity.getTableAlias() == null) {
        // determine the meaningful letters: the start of each camel-case word and all digits
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < entity.getName().length(); i++) {
          char currentChar = entity.getName().charAt(i);
          // each uppercase letter and digit (2=to, 4=for, etc...)
          if (Character.isUpperCase(currentChar) || Character.isDigit(currentChar)) {
            letters.append(currentChar);
          }
        }
        applyTableAlias(aliasMap, entity, letters.toString().toLowerCase(Locale.ROOT));
      }
    }

    // for all others still unnamed
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      if (entity.getTableName() != null && entity.getTableAlias() == null) {
        StringBuilder letters = new StringBuilder();
        boolean aliasFound = false;

        // try syllables
        List<String> syllables = ModelUtilities.getInstance().extractSyllables(entity.getName());
        if (!syllables.isEmpty()) {
          for (String syllable : syllables) {
            letters.append(syllable.charAt(0));
            if (applyTableAlias(aliasMap, entity, letters.toString().toLowerCase(Locale.ROOT))) {
              aliasFound = true;
              break;
            }
          }
        }

        if (!aliasFound) {
          // try the first syllable's letter, if any, followed by the consonants of the next 5 characters,
          // starting at an alias length of 3
          if (letters.length() > 1) {
            letters.setLength(1);
          }
          for (int i = 1; i < entity.getName().length() && i < 6; i++) {
            char c = Character.toLowerCase(entity.getName().charAt(i));
            if (c != 'a' && c != 'e' && c != 'i' && c != 'o' && c != 'u') {
              letters.append(c);
              if (letters.length() >= 3) {
                if (applyTableAlias(aliasMap, entity, letters.toString().toLowerCase(Locale.ROOT))) {
                  aliasFound = true;
                  break;
                }
              }
            }
          }

          if (!aliasFound) {
            // just take the first letter and count. This will always work
            String aliasPrefix = entity.getName().substring(0, 1).toLowerCase(Locale.ROOT);
            int count = 1;
            while (!applyTableAlias(aliasMap, entity, aliasPrefix + count)) {
              count++;
            }
          }
        }
      }
    }

    // sets the inheritance level (ordinal)
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      int level = 0;
      Entity superEntity = entity.getSuperEntity();
      while (superEntity != null) {
        level++;
        superEntity = superEntity.getSuperEntity();
      }
      ((EntityImpl) entity).setOrdinal(level);
    }

    // sets the ordinals of attributes, relations and indexes
    for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
      Entity entity = entityInfo.getEntity();
      if (!entity.isAbstract()) {   // only concrete entities
        Entity[] entities = new Entity[entityInfo.getEntity().getOrdinal() + 1];
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
      // validate every (sub)hierarchy
      for (EntityInfo entityInfo : nameToEntityInfoMap.values()) {
        Entity entity = entityInfo.getEntity();
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
   * Checks whether extending the given super entity would create a cycle in the inheritance hierarchy.<br>
   * Walking up such a hierarchy would never terminate, so the link must not be established at all.
   *
   * @param entity the entity that would extend the super entity
   * @param superEntity the super entity to be extended
   * @return true if the link would create a cycle
   */
  protected boolean createsInheritanceCycle(Entity entity, Entity superEntity) {
    Set<Entity> visited = new HashSet<>();
    Entity current = superEntity;
    while (current != null && visited.add(current)) {   // visited also guards against pre-existing cycles
      if (current.equals(entity)) {
        return true;
      }
      current = current.getSuperEntity();
    }
    return false;
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
   * @param compositePath the path that leads to the given entity so far
   * @param entity the entity
   */
  protected void updateCompositePath(List<Relation> compositePath, Entity entity) {
    Relation lastRelation = compositePath.getLast();
    if (lastRelation.getForeignAttribute() != null) {
      // the attribute actually may belong to a superclass
      entity = lastRelation.getForeignAttribute().getEntity();
    }
    entity.getCompositePaths().add(compositePath);
    for (Relation relation: entity.getAllRelations()) {
      if (relation.isComposite()) {
        Entity foreignEntity = relation.getForeignEntity();
        if (foreignEntity != null && !foreignEntity.equals(entity) && !compositePath.contains(relation)) {
          // a relation already within the path would mean a cyclic composition -> stop, else endless recursion
          List<Relation> newPath = new ArrayList<>(compositePath);
          newPath.add(relation);
          updateCompositePath(newPath, foreignEntity);
        }
      }
    }
  }

  /**
   * Creates the entity factory for this model.<br>
   * The method is invoked once from within the Model constructor.
   *
   * @return the entity factory
   */
  protected EntityFactory createEntityFactory() {
    return new EntityFactoryImpl(this);
  }

  /**
   * Creates a model directory object.
   *
   * @param modelDir the directory name
   * @return the model directory
   * @throws ModelException if the directory does not exist or is not a directory
   */
  protected ModelDirectory createModelDirectory(String modelDir) throws ModelException {
    return new ModelDirectoryImpl(modelDir);
  }

  /**
   * Creates the entity info for a model source.
   *
   * @param defaults the optional model defaults, overridden by the defaults embedded in the model source
   * @param url the URL of the model source
   * @return the entity info
   * @throws ModelException if creation failed
   */
  protected EntityInfo createEntityInfo(ModelDefaults defaults, URL url) throws ModelException {
    StringBuilder modelBuf = new StringBuilder();
    try (Reader reader = createReader(url)) {
      char[] buf = new char[1024];
      int len;
      while ((len = reader.read(buf)) != -1)  {
        modelBuf.append(buf, 0, len);
      }
    }
    catch (IOException ex) {
      throw new ModelException("reading model '" + url + "' failed", ex);
    }
    String modelSource = modelBuf.toString();
    Document document = new Document(modelSource);
    if (document.getModelDefaults() != null) {
      defaults = document.getModelDefaults(); // overridden by the document
    }
    Entity entity = getEntityFactory().createEntity(document, defaults);
    return new EntityInfoImpl(entity, url, modelSource, defaults);
  }

  /**
   * Creates a reader for the given URL.
   *
   * @param url the model url
   * @return the reader
   * @throws ModelException if creating the reader failed (because the file was not found)
   */
  protected Reader createReader(URL url) throws ModelException {
    try {
      return new BufferedReader(new InputStreamReader(FileHelper.openStreamUncached(url), Settings.getEncodingCharset()));
    }
    catch (IOException ex) {
      throw new ModelException("cannot open reader for " + url, ex);
    }
  }

  /**
   * Verifies that the inheritance, integrity, and track type are the same within a given inheritance hierarchy.<br>
   * Notice that concrete classes (the leaf children of the hierarchy) must have {@code InheritanceType.NONE} defined!
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
    // of development. We could issue warnings, though, but there is no logger...
  }

  /**
   * Verifies that the integrity is the same within a given composite hierarchy.
   *
   * @param entity the entity to check
   * @param integrity the integrity type
   * @param errors the collected model errors
   */
  protected void validateComponents(Entity entity, Integrity integrity, Set<ModelError> errors) {
    validateComponents(entity, integrity, errors, new HashSet<>());
  }

  /**
   * Verifies that the integrity is the same within a given composite hierarchy.
   *
   * @param entity the entity to check
   * @param integrity the integrity type
   * @param errors the collected model errors
   * @param processedEntities the already processed entities (to avoid endless recursion on cyclic compositions)
   */
  private void validateComponents(Entity entity, Integrity integrity, Set<ModelError> errors,
                                  Set<Entity> processedEntities) {

    if (!processedEntities.add(entity)) {
      return;   // already checked, may be a cyclic composition
    }

    if (entity.getIntegrity() != integrity) {
      errors.add(new ModelError(entity,
              entity + " has wrong integrity type " + entity.getIntegrity() +
              ", expected " + integrity));
    }

    for (Relation relation: entity.getRelations()) {
      if (relation.isComposite()) {
        Entity foreignEntity = relation.getForeignEntity();
        if (foreignEntity != null && !foreignEntity.equals(entity)) {
          validateComponents(foreignEntity, integrity, errors, processedEntities);
        }
      }
    }
  }

  /**
   * Translates an entity name if it is an alias.
   *
   * @param name the original name
   * @return the possibly translated name
   */
  protected String translateAlias(String name) {
    if (entityAliases != null) {
      String mappedName = entityAliases.get(name);
      if (mappedName != null) {
        name = mappedName;
      }
    }
    return name;
  }

}

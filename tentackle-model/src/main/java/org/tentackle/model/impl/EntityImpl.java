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

import org.tentackle.common.Compare;
import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeSorting;
import org.tentackle.model.CodeFactory;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Integrity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelElement;
import org.tentackle.model.ModelException;
import org.tentackle.model.NameVerifier;
import org.tentackle.model.Relation;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parse.ConfigurationLine;
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlNameType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The entity model implementation.
 *
 * @author harald
 */
public class EntityImpl implements Entity, Comparable<EntityImpl> {

  /** name := ... */
  static final String NAME = "NAME";

  /** id := ... */
  static final String ID = "ID";

  /** table := ... */
  static final String TABLE = "TABLE";

  /** alias := ... */
  static final String ALIAS = "ALIAS";

  /** integrity := ... */
  static final String INTEGRITY = "INTEGRITY";

  /** inheritance := ... */
  static final String INHERITANCE = "INHERITANCE";

  /** extends := ... */
  static final String EXTENDS = "EXTENDS";

  /** comment := ... */
  static final String COMMENT = "COMMENT";


  private final EntityFactoryImpl factory;                  // the factory
  private final SourceInfo sourceInfo;                      // the source info

  private String name;                                      // the entity's name
  private int ordinal;                                      // the ordinal, 0 = top, 1 = first inherited, ...
  private int classId;                                      // the class id
  private String tableName;                                 // the table name
  private String schemaName;                                // the schema name
  private String tableNameWithoutSchema;                    // schema relative table name
  private String tableAlias;                                // the table alias
  private String definedTableAlias;                         // the fixed table alias (from model specs)
  private Integrity integrity;                              // referential integrity
  private InheritanceType inheritanceType;                  // the inheritance type
  private String superEntityName;                           // the name of the extended super class
  private Entity superEntity;                               // the super entity
  private final List<Entity> subEntities;                   // the sub entities
  private final EntityOptionsImpl options;                  // options
  private final List<Attribute> attributes;                 // the attributes
  private Attribute contextIdAttribute;                     // attribute holding the context id, null if none
  private List<Attribute> uniqueDomainKey;                  // the unique domain key attributes
  private List<AttributeSorting> sorting;                   // the default sorting
  private final List<Relation> relations;                   // the relations
  private final List<Relation> referencingRelations;        // the referencing relations from other entities
  private final List<Index> indexes;                        // the indexes
  private final Set<List<Relation>> compositePaths;         // paths of composite relations to this entity
  private Set<List<Relation>> allCompositePaths;            // paths of composite relations to this entity and super entities
  private final List<Relation> deepReferencesToComponents;  // deep references to components of this root entity
  private final List<Relation> deepReferences;              // deep references to this entity
  private boolean deeplyReferenced;                         // true if this or any component is deeply referenced
  private Boolean rootEntity;                               // true if root entity (according to model or explicitly set)
  private boolean modelRoot;                                // is a root entity, according to the model
  private boolean modelRootId;                              // provides a root-ID according to model
  private boolean modelRootClassId;                         // provides a root-class-ID according to model



  /**
   * Creates an entity.
   *
   * @param factory the factory to create entity options
   * @param sourceInfo the source information
   */
  public EntityImpl(EntityFactoryImpl factory, SourceInfo sourceInfo) {
    this.factory = factory;
    this.sourceInfo = sourceInfo;

    integrity = Integrity.NONE;
    inheritanceType = InheritanceType.NONE;
    options = factory.createEntityOptions(this, sourceInfo);
    attributes = new ArrayList<>();
    relations = new ArrayList<>();
    referencingRelations = new ArrayList<>();
    subEntities = new ArrayList<>();
    compositePaths = new LinkedHashSet<>();
    deepReferences = new ArrayList<>();
    deepReferencesToComponents = new ArrayList<>();
    indexes = new ArrayList<>();
  }

  @Override
  public Model getModel() {
    return factory.getModel();
  }

  @Override
  public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  @Override
  public ModelElement getParent() {
    return getSuperEntity();
  }


  @Override
  public int hashCode() {
    int hash = 7;
    hash = 17 * hash + Objects.hashCode(this.name);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final EntityImpl other = (EntityImpl) obj;
    return Objects.equals(this.name, other.name);
  }

  @Override
  public int compareTo(EntityImpl o) {
    return Compare.compare(name, o.name);
  }

  /**
   * Parses a configuration line.
   *
   * @param line the configuration line
   * @return true if known configuration processed
   * @throws ModelException if parsing failed
   */
  public boolean parseConfiguration(ConfigurationLine line) throws ModelException {

    boolean parsed = true;

    switch (line.getKey().toUpperCase(Locale.ROOT)) {

      case NAME:
        String str = line.getValue();
        if (str.indexOf('.') >= 0) {
          throw line.createModelException("entity name must not contain package name");
        }
        setName(str);
        break;

      case ID:
        String idStr = line.getValue();
        setClassId(Integer.parseInt(idStr));
        break;

      case TABLE:
        setTableName(line.getValue());
        break;

      case ALIAS:
        setDefinedTableAlias(line.getValue());
        setTableAlias(line.getValue());
        break;

      case INTEGRITY:
        try {
          setIntegrity(Integrity.valueOf(line.getValue().toUpperCase(Locale.ROOT)));
        }
        catch (IllegalArgumentException ex) {
          throw line.createModelException("illegal integrity level: " + line.getValue());
        }
        break;

      case INHERITANCE:
        try {
          setInheritanceType(InheritanceType.valueOf(line.getValue().toUpperCase(Locale.ROOT)));
        }
        catch (IllegalArgumentException ex) {
          throw line.createModelException("illegal inheritance type: " + line.getValue());
        }
        break;

      case EXTENDS:
        setSuperEntityName(line.getValue());
        break;

      case COMMENT:
        getOptions().setComment(line.getValue());
        break;

      default:
        parsed = false;
    }

    return parsed;
  }




  @Override
  public String getName() {
    return name;
  }

  /**
   * Sets the entity's name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }


  @Override
  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  @Override
  public int getClassId() {
    return classId;
  }

  /**
   * Sets the class id.
   *
   * @param classId the class id
   */
  public void setClassId(int classId) {
    this.classId = classId;
  }

  @Override
  public String getTableName() {
    return tableName;
  }

  @Override
  public String getSchemaName() {
    return schemaName;
  }

  @Override
  public String getTableNameWithoutSchema() {
    return tableNameWithoutSchema;
  }

  @Override
  public String getTableAlias() {
    return tableAlias;
  }

  @Override
  public String getDefinedTableAlias() {
    return definedTableAlias;
  }



  /**
   * Sets the table name.<br>
   * The name is converted to lowercase. A schema prefix ({@code schema.table}) is split off into the schema name.
   *
   * @param tableName the table name, null if none
   */
  public void setTableName(String tableName) {
    if (tableName == null) {
      this.tableName = null;
      schemaName = null;
      tableNameWithoutSchema = null;
    }
    else  {
      this.tableName = tableName.toLowerCase(Locale.ROOT);
      int ndx = this.tableName.indexOf('.');
      if (ndx > 0) {
        schemaName = this.tableName.substring(0, ndx);
        tableNameWithoutSchema = this.tableName.substring(ndx + 1);
      }
      else  {
        tableNameWithoutSchema = this.tableName;
      }
    }
  }

  /**
   * Sets the table alias.
   *
   * @param tableAlias the alias
   */
  public void setTableAlias(String tableAlias) {
    if (tableAlias != null) {
      this.tableAlias = tableAlias.toLowerCase(Locale.ROOT);
    }
    else {
      this.tableAlias = null;
    }
  }

  /**
   * Sets the table alias defined by the model specification.
   *
   * @param definedTableAlias the original alias, null if automatic
   */
  public void setDefinedTableAlias(String definedTableAlias) {
    this.definedTableAlias = definedTableAlias;
  }


  @Override
  public Integrity getIntegrity() {
    return integrity;
  }

  @Override
  public InheritanceType getInheritanceType() {
    return inheritanceType;
  }

  @Override
  public InheritanceType getHierarchyInheritanceType() {
    return superEntity == null ? inheritanceType : superEntity.getInheritanceType();
  }

  @Override
  public String getSuperEntityName() {
    return superEntityName;
  }

  @Override
  public Entity getSuperEntity() {
    return superEntity;
  }

  @Override
  public List<Entity> getSuperEntities() {
    List<Entity> superEntities = new ArrayList<>();
    Entity entity = this;
    while (entity.getSuperEntity() != null) {
      superEntities.add(entity.getSuperEntity());
      entity = entity.getSuperEntity();
    }
    return superEntities;
  }

  @Override
  public Entity getTopSuperEntity() {
    Entity entity = this;
    while (entity.getSuperEntity() != null) {
      entity = entity.getSuperEntity();
    }
    return entity;
  }

  @Override
  public List<Entity> getInheritanceChain(Entity childEntity) throws ModelException {
    List<Entity> chain = new ArrayList<>();
    Entity entity = childEntity;
    while (entity != null) {
      chain.addFirst(entity);
      if (entity == this) {
        break;
      }
      entity = entity.getSuperEntity();
    }
    if (chain.isEmpty() || chain.get(0) != this) {
      throw new ModelException(this + " is not a superclass of " + childEntity);
    }
    return chain;
  }

  @Override
  public List<Entity> getSubEntities() {
    return subEntities;
  }

  @Override
  public List<Entity> getAllSubEntities() {
    List<Entity> children = new ArrayList<>();
    collectSubEntities(children, this);
    return children;
  }

  @Override
  public List<Entity> getLeafEntities() {
    List<Entity> leaves = new ArrayList<>();
    collectLeafEntities(leaves, this);
    return leaves;
  }

  /**
   * Recursively collects the leaf children.
   *
   * @param leaves the list of leaf children so far
   * @param entity the current entity to analyze
   */
  protected void collectLeafEntities(List<Entity> leaves, Entity entity) {
    if (entity.isAbstract()) {
      for (Entity child: entity.getSubEntities()) {
        collectLeafEntities(leaves, child);
      }
    }
    else  {
      leaves.add(entity);
    }
  }

  /**
   * Recursively collects all sub entities.
   *
   * @param subEntities the list of collected sub entities
   * @param entity the current entity to analyze
   */
  protected void collectSubEntities(List<Entity> subEntities, Entity entity) {
    for (Entity sub: entity.getSubEntities()) {
      subEntities.add(sub);
      collectSubEntities(subEntities, sub);
    }
  }

  @Override
  public Set<Entity> getAssociatedEntities() {
    Set<Entity> associates = new LinkedHashSet<>();
    collectAssociatedEntities(associates, this, new HashSet<>());
    return associates;
  }


  /**
   * Recursively collects the associated entities for a given entity.
   *
   * @param associates the set of associated entities so far
   * @param entity the current entity to analyze
   * @param processedEntities the already processed entities (to avoid loops)
   */
  protected void collectAssociatedEntities(Set<Entity> associates, Entity entity, Set<Entity> processedEntities) {
    if (processedEntities.add(entity)) {
      associates.add(entity);
      Entity superOfEntity = entity.getSuperEntity();
      if (superOfEntity != null) {
        associates.add(superOfEntity);
        collectAssociatedEntities(associates, superOfEntity, processedEntities);
      }
      associates.addAll(entity.getSubEntities());
      for (Entity child: entity.getSubEntities()) {
        collectAssociatedEntities(associates, child, processedEntities);
      }
      for (Relation relation: entity.getRelations()) {
        // the entities this entity points at
        Entity relatedEntity = relation.getForeignEntity();
        if (relatedEntity != null) {    // model may be incomplete...
          associates.add(relatedEntity);
          collectAssociatedEntities(associates, relatedEntity, processedEntities);
        }
      }
      for (Relation relation: entity.getReferencingRelations()) {
        // the entities pointing at this entity
        Entity referencingEntity = relation.getEntity();
        if (referencingEntity != null) {    // model may be incomplete...
          associates.add(referencingEntity);
          collectAssociatedEntities(associates, referencingEntity, processedEntities);
        }
      }
    }
  }


  /**
   * Sets the integrity.
   *
   * @param integrity the integrity
   */
  public void setIntegrity(Integrity integrity) {
    this.integrity = Objects.requireNonNull(integrity, "integrity");
  }

  /**
   * Sets the inheritance type.
   *
   * @param inheritanceType the inheritance type
   */
  public void setInheritanceType(InheritanceType inheritanceType) {
    this.inheritanceType = Objects.requireNonNull(inheritanceType, "inheritanceType");
  }

  /**
   * Sets the name of the super entity.
   *
   * @param superEntityName the name of the super entity
   */
  public void setSuperEntityName(String superEntityName) {
    this.superEntityName = superEntityName;
  }

  /**
   * Sets the super entity.
   *
   * @param superEntity the super entity
   */
  public void setSuperEntity(Entity superEntity) {
    this.superEntity = superEntity;
  }

  /**
   * Sets the context id attribute.
   *
   * @param contextIdAttribute the attribute providing the context ID
   */
  public void setContextIdAttribute(Attribute contextIdAttribute) {
    this.contextIdAttribute = contextIdAttribute;
  }

  /**
   * Sets the default sorting.
   *
   * @param sorting the sorting, null if none
   */
  public void setSorting(List<AttributeSorting> sorting) {
    this.sorting = sorting;
  }

  @Override
  public EntityOptionsImpl getOptions() {
    return options;
  }

  @Override
  public List<Attribute> getAttributes() {
    return attributes;
  }


  /**
   * Returns whether this entity is a root entity, according to the model.
   *
   * @return true if root entity
   */
  @Override
  public boolean isRootEntityAccordingToModel() {
    return modelRoot;
  }

  /**
   * Sets whether this entity is a root entity, according to the model.
   *
   * @param modelRoot true if root entity
   */
  public void setRootEntityAccordingToModel(boolean modelRoot) {
    this.modelRoot = modelRoot;
  }

  /**
   * Returns whether this entity should provide a root class id according to model.
   *
   * @return true if entity should provide a root class id
   */
  @Override
  public boolean isProvidingRootClassIdAccordingToModel() {
    return modelRootClassId;
  }

  /**
   * Sets whether this entity should provide a root class id according to model.
   *
   * @param modelRootClassId  true if entity should provide a root class id
   */
  public void setProvidingRootClassIdAccordingToModel(boolean modelRootClassId) {
    this.modelRootClassId = modelRootClassId;
  }

  /**
   * Returns whether this entity should provide a root id according to model.
   *
   * @return true if the entity should provide a root id
   */
  @Override
  public boolean isProvidingRootIdAccordingToModel() {
    return modelRootId;
  }

  /**
   * Sets whether this entity should provide a root id according to model.
   *
   * @param modelRootId  true if the entity should provide a root id
   */
  public void setProvidingRootIdAccordingToModel(boolean modelRootId) {
    this.modelRootId = modelRootId;
  }


  @Override
  public List<Attribute> getInheritedAttributes() throws ModelException {
    return getInheritedAttributes(false);
  }

  private List<Attribute> getInheritedAttributes(boolean includeEmbedded) throws ModelException {
    List<Attribute> inheritedAttributes = new ArrayList<>();
    Entity se = getSuperEntity();
    while (se != null) {
      inheritedAttributes.addAll(0, se.getAttributes());
      if (includeEmbedded) {
        inheritedAttributes.addAll(0, se.getEmbeddedAttributes());
      }
      se = se.getSuperEntity();
    }
    return inheritedAttributes;
  }

  @Override
  public List<Attribute> getEmbeddedAttributes() throws ModelException {
    List<Attribute> embeddedAttributes = new ArrayList<>();
    Set<String> recursionSet = new HashSet<>();    // to detect recursions of embedded entities
    collectEmbeddedAttributes(this, recursionSet, embeddedAttributes,"", "");
    return embeddedAttributes;
  }

  private void collectEmbeddedAttributes(Entity embeddingEntity, Set<String> recursionSet, List<Attribute> embeddedAttributes, String pathPrefix, String columnPrefix) throws ModelException {
    for (Relation relation : getRelations()) {
      if (relation.isEmbedding()) {
        EntityImpl embeddedEntity = (EntityImpl) relation.getForeignEntity();
        if (embeddedEntity != null) {
          if (recursionSet.add(embeddedEntity.getName() + "." + relation.getName())) {
            String pPrefix = pathPrefix + StringHelper.firstToLower(relation.getName()) + ".";
            String cPrefix = columnPrefix + relation.getColumnPrefix();
            for (Attribute attribute : embeddedEntity.getAttributes()) {
              embeddedAttributes.add(attribute.createEmbedded(embeddingEntity, pPrefix + attribute.getPathName(), cPrefix + attribute.getColumnName()));
            }
            // recursively add embedded PDOs of this embedded PDO, if any
            embeddedEntity.collectEmbeddedAttributes(embeddingEntity, new HashSet<>(recursionSet), embeddedAttributes, pPrefix, cPrefix);
          }
          else {
            throw new ModelException(embeddedEntity + " embedded recursively via '" + pathPrefix + "..'", relation);
          }
        }
      }
    }
  }

  @Override
  public List<Relation> getEmbeddedRelations(Entity embeddingEntity) throws ModelException {
    List<Relation> embeddedRelations = new ArrayList<>();
    Set<String> recursionSet = new HashSet<>();    // to detect recursions of embedded entities
    collectEmbeddedRelations(embeddingEntity, recursionSet, embeddedRelations, "", "");
    return embeddedRelations;
  }

  private void collectEmbeddedRelations(Entity embeddingEntity, Set<String> recursionSet, List<Relation> embeddedRelations, String pathPrefix, String columnPathPrefix) throws ModelException {
    for (Relation relation : getRelations()) {
      if (relation.isEmbedding()) {
        EntityImpl embeddedEntity = (EntityImpl) relation.getForeignEntity();
        if (embeddedEntity != null) {
          if (recursionSet.add(embeddedEntity.getName() + "." + relation.getName())) {
            String pPrefix = pathPrefix + StringHelper.firstToLower(relation.getName()) + ".";
            String cPrefix = columnPathPrefix + relation.getColumnPrefix();
            for (Relation embeddedEntityRelation : embeddedEntity.getRelations()) {
              if (!embeddedEntityRelation.isEmbedding() && embeddedEntityRelation.isEmbedded()) {
                embeddedRelations.add(embeddedEntityRelation.createEmbedded(embeddingEntity, pPrefix, cPrefix));
              }
            }
            embeddedEntity.collectEmbeddedRelations(embeddingEntity, new HashSet<>(recursionSet), embeddedRelations, pPrefix, cPrefix);
          }
          else {
            throw new ModelException(embeddedEntity + " embedded recursively via '" + pathPrefix + "..'", relation);
          }
        }
      }
    }
  }

  @Override
  public List<Attribute> getSubEntityAttributes() {
    List<Attribute> subAttributes = new ArrayList<>();
    for (Entity sub: getAllSubEntities()) {
      for (Attribute attribute: sub.getAttributes()) {
        if (!attribute.getName().equals(Constants.AN_ID) &&
            !attribute.getName().equals(Constants.AN_SERIAL)) {
          subAttributes.add(attribute);
        }
      }
    }
    return subAttributes;
  }


  @Override
  public List<Attribute> getAttributesIncludingInherited() throws ModelException {
    return getAttributesIncludingInherited(false);
  }

  private List<Attribute> getAttributesIncludingInherited(boolean includeEmbedded) throws ModelException {
    List<Attribute> allAttributes = new ArrayList<>(getInheritedAttributes(includeEmbedded));
    allAttributes.addAll(attributes);
    allAttributes = moveIdSerialToEnd(allAttributes);
    return allAttributes;
  }

  @Override
  public List<Attribute> getAttributesIncludingSubEntities() {
    List<Attribute> allAttributes = new ArrayList<>(attributes);
    allAttributes.addAll(getSubEntityAttributes());
    allAttributes = moveIdSerialToEnd(allAttributes);
    return allAttributes;
  }

  @Override
  public List<Attribute> getAllAttributes() throws ModelException {
    List<Attribute> allAttributes = new ArrayList<>(getInheritedAttributes());
    allAttributes.addAll(attributes);
    allAttributes.addAll(getSubEntityAttributes());
    allAttributes = moveIdSerialToEnd(allAttributes);
    return allAttributes;
  }

  @Override
  public List<Attribute> getTableAttributes() throws ModelException {
    InheritanceType ihType = getHierarchyInheritanceType();
    List<Attribute> tableAttributes = new ArrayList<>(getEmbeddedAttributes());
    if (ihType.isMappingToOwnTable()) {
      tableAttributes.addAll(attributes);
      if (getSuperEntity() != null) {
        // always add the ID for subclasses in MULTI inheritance
        tableAttributes.add(getAttributeByJavaName(Constants.AN_ID, true));
      }
    }
    else if (ihType.isMappingToSuperTable()) {
      tableAttributes = new ArrayList<>();
      if (isRootOfInheritanceHierarchy()) {
        tableAttributes.addAll(attributes);
        for (Entity child: getAllSubEntities()) {
          tableAttributes.addAll(child.getEmbeddedAttributes());
          tableAttributes.addAll(child.getAttributes());
        }
        tableAttributes = moveIdSerialToEnd(tableAttributes);
      }
    }
    else {
      tableAttributes.addAll(getAttributesIncludingInherited(true));
    }
    return tableAttributes;
  }

  @Override
  public List<Attribute> getMappedAttributes() throws ModelException {
    List<Attribute> mappedAttributes;
    if (isAbstract()) {
      if (inheritanceType.isMappingToOwnTable()) {
        // MULTI
        mappedAttributes = attributes;
      }
      else if (inheritanceType.isMappingToSuperTable()) {
        // SINGLE: strip id and serial. It's mapped by the leaf children
        mappedAttributes = removeIdSerial(attributes);
      }
      else  {
        // PLAIN
        mappedAttributes = new ArrayList<>();
      }
    }
    else  {   // leaf
      InheritanceType ihType = getHierarchyInheritanceType();
      if (ihType.isMappingToOwnTable()) {
        // MULTI
        mappedAttributes = attributes;
      }
      else if (ihType.isMappingToSuperTable()) {
        // SINGLE: get id and serial from top of hierarchy
        Entity top = getTopSuperEntity();
        mappedAttributes = appendIdSerial(top, attributes);
      }
      else if (ihType.isMappingToNoTable()) {
        // PLAIN
        mappedAttributes = getAttributesIncludingInherited();
      }
      else  {  // NONE
        mappedAttributes = attributes;
      }
    }
    return mappedAttributes;
  }


  /**
   * Moves id and serial to the end of a newly created attribute list.
   *
   * @param attributes the attribute list
   * @return a new list with id and serial moved to the end
   */
  protected List<Attribute> moveIdSerialToEnd(List<Attribute> attributes) {
    Attribute idAttribute = null;
    Attribute serialAttribute = null;
    List<Attribute> filteredList = new ArrayList<>();
    for (Attribute attribute: attributes) {
      if (Constants.CN_ID.equals(attribute.getName())) {
        idAttribute = attribute;
        continue;
      }
      if (Constants.CN_SERIAL.equals(attribute.getName())) {
        serialAttribute = attribute;
        continue;
      }
      filteredList.add(attribute);
    }
    if (idAttribute != null) {
      filteredList.add(idAttribute);
    }
    if (serialAttribute != null) {
      filteredList.add(serialAttribute);
    }
    return filteredList;
  }


  /**
   * Removes id and serial from the list of attributes.
   *
   * @param attributes the attribute list
   * @return the new filtered attribute list
   */
  protected List<Attribute> removeIdSerial(List<Attribute> attributes) {
    List<Attribute> filteredList = new ArrayList<>();
    for (Attribute attribute: attributes) {
      if (!Constants.CN_ID.equals(attribute.getName()) &&
          !Constants.CN_SERIAL.equals(attribute.getName())) {
        filteredList.add(attribute);
      }
    }
    return filteredList;
  }

  /**
   * Appends id and serial from another entity.
   *
   * @param entity the entity defining id and serial
   * @param attributes the attribute list
   * @return a new list with id and serial of the given entity appended
   */
  protected List<Attribute> appendIdSerial(Entity entity, List<Attribute> attributes) {
    Attribute idAttribute = null;
    Attribute serialAttribute = null;
    for (Attribute attribute: entity.getAttributes()) {
      if (Constants.CN_ID.equals(attribute.getName())) {
        idAttribute = attribute;
      }
      else if (Constants.CN_SERIAL.equals(attribute.getName())) {
        serialAttribute = attribute;
      }
    }
    List<Attribute> filteredList = new ArrayList<>(attributes);
    if (idAttribute != null) {
      filteredList.add(idAttribute);
    }
    if (serialAttribute != null) {
      filteredList.add(serialAttribute);
    }
    return filteredList;
  }

  @Override
  public List<Index> getInheritedIndexes() {
    List<Index> inheritedIndexes = new ArrayList<>();
    Entity se = getSuperEntity();
    while (se != null) {
      inheritedIndexes.addAll(0, se.getIndexes());
      se = se.getSuperEntity();
    }
    return inheritedIndexes;
  }

  @Override
  public List<Index> getIndexesIncludingInherited() {
    List<Index> allIndexes = new ArrayList<>(getInheritedIndexes());
    allIndexes.addAll(indexes);
    return allIndexes;
  }

  @Override
  public List<Index> getSubEntityIndexes() {
    List<Index> subIndexes = new ArrayList<>();
    for (Entity sub: getSubEntities()) {
      subIndexes.addAll(sub.getIndexes());
    }
    return subIndexes;
  }

  @Override
  public List<Index> getIndexesIncludingSubEntities() {
    List<Index> allIndexes = new ArrayList<>(indexes);
    allIndexes.addAll(getSubEntityIndexes());
    return allIndexes;
  }

  @Override
  public List<Index> getAllIndexes() {
    List<Index> allIndexes = new ArrayList<>(getInheritedIndexes());
    allIndexes.addAll(indexes);
    allIndexes.addAll(getSubEntityIndexes());
    return allIndexes;
  }

  @Override
  public List<Index> getTableIndexes() {
    InheritanceType ihType = getHierarchyInheritanceType();
    List<Index> tableIndexes;
    if (ihType.isMappingToOwnTable()) {
      tableIndexes = indexes;
    }
    else if (ihType.isMappingToSuperTable()) {
      tableIndexes = new ArrayList<>();
      if (isRootOfInheritanceHierarchy()) {
        tableIndexes.addAll(indexes);
        for (Entity child: getAllSubEntities()) {
          tableIndexes.addAll(child.getIndexes());
        }
      }
    }
    else {
      tableIndexes = getIndexesIncludingInherited();
    }
    return tableIndexes;
  }

  @Override
  public List<Relation> getInheritedRelations() {
    List<Relation> allRelations = new ArrayList<>();
    Entity se = getSuperEntity();
    while (se != null) {
      allRelations.addAll(0, se.getRelations());
      se = se.getSuperEntity();
    }
    return allRelations;
  }

  @Override
  public List<Relation> getSubEntityRelations() {
    List<Relation> allRelations = new ArrayList<>();
    for (Entity sub: getAllSubEntities()) {
      allRelations.addAll(sub.getRelations());
    }
    return allRelations;
  }

  @Override
  public List<Relation> getRelationsIncludingInherited() {
    List<Relation> allRelations = new ArrayList<>(getInheritedRelations());
    allRelations.addAll(relations);
    return allRelations;
  }

  @Override
  public List<Relation> getRelationsIncludingSubEntities() {
    List<Relation> allRelations = new ArrayList<>(relations);
    allRelations.addAll(getSubEntityRelations());
    return allRelations;
  }

  @Override
  public List<Relation> getAllRelations() {
    List<Relation> allRelations = getRelationsIncludingInherited();
    allRelations.addAll(getSubEntityRelations());
    return allRelations;
  }

  @Override
  public List<Relation> getTableRelations() throws ModelException {
    InheritanceType ihType = getHierarchyInheritanceType();
    List<Relation> tableRelations = new ArrayList<>();
    if (ihType.isMappingToOwnTable()) {
      tableRelations.addAll(relations);
    }
    else if (ihType.isMappingToSuperTable()) {
      tableRelations = new ArrayList<>();
      if (isRootOfInheritanceHierarchy()) {
        tableRelations.addAll(relations);
        for (Entity sub: getAllSubEntities()) {
          tableRelations.addAll(sub.getRelations());
          tableRelations.addAll(sub.getEmbeddedRelations(this));
        }
      }
    }
    else {
      tableRelations.addAll(getRelationsIncludingInherited());
    }
    tableRelations.addAll(getEmbeddedRelations(this));
    return tableRelations;
  }

  @Override
  public Set<List<Relation>> getCompositePaths() {
    return compositePaths;
  }

  @Override
  public Set<List<Relation>> getAllCompositePaths() {
    if (allCompositePaths == null) {
      allCompositePaths = new LinkedHashSet<>();
      Entity currentEntity = this;
      while (currentEntity != null) {
        allCompositePaths.addAll(currentEntity.getCompositePaths());
        currentEntity = currentEntity.getSuperEntity();
      }
    }
    return allCompositePaths;
  }

  @Override
  public Set<List<Relation>> getEmbeddingPaths() {
    Set<List<Relation>> embeddingPaths = new LinkedHashSet<>();
    // skip non-embedding relations
    for (List<Relation> compositePath : getCompositePaths()) {
      List<Relation> embeddingPath = new ArrayList<>();
      for (Relation relation : compositePath) {
        if (relation.isEmbedding()) {
          embeddingPath.add(relation);
        }
      }
      embeddingPaths.add(embeddingPath);
    }
    return embeddingPaths;
  }

  @Override
  public Set<Entity> getEmbeddingEntities() {
    Set<Entity> embeddingEntities = new LinkedHashSet<>();
    for (List<Relation> embeddingPath : getEmbeddingPaths()) {
      embeddingEntities.add(embeddingPath.getFirst().getEntity());
    }
    return embeddingEntities;
  }

  @Override
  public Set<Entity> getComponents() {
    Set<Entity> components = new TreeSet<>();
    for (Relation relation: getRelations()) {
      if (relation.isComposite()) {
        components.add(relation.getForeignEntity());
      }
    }
    return components;
  }

  @Override
  public Set<Entity> getComponentsIncludingInherited() {
    Set<Entity> components = new TreeSet<>();
    for (Relation relation: getRelationsIncludingInherited()) {
      if (relation.isComposite()) {
        components.add(relation.getForeignEntity());
      }
    }
    return components;
  }

  @Override
  public Set<Entity> getAllComponents() {
    Set<Entity> components = new TreeSet<>();
    for (Relation relation: getRelationsIncludingInherited()) {
      if (relation.isComposite()) {
        Entity component = relation.getForeignEntity();
        components.add(component);
        components.addAll(component.getComponentsIncludingInherited());
      }
    }
    return components;
  }

  @Override
  public boolean isDeeplyReferenced() {
    return deeplyReferenced;
  }

  /**
   * Sets whether this entity or one of its components is deeply referenced.
   *
   * @param deeplyReferenced true if deeply referenced
   */
  public void setDeeplyReferenced(boolean deeplyReferenced) {
    this.deeplyReferenced = deeplyReferenced;
  }

  @Override
  public List<Relation> getDeepReferences() {
    return deepReferences;
  }

  @Override
  public List<Relation> getDeepReferencesToComponents() {
    return deepReferencesToComponents;
  }

  @Override
  public List<Relation> getDeeplyReferencedComponents() {
    List<Relation> deepRelations = new ArrayList<>();
    for (Relation relation : relations) {
      if (relation.isComposite()) {
        Entity component = relation.getForeignEntity();
        if (component.isDeeplyReferenced()) {
          deepRelations.add(relation);
        }
      }
    }
    return deepRelations;
  }

  @Override
  public Attribute getAttributeByJavaName(String javaName, boolean all) {
    if (javaName != null) {
      Entity entity = this;
      while (entity != null) {
        for (Attribute attribute: entity.getAttributes()) {
          if (javaName.equalsIgnoreCase(attribute.getName())) {
            return attribute;
          }
        }
        if (all) {
          entity = entity.getSuperEntity();
        }
        else  {
          break;
        }
      }
    }
    return null;
  }

  @Override
  public Attribute getAttributeByColumnName(String columnName, boolean all) {
    if (columnName != null) {
      Entity entity = this;
      while (entity != null) {
        for (Attribute attribute: entity.getAttributes()) {
          if (columnName.equalsIgnoreCase(attribute.getColumnName())) {
            return attribute;
          }
        }
        if (all) {
          entity = entity.getSuperEntity();
        }
        else  {
          break;
        }
      }
    }
    return null;
  }

  @Override
  public Attribute getContextIdAttribute() {
    return contextIdAttribute;
  }

  @Override
  public List<Attribute> getUniqueDomainKey() throws ModelException {
    if (uniqueDomainKey == null) {
      uniqueDomainKey = new ArrayList<>();
      if (isRootEntity()) {
        getUniqueDomainKeyImpl(uniqueDomainKey, this);
      }
    }
    return uniqueDomainKey;
  }

  /**
   * Adds the unique domain keys recursively for the given entity to the list.
   *
   * @param uniqueDomainKeys the unique domain key attributes
   * @param entity the entity to inspect
   * @throws ModelException if the model is inconsistent
   */
  protected void getUniqueDomainKeyImpl(List<Attribute> uniqueDomainKeys, Entity entity) throws ModelException {
    getUniqueDomainKeyImpl(uniqueDomainKeys, entity, new HashSet<>());
  }

  private void getUniqueDomainKeyImpl(List<Attribute> uniqueDomainKeys, Entity entity,
                                      Set<Entity> processedEntities) throws ModelException {
    if (processedEntities.add(entity)) {
      for (Attribute attribute : entity.getAttributesIncludingInherited()) {
        if (attribute.getOptions().isDomainKey()) {
          uniqueDomainKeys.add(attribute);
        }
      }
      for (Entity component : entity.getComponentsIncludingInherited()) {
        if (!component.isRootEntity() && !component.isEmbedded()) {   // stop at sub-roots!
          getUniqueDomainKeyImpl(uniqueDomainKeys, component, processedEntities);
        }
      }
    }
  }



  @Override
  public List<AttributeSorting> getSorting() {
    return sorting;
  }

  @Override
  public List<Index> getIndexes() {
    return indexes;
  }

  @Override
  public Index getIndex(String name, boolean all) {
    if (name != null) {
      Entity entity = this;
      while (entity != null) {
        for (Index index: entity.getIndexes()) {
          if (name.equalsIgnoreCase(index.getName())) {
            return index;
          }
        }
        if (all) {
          entity = entity.getSuperEntity();
        }
        else  {
          break;
        }
      }
    }
    return null;
  }

  @Override
  public List<Relation> getRelations() {
    return relations;
  }

  @Override
  public List<Relation> getReferencingRelations() {
    return referencingRelations;
  }

  @Override
  public List<Relation> getInheritedReferencingRelations() {
    List<Relation> inheritedReferencingRelations = new ArrayList<>();
    Entity se = getSuperEntity();
    while (se != null) {
      inheritedReferencingRelations.addAll(0, se.getReferencingRelations());
      se = se.getSuperEntity();
    }
    return inheritedReferencingRelations;
  }

  @Override
  public List<Relation> getReferencingRelationsIncludingInherited() {
    List<Relation> allRelations = new ArrayList<>(getInheritedReferencingRelations());
    allRelations.addAll(getReferencingRelations());
    return allRelations;
  }

  @Override
  public List<Relation> getSubEntityReferencingRelations() {
    List<Relation> subReferencingRelations = new ArrayList<>();
    for (Entity sub: getAllSubEntities()) {
      subReferencingRelations.addAll(sub.getReferencingRelations());
    }
    return subReferencingRelations;
  }

  @Override
  public List<Relation> getReferencingRelationsIncludingSubEntities() {
    List<Relation> allRelations = new ArrayList<>(getReferencingRelations());
    allRelations.addAll(getSubEntityReferencingRelations());
    return allRelations;
  }

  @Override
  public List<Relation> getAllReferencingRelations() {
    List<Relation> allRelations = new ArrayList<>(getReferencingRelationsIncludingInherited());
    allRelations.addAll(getSubEntityReferencingRelations());
    return allRelations;
  }

  @Override
  public Relation getRelation(String name, boolean all) {
    if (name != null) {
      Entity entity = this;
      while (entity != null) {
        for (Relation relation: entity.getRelations()) {
          if (name.equalsIgnoreCase(relation.getName()) || name.equalsIgnoreCase(relation.getVariableName())) {
            return relation;
          }
        }
        if (all) {
          entity = entity.getSuperEntity();
        }
        else  {
          break;
        }
      }
    }
    return null;
  }

  @Override
  public List<Relation> getRelations(Entity entity, boolean all) {
    List<Relation> rels = new ArrayList<>();
    if (entity != null) {
      Entity e = this;
      while (e != null) {
        for (Relation relation: e.getRelations()) {
          if (entity.equals(relation.getForeignEntity())) {
            rels.add(relation);
          }
        }
        if (all) {
          e = e.getSuperEntity();
        }
        else  {
          break;
        }
      }
    }
    return rels;
  }


  /**
   * Validates the entity without relation-dependent settings.
   *
   * @throws ModelException if validation failed
   */
  public void validate() throws ModelException {

    if (StringHelper.isAllWhitespace(getName())) {
      throw new ModelException("configuration 'name := ...' missing", this);
    }
    String diag = NameVerifier.getInstance().verifyEntityName(this);
    if (diag != null) {
      throw new ModelException(diag, this);
    }
    diag = NameVerifier.getInstance().verifyTableName(this);
    if (diag != null) {
      throw new ModelException(diag, this);
    }
    diag = NameVerifier.getInstance().verifyTableAlias(this);
    if (diag != null) {
      throw new ModelException(diag, this);
    }

    if (!isAbstract() && !isEmbedded()) {
      if (getClassId() == 0) {
        throw new ModelException("configuration 'id := ...' missing", this);
      }
    }
    else  {
      if (getClassId() != 0) {
        throw new ModelException("configuration 'id := ...' not allowed", this);
      }
    }

    if (isEmbedded()) {
      if (getSorting() != null) {
        throw new ModelException("sorting not applicable to embedded entities", this);
      }
      if (getSuperEntityName() != null) {
        throw new ModelException("embedded entities cannot extend other entities", this);
      }
      if (getOptions().isRoot()) {
        throw new ModelException("embedded entities cannot be root entities", this);
      }
      if (getOptions().isCached()) {
        throw new ModelException("embedded entities cannot be cached", this);
      }
      if (getOptions().isProvided()) {
        throw new ModelException("embedded entities cannot be provided by foreign applications", this);
      }
      if (getOptions().isNoPrimaryKey()) {
        throw new ModelException("[NOPKEY] option is not applicable to embedded entities", this);
      }
      if (getOptions().isRootIdProvided()) {
        throw new ModelException("embedded entities cannot provide a rootid column", this);
      }
      if (getOptions().isRootClassIdProvided()) {
        throw new ModelException("embedded entities cannot provide a rootclassid column", this);
      }
      if (getOptions().isNormTextProvided()) {
        throw new ModelException("embedded entities cannot provide a normtext", this);
      }
      if (getOptions().isTableSerialProvided()) {
        throw new ModelException("embedded entities cannot provide a table serial", this);
      }
      if (getOptions().isTokenLockProvided()) {
        throw new ModelException("embedded entities cannot provide a token lock", this);
      }
    }
    else {
      List<AttributeSorting> defaultSorting = getSorting();
      if (defaultSorting != null) {
        for (AttributeSorting attributeSorting : defaultSorting) {
          if (!attributeSorting.attribute().getEffectiveDataType().isComparable()) {
            throw new ModelException("default sorting attribute " +
                                     attributeSorting.attribute().getName() + " is not comparable", this);
          }
        }
      }
    }

    getOptions().validate();

    if (getOptions().isRoot()) {
      if (getSuperEntityName() != null) {
        throw new ModelException("[ROOT] option cannot be applied to subclasses", this);
      }
      if (getOptions().isRootIdProvided()) {
        throw new ModelException("root entities cannot provide a rootid column", this);
      }
      if (getOptions().isRootClassIdProvided()) {
        throw new ModelException("root entities cannot provide a rootclassid column", this);
      }
    }

    // names that differ only in case or non-alphanumeric characters (e.g. underscores) are considered confusing
    Map<String,Attribute> javaNames = new HashMap<>();
    for (Attribute attribute: getAttributes()) {
      attribute.validate();
      Attribute other = javaNames.put(normalizeName(attribute.getName()), attribute);
      if (other != null) {
        if (other.getName().equals(attribute.getName())) {
          throw new ModelException("duplicate Java name '" + attribute.getName() + "'", this);
        }
        else {
          throw new ModelException("Java name '" + attribute.getName() + "' too close to '" +
                                   other.getName() + "' -> danger of confusion", this);
        }
      }
    }

    Set<String> columnNames = new HashSet<>();
    for (Attribute attribute: getAttributes()) {
      String normalizedColumnName = normalizeName(attribute.getColumnName());
      if (!columnNames.add(normalizedColumnName)) {
        throw new ModelException("duplicate column name '" + attribute.getColumnName() + "'", this);
      }
      // a column name must not normalize to the Java name of another attribute
      Attribute other = javaNames.get(normalizedColumnName);
      if (other != null && !other.equals(attribute)) {
        throw new ModelException("misleading column name '" + attribute.getColumnName() +
                                 "' for Java name '" + attribute.getName() +
                                 "' -> danger of confusion with '" +
                                 other.getName() + " / " + other.getColumnName() + "'", this);
      }
    }

    // indexes are parsed later in ModelImpl.updateRelations(): skip unparsed ones here
    Set<String> indexNames = new HashSet<>();
    for (Index index: getIndexes()) {
      if (((IndexImpl) index).isParsed()) {
        index.validate();
        if (!indexNames.add(normalizeName(index.getName()))) {
          throw new ModelException("duplicate index name '" + index.getName() + "'", this);
        }
      }
    }

    for (Index index: getIndexes()) {
      IndexImpl indexImpl = (IndexImpl) index;
      if (indexImpl.isParsed()) {
        for (Index otherIndex : getIndexes()) {
          IndexImpl otherIndexImpl = (IndexImpl) otherIndex;
          if (otherIndex != index && otherIndexImpl.isParsed() && indexImpl.isLogicallyEqualTo(otherIndexImpl)) {
            throw new ModelException("index '" + index.getName() + "' is logically identical to '" + otherIndex.getName() + "'", this);
          }
        }
      }
    }

    Set<String> relationNames = new HashSet<>();
    for (Relation relation: getRelations()) {
      relation.validate();
      if (!relationNames.add(normalizeName(relation.getName()))) {
        throw new ModelException("duplicate relation name '" + relation.getName() + "'", this);
      }
    }

  }

  /**
   * Validates the relation-dependent settings.<br>
   * Assumes {@link ModelImpl#updateRelations} applied successfully.
   *
   * @throws ModelException if validation failed
   */
  public void validateRelated() throws ModelException {
    if (getTableProvidingEntity() == this) {
      if (StringHelper.isAllWhitespace(getTableName())) {
        throw new ModelException("configuration 'table := ...' missing", this);
      }
      for (Backend backend: factory.getBackends()) {
        try {
          backend.assertValidName(SqlNameType.TABLE_NAME, getTableName());
          backend.assertValidName(SqlNameType.TABLE_ALIAS, getTableAlias());
        }
        catch (RuntimeException rex) {
          throw new ModelException(rex.getMessage(), this, rex);
        }
      }
    }
    else  {
      if (!StringHelper.isAllWhitespace(getTableName())) {
        throw new ModelException("configuration 'table := ...' not allowed", this);
      }
      if (!StringHelper.isAllWhitespace(getTableAlias())) {
        throw new ModelException("configuration 'alias := ...' not allowed", this);
      }
    }

    if (isRootOfInheritanceHierarchy() && inheritanceType.isMappingToSuperTable()) {
      // set attributes to nullable (except the ones in root)
      for (Entity sub: getAllSubEntities()) {
        for (Attribute attribute: sub.getAttributes()) {
          ((AttributeImpl) attribute).setNullable(true);
        }
      }
    }

    if (getIntegrity().isCompositesCheckedByDatabase() &&
        // either all composite relations must be cascaded or none, not mixed!
        isDeletionCascaded() == null) {
      throw new ModelException(getIntegrity() +
              " integrity requires all composite relations to be either cascaded or non-cascaded", this);
    }
  }

  @Override
  public String sqlCreateTable(Backend backend) throws ModelException {
    return CodeFactory.getInstance().createSqlTable(this, backend);
  }

  @Override
  public boolean isComposite() {
    for (Relation relation: getRelationsIncludingSubEntities()) {
      if (relation.isComposite()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isEffectivelyComposite() {
    for (Relation relation: getRelationsIncludingSubEntities()) {
      if (relation.isComposite() && !relation.isEmbedding()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isAbstract() {
    return inheritanceType.isAbstractClass();
  }

  @Override
  public boolean isEmbedded() {
    return InheritanceType.EMBEDDED == inheritanceType;
  }

  @Override
  public boolean isTracked() {
    return getOptions().getTrackType().isTracked();
  }

  @Override
  public Boolean isDeletionCascaded() {
    Boolean cascaded = null;
    for (Relation relation: getRelations()) {
      if (relation.isComposite()) {
        if (cascaded == null) {
          cascaded = relation.isDeletionCascaded();
        }
        else if (cascaded != relation.isDeletionCascaded()) {
          return null;    // mixed
        }
      }
    }
    if (cascaded == null) {
      // no composite relation at all
      cascaded = false;
    }
    return cascaded;
  }

  @Override
  public boolean isRootOfInheritanceHierarchy() {
    return superEntityName == null && inheritanceType != InheritanceType.NONE;
  }


  @Override
  public Set<Attribute> getRootAttributes() {
    Set<Attribute> rootAttributes = new LinkedHashSet<>();
    for (List<Relation> compositePath: compositePaths) {
      Relation lastRelation = compositePath.getLast();
      if (lastRelation.getForeignAttribute() != null) {
        rootAttributes.add(lastRelation.getForeignAttribute());
      }
    }
    return rootAttributes;
  }

  @Override
  public Attribute getRootAttribute() {
    Set<Attribute> rootAttributes = getRootAttributes();
    return rootAttributes.size() == 1 ? rootAttributes.iterator().next() : null;
  }

  @Override
  public Set<Entity> getRootEntities() {
    Set<Entity> rootEntities = new LinkedHashSet<>();
    for (List<Relation> compositePath: compositePaths) {
      Relation firstRelation = compositePath.getFirst();
      rootEntities.add(firstRelation.getEntity());
    }
    return rootEntities;
  }

  @Override
  public Entity getRootEntity() {
    Set<Entity> rootEntities = getRootEntities();
    if (rootEntities.size() == 1) {
      Entity root = rootEntities.iterator().next();
      if (!root.isAbstract()) {
        return root;
      }
    }
    return null;
  }

  @Override
  public boolean isRootEntity() {
    if (rootEntity == null) {
      // not determined yet
      rootEntity = false;
      Entity entity = this;
      while (entity != null) {
        if (entity.getOptions().isRoot()) {
          rootEntity = true;
          break;
        }
        entity = entity.getSuperEntity();
      }
    }
    return rootEntity;
  }

  @Override
  public Entity getTableProvidingEntity() {
    InheritanceType ihType = getHierarchyInheritanceType();
    if (ihType.isMappingToOwnTable()) {
      return this;
    }
    if (ihType.isMappingToSuperTable()) {
      return getTopSuperEntity();
    }
    if (inheritanceType.isMappingToOwnTable()) {
      return this;
    }
    return null;
  }

  @Override
  public String toString() {
    return getName();
  }


  /**
   * Normalizes a name to detect misleading Java or column names.
   *
   * @param name the name
   * @return the normalized name
   */
  private String normalizeName(String name) {
    StringBuilder buf = new StringBuilder(name.length());
    for (int i=0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        buf.append(Character.toUpperCase(c));
      }
    }
    return buf.toString();
  }

}

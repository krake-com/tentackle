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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.tentackle.common.StringHelper;
import org.tentackle.common.Compare;
import org.tentackle.common.Constants;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeSorting;
import org.tentackle.model.CodeFactory;
import org.tentackle.model.DataType;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Integrity;
import org.tentackle.model.ModelElement;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parse.ConfigurationLine;
import org.tentackle.sql.Backend;

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

  private String name;                                      // the entities name
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
  private int inheritanceLevel;                             // number of super entities
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
  private boolean modelRoot;                                // is root entity according to model
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
    compositePaths = new HashSet<>();
    deepReferences = new ArrayList<>();
    deepReferencesToComponents = new ArrayList<>();
    indexes = new ArrayList<>();
  }

  @Override
  public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  @Override
  public ModelElement getParent() {
    return superEntity;
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
   * @param line the configuation line
   * @return true if known configuration processed
   * @throws ModelException if parsing failed
   */
  public boolean parseConfiguration(ConfigurationLine line) throws ModelException {

    boolean parsed = true;

    switch (line.getKey().toUpperCase()) {

      case NAME:
        String str = line.getValue();
        if (str.indexOf('.') >= 0) {
          throw line.createModelException("entity name must not contain package name");
        }
        if (!StringHelper.isValidJavaClassName(str)) {
          throw line.createModelException("'" + str + "' is not a valid Java class name");
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
          setIntegrity(Integrity.valueOf(line.getValue().toUpperCase()));
        }
        catch (IllegalArgumentException ex) {
          throw line.createModelException("illegal integrity level: " + line.getValue());
        }
        break;

      case INHERITANCE:
        try {
          setInheritanceType(InheritanceType.valueOf(line.getValue().toUpperCase()));
        }
        catch (IllegalArgumentException ex) {
          throw line.createModelException("illegal inhertitance type: " + line.getValue());
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
  public int getClassId() {
    return classId;
  }

  /**
   * Sets the entity id.
   *
   * @param classId the id
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
   * Sets the tablename.
   *
   * @param tableName the tablename
   */
  public void setTableName(String tableName) {
    if (tableName == null) {
      this.tableName = null;
      schemaName = null;
      tableNameWithoutSchema = null;
    }
    else  {
      this.tableName = tableName.toLowerCase();
      int ndx = tableName.indexOf('.');
      if (ndx > 0) {
        schemaName = tableName.substring(0, ndx);
        tableNameWithoutSchema = tableName.substring(ndx + 1);
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
    this.tableAlias = tableAlias;
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
      chain.add(0, entity);
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
  public int getInheritanceLevel() {
    return inheritanceLevel;
  }

  @Override
  public List<Entity> getSubEntities() {
    return subEntities;
  }

  @Override
  public List<Entity> getAllSubEntities() {
    List<Entity> childs = new ArrayList<>();
    collectSubEntities(childs, this);
    return childs;
  }

  @Override
  public List<Entity> getLeafEntities() {
    List<Entity> leafs = new ArrayList<>();
    collectLeafEntities(leafs, this);
    return leafs;
  }

  /**
   * Recursivly collects the leaf childs.
   *
   * @param leafs the list if leafs
   * @param entity the current entity to analyze
   */
  protected void collectLeafEntities(List<Entity> leafs, Entity entity) {
    if (entity.isAbstract()) {
      for (Entity child: entity.getSubEntities()) {
        collectLeafEntities(leafs, child);
      }
    }
    else  {
      leafs.add(entity);
    }
  }

  /**
   * Recursivly collects all sub entities.
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
    Set<Entity> associates = new HashSet<>();
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
    if (!processedEntities.contains(entity)) {
      processedEntities.add(entity);
      associates.add(entity);
      if (superEntity != null) {
        associates.add(superEntity);
        collectAssociatedEntities(associates, superEntity, processedEntities);
      }
      associates.addAll(getSubEntities());
      for (Entity child: getSubEntities()) {
        collectAssociatedEntities(associates, child, processedEntities);
      }
      for (Relation relation: getRelations()) {
        Entity relatedEntity = relation.getEntity();
        if (relatedEntity != null) {    // model may be incomplete...
          associates.add(relatedEntity);
          collectAssociatedEntities(associates, relatedEntity, processedEntities);
        }
      }
      for (Relation relation: getReferencingRelations()) {
        Entity foreignEntity = relation.getForeignEntity();
        if (foreignEntity != null) {    // model may be incomplete...
          associates.add(foreignEntity);
          collectAssociatedEntities(associates, foreignEntity, processedEntities);
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
    if (integrity == null) {
      throw new IllegalArgumentException("integrity cannot be set to null");
    }
    this.integrity = integrity;
  }

  /**
   * Sets the inheritance type.
   *
   * @param inheritanceType the inheritance type
   */
  public void setInheritanceType(InheritanceType inheritanceType) {
    if (inheritanceType == null) {
      throw new IllegalArgumentException("inheritance type cannot be set to null");
    }
    this.inheritanceType = inheritanceType;
  }

  /**
   * Sets the inheritance level.
   *
   * @param inheritanceLevel the inheritance level
   */
  public void setInheritanceLevel(int inheritanceLevel) {
    this.inheritanceLevel = inheritanceLevel;
  }

  /**
   * Sets the name of the super entity.
   *
   * @param superEntityName the super entity
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
   * Returns whether this entity is a root-entity according to model.
   *
   * @return true if root entity
   */
  @Override
  public boolean isRootEntityAccordingToModel() {
    return modelRoot;
  }

  /**
   * Sets whether this entity is a root-entity according to model.
   *
   * @param modelRoot true if root entity
   */
  public void setRootEntityAccordingToModel(boolean modelRoot) {
    this.modelRoot = modelRoot;
  }

  /**
   * Returns whether this entity should provide a root class id according to model.
   *
   * @return true if should provide a root class id
   */
  @Override
  public boolean isProvidingRootClassIdAccordingToModel() {
    return modelRootClassId;
  }

  /**
   * Sets whether this entity should provide a root class id according to model.
   *
   * @param modelRootClassId  true if should provide a root class id
   */
  public void setProvidingRootClassIdAccordingToModel(boolean modelRootClassId) {
    this.modelRootClassId = modelRootClassId;
  }

  /**
   * Returns whether this entity should provide a root id according to model.
   *
   * @return true if should provide a root id
   */
  @Override
  public boolean isProvidingRootIdAccordingToModel() {
    return modelRootId;
  }

  /**
   * Sets whether this entity should provide a root id according to model.
   *
   * @param modelRootId  true if should provide a root id
   */
  public void setProvidingRootIdAccordingToModel(boolean modelRootId) {
    this.modelRootId = modelRootId;
  }


  @Override
  public List<Attribute> getInheritedAttributes() {
    List<Attribute> inheritedAttributes = new ArrayList<>();
    Entity se = getSuperEntity();
    while (se != null) {
      inheritedAttributes.addAll(0, se.getAttributes());
      se = se.getSuperEntity();
    }
    return inheritedAttributes;
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
  public List<Attribute> getAttributesIncludingInherited() {
    List<Attribute> allAttributes = new ArrayList<>(getInheritedAttributes());
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
  public List<Attribute> getAllAttributes() {
    List<Attribute> allAttributes = new ArrayList<>(getInheritedAttributes());
    allAttributes.addAll(attributes);
    allAttributes.addAll(getSubEntityAttributes());
    allAttributes = moveIdSerialToEnd(allAttributes);
    return allAttributes;
  }

  @Override
  public List<Attribute> getTableAttributes() {
    InheritanceType ihType = getHierarchyInheritanceType();
    List<Attribute> tableAttributes;
    if (ihType.isMappingToOwnTable()) {
      tableAttributes = new ArrayList<>(attributes);
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
          tableAttributes.addAll(child.getAttributes());
        }
        tableAttributes = moveIdSerialToEnd(tableAttributes);
      }
    }
    else {
      tableAttributes = getAttributesIncludingInherited();
    }
    return tableAttributes;
  }

  @Override
  public List<Attribute> getMappedAttributes() {
    List<Attribute> mappedAttributes;
    if (isAbstract()) {
      if (inheritanceType.isMappingToOwnTable()) {
        // MULTI
        mappedAttributes = attributes;
      }
      else if (inheritanceType.isMappingToSuperTable()) {
        // SINGLE: strip id and serial. It's mapped by the leafs
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
   * Moved id and serial to the end of a new created attribute list.
   *
   * @param attributes the attribute list
   * @return the new filtered attribute list
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
   * @return the new filtered attribute list
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
  public List<Relation> getTableRelations() {
    InheritanceType ihType = getHierarchyInheritanceType();
    List<Relation> tableRelations;
    if (ihType.isMappingToOwnTable()) {
      tableRelations = relations;
    }
    else if (ihType.isMappingToSuperTable()) {
      tableRelations = new ArrayList<>();
      if (isRootOfInheritanceHierarchy()) {
        tableRelations.addAll(relations);
        for (Entity sub: getAllSubEntities()) {
          tableRelations.addAll(sub.getRelations());
        }
      }
    }
    else {
      tableRelations = getRelationsIncludingInherited();
    }
    return tableRelations;
  }

  @Override
  public Set<List<Relation>> getCompositePaths() {
    return compositePaths;
  }

  @Override
  public Set<List<Relation>> getAllCompositePaths() {
    if (allCompositePaths == null) {
      allCompositePaths = new HashSet<>();
      Entity currentEntity = this;
      while (currentEntity != null) {
        allCompositePaths.addAll(currentEntity.getCompositePaths());
        currentEntity = currentEntity.getSuperEntity();
      }
    }
    return allCompositePaths;
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
      columnName = columnName.toLowerCase();
      Entity entity = this;
      while (entity != null) {
        for (Attribute attribute: entity.getAttributes()) {
          if (columnName.equals(attribute.getColumnName())) {
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
  public List<Attribute> getUniqueDomainKey() {
    if (uniqueDomainKey == null) {
      uniqueDomainKey = new ArrayList<>();
      if (isRootEntity()) {
        getUniqueDomainKeyImpl(uniqueDomainKey, this);
      }
    }
    return uniqueDomainKey;
  }

  /**
   * Adds the unique domain keys recursively for given entity to the list.
   *
   * @param uniqueDomainKeys the unique domain key attributes
   * @param entity the entity to inspect
   */
  protected void getUniqueDomainKeyImpl(List<Attribute> uniqueDomainKeys, Entity entity) {
    for (Attribute attribute : entity.getAttributesIncludingInherited()) {
      if (attribute.getOptions().isDomainKey()) {
        uniqueDomainKeys.add(attribute);
      }
    }
    for (Entity component: entity.getComponentsIncludingInherited()) {
      if (!component.isRootEntity()) {   // stop at sub-roots!
        getUniqueDomainKeyImpl(uniqueDomainKeys, component);
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
          if (name.equalsIgnoreCase(relation.getName())) {
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
   * Validates the entity without relation dependent settings.
   *
   * @throws ModelException if validation failed
   */
  public void validate() throws ModelException {

    if (StringHelper.isAllWhitespace(getName())) {
      throw new ModelException("configuration 'name = ...' missing", this);
    }

    if (!isAbstract()) {
      if (getClassId() == 0) {
        throw new ModelException("configuration 'id = ...' missing", this);
      }
    }
    else  {
      if (getClassId() != 0) {
        throw new ModelException("configuration 'id = ...' not allowed", this);
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
      Attribute other = javaNames.get(normalizedColumnName);
      if (other != null && !other.equals(attribute)) {
        throw new ModelException("misleading column name '" + attribute.getColumnName() +
                                 "' for Java name '" + attribute.getName() +
                                 "' -> danger of confusion with '" +
                                 other.getName() + " / " + other.getColumnName() + "'", this);
      }
      if (!columnNames.add(normalizedColumnName)) {
        throw new ModelException("duplicate column name '" + attribute.getColumnName() + "'", this);
      }
    }

    Set<String> indexNames = new HashSet<>();
    for (Index index: getIndexes()) {
      if (((IndexImpl) index).isParsed()) {
        index.validate();
        if (!indexNames.add(normalizeName(index.getName()))) {
          throw new ModelException("duplicate index name '" + index.getName() + "'", this);
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
   * Validates the relation dependent settings.<br>
   * Assumes {@link ModelImpl#updateRelations} applied successfully.
   *
   * @throws ModelException if validation failed
   */
  public void validateRelated() throws ModelException {
    if (getTableProvidingEntity() == this) {
      if (StringHelper.isAllWhitespace(getTableName())) {
        throw new ModelException("configuration 'table = ...' missing", this);
      }
      for (Backend backend: factory.getBackends()) {
        try {
          backend.assertValidName("table name", getTableName());
          backend.assertValidName("table alias", getTableAlias());
        }
        catch (RuntimeException rex) {
          throw new ModelException(rex.getMessage(), this, rex);
        }
      }
    }
    else  {
      if (!StringHelper.isAllWhitespace(getTableName())) {
        throw new ModelException("configuration 'table = ...' not allowed", this);
      }
      if (!StringHelper.isAllWhitespace(getTableAlias())) {
        throw new ModelException("configuration 'alias = ...' not allowed", this);
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

    if (getIntegrity().isCompositesCheckedByDatabase()) {
      // either all composite relations must be cascaded or none, not mixed!
      if (isDeletionCascaded() == null) {
        throw new ModelException(getIntegrity() +
                " integrity requires all composite relations to be either cascaded or non-cascaded", this);
      }
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
  public boolean isAbstract() {
    return inheritanceType.isAbstractClass();
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
    Set<Attribute> rootAttributes = new HashSet<>();
    for (List<Relation> compositePath: compositePaths) {
      Relation lastRelation = compositePath.get(compositePath.size() - 1);
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
    Set<Entity> rootEntities = new HashSet<>();
    for (List<Relation> compositePath: compositePaths) {
      Relation firstRelation = compositePath.get(0);
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
   * Normalizes a name to detect misleading java- or column names.
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

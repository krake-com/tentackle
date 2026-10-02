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

import org.tentackle.sql.Backend;

import java.util.List;
import java.util.Set;

/**
 * The entity.
 * <p>
 * This is the top-level model element.
 *
 * @author harald
 */
public interface Entity extends ModelElement {

  /**
   * Gets the unique id of this entity.<br>
   * A sort of class-ID that is used to map from a small number to a class or classname.
   *
   * @return the entity class id
   */
  int getClassId();

  /**
   * Gets the database table name.<br>
   * An optional schema may be prepended separated by a dot.
   * <p>
   * Important: similar to the entity name, the table name must be unique among the whole model
   * and all schemas.
   * This restriction avoids unnecessary confusion and reduces the potential for errors
   * in SQL statements.
   *
   * @return the table name (always in lowercase), null if the entity doesn't map to its own table
   */
  String getTableName();

  /**
   * Gets the schema name.
   *
   * @return the schema (always in lowercase), null if default
   */
  String getSchemaName();

  /**
   * Gets the table name without the leading schema.
   * <p>
   * Important: similar to the entity name, the table name must be unique among the whole model
   * and all schemas.
   * This restriction avoids unnecessary confusion and reduces the potential for errors
   * in SQL statements.
   *
   * @return the table name relative to its schema (always in lowercase), null if the entity doesn't map to its own table
   */
  String getTableNameWithoutSchema();

  /**
   * Gets the table alias for joined SQL SELECTs.<br>
   * The alias is unique among all tables.
   * If the model does not provide an alias, a unique alias will be generated.
   *
   * @return the table alias
   */
  String getTableAlias();

  /**
   * Gets the table alias originally configured in the model.
   *
   * @return the fixed alias, null if automatic
   */
  String getDefinedTableAlias();

  /**
   * Gets the referential integrity mode.
   *
   * @return the integrity mode
   */
  Integrity getIntegrity();

  /**
   * Gets the options.
   *
   * @return the options
   */
  EntityOptions getOptions();

  /**
   * Gets the attributes.
   *
   * @return the attributes
   */
  List<Attribute> getAttributes();

  /**
   * Gets the inherited attributes.
   *
   * @return the attributes
   * @throws ModelException if the model is inconsistent
   */
  List<Attribute> getInheritedAttributes() throws ModelException;

  /**
   * Gets the attributes of embedded entities.
   *
   * @return the embedded attributes
   * @throws ModelException if the model is inconsistent
   */
  List<Attribute> getEmbeddedAttributes() throws ModelException;

  /**
   * Gets the relations of all embedded entities.<br>
   * Due to the restriction of embedded entities, these relations are all non-composite object relations.
   *
   * @param embeddingEntity the effectively embedding entity
   * @return the list of embedded relations
   * @throws ModelException if failed
   */
  List<Relation> getEmbeddedRelations(Entity embeddingEntity) throws ModelException;

  /**
   * Gets the attributes from the sub entities.
   *
   * @return the attributes
   */
  List<Attribute> getSubEntityAttributes();

  /**
   * Gets the attributes including the inherited ones.
   *
   * @return the attributes
   * @throws ModelException if the model is inconsistent
   */
  List<Attribute> getAttributesIncludingInherited() throws ModelException;

  /**
   * Gets the attributes including the ones from the sub entities.
   *
   * @return the attributes
   */
  List<Attribute> getAttributesIncludingSubEntities();

  /**
   * Gets all attributes including inherited and from sub entities.
   *
   * @return the attributes
   * @throws ModelException if the model is inconsistent
   */
  List<Attribute> getAllAttributes() throws ModelException;

  /**
   * Gets the attributes of the table mapped by this entity.
   *
   * @return the attributes, empty if the entity does not map to a table or does not provide attributes
   * @throws ModelException if the model is inconsistent
   */
  List<Attribute> getTableAttributes() throws ModelException;

  /**
   * Gets the attributes mapped by the persistence layer for this entity.
   *
   * @return the attributes
   * @throws ModelException if the model is inconsistent
   */
  List<Attribute> getMappedAttributes() throws ModelException;

  /**
   * Gets an attribute by its Java name.
   *
   * @param javaName the Java name
   * @param all true if include inherited attributes
   * @return the attribute, null if no such attribute
   */
  Attribute getAttributeByJavaName(String javaName, boolean all);

  /**
   * Gets an attribute by its database column name.
   *
   * @param columnName the database column name (case-insensitive)
   * @param all true if include inherited attributes
   * @return the attribute, null if no such attribute
   */
  Attribute getAttributeByColumnName(String columnName, boolean all);

  /**
   * Gets the attribute that holds the context id.
   *
   * @return the context attribute, null if none
   */
  Attribute getContextIdAttribute();

  /**
   * Gets the attributes describing the unique domain key.
   *
   * @return the UDK attributes, empty if no UDK
   * @throws ModelException if the model is inconsistent
   */
  List<Attribute> getUniqueDomainKey() throws ModelException;

  /**
   * Gets the default sorting for this entity.
   *
   * @return the sorting, null if unsorted by default
   */
  List<AttributeSorting> getSorting();

  /**
   * Gets the relations.
   *
   * @return the relations
   */
  List<Relation> getRelations();

  /**
   * Gets the inherited relations.
   *
   * @return the relations
   */
  List<Relation> getInheritedRelations();

  /**
   * Gets the relations from the sub entities.
   *
   * @return the relations
   */
  List<Relation> getSubEntityRelations();

  /**
   * Gets all including inherited relations.
   *
   * @return the relations
   */
  List<Relation> getRelationsIncludingInherited();

  /**
   * Gets all relations including the ones from the sub entities.
   *
   * @return the relations
   */
  List<Relation> getRelationsIncludingSubEntities();

  /**
   * Gets all relations including the inherited and the ones from the sub entities.
   *
   * @return the relations
   */
  List<Relation> getAllRelations();

  /**
   * Gets the relations of the table mapped by this entity.<br>
   * Will also contain the relations from embedded entities.
   *
   * @return the relations, empty if the entity does not map to a table or provides no relations
   * @throws ModelException if the model is inconsistent
   */
  List<Relation> getTableRelations() throws ModelException;

  /**
   * Gets a relation by its name.
   *
   * @param name the relation's name
   * @param all true if include inherited relations
   * @return the relation, null if no such relation
   */
  Relation getRelation(String name, boolean all);

  /**
   * Gets all relations for a given entity type.
   *
   * @param entity the entity
   * @param all true if include inherited relations
   * @return the relations, empty if no such relation
   */
  List<Relation> getRelations(Entity entity, boolean all);

  /**
   * Gets the relations from other entities directly referencing this entity.
   *
   * @return the relations
   */
  List<Relation> getReferencingRelations();

  /**
   * Gets the relations from other entities referencing any super-entity.
   *
   * @return the relations
   */
  List<Relation> getInheritedReferencingRelations();

  /**
   * Gets the relations from other entities referencing any sub-entity.
   *
   * @return the relations
   */
  List<Relation> getSubEntityReferencingRelations();

  /**
   * Gets the relations from other entities referencing this entity or any super-entity.
   *
   * @return the relations
   */
  List<Relation> getReferencingRelationsIncludingInherited();

  /**
   * Gets the relations from other entities referencing this entity or any sub-entity.
   *
   * @return the relations
   */
  List<Relation> getReferencingRelationsIncludingSubEntities();

  /**
   * Gets the relations from other entities referencing this entity or any sub-entity or super entity.
   * @return the relations
   */
  List<Relation> getAllReferencingRelations();

  /**
   * Gets the indexes.
   *
   * @return the indexes
   */
  List<Index> getIndexes();

  /**
   * Gets inherited indexes.
   *
   * @return the indexes
   */
  List<Index> getInheritedIndexes();

  /**
   * Gets indexes from sub entities.
   *
   * @return the indexes
   */
  List<Index> getSubEntityIndexes();

  /**
   * Gets all including inherited indexes.
   *
   * @return the indexes
   */
  List<Index> getIndexesIncludingInherited();

  /**
   * Gets all including indexes from sub entities.
   *
   * @return the indexes
   */
  List<Index> getIndexesIncludingSubEntities();

  /**
   * Gets all including inherited and indexes from sub entities.
   *
   * @return the indexes
   */
  List<Index> getAllIndexes();

  /**
   * Gets the indexes of the table mapped by this entity.
   *
   * @return the indexes, empty if the entity does not map to a table or provides no indexes
   */
  List<Index> getTableIndexes();

  /**
   * Gets an index by its name.
   *
   * @param name the index name
   * @param all true if include inherited indexes
   * @return the index, null if no such index
   */
  Index getIndex(String name, boolean all);

  /**
   * Creates the table creation SQL code.
   *
   * @param backend the backend to create SQL code for
   * @return the SQL code
   * @throws ModelException if the model is inconsistent
   */
  String sqlCreateTable(Backend backend) throws ModelException;

  /**
   * Returns whether the entity or one of its sub entities has composite relations.
   *
   * @return true if composite entity
   */
  boolean isComposite();

  /**
   * Returns whether the entity or one of its sub entities has composite non-embedded relations.
   *
   * @return true if not only embedded composite relations
   */
  boolean isEffectivelyComposite();

  /**
   * Returns whether the entity is abstract, i.e. cannot be instantiated.
   *
   * @return true if abstract
   */
  boolean isAbstract();

  /**
   * Returns whether this is an embedded entity.<br>
   * Such entities are embedded via embedding relations.
   * Embedded entities are mapped to their embedding entity, which is always a regular non-embedded entity.
   * Embedded entities can embed other embedded entities but cannot have regular components.
   * However, embedded entities can have non-composite object relations (1:1).
   *
   * @return true if embedded
   * @see SelectionType#EMBEDDED
   */
  boolean isEmbedded();

  /**
   * Returns whether the entity is tracked.
   *
   * @return true if tracked
   */
  boolean isTracked();

  /**
   * Returns the cascade-flag if it is the same for all composite relations.<br>
   * If there are no composite relations at all, false is returned.
   *
   * @return the cascade flag, null if mixed composite relations
   */
  Boolean isDeletionCascaded();

  /**
   * Returns the attributes linking this component to its composite parents.<br>
   * The attributes are derived from the composite paths.
   * For direct components of a root entity, they correspond to the root id.
   *
   * @return the attributes, empty set if this is not a component
   */
  Set<Attribute> getRootAttributes();

  /**
   * Returns the single root attribute if there is exactly one.
   *
   * @return the attribute, null if none or more than one
   */
  Attribute getRootAttribute();

  /**
   * Returns the root entities.<br>
   * Returns all roots, even abstract ones.
   * The roots are derived from the composite paths.
   *
   * @return the root entities, empty set if this is not a component
   */
  Set<Entity> getRootEntities();

  /**
   * Returns the single root entity if there is exactly one, and it is not abstract.
   *
   * @return the root entity, null if none or abstract or more than one
   */
  Entity getRootEntity();

  /**
   * Returns whether this is a root entity.<br>
   * Inspects the super entities as well.
   *
   * @return true if root entity
   */
  boolean isRootEntity();

  /**
   * Returns whether the entity is the root of an inheritance hierarchy.
   *
   * @return true if root
   */
  boolean isRootOfInheritanceHierarchy();

  /**
   * Gets the entity that provides the database table.
   *
   * @return the table providing entity, null if the entity is abstract and does not correspond to a single table
   */
  Entity getTableProvidingEntity();

  /**
   * Gets the inheritance type.
   *
   * @return the inheritance type
   */
  InheritanceType getInheritanceType();

  /**
   * Gets the inheritance type of the hierarchy.<br>
   * For leaf children, which have {@link InheritanceType#NONE}, the type of the super entity is returned.
   *
   * @return the effective inheritance type
   */
  InheritanceType getHierarchyInheritanceType();

  /**
   * Gets the name of the super class entity.
   *
   * @return the super class name, null if not inherited
   */
  String getSuperEntityName();

  /**
   * Gets the super class entity if inherited.
   *
   * @return the super entity, null if not inherited
   */
  Entity getSuperEntity();

  /**
   * Gets all super entities.
   *
   * @return the super entities, starting with the direct super entity up to the top super entity
   */
  List<Entity> getSuperEntities();

  /**
   * Gets the top most entity of the inheritance tree.<br>
   * Returns this entity if there is no inheritance.
   *
   * @return the root of the inheritance hierarchy
   */
  Entity getTopSuperEntity();

  /**
   * Gets the inheritance chain from this entity down to the given sub entity.
   *
   * @param subEntity the sub entity
   * @return the chain starting with this entity and ending with the sub entity
   * @throws ModelException if subEntity does not inherit this entity
   */
  List<Entity> getInheritanceChain(Entity subEntity) throws ModelException;

  /**
   * Gets the direct sub entities.
   *
   * @return the sub entities, empty list if no sub entities
   */
  List<Entity> getSubEntities();

  /**
   * Gets all sub entities.
   *
   * @return the sub entities, empty list if no sub entities
   */
  List<Entity> getAllSubEntities();

  /**
   * Gets the list of non-abstract leaf sub entities.<br>
   * If this entity is already non-abstract, the list consists of this entity.
   *
   * @return the leaf children, empty list if no leaf children
   */
  List<Entity> getLeafEntities();

  /**
   * Gets a set of all entities that are associated with this entity.<br>
   * The kind of association can be a relation, a foreign relation or some hierarchy dependency
   * or a combination thereof.
   *
   * @return the set of entities including this entity
   */
  Set<Entity> getAssociatedEntities();

  /**
   * Gets the paths of composite relations to this entity.<br>
   * Notice that this includes paths to sub-entities as well.
   *
   * @return the paths, empty set if this is not a component of any other entity
   */
  Set<List<Relation>> getCompositePaths();

  /**
   * Gets the paths of composite relations to this entity.<br>
   * Notice that this includes paths to sub-entities and super-entities as well.
   *
   * @return the paths, empty set if this is not a component of any other entity
   */
  Set<List<Relation>> getAllCompositePaths();

  /**
   * Gets the paths of embedding relations to this entity.
   *
   * @return the paths, empty set if this is not an embedded entity
   */
  Set<List<Relation>> getEmbeddingPaths();

  /**
   * Gets the embedding entities for this embedded entity.
   *
   * @return the embedding entities, empty if this is not an embedded entity
   */
  Set<Entity> getEmbeddingEntities();

  /**
   * Gets the direct components of this entity.
   *
   * @return the components
   */
  Set<Entity> getComponents();

  /**
   * Gets the direct and inherited components of this entity.
   *
   * @return the components
   */
  Set<Entity> getComponentsIncludingInherited();

  /**
   * Gets all direct and indirect components of this entity.
   *
   * @return the components
   */
  Set<Entity> getAllComponents();

  /**
   * Returns whether this entity should provide a root class id according to the model.
   *
   * @return true if the entity should provide a root class id
   */
  boolean isProvidingRootClassIdAccordingToModel();

  /**
   * Returns whether this entity should provide a root id according to the model.
   *
   * @return true if the entity should provide a root id
   */
  boolean isProvidingRootIdAccordingToModel();

  /**
   * Returns whether this entity is a root entity, according to the model.
   *
   * @return true if root entity
   */
  boolean isRootEntityAccordingToModel();

  /**
   * Gets the deep references to this entity.
   *
   * @return the deep references, empty if no deep references
   */
  List<Relation> getDeepReferences();

  /**
   * Returns whether this entity or one of its components is deeply referenced.
   *
   * @return true if deeply referenced, false if not
   */
  boolean isDeeplyReferenced();

  /**
   * Gets the deep references to components of this root entity.<br>
   * Will contain all deep references to all components, not only the direct children.
   *
   * @return the deep references, empty if this is not a root entity or there are no deep references
   */
  List<Relation> getDeepReferencesToComponents();

  /**
   * Gets the deeply referenced components.<br>
   * Will contain the relations to components where {@link #isDeeplyReferenced()} is true.
   *
   * @return the composite relations deeply referenced
   */
  List<Relation> getDeeplyReferencedComponents();

}

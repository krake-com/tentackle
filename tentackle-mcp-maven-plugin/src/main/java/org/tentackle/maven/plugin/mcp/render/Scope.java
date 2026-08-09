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

package org.tentackle.maven.plugin.mcp.render;

import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;

import java.util.List;
import java.util.Locale;

/**
 * Which part of an inheritance hierarchy a member list covers.
 * <p>
 * The model API repeats the same own / inherited / sub-entity / all quartet for attributes, relations, indexes,
 * and referencing relations. Collapsing it into one argument turns four times four getters into one.
 *
 * @author harald
 */
public enum Scope {

  /** Only what the entity declares itself. */
  OWN,

  /** What the entity declares plus what it inherits from its super entities. */
  INHERITED,

  /** What the entity declares plus what its sub entities add. */
  SUBENTITIES,

  /** Everything in the hierarchy: own, inherited, and sub-entity members. */
  ALL;

  /** The allowed values, for a JSON schema enumeration. */
  public static final String[] NAMES = { "own", "inherited", "subentities", "all" };


  /**
   * Parses a scope.
   *
   * @param name the scope name, null or empty for the default
   * @param defaultScope the scope to use if none given
   * @return the scope
   * @throws IllegalArgumentException if the name is not a scope
   */
  public static Scope parse(String name, Scope defaultScope) {
    if (name == null || name.isBlank()) {
      return defaultScope;
    }
    try {
      return valueOf(name.trim().toUpperCase(Locale.ROOT));
    }
    catch (IllegalArgumentException iax) {
      throw new IllegalArgumentException("unknown scope '" + name + "', expected one of " +
                                         String.join(", ", NAMES), iax);
    }
  }

  /**
   * Gets the attributes of an entity in this scope.
   *
   * @param entity the entity
   * @return the attributes
   * @throws ModelException if the model is inconsistent
   */
  public List<Attribute> attributes(Entity entity) throws ModelException {
    return switch (this) {
      case OWN -> entity.getAttributes();
      case INHERITED -> entity.getAttributesIncludingInherited();
      case SUBENTITIES -> entity.getAttributesIncludingSubEntities();
      case ALL -> entity.getAllAttributes();
    };
  }

  /**
   * Gets the relations of an entity in this scope.
   *
   * @param entity the entity
   * @return the relations
   */
  public List<Relation> relations(Entity entity) {
    return switch (this) {
      case OWN -> entity.getRelations();
      case INHERITED -> entity.getRelationsIncludingInherited();
      case SUBENTITIES -> entity.getRelationsIncludingSubEntities();
      case ALL -> entity.getAllRelations();
    };
  }

  /**
   * Gets the relations referencing an entity in this scope.
   *
   * @param entity the entity
   * @return the referencing relations
   */
  public List<Relation> referencingRelations(Entity entity) {
    return switch (this) {
      case OWN -> entity.getReferencingRelations();
      case INHERITED -> entity.getReferencingRelationsIncludingInherited();
      case SUBENTITIES -> entity.getReferencingRelationsIncludingSubEntities();
      case ALL -> entity.getAllReferencingRelations();
    };
  }

  /**
   * Gets the indexes of an entity in this scope.
   *
   * @param entity the entity
   * @return the indexes
   */
  public List<Index> indexes(Entity entity) {
    return switch (this) {
      case OWN -> entity.getIndexes();
      case INHERITED -> entity.getIndexesIncludingInherited();
      case SUBENTITIES -> entity.getIndexesIncludingSubEntities();
      case ALL -> entity.getAllIndexes();
    };
  }

  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT);
  }

}

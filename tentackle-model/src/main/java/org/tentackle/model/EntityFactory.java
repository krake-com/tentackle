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

import java.util.Collection;
import org.tentackle.sql.Backend;

/**
 * A factory for entity models.
 *
 * @author harald
 */
public interface EntityFactory {

  /**
   * Sets the backends to validate the model against.
   *
   * @param backends the backends, null to disable check
   */
  void setBackends(Collection<Backend> backends);

  /**
   * Gets the backends to validate the model against.
   *
   * @return the backends, empty if check is disabled, never null
   */
  Collection<Backend> getBackends();

  /**
   * Creates an empty entity.
   *
   * @param sourceInfo source information, null if none
   * @return the created entity
   */
  Entity createEntity(SourceInfo sourceInfo);

  /**
   * Creates an entity from a model source.
   *
   * @param model the model source
   * @param modelDefaults the model defaults, null if none
   * @return the created entity
   * @throws ModelException if parsing the model failed
   */
  Entity createEntity(String model, ModelDefaults modelDefaults) throws ModelException;

  /**
   * Creates an attribute.
   *
   * @param entity the entity this attribute belongs to
   * @param sourceInfo source information, null if none
   * @param implicit true if implicit attribute
   * @return the created attribute
   */
  Attribute createAttribute(Entity entity, SourceInfo sourceInfo, boolean implicit);

  /**
   * Creates attribute options.
   *
   * @param attribute the attribute
   * @param sourceInfo source information, null if none
   * @return the created options
   */
  AttributeOptions createAttributeOptions(Attribute attribute, SourceInfo sourceInfo);

  /**
   * Creates entity options.
   *
   * @param entity the entity
   * @param sourceInfo source information, null if none
   * @return the created options
   */
  EntityOptions createEntityOptions(Entity entity, SourceInfo sourceInfo);

  /**
   * Creates an index.
   *
   * @param entity the entity this index belongs to
   * @param sourceInfo source information, null if none
   * @return the created index
   */
  Index createIndex(Entity entity, SourceInfo sourceInfo);

  /**
   * Creates an index attribute.
   *
   * @param index the index
   * @return the created index attribute
   */
  IndexAttribute createIndexAttribute(Index index);

  /**
   * Creates a relation.
   *
   * @param entity the entity this relation belongs to
   * @param sourceInfo source information, null if none
   * @return the created relation
   */
  Relation createRelation(Entity entity, SourceInfo sourceInfo);

}

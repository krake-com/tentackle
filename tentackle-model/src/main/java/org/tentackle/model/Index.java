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

import java.util.List;
import org.tentackle.sql.Backend;

/**
 * Describes a database index.
 *
 * @author harald
 */
public interface Index extends ModelElement {

  /**
   * Gets the entity this index belongs to.
   *
   * @return the entity
   */
  Entity getEntity();

  /**
   * Creates the database index name for given entity.
   *
   * @param entity the entity
   * @return the effective sql name
   */
  String createDatabaseIndexName(Entity entity);

  /**
   * Gets the comment.
   *
   * @return the comment
   */
  String getComment();

  /**
   * Returns whether the index is unique.
   *
   * @return true if unique, else non-unique
   */
  boolean isUnique();

  /**
   * Gets the optional filter condition.
   *
   * @return the condition, null if none
   */
  String getFilterCondition();

  /**
   * Gets the index attributes.
   *
   * @return the attributes
   */
  List<IndexAttribute> getAttributes();

  /**
   * Validates the index.
   *
   * @throws ModelException if validation failed
   */
  void validate() throws ModelException;

  /**
   * Creates the index creation sql code.
   *
   * @param backend the backend to create sql code for
   * @param entity the entity to create the index for
   * @return the SQL code
   */
  String sqlCreateIndex(Backend backend, Entity entity);

}

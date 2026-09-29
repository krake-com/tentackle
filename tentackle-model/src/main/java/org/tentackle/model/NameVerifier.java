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

import org.tentackle.common.ServiceFactory;

interface NameVerifierHolder {
  NameVerifier INSTANCE = ServiceFactory.createService(NameVerifier.class);
}

/**
 * Enforces application-specific rules for names.<br>
 * This is an additional check to the backend rules.
 */
public interface NameVerifier {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static NameVerifier getInstance() {
    return NameVerifierHolder.INSTANCE;
  }

  /**
   * Sets the verifier enabled.
   *
   * @param enabled true if enabled (default)
   */
  void setEnabled(boolean enabled);

  /**
   * Returns whether the verifier is enabled.
   *
   * @return true if enabled (default)
   */
  boolean isEnabled();

  /**
   * Verifies the Java name of an entity.
   *
   * @param entity the entity
   * @return null if ok, else a diagnostic message
   */
  String verifyEntityName(Entity entity);

  /**
   * Verifies the table name of an entity.
   *
   * @param entity the entity
   * @return null if ok, else a diagnostic message
   */
  String verifyTableName(Entity entity);

  /**
   * Verifies the table alias of an entity.
   *
   * @param entity the entity
   * @return null if ok, else a diagnostic message
   */
  String verifyTableAlias(Entity entity);

  /**
   * Verifies the Java name of an attribute.
   *
   * @param attribute the attribute
   * @return null if ok, else a diagnostic message
   */
  String verifyAttributeName(Attribute attribute);

  /**
   * Verifies the Java name of a relation.
   *
   * @param relation the relation
   * @return null if ok, else a diagnostic message
   */
  String verifyRelationName(Relation relation);

  /**
   * Verifies the column name of an attribute.
   *
   * @param attribute the attribute
   * @return null if ok, else a diagnostic message
   */
  String verifyColumnName(Attribute attribute);

  /**
   * Verifies the name of an index.
   *
   * @param index the index
   * @return null if ok, else a diagnostic message
   */
  String verifyIndexName(Index index);

}

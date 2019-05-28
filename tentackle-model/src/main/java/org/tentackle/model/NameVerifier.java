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
 * Enforces application specific rules for names.<br>
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
   * Verifies a name of an entity.
   *
   * @param name the name
   * @return null if ok, else a diagnostic message
   */
  String verifyEntityName(String name);

  /**
   * Verifies a table name.
   *
   * @param name the name
   * @return null if ok, else a diagnostic message
   */
  String verifyTableName(String name);

  /**
   * Verifies a table alias.
   *
   * @param name the name
   * @return null if ok, else a diagnostic message
   */
  String verifyTableAlias(String name);

  /**
   * Verifies a name of an attribute.
   *
   * @param name the name
   * @return null if ok, else a diagnostic message
   */
  String verifyAttributeName(String name);

  /**
   * Verifies a column name.
   *
   * @param name the name
   * @return null if ok, else a diagnostic message
   */
  String verifyColumnName(String name);

  /**
   * Verifies an index name.
   *
   * @param name the name
   * @return null if ok, else a diagnostic message
   */
  String verifyIndexName(String name);

}

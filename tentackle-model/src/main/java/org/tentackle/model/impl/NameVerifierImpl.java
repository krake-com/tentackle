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

package org.tentackle.model.impl;

import org.tentackle.common.Service;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.NameVerifier;

/**
 * Default implementation of a {@link NameVerifier}.
 */
@Service(NameVerifier.class)
public class NameVerifierImpl implements NameVerifier {

  private static final String TABLE_NAME = "table name '";
  private static final String TABLE_ALIAS = "table alias '";
  private static final String COLUMN_NAME = "column name '";

  private static final String MIXED_UC_LC = "' contains mixed upper- and lowercase characters";
  private static final String QUOTED = "' is quoted, which is only allowed for [PROVIDED] entities";


  /**
   * Creates the name verifier.
   */
  public NameVerifierImpl() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String verifyEntityName(Entity entity) {
    String name = entity.getName();
    return StringHelper.isValidJavaClassName(name) ? null : "'" + name + "' is not a valid Java class name";
  }

  @Override
  public String verifyTableName(Entity entity) {
    String name = entity.getTableName();
    if (isNotQuotedOrProvided(name, entity)) {
      return isAllUpperOrLowerCase(name, entity) ? null : TABLE_NAME + name + MIXED_UC_LC;
    }
    return TABLE_NAME + name + QUOTED;
  }

  @Override
  public String verifyTableAlias(Entity entity) {
    String name = entity.getTableAlias();
    if (isNotQuotedOrProvided(name, entity)) {
      return isAllUpperOrLowerCase(name, entity) ? null : TABLE_ALIAS + name + MIXED_UC_LC;
    }
    return TABLE_ALIAS + name + QUOTED;
  }

  @Override
  public String verifyAttributeName(Attribute attribute) {
    String name = attribute.getName();
    return StringHelper.isValidJavaIdentifier(name) ? null : "'" + name + "' is not a valid Java identifier name";
  }

  @Override
  public String verifyColumnName(Attribute attribute) {
    String name = attribute.getColumnName();
    if (isNotQuotedOrProvided(name, attribute.getEntity())) {
      return isAllUpperOrLowerCase(name, attribute.getEntity()) ? null : COLUMN_NAME + name + MIXED_UC_LC;
    }
    return COLUMN_NAME + name + QUOTED;
  }

  @Override
  public String verifyIndexName(Index index) {
    String name = index.getName();  // index not allowed for [PROVIDED] entities
    return isAllUpperOrLowerCase(name, index.getEntity()) ? null : "index name '" + name + MIXED_UC_LC;
  }


  /**
   * Checks whether the given name is all upper- or all lowercase.<br>
   * The backends are usually not case-sensitive (unless the SQL names are quoted),
   * but it is good style to either use all upper- or all lowercase.
   * Mixing the case pretends that the case is important, but in fact it's not.
   * This is only allowed for provided entities.
   *
   * @param name the name
   * @param entity the entity
   * @return true if ok, false if mixed
   */
  protected boolean isAllUpperOrLowerCase(String name, Entity entity) {
    return name == null || entity.getOptions().isProvided() || name.equals(name.toLowerCase()) || name.equals(name.toUpperCase());
  }

  /**
   * Checks whether the name is quoted.
   *
   * @param name the name
   * @param entity the entity
   * @return true if not quoted or entity is provided by another application
   */
  protected boolean isNotQuotedOrProvided(String name, Entity entity) {
    return name == null || !name.startsWith("\"") || entity.getOptions().isProvided();
  }

}

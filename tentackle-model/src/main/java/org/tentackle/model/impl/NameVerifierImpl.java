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
import org.tentackle.model.NameVerifier;

/**
 * Default implementation of a {@link NameVerifier}.
 */
@Service(NameVerifier.class)
public class NameVerifierImpl implements NameVerifier {

  private static final String MIXED_UC_LC = "' contains mixed upper- and lowercase characters";

  @Override
  public String verifyEntityName(String name) {
    return StringHelper.isValidJavaClassName(name) ? null : "'" + name + "' is not a valid Java class name";
  }

  @Override
  public String verifyTableName(String name) {
    return isAllUpperOrLowerCase(name) ? null : "table name '" + name + MIXED_UC_LC;
  }

  @Override
  public String verifyTableAlias(String name) {
    return isAllUpperOrLowerCase(name) ? null : "table alias '" + name + MIXED_UC_LC;
  }

  @Override
  public String verifyAttributeName(String name) {
    return StringHelper.isValidJavaIdentifier(name) ? null : "'" + name + "' is not a valid Java identifier name";
  }

  @Override
  public String verifyColumnName(String name) {
    return isAllUpperOrLowerCase(name) ? null : "column name '" + name + MIXED_UC_LC;
  }

  @Override
  public String verifyIndexName(String name) {
    return isAllUpperOrLowerCase(name) ? null : "index name '" + name + MIXED_UC_LC;
  }


  /**
   * Checks whether the given name is all upper- or all lowercase.<br>
   * The backends are usually not case-sensitive (unless the SQL names are quoted),
   * but it is good style to either use all upper- or all lowercase.
   * Mixing the case pretends that the case is important, but in fact it's not.
   *
   * @param name the name
   * @return true if ok, false if mixed
   */
  protected boolean isAllUpperOrLowerCase(String name) {
    return name == null || name.equals(name.toLowerCase()) || name.equals(name.toUpperCase());
  }

}

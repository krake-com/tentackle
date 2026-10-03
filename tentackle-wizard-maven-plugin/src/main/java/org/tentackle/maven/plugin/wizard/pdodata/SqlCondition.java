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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.tentackle.model.Attribute;

/**
 * Holds the data for the where condition to select the row(s) of a {@link DataNode} from the database.
 *
 * @param id the ID number to use in the where condition
 * @param attribute the attribute to use in the where condition, null if unknown
 */
public record SqlCondition(long id, Attribute attribute) {

  /**
   * Gets the name of the table holding the attribute.
   *
   * @return the table name, null if no attribute
   */
  public String getTableName() {
    return attribute == null ? null : attribute.getEntity().getTableName();
  }

  /**
   * Gets the alias of the table holding the attribute.
   *
   * @return the table alias, null if no attribute
   */
  public String getTableAlias() {
    return attribute == null ? null : attribute.getEntity().getTableAlias();
  }

  @Override
  public String toString() {
    return getTableAlias() + " = " + id;
  }
}

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
 *
 */

package org.tentackle.sql.metadata;

/**
 * Postgres index meta data.
 *
 * @author harald
 */
public class PostgresIndexMetaData extends IndexMetaData {

  /**
   * Creates an index meta data object.
   *
   * @param tableMetaData the table the index belongs to
   */
  public PostgresIndexMetaData(TableMetaData tableMetaData) {
    super(tableMetaData);
  }

  @Override
  public void validate() {
    super.validate();
    if (filterCondition != null) {
      // postgres appends ::<type> to all values which must be removed
      filterCondition = filterCondition.replaceAll("::[a-z|A-Z]*", "");
    }
  }

}

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
 *
 */

package org.tentackle.sql.metadata;

/**
 * Postgres index meta data.
 *
 * @author harald
 */
public class PostgresIndexMetaData extends IndexMetaData {

  /** the postgres type names consisting of more than one word, which the simple regex below cannot strip. */
  private static final String[] CASTS = {
      "::timestamp without time zone",
      "::timestamp with time zone",
      "::time without time zone",
      "::time with time zone",
      "::character varying",
      "::bit varying",
      "::double precision",
  };

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
    if (getFilterCondition() != null) {
      // postgres appends ::<type> to all values which must be removed
      // check for some special types that come in more than one word
      String condition = getFilterCondition();
      for (String cast: CASTS) {
        condition = condition.replace(cast, "");
      }
      // other simple casts such as ::long
      setFilterCondition(condition.replaceAll("::[a-z|A-Z]*", ""));
    }
  }

}

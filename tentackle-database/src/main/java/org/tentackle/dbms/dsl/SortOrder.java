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

package org.tentackle.dbms.dsl;

import org.tentackle.sql.Backend;

/**
 * The sort order of an {@code ORDER BY}-expression.
 *
 * @author harald
 */
public enum SortOrder {

  /** ascending. */
  ASCENDING(Backend.SQL_SORTASC),

  /** descending. */
  DESCENDING(Backend.SQL_SORTDESC);


  private final String sql;     // the SQL-code

  SortOrder(String sql) {
    this.sql = sql;
  }

  /**
   * Gets the SQL-code of this sort order.
   *
   * @return the SQL-code
   */
  public String getSql() {
    return sql;
  }

  @Override
  public String toString() {
    return sql;
  }

}

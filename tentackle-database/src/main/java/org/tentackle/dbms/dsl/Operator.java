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
 * A comparison operator.
 *
 * @author harald
 */
public enum Operator {

  /** equal. */
  EQUAL(Backend.SQL_EQUAL, true),

  /** not equal. */
  NOT_EQUAL(Backend.SQL_NOTEQUAL, true),

  /** less than. */
  LESS(Backend.SQL_LESS, false),

  /** less than or equal. */
  LESS_OR_EQUAL(Backend.SQL_LESSOREQUAL, false),

  /** greater than. */
  GREATER(Backend.SQL_GREATER, false),

  /** greater than or equal. */
  GREATER_OR_EQUAL(Backend.SQL_GREATEROREQUAL, false),

  /** matches a pattern. */
  LIKE(Backend.SQL_LIKE, false),

  /** does not match a pattern. */
  NOT_LIKE(Backend.SQL_NOTLIKE, false);


  private final String sql;                 // the SQL-code of the operator
  private final boolean multiColumn;        // true if applicable to multi-column datatypes

  Operator(String sql, boolean multiColumn) {
    this.sql = sql;
    this.multiColumn = multiColumn;
  }

  /**
   * Gets the SQL-code of this operator.
   *
   * @return the SQL-code
   */
  public String getSql() {
    return sql;
  }

  /**
   * Returns whether this operator can be applied to a datatype spanning more than one column.<br>
   * Only equality can be expressed column by column. Relational operators would compare
   * the columns independently, which is not what the datatype means.
   *
   * @return true if applicable to multi-column datatypes
   */
  public boolean isMultiColumnApplicable() {
    return multiColumn;
  }

  @Override
  public String toString() {
    return sql;
  }

}

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
 * The logical operator combining conditions.
 *
 * @author harald
 */
public enum Junction {

  /** logical AND. */
  AND(Backend.SQL_AND, "1=1"),

  /** logical OR. */
  OR(Backend.SQL_OR, "1=0");


  private final String sql;             // the SQL-code of the operator
  private final String neutralSql;      // the SQL-code of the neutral element

  Junction(String sql, String neutralSql) {
    this.sql = sql;
    this.neutralSql = neutralSql;
  }

  /**
   * Gets the SQL-code of this junction.
   *
   * @return the SQL-code
   */
  public String getSql() {
    return sql;
  }

  /**
   * Gets the SQL-code of the neutral element.<br>
   * This is the code rendered for an empty list of conditions:
   * always true for {@link #AND} and never true for {@link #OR}.
   *
   * @return the SQL-code
   */
  public String getNeutralSql() {
    return neutralSql;
  }

  @Override
  public String toString() {
    return sql;
  }

}

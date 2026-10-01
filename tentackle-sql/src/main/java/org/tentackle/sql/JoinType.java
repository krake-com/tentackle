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

package org.tentackle.sql;

/**
 * Join type.<br>
 * The {@link #toString()} method returns the SQL keyword, such as {@code LEFT JOIN}.
 *
 * @author harald
 */
public enum JoinType {

  /** inner join. */
  INNER("JOIN"),

  /** left outer join. */
  LEFT("LEFT JOIN"),

  /** right outer join. */
  RIGHT("RIGHT JOIN"),

  /** full outer join. */
  FULL("FULL JOIN");


  private final String sql;

  JoinType(String sql) {
    this.sql = sql;
  }

  @Override
  public String toString() {
    return sql;
  }

}

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
 */

package org.tentackle.sql.backends;

/**
 * Common to all SQL2008 backends.
 *
 * @author harald
 */
public abstract class AbstractSql2008Backend extends AbstractSql2003Backend {

  /** OFFSET string. */
  public static final String SQL_OFFSET = " OFFSET ? ROWS";

  /** FETCH FIRST string. */
  public static final String SQL_FETCH_FIRST = " FETCH FIRST ? ROWS ONLY";


  /**
   * Parent constructor.
   */
  public AbstractSql2008Backend() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    sqlBuilder.insert(0, SQL_SELECT);
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
    if (offset > 0) {
      sqlBuilder.append(SQL_OFFSET);
    }
    if (limit > 0) {
      sqlBuilder.append(SQL_FETCH_FIRST);
    }
  }


}

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

import org.tentackle.sql.BackendPreparedStatement;

/**
 * Common to all SQL2008 backends.
 * <p>
 * Paging uses the standard {@code OFFSET} and {@code FETCH FIRST} clauses instead of the
 * window function of {@link AbstractSql2003Backend}.
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

  /**
   * {@inheritDoc}
   * <p>
   * The clauses follow the order of the SQL standard: the OFFSET clause precedes the
   * FETCH FIRST clause and the locking clause comes last.
   * The trailing parameters are bound in the same order (see {@link #setTrailingSelectParameters}).
   */
  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (isLeadingSelectMissing(sqlBuilder)) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
    if (offset > 0) {
      sqlBuilder.append(SQL_OFFSET);
    }
    if (limit > 0) {
      sqlBuilder.append(SQL_FETCH_FIRST);
    }
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * The offset is bound before the limit, which is the number of rows to fetch.
   */
  @Override
  public int setTrailingSelectParameters(BackendPreparedStatement stmt, int index, int limit, int offset) {
    if (offset > 0) {
      stmt.setInt(index++, offset);
    }
    if (limit > 0) {
      stmt.setInt(index++, limit);
    }
    return index;
  }

}

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

  // the leading keywords of the clauses above, to cut them off
  private static final String SQL_OFFSET_KEYWORD = " OFFSET ";
  private static final String SQL_FETCH_FIRST_KEYWORD = " FETCH FIRST ";


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

  @Override
  protected String extractWhereClause(String sql, int whereOffset) {
    sql = super.extractWhereClause(sql, whereOffset);
    int ndx = sql.lastIndexOf(SQL_FETCH_FIRST_KEYWORD);
    if (ndx >= 0) {
      sql = sql.substring(0, ndx);
    }
    ndx = sql.lastIndexOf(SQL_OFFSET_KEYWORD);
    if (ndx >= 0) {
      sql = sql.substring(0, ndx);
    }
    return sql;
  }

}

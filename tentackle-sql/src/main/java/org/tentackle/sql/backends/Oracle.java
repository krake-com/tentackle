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

package org.tentackle.sql.backends;

import org.tentackle.common.Service;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;

/**
 * Backend for Oracle versions 12 or newer.
 *
 * @author harald
 */
@Service(Backend.class)
public class Oracle extends Oracle8 {

  private static final String SQL_FETCHNEXT_1 = " FETCH NEXT ";
  private static final String SQL_FETCHNEXT_2 = " ROWS ONLY";
  private static final String SQL_FETCHNEXT_PAR = SQL_FETCHNEXT_1 + SQL_PAR + SQL_FETCHNEXT_2;

  private static final String SQL_OFFSET_1 = " OFFSET ";
  private static final String SQL_OFFSET_2 = " ROWS";
  private static final String SQL_OFFSET_PAR = SQL_OFFSET_1 + SQL_PAR + SQL_OFFSET_2;


  /**
   * Creates the Oracle backend.
   */
  public Oracle() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getName() {
    return "Oracle";
  }

  @Override
  public void validateVersion(int databaseMajorVersion, int databaseMinorVersion) {
    if (databaseMajorVersion < 12) {
      throw new BackendException(this + " needs at least Oracle 12, but is connected to " + databaseMajorVersion + "." + databaseMinorVersion +
                                 " (consider adding '|Oracle8' to the connection URL)");
    }
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (isLeadingSelectMissing(sqlBuilder)) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
    if (offset > 0) {   // offset must come before fetch next
      sqlBuilder.append(SQL_OFFSET_PAR);
    }
    if (limit > 0) {
      sqlBuilder.append(SQL_FETCHNEXT_PAR);
    }
  }

  @Override
  protected String extractWhereClause(String sql, int whereOffset) {
    sql = super.extractWhereClause(sql, whereOffset);
    int ndx = sql.lastIndexOf(SQL_OFFSET_1);
    if (ndx >= 0) {
      sql = sql.substring(0, ndx);
    }
    ndx = sql.lastIndexOf(SQL_FETCHNEXT_1);
    if (ndx >= 0) {
      sql = sql.substring(0, ndx);
    }
    return sql;
  }

}

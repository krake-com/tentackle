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
import org.tentackle.sql.BackendVersionInfo;
import org.tentackle.sql.metadata.IndexColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.OracleIndexColumnMetaData;
import org.tentackle.sql.metadata.OracleIndexMetaData;
import org.tentackle.sql.metadata.TableMetaData;

/**
 * Backend for Oracle versions 12 or newer.
 *
 * @author harald
 */
@Service(Backend.class)
public class Oracle extends Oracle8 {

  /** leading part of the CASE-expression emulating a filtered index. */
  private static final String SQL_CASE_WHEN = "CASE WHEN (";

  /** middle part of the CASE-expression emulating a filtered index. */
  private static final String SQL_CASE_THEN = ") THEN ";

  /** trailing part of the CASE-expression emulating a filtered index. */
  private static final String SQL_CASE_END = " END";

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
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public String getName() {
    return "Oracle";
  }

  @Override
  public void validateVersion(BackendVersionInfo versionInfo) {
    if (versionInfo.majorVersion() < 12) {
      throw new BackendException(this + " needs at least Oracle 12, but is connected to " + versionInfo +
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
  public boolean isFunctionBasedIndexSupported() {
    return true;
  }

  @Override
  public boolean isFilteredIndexSupported() {
    // emulated by a function-based index, see sqlCreateIndex
    return true;
  }

  @Override
  public IndexMetaData createIndexMetaData(TableMetaData tableMetaData) {
    return new OracleIndexMetaData(tableMetaData);
  }

  @Override
  public IndexColumnMetaData createIndexColumnMetaData(IndexMetaData indexMetaData) {
    return new OracleIndexColumnMetaData(indexMetaData);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Oracle does not support {@code CREATE INDEX ... WHERE <condition>}.
   * A filtered index is therefore emulated by a function-based index wrapping each
   * index column into a {@code CASE WHEN <condition> THEN <column> END} expression.
   * Rows not matching the condition get an all-NULL key and oracle does not store
   * all-NULL keys, which gives exactly the semantics of a filtered (partial) index.
   * <p>
   * Notice that the query optimizer will only consider such an index if the query
   * contains the very same CASE-expression. See the
   * <a href="https://tentackle.org/oracle/">oracle backend documentation</a>.
   */
  @Override
  public String sqlCreateIndex(String tableName, String indexName, boolean unique, String filterCondition,
                               String... columnNames) {
    if (filterCondition != null) {
      String[] caseColumnNames = new String[columnNames.length];
      for (int ndx = 0; ndx < columnNames.length; ndx++) {
        String columnName = columnNames[ndx];
        String descending = "";
        if (columnName.startsWith("-")) {   // keep the marker interpreted by AbstractBackend
          columnName = columnName.substring(1);
          descending = "-";
        }
        caseColumnNames[ndx] = descending + SQL_CASE_WHEN + filterCondition + SQL_CASE_THEN + columnName + SQL_CASE_END;
      }
      return super.sqlCreateIndex(tableName, indexName, unique, null, caseColumnNames);
    }
    return super.sqlCreateIndex(tableName, indexName, unique, null, columnNames);
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

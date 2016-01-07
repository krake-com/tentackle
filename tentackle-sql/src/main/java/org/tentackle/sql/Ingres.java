/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.sql;

import java.sql.Connection;
import java.sql.Types;
import org.tentackle.common.Service;


/**
 * Backend for Ingres.
 *
 * @author harald
 */
@Service(Backend.class)
public class Ingres extends AbstractSql2003Backend {

  /** FIRST string. */
  public static final String SQL_FIRST = "FIRST ";

  /** FIRST string. */
  public static final String SQL_FIRST_PAR = SQL_FIRST + "? ";


  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":ingres");
  }

  @Override
  public String getName() {
    return "Ingres";
  }

  @Override
  public String getBackendId(Connection connection) {
    // @todo
    return null;
  }

  @Override
  public boolean sqlRequiresExtraCommit() {
    return true;
  }

  @Override
  public boolean needSetLongWorkaround() {
    return true;
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (limit > 0 && offset <= 0) {
      // Ingres supports only FIRST
      sqlBuilder.insert(0, SQL_FIRST_PAR);
      sqlBuilder.insert(0, SQL_SELECT);
    }
    else  {
      super.buildSelectSql(sqlBuilder, writeLock, limit, offset);
    }
  }

  @Override
  public int setLeadingSelectParameters(BackendPreparedStatement stmt, int limit, int offset) {
    int index = 1;
    if (limit > 0 && offset <= 0) {
      stmt.setInt(index++, limit);
    }
    return index;
  }

  @Override
  public int setTrailingSelectParameters(BackendPreparedStatement stmt, int index, int limit, int offset) {
    if (limit > 0 && offset <= 0) {
      return index;
    }
    else  {
      return super.setTrailingSelectParameters(stmt, index, limit, offset);
    }
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    switch(sqlType) {

      case DECIMAL:
        return 38;

      case VARCHAR:
        return 1024;

      default:
        return super.getMaxSize(sqlType);
    }
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    switch(sqlType) {

      case BIT:
        return TYPE_TINYINT;

      case TINYINT:
        return TYPE_TINYINT;

      case SMALLINT:
        return TYPE_SMALLINT;

      case INTEGER:
        return TYPE_INTEGER;

      case BIGINT:
        return TYPE_BIGINT;

      case FLOAT:
        return TYPE_FLOAT4;

      case DOUBLE:
        return TYPE_FLOAT;

      case DECIMAL:
        return TYPE_NUMBER;

      case CHAR:
        return TYPE_CHAR_1;

      case VARCHAR:
        return TYPE_VARCHAR;

      case DATE:
        return TYPE_DATE;

      case TIME:
        return TYPE_DATE;

      case TIMESTAMP:
        return TYPE_TIMESTAMP;

      case LONGVARBINARY:
        return TYPE_BLOB;

      default:
        return super.sqlTypeToString(sqlType, size);
    }
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    switch(jdbcType) {
      case Types.BIT:
      case Types.BOOLEAN:
      case Types.TINYINT:
        return new SqlType[] { SqlType.BIT, SqlType.TINYINT };

      case Types.DATE:
      case Types.TIME:
        return new SqlType[] { SqlType.DATE, SqlType.TIME };

      default:
        return super.jdbcTypeToSqlType(jdbcType, size, scale);
    }
  }

  @Override
  public String sqlCreateTableComment(String tableName, String comment) {
    return NonStandardCommons.sqlCreateCommentOnTable(this, tableName, comment);
  }

  @Override
  public String sqlCreateColumnComment(String tableName, String columnName, String comment) {
    return NonStandardCommons.sqlCreateCommentOnColumn(this, tableName, columnName, comment);
  }

  @Override
  protected String extractWhereClause(String sql, int whereOffset) {
    int ndx = sql.indexOf(SQL_FIRST);
    if (ndx >= 0) {
      throw new BackendException("backend does not support merging selects with FIRST");
    }
    return super.extractWhereClause(sql, whereOffset);
  }

}

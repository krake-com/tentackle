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
import org.tentackle.sql.BackendPreparedStatement;
import org.tentackle.sql.NonStandardCommons;
import org.tentackle.sql.SqlType;

import java.sql.Connection;
import java.sql.Types;


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
  public String getDriverClassName() {
    return "com.ingres.jdbc.IngresDriver";
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
    return switch (sqlType) {
      case DECIMAL -> 38;
      case VARCHAR -> 1024;
      default -> super.getMaxSize(sqlType);
    };
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT, TINYINT -> TYPE_TINYINT;
      case SMALLINT -> TYPE_SMALLINT;
      case INTEGER -> TYPE_INTEGER;
      case BIGINT -> TYPE_BIGINT;
      case FLOAT -> TYPE_FLOAT4;
      case DOUBLE -> TYPE_FLOAT;
      case DECIMAL -> TYPE_NUMBER;
      case CHAR -> TYPE_CHAR_1;
      case VARCHAR -> TYPE_VARCHAR;
      case DATE, TIME -> TYPE_DATE;
      case TIMESTAMP -> TYPE_TIMESTAMP;
      case BLOB -> TYPE_BLOB;
      case CLOB -> TYPE_CLOB;
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    return switch (jdbcType) {
      case Types.BIT, Types.BOOLEAN, Types.TINYINT -> new SqlType[]{SqlType.BIT, SqlType.TINYINT};
      case Types.DATE, Types.TIME -> new SqlType[]{SqlType.DATE, SqlType.TIME};
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
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

  @Override
  protected boolean isDropIfExistsSupported() {
    return true;
  }
}

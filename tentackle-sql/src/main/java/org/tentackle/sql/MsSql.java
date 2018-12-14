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


package org.tentackle.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import org.tentackle.common.Service;
import org.tentackle.common.TentackleRuntimeException;


/**
 * Backend for MicrosoftSQL.
 *
 * @author harald
 */
@Service(Backend.class)
public class MsSql extends AbstractSql2003Backend {

  /** TOP string. */
  public static final String SQL_TOP = "TOP ";

  /** TOP string. */
  public static final String SQL_TOP_PAR = SQL_TOP + "? ";

  @Override
  public boolean isFilteredIndexSupported() {
    return true;
  }

  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":sqlserver");
  }

  @Override
  public String getName() {
    return "MsSQL";
  }

  @Override
  public String getDriverClassName() {
    return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
  }

  @Override
  public String getBackendId(Connection connection) {
    try (Statement stmt = connection.createStatement()) {
      ResultSet rs = stmt.executeQuery("SELECT @@SPID");
      if (rs.next()) {
        return "ID-" + rs.getString(1);
      }
      return null;
    }
    catch (SQLException ex) {
      throw new TentackleRuntimeException("cannot determine backend id", ex);
    }
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (limit > 0 && offset <= 0) {
      // MSSQL supports TOP
      sqlBuilder.insert(0, SQL_TOP_PAR);
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

      default:
        return super.getMaxSize(sqlType);
    }
  }


  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    switch(sqlType) {

      case BIT:
        return TYPE_BIT;

      case TINYINT:
        return TYPE_TINYINT;

      case SMALLINT:
        return TYPE_SMALLINT;

      case INTEGER:
        return TYPE_INT;

      case BIGINT:
        return TYPE_BIGINT;

      case FLOAT:
        return TYPE_REAL;

      case DOUBLE:
        return TYPE_FLOAT;

      case DECIMAL:
        return TYPE_DECIMAL;

      case CHAR:
        return TYPE_NCHAR_1;

      case VARCHAR:
        return size == 0 ? TYPE_NVARCHAR_MAX : TYPE_NVARCHAR;

      case DATE:
        return TYPE_DATETIME;

      case TIME:
        return TYPE_DATETIME;

      case TIMESTAMP:
        return TYPE_DATETIME;

      case BLOB:
        return TYPE_VARBINARY_MAX;

      case CLOB:
        return TYPE_CLOB;

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
      case Types.SMALLINT:
        return new SqlType[] { SqlType.BIT, SqlType.TINYINT, SqlType.SMALLINT };

      case Types.DATE:
      case Types.TIMESTAMP:
      case Types.TIME:
        return new SqlType[] { SqlType.DATE, SqlType.TIME, SqlType.TIMESTAMP };

      default:
        return super.jdbcTypeToSqlType(jdbcType, size, scale);
    }
  }

  @Override
  protected String extractWhereClause(String sql, int whereOffset) {
    int ndx = sql.indexOf(SQL_TOP);
    if (ndx >= 0) {
      throw new BackendException("backend does not support merging selects with TOP");
    }
    return super.extractWhereClause(sql, whereOffset);
  }

}

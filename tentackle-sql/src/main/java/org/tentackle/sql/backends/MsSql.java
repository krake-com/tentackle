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
import org.tentackle.sql.SqlType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

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


  /**
   * Creates the MS-Sql backend.
   */
  public MsSql() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

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
  public String getXADataSourceClassName() {
    return "com.microsoft.sqlserver.jdbc.SQLServerXADataSource";
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
      throw new BackendException("cannot determine backend id", ex);
    }
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (limit > 0 && offset <= 0) {
      // MSSQL supports TOP
      boolean insertSelect = isLeadingSelectMissing(sqlBuilder);
      sqlBuilder.insert(0, SQL_TOP_PAR);
      if (insertSelect) {
        sqlBuilder.insert(0, SQL_SELECT);
      }
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
    if (sqlType == SqlType.DECIMAL) {
      return 38;
    }
    return super.getMaxSize(sqlType);
  }


  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT -> TYPE_BIT;
      case TINYINT -> TYPE_TINYINT;
      case SMALLINT -> TYPE_SMALLINT;
      case INTEGER -> TYPE_INT;
      case BIGINT -> TYPE_BIGINT;
      case FLOAT -> TYPE_REAL;
      case DOUBLE -> TYPE_FLOAT;
      case DECIMAL -> TYPE_DECIMAL;
      case CHAR -> TYPE_NCHAR_1;
      case VARCHAR -> size == 0 ? TYPE_NVARCHAR_MAX : TYPE_NVARCHAR;
      case DATE, TIME, TIMESTAMP -> TYPE_DATETIME;
      case BLOB -> TYPE_VARBINARY_MAX;
      case CLOB -> TYPE_CLOB;
      case XML -> TYPE_XML;
      case JSON -> TYPE_NVARCHAR_MAX;   // no native json type before SQL Server 2025
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    return switch (jdbcType) {
      case Types.BIT, Types.BOOLEAN, Types.TINYINT, Types.SMALLINT -> new SqlType[]{SqlType.BIT, SqlType.TINYINT, SqlType.SMALLINT};
      case Types.DATE, Types.TIMESTAMP, Types.TIME -> new SqlType[]{SqlType.DATE, SqlType.TIME, SqlType.TIMESTAMP};
      // JSON is emulated as NVARCHAR(MAX), so a character column must match a JSON attribute too
      case Types.VARCHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.LONGNVARCHAR
              -> new SqlType[]{SqlType.VARCHAR, SqlType.JSON};
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
  }

  @Override
  public String sqlRenameIndex(String tableName, String oldIndexName, String newIndexName) {
    return null;    // not supported via SQL -> drop and create
  }

  @Override
  public boolean isTransientTransactionException(SQLException ex) {
    return super.isTransientTransactionException(ex) ||
           isExceptionErrorCodeMatching(ex, 1222) ||
           isExceptionStateMatching(ex, "HY008");
  }

  @Override
  protected String extractWhereClause(String sql, int whereOffset) {
    int ndx = sql.indexOf(SQL_TOP);
    if (ndx >= 0) {
      throw new BackendException("backend does not support merging selects with TOP");
    }
    return super.extractWhereClause(sql, whereOffset);
  }

  @Override
  protected boolean isDropIfExistsSupported() {
    return true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Needs at least {@code sqljdbc4.jar} on the classpath.
   * Does not work with {@code sqljdbc3.jar}!
   */
  @Override
  public boolean isSqlxmlSupported() {
    return true;
  }
}

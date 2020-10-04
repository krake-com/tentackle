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
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.NonStandardCommons;
import org.tentackle.sql.SqlType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;


/**
 * Backend for DB2.
 *
 * @author harald
 */
@Service(Backend.class)
public class Db2 extends AbstractSql2003Backend {


  private static final String STR_CONTINUATION = " \\";


  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":db2");
  }

  @Override
  public String getName() {
    return "DB2";
  }

  @Override
  public String getDriverClassName() {
    return "com.ibm.db2.jcc.DB2Driver";
  }

  @Override
  public boolean sqlResultSetIsClosedSupported() {
    return true;
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    switch(sqlType) {

      case DECIMAL:
        return 31;

      case VARCHAR:
        return 512;

      default:
        return super.getMaxSize(sqlType);
    }
  }

  @Override
  public String getBackendId(Connection connection) {
    try (Statement stmt = connection.createStatement()) {
      ResultSet rs = stmt.executeQuery("SELECT application_id() AS appid FROM SYSIBM.SYSDUMMY1");
      if (rs.next()) {
        return rs.getString(1);
      }
      return null;
    }
    catch (SQLException ex) {
      throw new TentackleRuntimeException("cannot determine backend id", ex);
    }
  }

  @Override
  public boolean isFunctionBasedIndexSupported() {
    return true;
  }

  @Override
  public String sqlCreateTableIntro(String tableName, String comment) {
    return appendContinuationString(super.sqlCreateTableIntro(tableName, comment));
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    switch(sqlType) {

      case BIT:
        return TYPE_SMALLINT;

      case TINYINT:
        return TYPE_SMALLINT;

      case SMALLINT:
        return TYPE_SMALLINT;

      case INTEGER:
        return TYPE_INTEGER;

      case BIGINT:
        return TYPE_BIGINT;

      case FLOAT:
        return TYPE_REAL;

      case DOUBLE:
        return TYPE_DOUBLE;

      case DECIMAL:
        return TYPE_DECIMAL;

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

      case BLOB:
        return TYPE_BLOB;

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
      case Types.TINYINT:
      case Types.SMALLINT:
        return new SqlType[] { SqlType.BIT, SqlType.TINYINT, SqlType.SMALLINT };

      case Types.DATE:
      case Types.TIME:
        return new SqlType[] { SqlType.DATE, SqlType.TIME };

      default:
        return super.jdbcTypeToSqlType(jdbcType, size, scale);
    }
  }


  @Override
  public String sqlCreateColumn(String columnName, String comment, SqlType sqlType, int size, int scale,
                                boolean nullable, Object defaultValue, boolean primaryKey, boolean withTrailingComma) {
    return appendContinuationString(super.sqlCreateColumn(columnName, comment, sqlType, size, scale, nullable,
            defaultValue, primaryKey, withTrailingComma));
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
  public String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType, int size, int scale,
          boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            " ADD (" +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ");\n";
  }

  @Override
  public String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType,
                                   int size, int scale, boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            SQL_ALTER_COLUMN +
            columnName +
            " DATA TYPE " +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ";\n";
  }

  @Override
  public String sqlRenameIndex(String tableName, String oldIndexName, String newIndexName) {
    return  "RENAME INDEX " + oldIndexName +
            " TO " +
            newIndexName +
            ";\n";
  }

  @Override
  protected boolean isDropIfExistsSupported() {
    return true;
  }

  /**
   * Appends the continuation sequence to an sql statement.
   *
   * @param sql the sql code
   * @return the modified code
   */
  private String appendContinuationString(String sql) {
    if (sql.endsWith("\n")) {
      sql = sql.substring(0, sql.length() - 1) + STR_CONTINUATION + "\n";
    }
    else  {
      sql += STR_CONTINUATION;
    }
    return sql;
  }
}

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
import org.tentackle.sql.NonStandardCommons;
import org.tentackle.sql.SqlNameType;
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

  // maximum length for identifiers, names, etc...
  private static final int MAX_COLUMN_NAME_LENGTH = 30;
  private static final int MAX_NAME_LENGTH = 128;

  /**
   * Creates the DB2 backend.
   */
  public Db2() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public int getMaxNameLength(SqlNameType nameType) {
    return nameType == SqlNameType.COLUMN_NAME ? MAX_COLUMN_NAME_LENGTH : MAX_NAME_LENGTH;
  }

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
  public int getMaxSize(SqlType sqlType) {
    return switch (sqlType) {
      case DECIMAL -> 31;
      case VARCHAR -> 512;
      default -> super.getMaxSize(sqlType);
    };
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
      throw new BackendException("cannot determine backend id", ex);
    }
  }

  @Override
  public boolean isTransientTransactionException(SQLException ex) {
    return super.isTransientTransactionException(ex) ||
           isExceptionStateMatching(ex, "57014") ||
           isExceptionErrorCodeMatching(ex, -952);
  }

  @Override
  public boolean isFunctionBasedIndexSupported() {
    return true;
  }

  @Override
  public boolean isSqlxmlSupported() {
    return true;
  }

  @Override
  public String sqlCreateTableIntro(String tableName, String comment) {
    return appendContinuationString(super.sqlCreateTableIntro(tableName, comment));
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT, TINYINT, SMALLINT -> TYPE_SMALLINT;
      case INTEGER -> TYPE_INTEGER;
      case BIGINT -> TYPE_BIGINT;
      case FLOAT -> TYPE_REAL;
      case DOUBLE -> TYPE_DOUBLE;
      case DECIMAL -> TYPE_DECIMAL;
      case CHAR -> TYPE_CHAR_1;
      case VARCHAR -> TYPE_VARCHAR;
      case DATE, TIME -> TYPE_DATE;
      case TIMESTAMP -> TYPE_TIMESTAMP;
      case BLOB -> TYPE_BLOB;
      case CLOB -> TYPE_CLOB;
      case XML -> TYPE_XML;
      case JSON -> TYPE_CLOB;   // no native JSON column type
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    return switch (jdbcType) {
      case Types.BIT, Types.TINYINT, Types.SMALLINT -> new SqlType[]{SqlType.BIT, SqlType.TINYINT, SqlType.SMALLINT};
      case Types.DATE, Types.TIME -> new SqlType[]{SqlType.DATE, SqlType.TIME};
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
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
  public String sqlCreateIndexComment(String tableName, String indexName, String comment) {
    return NonStandardCommons.sqlCreateCommentOnIndex(this, tableName, indexName, comment);
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
            " SET DATA TYPE " +
            // DB2 takes only the type here: the column name is already part of the ALTER COLUMN
            // clause, and the NOT NULL and DEFAULT constraints are separate column alterations
            columnTypeToString(sqlType, size, scale) +
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
   * Appends the continuation sequence to a SQL statement.
   *
   * @param sql the SQL code
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

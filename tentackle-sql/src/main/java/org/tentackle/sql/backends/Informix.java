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
import org.tentackle.sql.SqlNameType;
import org.tentackle.sql.SqlType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Backend for Informix.
 *
 * @author harald
 */
@Service(Backend.class)
public class Informix extends AbstractSql92Backend {

  // maximum length for identifiers, names, etc...
  private static final int MAX_NAME_LENGTH = 128;

  /** NVL keyword. */
  public static final String SQL_NVL = "NVL";   // instead of COALESCE

  /** FIRST string. */
  public static final String SQL_FIRST = "FIRST ";

  /** SKIP string. */
  public static final String SQL_SKIP = "SKIP ";

  /** FIRST string. */
  public static final String SQL_FIRST_PAR = SQL_FIRST + "? ";

  /** SKIP string. */
  public static final String SQL_SKIP_PAR = SQL_SKIP + "? ";


  /**
   * Creates the Informix backend.
   */
  public Informix() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public int getMaxNameLength(SqlNameType nameType) {
    return MAX_NAME_LENGTH;
  }

  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":informix");
  }

  @Override
  public String getName() {
    return "Informix";
  }

  @Override
  public String getDriverClassName() {
    return "com.informix.jdbc.IfxDriver";
  }

  @Override
  public String getBackendId(Connection connection) {
    // @todo
    return null;
  }

  @Override
  public Connection createConnection(String url, String username, char[] password)
         throws SQLException {

    return DriverManager.getConnection (
                url +
                ";user=" + username +
                ";password=" + createPassword(password));
  }

  @Override
  public String getCoalesceKeyword() {
    return SQL_NVL;
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    boolean insertSelect = isLeadingSelectMissing(sqlBuilder);
    if (limit > 0) {
      sqlBuilder.insert(0, SQL_FIRST_PAR);
    }
    if (offset > 0) {
      sqlBuilder.insert(0, SQL_SKIP_PAR); // SKIP comes before FIRST
    }
    if (insertSelect) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
  }

  @Override
  public int setLeadingSelectParameters(BackendPreparedStatement stmt, int limit, int offset) {
    int index = 1;
    if (offset > 0) {
      stmt.setInt(index++, offset);
    }
    if (limit > 0) {
      stmt.setInt(index++, limit);
    }
    return index;
  }

  @Override
  public int setTrailingSelectParameters(BackendPreparedStatement stmt, int index, int limit, int offset) {
    return index;
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    return switch (sqlType) {
      case DECIMAL -> 31;
      case VARCHAR -> 255;
      default -> super.getMaxSize(sqlType);
    };
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT -> TYPE_BOOLEAN;
      case TINYINT, SMALLINT -> TYPE_SMALLINT;
      case INTEGER -> TYPE_INT;
      case BIGINT -> TYPE_INT8;
      case FLOAT -> TYPE_SMALLFLOAT;
      case DOUBLE -> TYPE_FLOAT;
      case DECIMAL -> TYPE_NUMBER;
      case CHAR -> TYPE_CHAR_1;
      case VARCHAR -> TYPE_VARCHAR;
      case DATE -> TYPE_DATE;
      case TIME -> TYPE_DATETIME;
      case TIMESTAMP -> TYPE_DATETIME_YEAR_TO_SECOND;
      case BLOB -> TYPE_BYTE;
      case CLOB -> TYPE_CLOB;
      case XML -> TYPE_CLOB;   // no native XML type
      case JSON -> TYPE_CLOB;     // informix has a native JSON type, but only in some editions
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    return switch (jdbcType) {
      case Types.SMALLINT, Types.TINYINT -> new SqlType[]{SqlType.TINYINT, SqlType.SMALLINT};
      case Types.TIMESTAMP, Types.TIME -> new SqlType[]{SqlType.TIME, SqlType.TIMESTAMP};
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
  }

  @Override
  public boolean isSequenceSupported() {
    return true;
  }

  @Override
  public boolean isSchemaSupported() {
    // there is a CREATE SCHEMA, but this is a different beast
    return false;
  }

  @Override
  public String sqlCreateSequence(String name, Long start, Long increment) {
    return NonStandardCommons.sqlCreateSequence(name, start, increment);
  }

  @Override
  public String sqlNextFromSequence(String name) {
    return "SELECT " + name + ".NEXTVAL";
  }

  @Override
  public String sqlRenameColumn(String tableName, String oldColumnName, String newColumnName) {
    return  "RENAME COLUMN " + tableName +
            "." +
            oldColumnName +
            " TO " +
            newColumnName +
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
  public String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType, int size, int scale,
          boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            " ADD (" +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ");\n";
  }

  @Override
  public String sqlDropColumn(String tableName, String columnName) {
    return  SQL_ALTER_TABLE + tableName +
            " DROP (" +
            columnName +
            ");\n";
  }

  @Override
  public String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType,
                                   int size, int scale, boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            " MODIFY (" +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ");\n";
  }

  @Override
  public String sqlAlterColumnDefault(String tableName, String columnName, SqlType sqlType, Object defaultValue) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(" MODIFY (");
    buf.append(columnName);
    buf.append(" ");
    if (defaultValue == null) {
      buf.append("DROP DEFAULT");
    }
    else  {
      buf.append("SET DEFAULT ");
      buf.append(valueToLiteral(sqlType, defaultValue));
    }
    buf.append(");\n");
    return buf.toString();
  }

  @Override
  protected String extractWhereClause(String sql, int whereOffset) {
    int ndx = sql.indexOf(SQL_FIRST);
    if (ndx >= 0) {
      throw new BackendException("backend does not support merging selects with FIRST");
    }
    ndx = sql.indexOf(SQL_SKIP);
    if (ndx >= 0) {
      throw new BackendException("backend does not support merging selects with SKIP");
    }
    return super.extractWhereClause(sql, whereOffset);
  }

  @Override
  protected boolean isDropIfExistsSupported() {
    return true;
  }
}

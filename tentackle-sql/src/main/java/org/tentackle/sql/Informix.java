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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import org.tentackle.common.Service;

/**
 * Backend for Informix.
 *
 * @author harald
 */
@Service(Backend.class)
public class Informix extends AbstractSql92Backend {

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
                ";password=" + (password == null ? "" : new String(password)));
  }

  @Override
  public String getCoalesceKeyword() {
    return SQL_NVL;
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (limit > 0) {
      sqlBuilder.insert(0, SQL_FIRST_PAR);
    }
    if (offset > 0) {
      sqlBuilder.insert(0, SQL_SKIP_PAR); // SKIP comes before FIRST
    }
    sqlBuilder.insert(0, SQL_SELECT);
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
    switch(sqlType) {

      case DECIMAL:
        return 31;

      case VARCHAR:
        return 255;

      default:
        return super.getMaxSize(sqlType);
    }
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    switch(sqlType) {

      case BIT:
        return TYPE_BOOLEAN;

      case TINYINT:
        return TYPE_SMALLINT;

      case SMALLINT:
        return TYPE_SMALLINT;

      case INTEGER:
        return TYPE_INT;

      case BIGINT:
        return TYPE_INT8;

      case FLOAT:
        return TYPE_SMALLFLOAT;

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
        return TYPE_DATETIME;

      case TIMESTAMP:
        return TYPE_DATETIME_YEAR_TO_SECOND;

      case LONGVARBINARY:
        return TYPE_BYTE;

      default:
        return super.sqlTypeToString(sqlType, size);
    }
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    switch(jdbcType) {
      case Types.SMALLINT:
      case Types.TINYINT:
        return new SqlType[] { SqlType.TINYINT, SqlType.SMALLINT };

      case Types.TIMESTAMP:
      case Types.TIME:
        return new SqlType[] { SqlType.TIME, SqlType.TIMESTAMP };

      default:
        return super.jdbcTypeToSqlType(jdbcType, size, scale);
    }
  }

  @Override
  public boolean supportsSequences() {
    return true;
  }

  @Override
  public String sqlNextFromSequene(String name) {
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
  public String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType, int size, int scale,
          boolean nullable, Object defaultValue) {
    return  "ALTER TABLE " + tableName +
            " ADD (" +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ");\n";
  }

  @Override
  public String sqlDropColumn(String tableName, String columnName) {
    return  "ALTER TABLE " + tableName +
            " DROP (" +
            columnName +
            ");\n";
  }

  @Override
  public String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType,
                                   int size, int scale, boolean nullable, Object defaultValue) {
    return  "ALTER TABLE " + tableName +
            " MODIFY (" +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ");\n";
  }

  @Override
  public String sqlAlterColumnDefault(String tableName, String columnName, SqlType sqlType, Object defaultValue) {
    StringBuilder buf = new StringBuilder("ALTER TABLE ");
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

}

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
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendPreparedStatement;
import org.tentackle.sql.MigrationStrategy;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.MySqlColumnMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Backend for MySQL.
 *
 * @author harald
 */
@Service(Backend.class)
public class MySql extends AbstractSql92Backend {

  /** LIMIT string. */
  public static final String SQL_LIMIT = " LIMIT ";

  /** OFFSET string. */
  public static final String SQL_OFFSET = " OFFSET ";

  /** LIMIT ? string. */
  public static final String SQL_LIMIT_PAR = SQL_LIMIT + SQL_PAR;

  /** OFFSET ? string. */
  public static final String SQL_OFFSET_PAR = SQL_OFFSET + SQL_PAR;

  private static final String[] RESERVED_SCHEMAS = {
      "information_schema",
      "mysql",
      "performance_schema",
      "sys"
  };


  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":mysql");
  }

  @Override
  public String getName() {
    return "MySQL";
  }

  @Override
  public String getDriverClassName() {
    return "com.mysql.jdbc.Driver";
  }

  @Override
  public String getBackendId(Connection connection) {
    try (Statement stmt = connection.createStatement()) {
      ResultSet rs = stmt.executeQuery("SELECT CONNECTION_ID()");
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
  public boolean isReservedSchemaName(String name) {
    for (String reserved_schema: RESERVED_SCHEMAS) {
      if (reserved_schema.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isReleaseSavepointSupported() {
    return true;
  }

  @Override
  public boolean isFunctionBasedIndexSupported() {
    return true;
  }

  @Override
  public boolean isPosixEscapeSyntaxSupported() {
    return true;
  }

  @Override
  public boolean allowsExpressionsReferringToTablesBeingUpdated() {
    return false;
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    sqlBuilder.insert(0, SQL_SELECT);
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
    if (offset > 0 && limit <= 0) {
      limit = 99999999;   // mysql can't do OFFSET without LIMIT -> fake it
    }
    if (limit > 0) {
      sqlBuilder.append(SQL_LIMIT_PAR);
    }
    if (offset > 0) {
      sqlBuilder.append(SQL_OFFSET_PAR);
    }
  }

  @Override
  public int setLeadingSelectParameters(BackendPreparedStatement stmt, int limit, int offset) {
    return 1;
  }

  @Override
  public int setTrailingSelectParameters(BackendPreparedStatement stmt, int index, int limit, int offset) {
    if (offset > 0 && limit <= 0) {
      limit = 99999999;   // mysql can't do OFFSET without LIMIT -> fake it
    }
    if (limit > 0) {
      stmt.setInt(index++, limit);
    }
    if (offset > 0) {
      stmt.setInt(index++, offset);
    }
    return index;
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    return switch (sqlType) {
      case DECIMAL -> 64;
      case VARCHAR -> 4096;
      default -> super.getMaxSize(sqlType);
    };
  }


  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT, TINYINT -> TYPE_TINYINT;
      case SMALLINT -> TYPE_SMALLINT;
      case INTEGER -> TYPE_INT;
      case BIGINT -> TYPE_BIGINT;
      case FLOAT -> TYPE_FLOAT;
      case DOUBLE -> TYPE_DOUBLE;
      case DECIMAL -> size == 0 ? TYPE_DECIMAL_19 : TYPE_DECIMAL;
      case CHAR -> TYPE_CHAR_1;
      case VARCHAR -> TYPE_VARCHAR;
      case DATE -> TYPE_DATE;
      case TIME, TIMESTAMP -> TYPE_DATETIME;
      case BLOB -> TYPE_BLOB;
      case CLOB -> TYPE_CLOB;
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    return switch (jdbcType) {
      case Types.BIT, Types.BOOLEAN, Types.TINYINT -> new SqlType[]{SqlType.BIT, SqlType.TINYINT};
      case Types.TIMESTAMP, Types.TIME -> new SqlType[]{SqlType.TIMESTAMP, SqlType.TIME};
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
  }

  @Override
  public String sqlCreateTableClosing(String tableName, String comment) {
    if (comment == null) {
      return super.sqlCreateTableClosing(tableName, null);
    }
    StringBuilder buf = new StringBuilder(super.sqlCreateTableClosing(tableName, comment));
    buf.setLength(buf.length() - 2);  // cut trailing ";\n"
    buf.append(" COMMENT=");
    buf.append(toQuotedString(comment));
    buf.append(";\n");
    return buf.toString();
  }

  @Override
  public DatabaseMetaData[] getMetaData(BackendInfo backendInfo) throws SQLException {
    if (backendInfo.getSchemas() != null) {
      /*
       * MySQL interprets schemas as databases.
       * We need a connection for each schema!
       */
      DatabaseMetaData[] metaData = new DatabaseMetaData[backendInfo.getSchemas().length];
      for (int i=0; i < metaData.length; i++) {
        // replace database name with schema name
        int ndx1 = backendInfo.getUrl().lastIndexOf('/');
        if (ndx1 == -1) {
          throw new BackendException("missing slash in " + backendInfo.getUrl());
        }
        int ndx2 = backendInfo.getUrl().indexOf('?', ndx1);
        if (ndx2 == -1) {
          ndx2 = backendInfo.getUrl().length();
        }
        if (ndx2 > ndx1 + 1) {
          ndx1++;
          String schema = backendInfo.getSchemas()[i];
          String url = backendInfo.getUrl().substring(0, ndx1) + schema + backendInfo.getUrl().substring(ndx2);
          Connection con = createConnection(url, backendInfo.getUser(), backendInfo.getPassword());
          metaData[i] = con.getMetaData();
        }
        else  {
          throw new BackendException("missing database name in " + backendInfo.getUrl());
        }
      }
      return metaData;
    }
    else  {
      return super.getMetaData(backendInfo);
    }
  }

  @Override
  public ColumnMetaData createColumnMetaData(TableMetaData tableMetaData) {
    return new MySqlColumnMetaData(tableMetaData);
  }

  @Override
  public String sqlCreateColumn(String columnName, String comment, SqlType sqlType, int size, int scale,
          boolean nullable, Object defaultValue, boolean primaryKey, boolean withTrailingComma) {
    if (comment == null) {
      return super.sqlCreateColumn(columnName, null, sqlType, size, scale, nullable, defaultValue, primaryKey,
              withTrailingComma);
    }

    StringBuilder buf = new StringBuilder(sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, primaryKey,
              false));
    buf.append(" COMMENT ");
    buf.append(toQuotedString(comment));
    if (withTrailingComma) {
      buf.append(",");
    }
    buf.append('\n');
    return buf.toString();
  }

  @Override
  public String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType, int size, int scale,
          boolean nullable, Object defaultValue) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(" ADD COLUMN ");
    buf.append(sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false));
    if (comment != null) {
      buf.append(" COMMENT ");
      buf.append(toQuotedString(comment));
    }
    buf.append(";\n");
    return buf.toString();
  }

  @Override
  public String sqlRenameColumn(String tableName, String oldColumnName, String newColumnName) {
    return null;    // need full spec
  }

  @Override
  public String sqlRenameIndex(String tableName, String oldIndexName, String newIndexName) {
    int ndx = oldIndexName.indexOf('.');
    if (ndx >= 0) {
      // cut schema
      oldIndexName = oldIndexName.substring(ndx + 1);
    }
    return  SQL_ALTER_TABLE + tableName +
            " RENAME INDEX " +
            oldIndexName +
            " TO " +
            newIndexName +
            ";\n";
  }

  @Override
  public String sqlRenameAndAlterColumnType(String tableName, String oldColumnName, String newColumnName, String comment,
          SqlType sqlType, int size, int scale, boolean nullable, Object defaultValue) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(" CHANGE ");
    buf.append(oldColumnName);
    buf.append(" ");
    buf.append(sqlCreateTableAttributeWithoutComment(newColumnName, sqlType, size, scale, nullable, defaultValue, false, false));
    if (comment != null) {
      buf.append(" COMMENT ");
      buf.append(toQuotedString(comment));
    }
    buf.append(";\n");
    return buf.toString();
  }

  @Override
  public String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType,
                                   int size, int scale, boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            SQL_ALTER_COLUMN +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ";\n";
  }

  @Override
  public String sqlAlterColumnComment(String tableName, String columnName, String comment) {
    return null;    // use sqlRenameAndAlterColumnType
  }

  @Override
  public String sqlDropIndex(String schemaName, String tableNameWithoutSchema, String indexName) {
    StringBuilder buf = new StringBuilder("DROP INDEX ");
    if (isDropIfExistsEnabled()) {
      buf.append(SQL_IF_EXISTS);
    }
    buf.append(indexName);
    buf.append(" ON ");
    if (schemaName != null) {
      buf.append(schemaName);
      buf.append(".");
    }
    buf.append(tableNameWithoutSchema);
    buf.append(";\n");
    return buf.toString();
  }

  @Override
  public MigrationStrategy[] getMigrationStrategy(ColumnMetaData column, String columnName, String comment,
                                                  SqlType sqlType, int size, int scale, boolean nullable, Object defaultValue) {

    List<MigrationStrategy> strategies = new ArrayList<>();
    int maxSize = getMaxSize(sqlType);

    boolean nameChanged = !column.getColumnName().equalsIgnoreCase(columnName);
    boolean commentChanged = !Objects.equals(column.getComment(), comment);
    boolean defaultChanged = !isDefaultEqual(column, sqlType, defaultValue);
    boolean nullChanged = column.isNullable() != nullable;
    boolean typeChanged = !column.matchesSqlType(sqlType);
    boolean sizeChanged = maxSize != -1 &&
                          (size > column.getSize() ||    // model size increased
                           // or changed from VARCHAR(x) to VARCHAR(maxSize)
                           size == 0 && sqlType == SqlType.VARCHAR &&
                           column.getSize() > 0 && column.getSize() != maxSize);
    boolean sizeWarning = maxSize != -1 && size != 0 && size < column.getSize();
    boolean scaleChanged = scale > column.getScale(); // smaller model scale is allowed
    boolean scaleWarning = maxSize != -1 && scale < column.getScale();

    if (nameChanged || typeChanged || sizeChanged || scaleChanged || nullChanged || defaultChanged || commentChanged) {
      strategies.add(MigrationStrategy.NAME_AND_TYPE);
    }
    else if (sizeWarning || scaleWarning) {
      strategies.add(MigrationStrategy.TYPE_WARNING); // only as comment
    }

    return strategies.toArray(new MigrationStrategy[0]);
  }

  @Override
  protected String extractWhereClause(String sql, int whereOffset) {
    sql = super.extractWhereClause(sql, whereOffset);
    int ndx = sql.lastIndexOf(SQL_LIMIT);
    if (ndx >= 0) {
      sql = sql.substring(0, ndx);
    }
    ndx = sql.lastIndexOf(SQL_OFFSET);
    if (ndx >= 0) {
      sql = sql.substring(0, ndx);
    }
    return sql;
  }

}

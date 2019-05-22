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

import org.tentackle.common.Service;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.PostgresColumnMetaData;
import org.tentackle.sql.metadata.PostgresIndexMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/**
 * Backend for Postgres.
 *
 * @author harald
 */
@Service(Backend.class)
public class Postgres extends AbstractSql92Backend {

  /** postgres' default schema. */
  public static final String DEFAULT_SCHEMA = "public";

  /** LIMIT string. */
  public static final String SQL_LIMIT = " LIMIT ";

  /** OFFSET string. */
  public static final String SQL_OFFSET = " OFFSET ";

  /** LIMIT ? string. */
  public static final String SQL_LIMIT_PAR = SQL_LIMIT + SQL_PAR;

  /** OFFSET ? string. */
  public static final String SQL_OFFSET_PAR = SQL_OFFSET + SQL_PAR;


  /** array of additional reserved words. */
  public static final String[] RESERVED_WORDS_PG = new String[] {
    "TEXT"
  };

  /** set of reserved words (built only once). */
  private static Set<String> reservedWords;

  @Override
  public synchronized Set<String> getReservedWords() {
    if (reservedWords == null) {
      reservedWords = new HashSet<>(super.getReservedWords());
      reservedWords.addAll(Arrays.asList(RESERVED_WORDS_PG));
    }
    return reservedWords;
  }


  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":postgresql");
  }

  @Override
  public String getName() {
    return "PostgreSQL";
  }

  @Override
  public String getDriverClassName() {
    return "org.postgresql.Driver";
  }

  @Override
  public String getBackendId(Connection connection) {
    try (Statement stmt = connection.createStatement()) {
      ResultSet rs = stmt.executeQuery("select pg_backend_pid()");
      if (rs.next()) {
        return "PID-" + rs.getString(1);
      }
      return null;
    }
    catch (SQLException ex) {
      throw new TentackleRuntimeException("cannot determine backend id", ex);
    }
  }

  @Override
  public boolean isReleaseSavepointSupported() {
    return true;
  }

  @Override
  public boolean isClobSupported() {
    return false;   // use TEXT instead
  }

  @Override
  public boolean isFilteredIndexSupported() {
    return true;
  }

  @Override
  public boolean needTxForFetchsize() {
    return true;
  }

  @Override
  public boolean needAliasForSubselect() {
    return true;
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    sqlBuilder.insert(0, SQL_SELECT);
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
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
    if (limit > 0) {
      stmt.setInt(index++, limit);
    }
    if (offset > 0) {
      stmt.setInt(index++, offset);
    }
    return index;
  }

  @Override
  public ColumnMetaData createColumnMetaData(TableMetaData tableMetaData) {
    return new PostgresColumnMetaData(tableMetaData);
  }

  @Override
  public IndexMetaData createIndexMetaData(TableMetaData tableMetaData) {
    return new PostgresIndexMetaData(tableMetaData);
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    switch(sqlType) {

      case DECIMAL:
        return 999;

      default:
        return super.getMaxSize(sqlType);
    }
  }

  @Override
  public int getDefaultSize(SqlType sqlType) {
    if (sqlType == SqlType.VARCHAR) {
      return 0;
    }
    return super.getDefaultSize(sqlType);
  }

  @Override
  public String getDefaultSchema() {
    return DEFAULT_SCHEMA;
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    switch(jdbcType) {
      case Types.TINYINT:
      case Types.SMALLINT:
        return new SqlType[] { SqlType.TINYINT, SqlType.SMALLINT };

      case Types.VARCHAR:
      case Types.LONGVARCHAR:
      case Types.NVARCHAR:
      case Types.LONGNVARCHAR:
      case Types.CLOB:
      case Types.NCLOB:
        return new SqlType[] { SqlType.VARCHAR, SqlType.CLOB };

      default:
        return super.jdbcTypeToSqlType(jdbcType, size, scale);
    }
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    switch(sqlType) {

      case BIT:
        return TYPE_BOOL;

      case TINYINT:
        return TYPE_INT2;

      case SMALLINT:
        return TYPE_INT2;

      case INTEGER:
        return TYPE_INT4;

      case BIGINT:
        return TYPE_INT8;

      case FLOAT:
        return TYPE_FLOAT4;

      case DOUBLE:
        return TYPE_FLOAT8;

      case DECIMAL:
        return TYPE_DECIMAL;

      case CHAR:
        return TYPE_CHAR_1;

      case VARCHAR:
        return size == 0 ? TYPE_TEXT : TYPE_VARCHAR;

      case DATE:
        return TYPE_DATE;

      case TIME:
        return TYPE_TIME;

      case TIMESTAMP:
        return TYPE_TIMESTAMP;

      case BLOB:
        return TYPE_BYTEA;

      case CLOB:
        return TYPE_TEXT;

      default:
        return super.sqlTypeToString(sqlType, size);
    }
  }



  @Override
  public boolean supportsSequences() {
    return true;
  }

  @Override
  public String sqlNextFromSequene(String name) {
    return "SELECT NEXTVAL('" + name + "')";
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
  public String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType,
                                   int size, int scale, boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            SQL_ALTER_COLUMN +
            columnName +
            " TYPE " +
            // nullable = true -> Postgres needs separate SET NOT NULL
            // default = null -> Postgres needs separate SET DEFAULT
            columnTypeNullDefaultToString(columnName, sqlType, size, scale, true, null) +
            ";\n";
  }

  @Override
  public String sqlAlterColumnNullConstraint(String tableName, String columnName, boolean nullable) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(SQL_ALTER_COLUMN);
    buf.append(columnName);
    if (nullable) {
      buf.append(" DROP");
    }
    else  {
      buf.append(" SET");

    }
    buf.append(" NOT NULL;\n");
    return buf.toString();
  }


  @Override
  public MigrationStrategy[] getMigrationStrategy(ColumnMetaData column, String columnName, String comment,
          SqlType sqlType, int size, int scale, boolean nullable, Object defaultValue) {

    List<MigrationStrategy> strategies = new ArrayList<>();
    boolean nameChanged = !column.getColumnName().equalsIgnoreCase(columnName);
    boolean commentChanged = !Objects.equals(column.getComment(), comment);
    boolean defaultChanged = !isDefaultEqual(column, sqlType, defaultValue);
    boolean nullChanged = column.isNullable() != nullable;
    boolean typeChanged = !column.matchesSqlType(sqlType);
    boolean sizeChanged = size > column.getSize();    // smaller model size is allowed
    boolean scaleChanged = scale > column.getScale(); // smaller model scale is allowed

    // ideal backend: name, comment, null and default can be changed separately from type,size and scale
    if (nameChanged) {
      strategies.add(MigrationStrategy.NAME);   // comes first because next steps refer to the new column name
    }
    if (typeChanged || sizeChanged || scaleChanged) {
      strategies.add(MigrationStrategy.TYPE);
    }
    if (nullChanged) {
      strategies.add(MigrationStrategy.NULL);
    }
    if (defaultChanged) {
      strategies.add(MigrationStrategy.DEFAULT);
    }
    if (commentChanged) {
      strategies.add(MigrationStrategy.COMMENT);
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

  @Override
  protected boolean isDropIfExistsSupported() {
    return true;
  }

}

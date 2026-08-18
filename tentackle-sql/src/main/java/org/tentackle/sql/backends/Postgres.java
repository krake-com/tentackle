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
import org.tentackle.sql.MigrationStrategy;
import org.tentackle.sql.NonStandardCommons;
import org.tentackle.sql.ScriptRunner;
import org.tentackle.sql.SqlNameType;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.IndexColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.PostgresColumnMetaData;
import org.tentackle.sql.metadata.PostgresIndexColumnMetaData;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;


/**
 * Backend for Postgres.
 *
 * @author harald
 */
@Service(Backend.class)
public class Postgres extends AbstractSql92Backend {

  // maximum length for identifiers, names, etc...
  private static final int MAX_NAME_LENGTH = 63;

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
    "TEXT", "UUID"
  };


  private static final String[] RESERVED_SCHEMAS = {
    "information_schema",
    "pg_catalog",
    "pg_temp_1",
    "pg_toast",
    "pg_toast_temp_1"
  };

  /** set of reserved words (built only once). */
  private Set<String> reservedWords;


  /**
   * Creates the postgres backend.
   */
  public Postgres() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public synchronized Set<String> getReservedWords() {
    if (reservedWords == null) {
      reservedWords = new HashSet<>(super.getReservedWords());
      reservedWords.addAll(Arrays.asList(RESERVED_WORDS_PG));
    }
    return reservedWords;
  }

  @Override
  public int getMaxNameLength(SqlNameType nameType) {
    return MAX_NAME_LENGTH;
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
      throw new BackendException("cannot determine backend id", ex);
    }
  }

  @Override
  public boolean isReleaseSavepointSupported() {
    return true;
  }

  @Override
  public boolean isReservedSchemaName(String name) {
    for (String reserved_schema: RESERVED_SCHEMAS) {
      if (reserved_schema.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return super.isReservedSchemaName(name);
  }

  @Override
  public boolean isTransientTransactionException(SQLException ex) {
    return super.isTransientTransactionException(ex) ||
           // deadlock detected, lock not available
           isExceptionStateMatching(ex, "40P01", "55P03");
  }

  @Override
  public boolean isClobSupported() {
    return false;   // use TEXT instead
  }

  @Override
  public boolean isUUIDSupported() {
    return true;
  }

  @Override
  public boolean isFilteredIndexSupported() {
    return true;
  }

  @Override
  public boolean isFunctionBasedIndexSupported() {
    return true;
  }

  @Override
  public boolean isTxRequiredForFetchsize() {
    return true;
  }

  @Override
  public boolean isAliasRequiredForSubSelect() {
    return true;
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (isLeadingSelectMissing(sqlBuilder)) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
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
  public IndexColumnMetaData createIndexColumnMetaData(IndexMetaData indexMetaData) {
    return new PostgresIndexColumnMetaData(indexMetaData);
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    if (sqlType == SqlType.DECIMAL) {
      return 999;
    }
    return super.getMaxSize(sqlType);
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
    return switch (jdbcType) {
      case Types.TINYINT, Types.SMALLINT -> new SqlType[]{SqlType.TINYINT, SqlType.SMALLINT};
      case Types.VARCHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> new SqlType[]{SqlType.VARCHAR, SqlType.CLOB};
      case Types.OTHER -> new SqlType[] { SqlType.UUID };
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT -> TYPE_BOOL;
      case TINYINT, SMALLINT -> TYPE_INT2;
      case INTEGER -> TYPE_INT4;
      case BIGINT -> TYPE_INT8;
      case FLOAT -> TYPE_FLOAT4;
      case DOUBLE -> TYPE_FLOAT8;
      case DECIMAL -> TYPE_DECIMAL;
      case CHAR -> TYPE_CHAR_1;
      case VARCHAR -> size == 0 ? TYPE_TEXT : TYPE_VARCHAR;
      case DATE -> TYPE_DATE;
      case TIME -> TYPE_TIME;
      case TIMESTAMP -> TYPE_TIMESTAMP;
      case BLOB -> TYPE_BYTEA;
      case CLOB -> TYPE_TEXT;
      case UUID -> TYPE_UUID;
      default -> super.sqlTypeToString(sqlType, size);
    };
  }



  @Override
  public boolean isSequenceSupported() {
    return true;
  }

  @Override
  public String sqlNextFromSequence(String name) {
    return "SELECT NEXTVAL('" + name + "')";
  }

  @Override
  public String sqlCreateSequence(String name, Long start, Long increment) {
    return NonStandardCommons.sqlCreateSequence(name, start, increment);
  }

  @Override
  public String sqlCreateSequenceComment(String name, String comment) {
    return NonStandardCommons.sqlCreateCommentOnSequence(this, name, comment);
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
  public String getIndexMetaDataCommentColumnName() {
    return "REMARKS";
  }

  @Override
  public String sqlCreateIndexComment(String tableName, String indexName, String comment) {
    return NonStandardCommons.sqlCreateCommentOnIndex(this, tableName, indexName, comment);
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
  public String toInternalType(String sqlTypeName) {
    String typeName = sqlTypeName.toLowerCase(Locale.ROOT);   // postgres is case-sensitive internally and needs lowercase
    // char(1) is okay for SQL scripts, but not a valid postgres type
    int ndx = typeName.indexOf('(');
    if (ndx >= 0) {
      typeName = typeName.substring(0, ndx);  // cut (xxx)
    }
    return typeName;
  }

  @Override
  public boolean isArrayOperatorSupported(String operator) {
    return SQL_ARRAY_ALL.equals(operator) || SQL_ARRAY_ANY.equals(operator);
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
                           // or changed from VARCHAR(x) to TEXT
                           size == 0 && (sqlType == SqlType.VARCHAR || sqlType == SqlType.CLOB) &&
                           column.getSize() > 0 && column.getSize() != Integer.MAX_VALUE);
    boolean sizeWarning = maxSize != -1 && size != 0 && size < column.getSize();
    boolean scaleChanged = scale > column.getScale(); // smaller model scale is allowed
    boolean scaleWarning = maxSize != -1 && scale < column.getScale();

    // ideal backend: name, comment, null and default can be changed separately from type,size and scale
    if (nameChanged) {
      strategies.add(MigrationStrategy.NAME);   // comes first because the next steps refer to the new column name
    }
    if (typeChanged || sizeChanged || scaleChanged) {
      strategies.add(MigrationStrategy.TYPE);
    }
    else if (sizeWarning || scaleWarning) {
      strategies.add(MigrationStrategy.TYPE_WARNING); // only as comment
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
  public ScriptRunner createScriptRunner(Connection connection) {
    return new PostgresScriptRunner(this, connection);
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

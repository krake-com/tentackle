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
import org.tentackle.sql.SqlNameType;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
 * Backend for the H2 Database.
 */
@Service(Backend.class)
public class H2 extends AbstractSql92Backend {

  // maximum length for identifiers, names, etc...
  private static final int MAX_NAME_LENGTH = 256;

  // default size of a DECIMAL if the model does not provide one
  private static final int DEFAULT_DECIMAL_SIZE = 19;

  /** H2's default schema. */
  public static final String DEFAULT_SCHEMA = "public";

  /** LIMIT string. */
  public static final String SQL_FETCH_FIRST = " FETCH FIRST ";

  /** OFFSET string. */
  public static final String SQL_OFFSET = " OFFSET ";

  /** FETCH FIRST ? ROWS ONLY string. */
  public static final String SQL_FETCH_FIRST_PAR = SQL_FETCH_FIRST + SQL_PAR + " ROWS ONLY";

  /** OFFSET ? ROWS string. */
  public static final String SQL_OFFSET_PAR = SQL_OFFSET + SQL_PAR + " ROWS";


  /** array of additional reserved words. */
  public static final String[] RESERVED_WORDS_H2 = new String[] {
    "UUID"
  };


  private static final String[] RESERVED_SCHEMAS = {
    "information_schema"
  };

  /** set of reserved words (built only once). */
  private Set<String> reservedWords;


  /**
   * Creates the H2 backend.
   */
  public H2() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public int getMaxNameLength(SqlNameType nameType) {
    return MAX_NAME_LENGTH;
  }

  @Override
  public synchronized Set<String> getReservedWords() {
    if (reservedWords == null) {
      reservedWords = new HashSet<>(super.getReservedWords());
      reservedWords.addAll(Arrays.asList(RESERVED_WORDS_H2));
    }
    return reservedWords;
  }


  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":h2:");
  }

  @Override
  public String getName() {
    return "H2";
  }

  @Override
  public String getDriverClassName() {
    return "org.h2.Driver";
  }

  @Override
  public String getXADataSourceClassName() {
    return "org.h2.jdbcx.JdbcDataSource";
  }

  @Override
  public String getBackendId(Connection connection) {
    try (Statement stmt = connection.createStatement()) {
      ResultSet rs = stmt.executeQuery("select SESSION_ID()");
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
    return !"public".equalsIgnoreCase(name) && // public is a legal schema name (it's the default schema)
           super.isReservedSchemaName(name);
  }

  @Override
  public boolean isTransientTransactionException(SQLException ex) {
    return super.isTransientTransactionException(ex) ||
           // deadlock detected, lock timeout, concurrent update
           isExceptionStateMatching(ex, "40001", "50200", "90131");
  }

  @Override
  public boolean isUUIDSupported() {
    return true;
  }

  @Override
  public boolean isAliasRequiredForSubSelect() {
    return true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * H2 follows the SQL standard here, unlike postgres: the OFFSET clause precedes the
   * FETCH FIRST clause and the locking clause comes last. Any other order is a syntax error.
   */
  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (isLeadingSelectMissing(sqlBuilder)) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
    if (offset > 0) {
      sqlBuilder.append(SQL_OFFSET_PAR);
    }
    if (limit > 0) {
      sqlBuilder.append(SQL_FETCH_FIRST_PAR);
    }
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
  }

  @Override
  public int setLeadingSelectParameters(BackendPreparedStatement stmt, int limit, int offset) {
    return 1;
  }

  @Override
  public int setTrailingSelectParameters(BackendPreparedStatement stmt, int index, int limit, int offset) {
    // same order as in buildSelectSql: offset first, then limit
    if (offset > 0) {
      stmt.setInt(index++, offset);
    }
    if (limit > 0) {
      stmt.setInt(index++, limit);
    }
    return index;
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    return switch (sqlType) {
      case DECIMAL -> 99999;
      case VARCHAR -> 1048576;
      default -> super.getMaxSize(sqlType);
    };
  }

  @Override
  public int getDefaultSize(SqlType sqlType) {
    // without an explicit size H2 reads a bare DECIMAL as DECIMAL(100000,0) and silently
    // rounds away all fraction digits, just like mysql does with its DECIMAL(10,0)
    return sqlType == SqlType.DECIMAL ? DEFAULT_DECIMAL_SIZE : super.getDefaultSize(sqlType);
  }

  @Override
  public String getDefaultSchema() {
    return DEFAULT_SCHEMA;
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    return switch (jdbcType) {
      case Types.TINYINT, Types.SMALLINT -> new SqlType[]{SqlType.TINYINT, SqlType.SMALLINT};
      case Types.VARCHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> new SqlType[]{SqlType.VARCHAR, SqlType.CLOB};
      case Types.OTHER -> new SqlType[] { SqlType.UUID, SqlType.JSON };
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, String typeName, int size, int scale) {
    if (jdbcType == Types.OTHER && typeName != null) {
      // H2 reports a JSON column as OTHER with the type name "JSON"
      if (typeName.equalsIgnoreCase(TYPE_JSON)) {
        return new SqlType[] { SqlType.JSON };
      }
      if (typeName.equalsIgnoreCase(TYPE_UUID)) {
        return new SqlType[] { SqlType.UUID };
      }
      return new SqlType[0];
    }
    return super.jdbcTypeToSqlType(jdbcType, typeName, size, scale);
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT -> TYPE_BOOLEAN;
      case TINYINT -> TYPE_TINYINT;
      case SMALLINT -> TYPE_SMALLINT;
      case INTEGER -> TYPE_INTEGER;
      case BIGINT -> TYPE_BIGINT;
      case FLOAT -> TYPE_REAL;
      case DOUBLE -> TYPE_DOUBLE_PRECISION;
      case DECIMAL -> TYPE_DECIMAL;
      case CHAR -> TYPE_CHAR_1;
      case VARCHAR -> TYPE_VARCHAR;
      case DATE -> TYPE_DATE;
      case TIME -> TYPE_TIME;
      case TIMESTAMP -> TYPE_TIMESTAMP;
      case BLOB -> TYPE_BLOB;
      case CLOB -> TYPE_CLOB;
      case XML -> TYPE_CLOB;   // H2's XML implementation is backed by a CLOB internally
      case JSON -> TYPE_JSON;
      case UUID -> TYPE_UUID;
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public boolean isSqlxmlSupported() {
    return true;
  }

  @Override
  public boolean isJsonSupported() {
    return true;
  }

  @Override
  public void setJson(PreparedStatement statement, int pos, String json) throws SQLException {
    // H2 reads a java string as a JSON *string value* and would store "{}" instead of {}.
    // Only the UTF-8 bytes are parsed as a document -- and are validated by H2 on the way in.
    if (json == null) {
      statement.setNull(pos, Types.OTHER);
    }
    else {
      statement.setBytes(pos, json.getBytes(StandardCharsets.UTF_8));
    }
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
  public boolean isDatabaseInMemory(String url) {
    return isMatchingUrl(url) && url.contains(":mem:");
  }

  @Override
  protected String extractWhereClause(String sql, int whereOffset) {
    sql = super.extractWhereClause(sql, whereOffset);
    int ndx = sql.lastIndexOf(SQL_FETCH_FIRST);
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

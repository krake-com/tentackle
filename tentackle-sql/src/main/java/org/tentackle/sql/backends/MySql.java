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
import org.tentackle.sql.SqlNameType;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Backend for MySQL.
 * <p>
 * Requires at least MySQL 8.0.13, which is the first version providing functional indexes
 * and default values for {@code TEXT}- and {@code BLOB}-columns.
 *
 * @author harald
 */
@Service(Backend.class)
public class MySql extends AbstractSql92Backend {

  // maximum length for identifiers, names, etc...
  private static final int MAX_NAME_LENGTH = 64;

  // minimum major version (8.0.13, but validateVersion only gets major and minor)
  private static final int MIN_MAJOR_VERSION = 8;

  // maximum scale of a DECIMAL, regardless of its size
  private static final int MAX_DECIMAL_SCALE = 30;

  // default size of a DECIMAL if the model does not provide one
  private static final int DEFAULT_DECIMAL_SIZE = 19;

  /** LIMIT string. */
  public static final String SQL_LIMIT = " LIMIT ";

  /** OFFSET string. */
  public static final String SQL_OFFSET = " OFFSET ";

  /** LIMIT ? string. */
  public static final String SQL_LIMIT_PAR = SQL_LIMIT + SQL_PAR;

  /** OFFSET ? string. */
  public static final String SQL_OFFSET_PAR = SQL_OFFSET + SQL_PAR;

  /** the limit used to emulate an OFFSET without a LIMIT (mysql requires both). */
  public static final int MAX_LIMIT = Integer.MAX_VALUE;

  private static final String[] RESERVED_SCHEMAS = {
      "information_schema",
      "mysql",
      "performance_schema",
      "sys"
  };

  /** array of additional reserved words (those not already covered by SQL92). */
  public static final String[] RESERVED_WORDS_MYSQL = new String[] {
    "ACCESSIBLE", "ANALYZE", "ASENSITIVE", "BEFORE", "BIGINT", "BINARY", "BLOB", "CHANGE", "CUBE",
    "CUME_DIST", "DATABASE", "DATABASES", "DAY_HOUR", "DAY_MICROSECOND", "DAY_MINUTE", "DAY_SECOND",
    "DELAYED", "DENSE_RANK", "DISTINCTROW", "DIV", "DUAL", "EACH", "EMPTY", "ENCLOSED", "ESCAPED",
    "EXPLAIN", "FIRST_VALUE", "FLOAT4", "FLOAT8", "FORCE", "FULLTEXT", "GENERATED", "GROUPING",
    "GROUPS", "HIGH_PRIORITY", "HOUR_MICROSECOND", "HOUR_MINUTE", "HOUR_SECOND", "IGNORE", "INDEX",
    "INFILE", "INT1", "INT2", "INT3", "INT4", "INT8", "IO_AFTER_GTIDS", "IO_BEFORE_GTIDS", "ITERATE",
    "JSON_TABLE", "KEYS", "KILL", "LAG", "LAST_VALUE", "LATERAL", "LEAD", "LIMIT", "LINEAR", "LINES",
    "LOAD", "LOCALTIME", "LOCALTIMESTAMP", "LOCK", "LONG", "LONGBLOB", "LONGTEXT", "LOW_PRIORITY",
    "MASTER_BIND", "MASTER_SSL_VERIFY_SERVER_CERT", "MAXVALUE", "MEDIUMBLOB", "MEDIUMINT",
    "MEDIUMTEXT", "MIDDLEINT", "MINUTE_MICROSECOND", "MINUTE_SECOND", "MOD", "MODIFIES",
    "NO_WRITE_TO_BINLOG", "NTH_VALUE", "NTILE", "OPTIMIZE", "OPTIMIZER_COSTS", "OPTIONALLY", "OVER",
    "OUTFILE", "PERCENT_RANK", "PURGE", "RANGE", "RANK", "READ_WRITE", "READS", "RECURSIVE",
    "REGEXP", "RELEASE", "RENAME", "REPLACE", "REQUIRE", "RESIGNAL", "RLIKE", "ROW", "ROW_NUMBER",
    "SCHEMAS", "SECOND_MICROSECOND", "SENSITIVE", "SEPARATOR", "SHOW", "SPATIAL", "SQL_BIG_RESULT",
    "SQL_CALC_FOUND_ROWS", "SQL_SMALL_RESULT", "SSL", "STARTING", "STORED", "STRAIGHT_JOIN",
    "SYSTEM", "TERMINATED", "TINYBLOB", "TINYINT", "TINYTEXT", "TRIGGER", "UNLOCK", "UNSIGNED",
    "USE", "UTC_DATE", "UTC_TIME", "UTC_TIMESTAMP", "VARBINARY", "VARCHARACTER", "VIRTUAL",
    "WINDOW", "XOR", "YEAR_MONTH", "ZEROFILL"
  };

  // set of reserved words (built only once, per instance because of the MariaDb subclass)
  private Set<String> reservedWords;


  /**
   * Creates the MySql backend.
   */
  public MySql() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public synchronized Set<String> getReservedWords() {
    if (reservedWords == null) {
      reservedWords = new HashSet<>(super.getReservedWords());
      reservedWords.addAll(Arrays.asList(RESERVED_WORDS_MYSQL));
    }
    return reservedWords;
  }

  @Override
  public int getMaxNameLength(SqlNameType nameType) {
    return MAX_NAME_LENGTH;
  }

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
    return "com.mysql.cj.jdbc.Driver";
  }

  @Override
  public void validateVersion(int databaseMajorVersion, int databaseMinorVersion) {
    if (databaseMajorVersion < MIN_MAJOR_VERSION) {
      throw new BackendException(this + " needs at least MySQL " + MIN_MAJOR_VERSION + ".0.13" +
                                 ", but is connected to " + databaseMajorVersion + "." + databaseMinorVersion);
    }
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
  public boolean isSchemaSupported() {
    return false;
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
  public boolean isReleaseSavepointSupported() {
    return true;
  }

  @Override
  public boolean isFunctionBasedIndexSupported() {
    return true;    // since 8.0.13, and with an extra pair of parentheses (see sqlCreateIndex)
  }

  @Override
  public boolean isPosixEscapeSyntaxSupported() {
    return true;
  }

  @Override
  public boolean isExpressionReferringToTableBeingUpdatedSupported() {
    return false;
  }

  @Override
  public boolean isAliasRequiredForSubSelect() {
    return true;    // "every derived table must have its own alias"
  }

  @Override
  public boolean isTransientTransactionException(SQLException ex) {
    return super.isTransientTransactionException(ex) ||
           // deadlock (SQLState 40001, but not on all versions), lock wait timeout (SQLState HY000)
           isExceptionErrorCodeMatching(ex, 1213, 1205);
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (isLeadingSelectMissing(sqlBuilder)) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
    if (offset > 0 && limit <= 0) {
      limit = MAX_LIMIT;   // mysql can't do OFFSET without LIMIT -> fake it
    }
    if (limit > 0) {
      sqlBuilder.append(SQL_LIMIT_PAR);
    }
    if (offset > 0) {
      sqlBuilder.append(SQL_OFFSET_PAR);
    }
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);   // the locking clause must come after LIMIT and OFFSET
    }
  }

  @Override
  public int setLeadingSelectParameters(BackendPreparedStatement stmt, int limit, int offset) {
    return 1;
  }

  @Override
  public int setTrailingSelectParameters(BackendPreparedStatement stmt, int index, int limit, int offset) {
    if (offset > 0 && limit <= 0) {
      limit = MAX_LIMIT;   // mysql can't do OFFSET without LIMIT -> fake it
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
  public int getMaxScale(SqlType sqlType, int size) {
    int maxScale = super.getMaxScale(sqlType, size);
    if (sqlType == SqlType.DECIMAL && maxScale > MAX_DECIMAL_SCALE) {
      maxScale = MAX_DECIMAL_SCALE;   // mysql allows at most 30 decimal digits, regardless of the size
    }
    return maxScale;
  }

  @Override
  public int getDefaultSize(SqlType sqlType) {
    return switch (sqlType) {
      // no size means "as large as possible" -> LONGTEXT (see sqlTypeToString)
      case VARCHAR -> 0;
      // without an explicit size mysql would default to DECIMAL(10,0) and silently drop all fraction digits
      case DECIMAL -> DEFAULT_DECIMAL_SIZE;
      default -> super.getDefaultSize(sqlType);
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
      case DECIMAL -> TYPE_DECIMAL;
      case CHAR -> TYPE_CHAR_1;
      // an unsized string must not become VARCHAR(max): the declared length counts towards
      // the 65535 bytes per row and the 3072 bytes per index key
      case VARCHAR -> size == 0 ? TYPE_LONGTEXT : TYPE_VARCHAR;
      case DATE -> TYPE_DATE;
      // mysql truncates to whole seconds unless the fractional precision is declared
      case TIME -> TYPE_TIME_6;
      case TIMESTAMP -> TYPE_DATETIME_6;
      // BLOB and TEXT are limited to 64k
      case BLOB -> TYPE_LONGBLOB;
      case CLOB -> TYPE_LONGTEXT;
      case SQLXML -> TYPE_LONGTEXT;   // no native XML type
      case JSON -> TYPE_JSON;         // native since 5.7.8, and we require 8.0.13 anyway
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public boolean isJsonSupported() {
    // a native JSON column, but it takes a plain string like any text column -> no setJson override
    return true;
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    return switch (jdbcType) {
      case Types.BIT, Types.BOOLEAN, Types.TINYINT -> new SqlType[]{SqlType.BIT, SqlType.TINYINT};
      // VARCHAR and LONGTEXT are both used for strings, sized or not, and the driver reports
      // a JSON column as one of the character types as well
      case Types.VARCHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB
              -> new SqlType[]{SqlType.VARCHAR, SqlType.CLOB, SqlType.JSON};
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
  }

  @Override
  public String columnTypeNullDefaultToString(String columnName, SqlType sqlType, int size, int scale,
                                              boolean nullable, Object defaultValue) {
    if (defaultValue == null || !isLobDeclaration(sqlType, size)) {
      return super.columnTypeNullDefaultToString(columnName, sqlType, size, scale, nullable, defaultValue);
    }
    // TEXT- and BLOB-columns only accept a default if it is written as an expression
    StringBuilder buf = new StringBuilder();
    buf.append(columnTypeToString(sqlType, size, scale));
    if (!nullable) {
      buf.append(" NOT NULL");
    }
    buf.append(" DEFAULT (");
    buf.append(valueToLiteral(sqlType, defaultValue));
    buf.append(')');
    return buf.toString();
  }

  /**
   * Returns whether the given type is declared as a TEXT- or BLOB-type.<br>
   * Such columns cannot be indexed without a key length and their default must be an expression.
   *
   * @param sqlType the SQL type
   * @param size the size from the model, 0 if none
   * @return true if TEXT or BLOB
   */
  protected boolean isLobDeclaration(SqlType sqlType, int size) {
    return switch (sqlType) {
      case CLOB, BLOB, JSON -> true;   // mysql rejects a literal default on a JSON column too
      case VARCHAR -> (size == 0 ? getDefaultSize(sqlType) : size) == 0;
      default -> false;
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
      List<Connection> connections = new ArrayList<>(metaData.length);
      try {
        for (int i = 0; i < metaData.length; i++) {
          // replace database name with schema name
          String url = replaceDatabaseInUrl(backendInfo.getUrl(), backendInfo.getSchemas()[i]);
          Connection con = createConnection(url, backendInfo.getUser(), backendInfo.getPassword());
          connections.add(con);   // ownership passes to the caller along with the metadata
          metaData[i] = con.getMetaData();
        }
      }
      catch (SQLException | RuntimeException ex) {
        // none of the metadata is returned -> don't leak the connections opened so far
        for (Connection con: connections) {
          try {
            con.close();
          }
          catch (SQLException sqx) {
            // ignored, we're already on the error path
          }
        }
        throw ex;
      }
      return metaData;
    }
    else  {
      return super.getMetaData(backendInfo);
    }
  }

  /**
   * Replaces the database name in a JDBC url.<br>
   * The database name is the path component of {@code jdbc:mysql://host:port/database?parameters}.
   *
   * @param url the JDBC url
   * @param database the database name to set
   * @return the modified url
   */
  protected String replaceDatabaseInUrl(String url, String database) {
    int hostNdx = url.indexOf("//");
    // the path separator, not just any slash: parameter values may contain slashes as well
    int start = hostNdx >= 0 ? url.indexOf('/', hostNdx + 2) : url.lastIndexOf('/');
    if (start == -1) {
      throw new BackendException("missing slash in " + url);
    }
    start++;
    int end = start;
    while (end < url.length() && url.charAt(end) != '?' && url.charAt(end) != ';') {
      end++;
    }
    if (end == start) {
      throw new BackendException("missing database name in " + url);
    }
    return url.substring(0, start) + database + url.substring(end);
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
    return  SQL_ALTER_TABLE + tableName +
            " RENAME COLUMN " +
            oldColumnName +
            " TO " +
            newColumnName +
            ";\n";
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
    // ALTER COLUMN can only set or drop the default in mysql -> MODIFY COLUMN with the full declaration
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(" MODIFY COLUMN ");
    buf.append(sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false));
    if (comment != null) {
      buf.append(" COMMENT ");     // MODIFY replaces the whole declaration: the comment must be repeated
      buf.append(toQuotedString(comment));
    }
    buf.append(";\n");
    return buf.toString();
  }

  @Override
  public String sqlAlterColumnNullConstraint(String tableName, String columnName, SqlType sqlType, int size, int scale,
                                             boolean nullable, Object defaultValue) {
    // mysql cannot change the null-constraint alone, the type must be repeated
    return sqlAlterColumnType(tableName, columnName, null, sqlType, size, scale, nullable, defaultValue);
  }

  @Override
  public String sqlAlterColumnComment(String tableName, String columnName, String comment) {
    return null;    // use sqlRenameAndAlterColumnType
  }

  @Override
  public String sqlAlterTableComment(String tableName, String comment) {
    return  SQL_ALTER_TABLE + tableName +
            " COMMENT=" +
            toQuotedString(comment == null ? "" : comment) +
            ";\n";
  }

  @Override
  public String sqlDropForeignKey(String referencingTableName, String foreignKeyName) {
    // mysql knows DROP CONSTRAINT only since 8.0.19, but DROP FOREIGN KEY in all versions
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(referencingTableName).append(" DROP FOREIGN KEY ");
    if (isDropIfExistsEnabled()) {
      buf.append(SQL_IF_EXISTS);
    }
    buf.append(foreignKeyName).append(";\n");
    return buf.toString();
  }

  @Override
  public String sqlCreateIndex(String tableName, String indexName, boolean unique, String filterCondition,
                               String... columnNames) {
    String[] indexParts = new String[columnNames.length];
    for (int ndx = 0; ndx < columnNames.length; ndx++) {
      String columnName = columnNames[ndx];
      String descending = "";
      if (columnName.startsWith("-")) {   // keep the marker interpreted by AbstractBackend
        columnName = columnName.substring(1);
        descending = "-";
      }
      // a functional key part must be enclosed in an extra pair of parentheses
      indexParts[ndx] = descending + (columnName.indexOf('(') >= 0 ? "(" + columnName + ")" : columnName);
    }
    return super.sqlCreateIndex(tableName, indexName, unique, filterCondition, indexParts);
  }

  @Override
  public boolean isDefaultEqual(ColumnMetaData column, SqlType sqlType, Object defaultValue) {
    // mysql reports the default of a string column without quotes, so an empty string comes back as
    // "" and is kept as such by MySqlColumnMetaData. The base class would normalize both the empty
    // string and "no default at all" to the same value and never detect the difference.
    if ((defaultValue == null) != (column.getDefaultValue() == null)) {
      return false;
    }
    return super.isDefaultEqual(column, sqlType, defaultValue);
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
                           // or changed from VARCHAR(x) to LONGTEXT
                           size == 0 && (sqlType == SqlType.VARCHAR || sqlType == SqlType.CLOB) &&
                           column.getSize() > 0 && column.getSize() <= maxSize);
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

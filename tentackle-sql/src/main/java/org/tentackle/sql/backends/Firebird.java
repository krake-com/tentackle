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
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.FirebirdColumnMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Backend for Firebird.
 * <p>
 * Requires at least Firebird 4.0, which is the first version providing identifiers of up to 63 characters
 * and decimals with a precision of up to 38 digits.
 */
@Service(Backend.class)
public class Firebird extends AbstractSql2008Backend {

  // maximum length for identifiers, names, etc...
  private static final int MAX_NAME_LENGTH = 63;

  // minimum major version
  private static final int MIN_MAJOR_VERSION = 4;

  // default size of a DECIMAL if the model does not provide one
  private static final int DEFAULT_DECIMAL_SIZE = 19;

  // maximum length of a VARCHAR in characters: 32765 bytes with up to 4 bytes per UTF8 character
  private static final int MAX_VARCHAR_SIZE = 8191;

  /** the locking clause: FOR UPDATE alone just declares an updatable cursor, but locks nothing. */
  public static final String SQL_WITH_LOCK = " WITH LOCK";

  private static final String TYPE_BLOB_BINARY = "BLOB SUB_TYPE BINARY";
  private static final String TYPE_BLOB_TEXT = "BLOB SUB_TYPE TEXT";

  // prefixes of the system tables
  private static final String[] SYSTEM_TABLE_PREFIXES = { "RDB$", "MON$", "SEC$" };

  /** array of additional reserved words (those not already covered by SQL92 and SQL2003). */
  public static final String[] RESERVED_WORDS_FIREBIRD = new String[] {
    "BINARY", "CORR", "COVAR_POP", "COVAR_SAMP", "CURRENT_CONNECTION", "CURRENT_ROLE", "CURRENT_TRANSACTION",
    "DECFLOAT", "DELETING", "GDSCODE", "INDEX", "INSERTING", "INT128", "LAG", "LEAD", "LONG", "OFFSET", "PLAN",
    "POST_EVENT", "PUBLICATION", "RECORD_VERSION", "RECREATE", "REGR_AVGX", "REGR_AVGY", "REGR_COUNT",
    "REGR_INTERCEPT", "REGR_R2", "REGR_SLOPE", "REGR_SXX", "REGR_SXY", "REGR_SYY", "RESETTING",
    "RETURNING_VALUES", "ROW_COUNT", "STDDEV_POP", "STDDEV_SAMP", "UNBOUNDED", "UPDATING", "VARBINARY",
    "VARIABLE", "VAR_POP", "VAR_SAMP"
  };

  // set of reserved words (built only once)
  private Set<String> reservedWords;


  /**
   * Creates the Firebird backend.
   */
  public Firebird() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public synchronized Set<String> getReservedWords() {
    if (reservedWords == null) {
      reservedWords = new HashSet<>(super.getReservedWords());
      reservedWords.addAll(Arrays.asList(RESERVED_WORDS_FIREBIRD));
    }
    return reservedWords;
  }

  @Override
  public int getMaxNameLength(SqlNameType nameType) {
    return MAX_NAME_LENGTH;
  }

  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":firebird");   // jdbc:firebirdsql: as well as jdbc:firebird:
  }

  @Override
  public String getName() {
    return "Firebird";
  }

  @Override
  public String getDriverClassName() {
    return "org.firebirdsql.jdbc.FBDriver";
  }

  @Override
  public void validateVersion(int databaseMajorVersion, int databaseMinorVersion) {
    if (databaseMajorVersion < MIN_MAJOR_VERSION) {
      throw new BackendException(this + " needs at least Firebird " + MIN_MAJOR_VERSION + ".0" +
                                 ", but is connected to " + databaseMajorVersion + "." + databaseMinorVersion);
    }
  }

  @Override
  public String getBackendId(Connection connection) {
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT CURRENT_CONNECTION FROM RDB$DATABASE")) {
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
  public String getDummySelect() {
    return "SELECT 1 FROM RDB$DATABASE";
  }

  @Override
  public boolean isSchemaSupported() {
    return false;   // not before Firebird 6
  }

  @Override
  public boolean isTemporaryName(String name) {
    // unquoted identifiers must start with a letter -> we use "tmp_" instead of the underscore
    return name != null && (name.startsWith("tmp_") || name.startsWith("TMP_"));
  }

  @Override
  public void assertValidName(SqlNameType nameType, String name) {
    super.assertValidName(nameType, name);
    if (name != null && name.startsWith("_")) {
      throw new BackendException(nameType + " '" + name + "' is illegal for backend " + this);
    }
  }

  @Override
  public boolean isReservedTableName(String name) {
    if (name != null) {
      String upperName = name.toUpperCase(Locale.ROOT);
      for (String prefix : SYSTEM_TABLE_PREFIXES) {
        if (upperName.startsWith(prefix)) {
          return true;
        }
      }
    }
    return super.isReservedTableName(name);
  }

  @Override
  public boolean isReleaseSavepointSupported() {
    return true;
  }

  @Override
  public boolean isAliasRequiredForSubSelect() {
    return true;
  }

  @Override
  public boolean isTransientTransactionException(SQLException ex) {
    return super.isTransientTransactionException(ex) ||
           isExceptionErrorCodeMatching(ex,
                                        335544336,    // isc_deadlock: deadlock
                                        335544345,    // isc_lock_conflict: lock conflict on no wait transaction
                                        335544451,    // isc_update_conflict: update conflicts with concurrent update
                                        335544510);   // isc_lock_timeout: lock time-out on wait transaction
  }

  /**
   * {@inheritDoc}
   * <p>
   * In Firebird, {@code FOR UPDATE} alone does not lock the selected rows. The explicit
   * {@code WITH LOCK} is required, which is only allowed for a select from a single table.
   */
  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    super.buildSelectSql(sqlBuilder, writeLock, limit, offset);
    if (writeLock) {
      sqlBuilder.append(SQL_WITH_LOCK);
    }
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    return switch (sqlType) {
      case DECIMAL -> 38;
      case VARCHAR -> MAX_VARCHAR_SIZE;
      default -> super.getMaxSize(sqlType);
    };
  }

  @Override
  public int getDefaultSize(SqlType sqlType) {
    // without an explicit size Firebird reads a bare DECIMAL as DECIMAL(9,0) and silently
    // rounds away all fraction digits, just like mysql does with its DECIMAL(10,0)
    return sqlType == SqlType.DECIMAL ? DEFAULT_DECIMAL_SIZE : super.getDefaultSize(sqlType);
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT -> TYPE_BOOLEAN;
      case TINYINT, SMALLINT -> TYPE_SMALLINT;    // there is no TINYINT
      case INTEGER -> TYPE_INTEGER;
      case BIGINT -> TYPE_BIGINT;
      case FLOAT -> TYPE_FLOAT;                   // single precision
      case DOUBLE -> TYPE_DOUBLE_PRECISION;
      case DECIMAL -> TYPE_DECIMAL;
      case CHAR -> TYPE_CHAR_1;
      case VARCHAR -> TYPE_VARCHAR;
      case DATE -> TYPE_DATE;
      case TIME -> TYPE_TIME;
      case TIMESTAMP -> TYPE_TIMESTAMP;
      case BLOB -> TYPE_BLOB_BINARY;
      case CLOB -> TYPE_BLOB_TEXT;
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    return switch (jdbcType) {
      case Types.TINYINT, Types.SMALLINT -> new SqlType[]{SqlType.TINYINT, SqlType.SMALLINT};
      // a BLOB SUB_TYPE TEXT is reported as LONGVARCHAR
      case Types.VARCHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB
              -> new SqlType[]{SqlType.VARCHAR, SqlType.CLOB};
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
  }

  @Override
  public String columnTypeNullDefaultToString(String columnName, SqlType sqlType, int size, int scale,
                                              boolean nullable, Object defaultValue) {
    StringBuilder buf = new StringBuilder();
    buf.append(columnTypeToString(sqlType, size, scale));
    // firebird wants the default first and then the null constraint
    if (defaultValue != null) {
      buf.append(" DEFAULT ");
      buf.append(valueToLiteral(sqlType, defaultValue));
    }
    if (!nullable) {
      buf.append(" NOT NULL");
    }
    return buf.toString();
  }

  @Override
  public String valueToLiteral(SqlType sqlType, Object value) {
    if (value instanceof Boolean bool) {
      return bool ? "TRUE" : "FALSE";
    }
    return super.valueToLiteral(sqlType, value);
  }

  @Override
  public ColumnMetaData createColumnMetaData(TableMetaData tableMetaData) {
    return new FirebirdColumnMetaData(tableMetaData);
  }

  @Override
  public String sqlCreateSequence(String name, Long start, Long increment) {
    // the grammar expects START WITH before INCREMENT BY
    StringBuilder buf = new StringBuilder();
    buf.append("CREATE SEQUENCE ").append(name);
    if (start != null) {
      buf.append(" START WITH ").append(start);
    }
    if (increment != null) {
      buf.append(" INCREMENT BY ").append(increment);
    }
    return buf.toString();
  }

  @Override
  public String sqlNextFromSequence(String name) {
    return "SELECT NEXT VALUE FOR " + name + " FROM RDB$DATABASE";
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

  /**
   * {@inheritDoc}
   * <p>
   * Firebird knows the sort order per index only, not per column: an index is either ascending or
   * descending as a whole. An index mixing both cannot be created.
   */
  @Override
  public String sqlCreateIndex(String tableName, String indexName, boolean unique, String filterCondition,
                               String... columnNames) {
    int descendingCount = 0;
    String[] plainColumnNames = new String[columnNames.length];
    for (int ndx = 0; ndx < columnNames.length; ndx++) {
      String columnName = columnNames[ndx];
      if (columnName.startsWith("-")) {
        columnName = columnName.substring(1);
        descendingCount++;
      }
      plainColumnNames[ndx] = columnName;
    }
    if (descendingCount > 0 && descendingCount < columnNames.length) {
      throw new BackendException(this + " cannot mix ascending and descending columns in index " + indexName);
    }
    String sql = super.sqlCreateIndex(tableName, indexName, unique, filterCondition, plainColumnNames);
    if (descendingCount > 0) {
      sql = sql.replaceFirst("INDEX ", "DESCENDING INDEX ");
    }
    return sql;
  }

  @Override
  public String sqlRenameTable(String tableName, String newTableName) {
    throw new BackendException(this + " cannot rename table " + tableName + " to " + newTableName +
                               ": the table must be created anew and the data copied");
  }

  @Override
  public String sqlRenameColumn(String tableName, String oldColumnName, String newColumnName) {
    return  SQL_ALTER_TABLE + tableName +
            SQL_ALTER_COLUMN +
            oldColumnName +
            " TO " +
            newColumnName +
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlRenameIndex(String tableName, String oldIndexName, String newIndexName) {
    return null;    // not supported -> drop and create
  }

  @Override
  public String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType, int size, int scale,
                             boolean nullable, Object defaultValue) {
    // no COLUMN keyword
    return  SQL_ALTER_TABLE + tableName +
            " ADD " +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlDropColumn(String tableName, String columnName) {
    // no COLUMN keyword
    return  SQL_ALTER_TABLE + tableName +
            " DROP " +
            columnName +
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType,
                                   int size, int scale, boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            SQL_ALTER_COLUMN +
            columnName +
            " TYPE " +
            // the null constraint and the default are separate column alterations
            columnTypeToString(sqlType, size, scale) +
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlAlterColumnNullConstraint(String tableName, String columnName, boolean nullable) {
    return  SQL_ALTER_TABLE + tableName +
            SQL_ALTER_COLUMN +
            columnName +
            (nullable ? " DROP" : " SET") +
            " NOT NULL" +
            getStatementSeparator() + "\n";
  }

}

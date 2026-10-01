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
import org.tentackle.sql.BackendVersionInfo;
import org.tentackle.sql.MigrationStrategy;
import org.tentackle.sql.NonStandardCommons;
import org.tentackle.sql.SqlNameType;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;

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
 * Backend for SAP HANA.
 * <p>
 * Requires at least SAP HANA 2.0. SAP HANA Cloud is supported as well.
 */
@Service(Backend.class)
public class Hana extends AbstractSql2003Backend {

  // maximum length for identifiers, names, etc...
  private static final int MAX_NAME_LENGTH = 127;

  // minimum major version (HANA Cloud reports 4)
  private static final int MIN_MAJOR_VERSION = 2;

  /** LIMIT string. */
  public static final String SQL_LIMIT = " LIMIT ";

  /** OFFSET string. */
  public static final String SQL_OFFSET = " OFFSET ";

  /** LIMIT ? string. */
  public static final String SQL_LIMIT_PAR = SQL_LIMIT + SQL_PAR;

  /** OFFSET ? string. */
  public static final String SQL_OFFSET_PAR = SQL_OFFSET + SQL_PAR;

  /** the limit used to emulate an OFFSET without a LIMIT (HANA requires both). */
  public static final int MAX_LIMIT = Integer.MAX_VALUE;

  private static final String TYPE_NCLOB = "NCLOB";

  private static final String[] RESERVED_SCHEMAS = {
      "PUBLIC",
      "SYS"
  };

  /** array of additional reserved words (those not already covered by SQL92 and SQL2003). */
  public static final String[] RESERVED_WORDS_HANA = new String[] {
    "CURRENT_CONNECTION", "CURRENT_SCHEMA", "CURRENT_TRANSACTION_ISOLATION_LEVEL", "CURRENT_UTCDATE",
    "CURRENT_UTCTIME", "CURRENT_UTCTIMESTAMP", "CURRVAL", "LIMIT", "MINUS", "NEXTVAL", "REVERSE", "ROWID",
    "SYSUUID", "TOP", "UTCTIMESTAMP"
  };

  // set of reserved words (built only once)
  private Set<String> reservedWords;


  /**
   * Creates the HANA backend.
   */
  public Hana() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public synchronized Set<String> getReservedWords() {
    if (reservedWords == null) {
      reservedWords = new HashSet<>(super.getReservedWords());
      reservedWords.addAll(Arrays.asList(RESERVED_WORDS_HANA));
    }
    return reservedWords;
  }

  @Override
  public int getMaxNameLength(SqlNameType nameType) {
    return MAX_NAME_LENGTH;
  }

  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":sap:");
  }

  @Override
  public String getName() {
    return "HANA";
  }

  @Override
  public String getDriverClassName() {
    return "com.sap.db.jdbc.Driver";
  }

  @Override
  public void validateVersion(BackendVersionInfo versionInfo) {
    if (versionInfo.majorVersion() < MIN_MAJOR_VERSION) {
      throw new BackendException(this + " needs at least SAP HANA " + MIN_MAJOR_VERSION + ".0" +
                                 ", but is connected to " + versionInfo);
    }
  }

  @Override
  public String getBackendId(Connection connection) {
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT CURRENT_CONNECTION FROM DUMMY")) {
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
    return "SELECT 1 FROM DUMMY";
  }

  @Override
  public boolean isReservedSchemaName(String name) {
    for (String reservedSchema: RESERVED_SCHEMAS) {
      if (reservedSchema.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return super.isReservedSchemaName(name);
  }

  @Override
  public boolean isAliasRequiredForSubSelect() {
    return true;
  }

  @Override
  public boolean isConstraintException(SQLException ex) {
    return super.isConstraintException(ex) ||
           isExceptionErrorCodeMatching(ex, 301);     // unique constraint violated
  }

  @Override
  public boolean isCommunicationLinkException(SQLException ex) {
    return super.isCommunicationLinkException(ex) ||
           isExceptionErrorCodeMatching(ex,
                                        -10709,   // connection failed
                                        -10807);  // connection down
  }

  @Override
  public boolean isTransientTransactionException(SQLException ex) {
    return super.isTransientTransactionException(ex) ||
           isExceptionErrorCodeMatching(ex,
                                        131,    // transaction rolled back by lock wait timeout
                                        133,    // transaction rolled back by detected deadlock
                                        146);   // resource busy and NOWAIT specified
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (isLeadingSelectMissing(sqlBuilder)) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
    if (offset > 0 && limit <= 0) {
      limit = MAX_LIMIT;   // HANA can't do OFFSET without LIMIT -> fake it
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
      limit = MAX_LIMIT;   // same as in buildSelectSql
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
      case DECIMAL -> 38;
      case VARCHAR -> 5000;
      default -> super.getMaxSize(sqlType);
    };
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT -> TYPE_BOOLEAN;
      case TINYINT, SMALLINT -> TYPE_SMALLINT;    // HANA's TINYINT is unsigned
      case INTEGER -> TYPE_INTEGER;
      case BIGINT -> TYPE_BIGINT;
      case FLOAT -> TYPE_REAL;
      case DOUBLE -> TYPE_DOUBLE;
      case DECIMAL -> TYPE_DECIMAL;               // without a size, this is a decimal floating point number
      case CHAR -> TYPE_NCHAR_1;                  // VARCHAR and CHAR are ASCII-only before HANA Cloud
      case VARCHAR -> TYPE_NVARCHAR;
      case DATE -> TYPE_DATE;
      case TIME -> TYPE_TIME;
      case TIMESTAMP -> TYPE_TIMESTAMP;
      case BLOB -> TYPE_BLOB;
      case CLOB -> TYPE_NCLOB;
      case XML -> TYPE_NCLOB;   // no native XML type
      case JSON -> TYPE_NCLOB;     // no native JSON column type either
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    return switch (jdbcType) {
      case Types.TINYINT, Types.SMALLINT -> new SqlType[]{SqlType.TINYINT, SqlType.SMALLINT};
      // XML and JSON are stored as NCLOB
      case Types.CLOB, Types.NCLOB -> new SqlType[]{SqlType.CLOB, SqlType.JSON, SqlType.XML};
      default -> super.jdbcTypeToSqlType(jdbcType, size, scale);
    };
  }

  @Override
  public String columnTypeNullDefaultToString(String columnName, SqlType sqlType, int size, int scale,
                                              boolean nullable, Object defaultValue) {
    StringBuilder buf = new StringBuilder();
    buf.append(columnTypeToString(sqlType, size, scale));
    // HANA wants the default first and then the null constraint
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
  public String sqlNextFromSequence(String name) {
    return "SELECT " + name + ".NEXTVAL FROM DUMMY";
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
  public String sqlRenameTable(String tableName, String newTableName) {
    return  "RENAME TABLE " + tableName +
            " TO " +
            newTableName +
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlRenameColumn(String tableName, String oldColumnName, String newColumnName) {
    return  "RENAME COLUMN " + tableName +
            "." +
            oldColumnName +
            " TO " +
            newColumnName +
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlRenameIndex(String tableName, String oldIndexName, String newIndexName) {
    return  "RENAME INDEX " + oldIndexName +
            " TO " +
            newIndexName +
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType, int size, int scale,
                             boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            " ADD (" +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ")" + getStatementSeparator() + "\n";
  }

  @Override
  public String sqlDropColumn(String tableName, String columnName) {
    return  SQL_ALTER_TABLE + tableName +
            " DROP (" +
            columnName +
            ")" + getStatementSeparator() + "\n";
  }

  /**
   * {@inheritDoc}
   * <p>
   * HANA alters a column by replacing its whole declaration. Hence, the null constraint and
   * the default value always change along with the type.
   */
  @Override
  public String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType,
                                   int size, int scale, boolean nullable, Object defaultValue) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(" ALTER (");
    buf.append(columnName);
    buf.append(' ');
    buf.append(columnTypeNullDefaultToString(columnName, sqlType, size, scale, nullable, defaultValue));
    if (nullable) {
      buf.append(" NULL");    // be explicit, since the declaration is replaced
    }
    buf.append(')');
    buf.append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  @Override
  public String sqlAlterColumnNullConstraint(String tableName, String columnName, SqlType sqlType, int size, int scale,
                                             boolean nullable, Object defaultValue) {
    // HANA cannot change the null-constraint alone, the type must be repeated
    return sqlAlterColumnType(tableName, columnName, null, sqlType, size, scale, nullable, defaultValue);
  }

  @Override
  public String sqlAlterColumnDefault(String tableName, String columnName, SqlType sqlType, Object defaultValue) {
    return null;    // HANA cannot change the default alone, see getMigrationStrategy
  }

  /**
   * {@inheritDoc}
   * <p>
   * HANA cannot change the default of a column alone: the whole column declaration is repeated instead.
   * Hence, a changed default yields {@link MigrationStrategy#TYPE}, and a changed null constraint
   * yields {@link MigrationStrategy#NULL}, which carries the type and the default as well.
   * {@link MigrationStrategy#DEFAULT} is never returned.
   */
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
                           // or changed from NVARCHAR(x) to NVARCHAR(maxSize)
                           size == 0 && sqlType == SqlType.VARCHAR &&
                           column.getSize() > 0 && column.getSize() != maxSize);
    boolean sizeWarning = maxSize != -1 && size != 0 && size < column.getSize();
    boolean scaleChanged = scale > column.getScale(); // smaller model scale is allowed
    boolean scaleWarning = maxSize != -1 && scale < column.getScale();

    if (nameChanged) {
      strategies.add(MigrationStrategy.NAME);   // comes first because the next steps refer to the new column name
    }
    if (nullChanged) {
      // updates the column to non-null values before, if necessary, and carries type and default as well
      strategies.add(MigrationStrategy.NULL);
    }
    else if (typeChanged || sizeChanged || scaleChanged || defaultChanged) {
      strategies.add(MigrationStrategy.TYPE);   // carries the default as well
    }
    else if (sizeWarning || scaleWarning) {
      strategies.add(MigrationStrategy.TYPE_WARNING); // only as comment
    }
    if (commentChanged) {
      strategies.add(MigrationStrategy.COMMENT);
    }

    return strategies.toArray(new MigrationStrategy[0]);
  }

}

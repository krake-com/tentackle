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
import org.tentackle.sql.metadata.Deferrability;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.OracleColumnMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Backend for older Oracle versions 8 to 11.<br>
 * Requires a URL with type postfix {@code "|Oracle8"}.
 *
 * @author harald
 */
@Service(Backend.class)
public class Oracle8 extends AbstractSql2003Backend {

  /** window function part 1. */
  public static final String SQL_NESTED_SELECT_1 = "/*+ FIRST_ROWS */ * FROM (SELECT ";

  /** window function part 2. */
  public static final String SQL_NESTED_SELECT_2 = ") WHERE ";

  /** condition for limit and offset. */
  public static final String SQL_NESTED_SELECT_ROWNUM = "ROWNUM";

  /** condition for limit and offset. */
  public static final String SQL_NESTED_SELECT_LIMIT = SQL_NESTED_SELECT_ROWNUM + SQL_LESSOREQUAL_PAR;

  /** condition for limit and offset. */
  public static final String SQL_NESTED_SELECT_OFFSET = SQL_NESTED_SELECT_ROWNUM + SQL_GREATER_PAR;


  // oracle format strings
  private static final String ORA_DATE_FORMAT      = "YYYY-MM-DD";
  private static final String ORA_TIME_FORMAT      = "HH24:MI:SS";
  private static final String ORA_TIMESTAMP_FORMAT = ORA_DATE_FORMAT + " " + ORA_TIME_FORMAT;

  private static final String ORA_TO_DATE_LEAD     = "TO_DATE('";
  private static final String ORA_TO_DATE_SEP      = "','";
  private static final String ORA_TO_DATE_TAIL     = "')";

  // java format strings
  private static final String JAVA_DATE_FORMAT      = "yyyy-MM-dd";
  private static final String JAVA_TIME_FORMAT      = "HH:mm:ss";
  private static final String JAVA_TIMESTAMP_FORMAT = JAVA_DATE_FORMAT + " " + JAVA_TIME_FORMAT;

  // java formats
  private static final DateTimeFormatter DATE_FORMAT      = DateTimeFormatter.ofPattern(JAVA_DATE_FORMAT);
  private static final DateTimeFormatter TIME_FORMAT      = DateTimeFormatter.ofPattern(JAVA_TIME_FORMAT);
  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(JAVA_TIMESTAMP_FORMAT);

  // Oracle empty string (special because Oracle treats it as NULL)
  private static final String ORACLE_EMPTY_STRING = " ";   // one blank

  // maximum length for identifiers, names, etc...
  private static final int MAX_NAME_LENGTH = 30;

  private static final String[] RESERVED_SCHEMAS = {
      "ANONYMOUS",
      "APEX_030200",
      "APEX_PUBLIC_USER",
      "APPQOSSYS",
      "BI",
      "CTXSYS",
      "DBSNMP",
      "DIP",
      "EXFSYS",
      "FLOWS_FILES",
      "HR",
      "IX",
      "MDDATA",
      "MDSYS",
      "MGMT_VIEW",
      "OE",
      "OLAPSYS",
      "ORACLE_OCM",
      "ORDDATA",
      "ORDPLUGINS",
      "ORDSYS",
      "OUTLN",
      "OWBSYS",
      "OWBSYS_AUDIT",
      "PM",
      "SCOTT",
      "SH",
      "SI_INFORMTN_SCHEMA",
      "SPATIAL_CSW_ADMIN_USR",
      "SPATIAL_WFS_ADMIN_USR",
      "SYS",
      "SYSMAN",
      "SYSTEM",
      "WMSYS",
      "XDB",
      "XS$NULL"
  };



  /**
   * Converts a date to a string.
   *
   * @param date the date
   * @return a TO_DATE-date-string
   */
  public static String dateString(Date date) {
    return ORA_TO_DATE_LEAD +
           DATE_FORMAT.format(date.toLocalDate()) +
           ORA_TO_DATE_SEP +
           ORA_DATE_FORMAT +
           ORA_TO_DATE_TAIL;
  }


  /**
   * Converts a time to a string.
   *
   * @param time the time
   * @return a TO_DATE-time-string
   */
  public static String timeString(Time time) {
    return ORA_TO_DATE_LEAD +
           TIME_FORMAT.format(time.toLocalTime()) +
           ORA_TO_DATE_SEP +
           ORA_TIME_FORMAT +
           ORA_TO_DATE_TAIL;
  }


  /**
   * Converts a timestamp to a string.
   *
   * @param timestamp the timestamp
   * @return a TO_DATE-timestamp-string
   */
  public static String timestampString(Timestamp timestamp) {
    return ORA_TO_DATE_LEAD +
           TIMESTAMP_FORMAT.format(timestamp.toLocalDateTime()) +
           ORA_TO_DATE_SEP +
           ORA_TIMESTAMP_FORMAT +
           ORA_TO_DATE_TAIL;
  }


  /**
   * Creates the backend for Oracle 8.
   */
  public Oracle8() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean isDeprecated() {
    return true;
  }

  @Override
  public String getDriverClassName() {
    return "oracle.jdbc.driver.OracleDriver";
  }

  @Override
  public Connection createConnection(String url, String username, char[] password) throws SQLException {
    Properties props = new Properties();
    props.put("user", username);
    if (password != null) {
      props.put("password", createPassword(password));
    }
    props.put("remarksReporting","true");   // for migrator

    // DriverManager.getConnection() doesn't work in most containers,
    // unless the class is preloaded within the application's classloader.
    try {
      Class.forName(getDriverClassName());
    }
    catch (ClassNotFoundException e) {
      // no logger available, will fail below anyway...
    }

    return DriverManager.getConnection (url, props);
  }

  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":oracle:");
  }

  @Override
  public String getName() {
    return "Oracle8";
  }

  @Override
  public String getBackendId(Connection connection) {
    try (Statement stmt = connection.createStatement()) {
      ResultSet rs = stmt.executeQuery("select sys_context('userenv','sid') from dual");
      if (rs.next()) {
        return "SID-" + rs.getString(1);
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
  public boolean isTemporaryName(String name) {
    // oracle does not allow identifiers to start with an underscore -> we use "tmp_" instead.
    return name != null && (name.startsWith("tmp_") || name.startsWith("TMP_"));
  }

  @Override
  public boolean isReservedTableName(String name) {
    return super.isReservedTableName(name) ||
           name != null && name.contains("$");
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
  public void assertValidName(SqlNameType nameType, String name) {
    super.assertValidName(nameType, name);
    if (name != null && name.startsWith("_")) {
      throw new BackendException(nameType + " '" + name + "' is illegal for backend " + this);
    }
  }

  @Override
  public int getMaxNameLength(SqlNameType nameType) {
    return MAX_NAME_LENGTH;
  }

  @Override
  public boolean isTransientTransactionException(SQLException ex) {
    return super.isTransientTransactionException(ex) ||
           isExceptionErrorCodeMatching(ex,
                                        54,     // ORA-00054: resource busy and acquire with NOWAIT specified or timeout expired
                                        60,     // ORA-00060: deadlock detected while waiting for resource
                                        1013,   // ORA-01013: user requested cancel of current operation
                                        4020,   // ORA-04020 deadlock detected while trying to lock object
                                        4021,   // ORA-04021 timeout occurred while waiting to lock object
                                        30006); // ORA-30006: resource busy; acquire with WAIT timeout expired
  }

  @Override
  public String getEmptyString() {
    return ORACLE_EMPTY_STRING;
  }

  @Override
  public String getDummySelect() {
    return "SELECT 1 FROM DUAL";
  }

  @Override
  public String columnTypeNullDefaultToString(String columnName, SqlType sqlType, int size, int scale,
                                              boolean nullable, Object defaultValue) {
    StringBuilder buf = new StringBuilder();
    buf.append(columnTypeToString(sqlType, size, scale));
    // oracle wants default first and then not null
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
    if (value instanceof String && ((String) value).isEmpty()) {
      value = ORACLE_EMPTY_STRING;
    }
    if (value instanceof Boolean) {
      return Boolean.TRUE.equals(value) ? "1" : "0";
    }
    return super.valueToLiteral(sqlType, value);
  }

  @Override
  public boolean isDefaultEqual(ColumnMetaData column, SqlType sqlType, Object defaultValue) {
    // Oracle stores the default as the source text of the DDL, so the model's value must be
    // mapped the same way valueToLiteral does before it is compared: the empty string is
    // written as a single blank, booleans as 1/0. Without that, the migrator would report
    // such a default as changed on every run - and re-applying it would not help.
    if (defaultValue instanceof String str && str.isEmpty()) {
      defaultValue = getEmptyString();
    }
    else if (defaultValue instanceof Boolean bool) {
      defaultValue = bool ? "1" : "0";
    }
    return super.isDefaultEqual(column, sqlType, defaultValue);
  }

  @Override
  public ColumnMetaData createColumnMetaData(TableMetaData tableMetaData) {
    return new OracleColumnMetaData(tableMetaData);
  }

  @Override
  public boolean isExtraCommitRequired() {
    return true;
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    boolean insertSelect = isLeadingSelectMissing(sqlBuilder);
    if (limit > 0 || offset > 0) {
      sqlBuilder.insert(0, SQL_NESTED_SELECT_1);
    }
    if (insertSelect) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
    if (limit > 0 || offset > 0) {
      sqlBuilder.append(SQL_NESTED_SELECT_2);
      if (offset > 0) {
        sqlBuilder.append(SQL_NESTED_SELECT_OFFSET);
      }
      if (limit > 0) {
        if (offset > 0) {
          sqlBuilder.append(SQL_AND);
        }
        sqlBuilder.append(SQL_NESTED_SELECT_LIMIT);
      }
    }
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    return switch (sqlType) {
      case DECIMAL -> 37;
      case VARCHAR -> 4000;
      default -> super.getMaxSize(sqlType);
    };
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    return switch (sqlType) {
      case BIT, TINYINT -> TYPE_NUMBER_1;
      case SMALLINT -> TYPE_NUMBER_5;
      case INTEGER -> TYPE_NUMBER_10;
      case BIGINT -> TYPE_NUMBER_19;
      case FLOAT -> TYPE_REAL;
      case DOUBLE -> TYPE_DOUBLE;
      case DECIMAL -> TYPE_NUMBER;
      case CHAR -> TYPE_CHAR_1;
      case VARCHAR -> TYPE_VARCHAR;
      case DATE, TIME -> TYPE_DATE;
      case TIMESTAMP -> TYPE_TIMESTAMP;
      case BLOB -> TYPE_BLOB;
      case CLOB -> TYPE_CLOB;
      default -> super.sqlTypeToString(sqlType, size);
    };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    switch(jdbcType) {
      case Types.BIT:
      case Types.TINYINT:
        return new SqlType[] { SqlType.BIT, SqlType.TINYINT };

      case Types.NUMERIC:
      case Types.DECIMAL:
        if (scale == 0) {
          switch (size) {
            case 1:
              return new SqlType[] {SqlType.BIT, SqlType.TINYINT};
            case 5:
              return new SqlType[] {SqlType.SMALLINT};
            case 10:
              return new SqlType[] {SqlType.INTEGER};
            case 19:
              return new SqlType[] {SqlType.BIGINT};
          }
        }
        return new SqlType[] { SqlType.DECIMAL };

      case Types.DATE:
      case Types.TIME:
        return new SqlType[] { SqlType.DATE, SqlType.TIME };

      default:
        return super.jdbcTypeToSqlType(jdbcType, size, scale);
    }
  }


  @Override
  public String sqlNextFromSequence(String name) {
    return "SELECT " + name + ".NEXTVAL FROM DUAL";
  }

  @Override
  public String sqlRenameTable(String tableName, String newTableName) {
    return "RENAME TABLE " + tableName +
           " TO " +
           newTableName +
           ";\n";
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
  public String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType, int size, int scale,
          boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            " ADD (" +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ");\n";
  }

  @Override
  public String sqlAlterColumnNullConstraint(String tableName, String columnName, boolean nullable) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(" MODIFY (");
    buf.append(columnName);
    if (nullable) {
      buf.append(" NULL");
    }
    else  {
      buf.append(" NOT NULL");
    }
    buf.append(");\n");
    return buf.toString();
  }

  @Override
  public String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType,
                                   int size, int scale, boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            " MODIFY (" +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, true, null, false, false) +
            ");\n";
  }

  @Override
  public String sqlAlterColumnDefault(String tableName, String columnName, SqlType sqlType, Object defaultValue) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(" MODIFY ");
    buf.append(columnName);
    if (defaultValue == null) {
      // functionally equivalent to DROP DEFAULT, but not on the DDL level,
      // To get rid of the default NULL the column must be added, copied, renamed, dropped
      // or the table renamed, re-created, copied, dropped.
      // Oracle...
      buf.append(" DEFAULT NULL");
    }
    else  {
      buf.append(" DEFAULT ");
      buf.append(valueToLiteral(sqlType, defaultValue));
    }
    buf.append(";\n");
    return buf.toString();
  }

  @Override
  protected String extractWhereClause(String sql, int whereOffset) {
    sql = super.extractWhereClause(sql, whereOffset);
    int ndx = sql.lastIndexOf(SQL_NESTED_SELECT_ROWNUM, whereOffset);
    if (ndx >= 0) {
      sql = sql.substring(0, ndx);
    }
    return sql;
  }

  @Override
  public boolean isSetConstraintsDeferredSupported() {
    return true;
  }

  @Override
  public String sqlSetConstraintsDeferred(boolean deferred) {
    return "SET CONSTRAINTS ALL " + (deferred ? "DEFERRED" : "IMMEDIATE");
  }

  @Override
  public void enrichIndexDeferrability(TableMetaData tableMetaData, Map<String, IndexMetaData> indexesByName,
                                        DatabaseMetaData metaData) throws SQLException {
    if (indexesByName.isEmpty()) {
      return;
    }
    String schemaName = tableMetaData.getSchemaName();
    if (schemaName == null) {
      schemaName = getDefaultSchema();
    }
    String sql = "SELECT constraint_name, deferrable, deferred FROM all_constraints " +
                 "WHERE constraint_type = 'U' AND table_name = ? AND owner = ?";
    try (PreparedStatement stmt = metaData.getConnection().prepareStatement(sql)) {
      stmt.setString(1, tableMetaData.getTableName().toUpperCase(Locale.ROOT));
      stmt.setString(2, schemaName.toUpperCase(Locale.ROOT));
      try (ResultSet resultSet = stmt.executeQuery()) {
        while (resultSet.next()) {
          IndexMetaData indexMetaData = indexesByName.get(resultSet.getString("constraint_name"));
          if (indexMetaData != null) {
            boolean deferrable = "DEFERRABLE".equals(resultSet.getString("deferrable"));
            Deferrability deferrability;
            if (!deferrable) {
              deferrability = Deferrability.NOT_DEFERRABLE;
            }
            else if ("DEFERRED".equals(resultSet.getString("deferred"))) {
              deferrability = Deferrability.DEFERRABLE_DEFERRED;
            }
            else {
              deferrability = Deferrability.DEFERRABLE_IMMEDIATE;
            }
            indexMetaData.setDeferrability(deferrability);
          }
        }
      }
    }
  }

}

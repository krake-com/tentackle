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

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Properties;


/**
 * Backend for Oracle.
 *
 * Contains some Oracle-specific utility methods as well:<p>
 *
 * Date-conversion: Oracle does not use an index on a DATE-column if passed as a Timestamp-variable
 * via a prepared statement, even if used in conjunction with an SQL-hint /*+ INDEX(...)
 * In such cases the TO_DATE-function must be explicitly used.
 *
 <pre>
 Example:

      if (getDb().isOracle()) {
        query.add(" AND m." + CN_ZEITPUNKT + "&gt;=" + OracleHelper.timestampString(par.vonZeitpunkt));
      }
      else  {
        query.add(" AND m." + CN_ZEITPUNKT + "&gt;=?", par.vonZeitpunkt);
      }


 or:
      if (getDb().isOracle()) {
        query.add(" AND m." + CN_ZEITPUNKT + "&gt;=TO_DATE(?,'" + OracleHelper.ORA_TIMESTAMP_FORMAT + "')",
                  OracleHelper.TIMESTAMP_FORMAT.format(par.vonZeitpunkt));
      }
      else  {
        query.add(" AND m." + CN_ZEITPUNKT + "&gt;=?", par.vonZeitpunkt);
      }
 </pre>
 *
 *
 * Rule: if possible use TIMESTAMP column type for a Timestamp-java attribute. In such a case
 * at least Oracle 10 uses the correct index and you don't need this workaround.
 *
 * @author harald
 */
@Service(Backend.class)
public class Oracle extends AbstractSql2003Backend {


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
  private static final DateFormat DATE_FORMAT      = new SimpleDateFormat(JAVA_DATE_FORMAT);
  private static final DateFormat TIME_FORMAT      = new SimpleDateFormat(JAVA_TIME_FORMAT);
  private static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat(JAVA_TIMESTAMP_FORMAT);

  // oracle empty string (special because oracle treats "" as NULL)
  private static final String ORACLE_EMPTY_STRING = " ";   // one blank

  // maximum length for identifiers, names, etc...
  private static final int MAX_NAME_LENGTH = 30;



  /**
   * Converts a date to a string.
   *
   * @param date the date
   * @return a TO_DATE-date-string
   */
  public static String dateString(Date date) {
    synchronized(DATE_FORMAT) {
      return  ORA_TO_DATE_LEAD +
              DATE_FORMAT.format(date) +
              ORA_TO_DATE_SEP +
              ORA_DATE_FORMAT +
              ORA_TO_DATE_TAIL;
    }
  }


  /**
   * Converts a time to a string.
   *
   * @param time the time
   * @return a TO_DATE-time-string
   */
  public static String timeString(Time time)  {
    synchronized(TIME_FORMAT) {
      return  ORA_TO_DATE_LEAD +
              TIME_FORMAT.format(time) +
              ORA_TO_DATE_SEP +
              ORA_TIME_FORMAT +
              ORA_TO_DATE_TAIL;
    }
  }


  /**
   * Converts a timestamp to a string.
   *
   * @param timestamp the timestamp
   * @return a TO_DATE-timestamp-string
   */
  public static String timestampString(Timestamp timestamp)  {
    synchronized(TIMESTAMP_FORMAT) {
      return  ORA_TO_DATE_LEAD +
              TIMESTAMP_FORMAT.format(timestamp) +
              ORA_TO_DATE_SEP +
              ORA_TIMESTAMP_FORMAT +
              ORA_TO_DATE_TAIL;
    }
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
    props.put("remarksReporting","true");   // for migator

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
    return url.contains(":oracle");
  }

  @Override
  public String getName() {
    return "Oracle";
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
      throw new TentackleRuntimeException("cannot determine backend id", ex);
    }
  }

  @Override
  public boolean isTemporaryName(String name) {
    // oracle does not allow identifiers to start with an underscore -> we use "tmp_" instead.
    return name != null && (name.startsWith("tmp_") || name.startsWith("TMP_"));
  }

  @Override
  public void assertValidName(SqlNameType nameType, String name) {
    super.assertValidName(nameType, name);
    assertNameLength(nameType, name);
    if (name != null && name.startsWith("_")) {
      throw new BackendException(nameType + " '" + name + "' is illegal for backend " + this);
    }
  }

  @Override
  public String getEmptyString() {
    return ORACLE_EMPTY_STRING;
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
  public boolean sqlRequiresExtraCommit() {
    return true;
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (limit > 0 || offset > 0) {
      sqlBuilder.insert(0, SQL_NESTED_SELECT_1);
    }
    sqlBuilder.insert(0, SQL_SELECT);
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
    switch(sqlType) {

      case DECIMAL:
        return 37;

      case VARCHAR:
        return 4000;

      default:
        return super.getMaxSize(sqlType);
    }
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    switch(sqlType) {

      case BIT:
        return TYPE_NUMBER_1;

      case TINYINT:
        return TYPE_NUMBER_1;

      case SMALLINT:
        return TYPE_NUMBER_5;

      case INTEGER:
        return TYPE_NUMBER_10;

      case BIGINT:
        return TYPE_NUMBER_19;

      case FLOAT:
        return TYPE_REAL;

      case DOUBLE:
        return TYPE_DOUBLE;

      case DECIMAL:
        return TYPE_NUMBER;

      case CHAR:
        return TYPE_CHAR_1;

      case VARCHAR:
        return TYPE_VARCHAR;

      case DATE:
        return TYPE_DATE;

      case TIME:
        return TYPE_DATE;

      case TIMESTAMP:
        return TYPE_TIMESTAMP;

      case BLOB:
        return TYPE_BLOB;

      case CLOB:
        return TYPE_CLOB;

      default:
        return super.sqlTypeToString(sqlType, size);
    }
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
  public String sqlNextFromSequene(String name) {
    return "SELECT " + name + ".NEXTVAL FROM DUAL";
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

  private void assertNameLength(SqlNameType nameType, String name) {
    if (name != null && name.length() > MAX_NAME_LENGTH) {
      throw new BackendException(nameType + " name " + name + " is too long (" + name.length() + "). " +
                                 this + " allows only " + MAX_NAME_LENGTH);
    }
  }

}

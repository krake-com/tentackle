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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.IndexColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;


/**
 * Abstract parent class for backends.
 *
 * @author harald
 */
public abstract class AbstractBackend implements Backend {

  // --------------------- type strings ----------------------------

  protected static final String TYPE_BIGINT = "BIGINT";
  protected static final String TYPE_BIT = "BIT";
  protected static final String TYPE_BLOB = "BLOB";
  protected static final String TYPE_BOOL = "BOOL";
  protected static final String TYPE_BOOLEAN = "BOOLEAN";
  protected static final String TYPE_BYTEA = "BYTEA";
  protected static final String TYPE_BYTE = "BYTE";
  protected static final String TYPE_CHAR_1 = "CHAR(1)";
  protected static final String TYPE_DATE = "DATE";
  protected static final String TYPE_DATETIME = "DATETIME";
  protected static final String TYPE_DATETIME_YEAR_TO_SECOND = "DATETIME YEAR TO SECOND";
  protected static final String TYPE_DECIMAL = "DECIMAL";
  protected static final String TYPE_DECIMAL_19 = "DECIMAL(19)";
  protected static final String TYPE_DOUBLE = "DOUBLE";
  protected static final String TYPE_FLOAT = "FLOAT";
  protected static final String TYPE_FLOAT4 = "FLOAT4";
  protected static final String TYPE_FLOAT8 = "FLOAT8";
  protected static final String TYPE_INT = "INT";
  protected static final String TYPE_INT2 = "INT2";
  protected static final String TYPE_INT4 = "INT4";
  protected static final String TYPE_INT8 = "INT8";
  protected static final String TYPE_INTEGER = "INTEGER";
  protected static final String TYPE_NCHAR_1 = "NCHAR(1)";
  protected static final String TYPE_NUMBER = "NUMBER";
  protected static final String TYPE_NUMBER_1 = "NUMBER(1)";
  protected static final String TYPE_NUMBER_5 = "NUMBER(5)";
  protected static final String TYPE_NUMBER_10 = "NUMBER(10)";
  protected static final String TYPE_NUMBER_19 = "NUMBER(19)";
  protected static final String TYPE_NVARCHAR = "NVARCHAR";
  protected static final String TYPE_NVARCHAR_MAX = "NVARCHAR(MAX)";
  protected static final String TYPE_REAL = "REAL";
  protected static final String TYPE_TEXT = "TEXT";
  protected static final String TYPE_TIME = "TIME";
  protected static final String TYPE_TIMESTAMP = "TIMESTAMP";
  protected static final String TYPE_TINYINT = "TINYINT";
  protected static final String TYPE_SMALLFLOAT = "SMALLFLOAT";
  protected static final String TYPE_SMALLINT = "SMALLINT";
  protected static final String TYPE_VARBINARY_MAX = "VARBINARY(MAX)";
  protected static final String TYPE_VARCHAR = "VARCHAR";


  /** the empty string. */
  public static final String EMPTY_STRING = "";

  /** use DROP IF EXISTS if supported. */
  private boolean dropIfExistsEnabled;


  @Override
  public boolean isMatchingName(String name) {
    return getName().equalsIgnoreCase(name);
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public Connection createConnection(String url, String username, char[] password) throws SQLException {
    // DriverManager.getConnection() doesn't work in most containers,
    // unless the class is preloaded within the application's classloader.
    try {
      Class.forName(getDriverClassName());
    }
    catch (ClassNotFoundException e) {
      // no logger available, will fail below anyway...
    }
    return DriverManager.getConnection (url, username, (password == null ? "" : new String(password)));
  }

  @Override
  public DatabaseMetaData[] getMetaData(BackendInfo backendInfo) throws SQLException {
    Connection con = createConnection(backendInfo.getUrl(), backendInfo.getUser(), backendInfo.getPassword());
    DatabaseMetaData metaData = con.getMetaData();
    return new DatabaseMetaData[] { metaData };
  }

  @Override
  public boolean isTemporaryName(String name) {
    return name != null && name.startsWith("_");
  }

  @Override
  public String getEmptyString() {
    return EMPTY_STRING;
  }

  @Override
  public String sqlAsBeforeTableAlias() {
    return " ";
  }

  @Override
  public boolean needAliasForSubselect() {
    return false;
  }

  @Override
  public boolean sqlRequiresExtraCommit() {
    return false;
  }

  @Override
  public boolean sqlResultSetIsClosedSupported() {
    return false;
  }

  @Override
  public String sqlComment(String text) {
    if (text != null) {
      StringTokenizer stok = new StringTokenizer(text, "\n");
      StringBuilder buf = new StringBuilder();
      while (stok.hasMoreTokens()) {
        String line = stok.nextToken();
        if (!line.startsWith("-- ")) {
          buf.append("-- ");
        }
        buf.append(line);
        buf.append("\n");
      }
      return buf.toString();
    }
    return null;
  }

  @Override
  public String sqlJoin(JoinType type, String joinedTableName, String joinedTableAlias, String join) {
    StringBuilder buf = new StringBuilder();
    buf.append(' ').append(type).append(' ');
    buf.append(joinedTableName);
    if (joinedTableAlias != null) {
      buf.append(sqlAsBeforeTableAlias()).append(joinedTableAlias);
    }
    buf.append(" ON ");
    buf.append(join);
    return buf.toString();
  }

  @Override
  public String sqlFunction(String functionName, String expression) {
    StringBuilder buf = new StringBuilder(functionName.toUpperCase()).append('(');
    if (expression != null) {
      buf.append(expression);
    }
    buf.append(')');
    return buf.toString();
  }

  @Override
  public String getCoalesceKeyword() {
    throw new BackendException("backend " + this + " does not support COALESCE or similar keywords");
  }

  @Override
  public boolean allowsExpressionsReferringToTablesBeingUpdated() {
    return true;
  }

  @Override
  public boolean needSetLongWorkaround() {
    return false;
  }

  @Override
  public boolean needTxForFetchsize() {
    return false;
  }

  @Override
  public boolean isConstraintException(SQLException ex) {
    return isExceptionStateStartingWith(ex, "23");
  }

  @Override
  public boolean isCommunicationLinkException(SQLException ex) {
    return isExceptionStateStartingWith(ex, "08") ||    // initial connection failed
           isExceptionStateStartingWith(ex, "57");      // connection vanished or became unusable
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    switch(sqlType) {
      case BIT:
      case TINYINT:
      case SMALLINT:
      case INTEGER:
      case BIGINT:
      case FLOAT:
      case DOUBLE:
      case CHAR:
      case DATE:
      case TIME:
      case TIMESTAMP:
      case LONGVARBINARY:
        return -1;    // sizeless type

      default:
        return 0;   // default is unlimited
    }
  }

  @Override
  public int getMaxScale(SqlType sqlType, int size) {
    if (size > 0) {
      return size - 1;
    }
    return 0;
  }

  @Override
  public int getDefaultSize(SqlType sqlType) {
    if (sqlType == SqlType.VARCHAR) {
      return getMaxSize(sqlType);
    }
    return 0;
  }


  @Override
  public String getDefaultSchema() {
    return null;
  }


  @Override
  public ModelMetaData getModelMetaData(DatabaseMetaData[] metaData, String[] schemas, String... tableNames) {
    ModelMetaData modelMetaData = new ModelMetaData(this, metaData, schemas);
    for (String tableName: tableNames) {
      TableMetaData table = getTableMetaData(modelMetaData, tableName);
      if (table != null) {
        modelMetaData.addTableMetaData(table);
      }
    }
    return modelMetaData;
  }


  @Override
  public TableMetaData getTableMetaData(ModelMetaData modelMetaData, String tableName) {

    // Query the database for table. If an exception is thrown, assume no such table.
    // Most implementations just return an empty result set if the table is not known,
    // but one never knows...

    // try original tablename
    TableMetaData table = createTableMetaData(modelMetaData, tableName);

    String schemaPattern = null;
    String tablePattern = tableName;
    int dotNdx = tableName.indexOf('.');
    if (dotNdx >= 0) {
      schemaPattern = tableName.substring(0, dotNdx);
      tablePattern = tableName.substring(dotNdx + 1);
    }

    boolean found = false;
    for (DatabaseMetaData metaData: modelMetaData.getMetaData()) {
      try {
        try {
          table.setupTableFromMetaData(metaData, modelMetaData.getSchemas(), schemaPattern, tablePattern);
          found = true;
          break;
        }
        catch (BackendException ex1) {
          try {
            table.setupTableFromMetaData(metaData, modelMetaData.getSchemas(),
                                         schemaPattern == null ? null : schemaPattern.toUpperCase(),
                                         tablePattern.toUpperCase());
            found = true;
            break;
          }
          catch (BackendException ex2) {
            try {
              table.setupTableFromMetaData(metaData, modelMetaData.getSchemas(),
                                           schemaPattern == null ? null : schemaPattern.toLowerCase(),
                                           tablePattern.toLowerCase());
              found = true;
              break;
            }
            catch (BackendException ex3) {
              // no such table
            }
          }
        }
      }
      catch (SQLException sqx) {
        throw new BackendException("backend lacks essential meta data support", sqx);
      }
    }

    return found ? table : null;
  }



  @Override
  public TableMetaData createTableMetaData(ModelMetaData modelMetaData, String tableName) {
    return new TableMetaData(modelMetaData, tableName);
  }

  @Override
  public ColumnMetaData createColumnMetaData(TableMetaData tableMetaData) {
    return new ColumnMetaData(tableMetaData);
  }

  @Override
  public IndexMetaData createIndexMetaData(TableMetaData tableMetaData) {
    return new IndexMetaData(tableMetaData);
  }

  @Override
  public IndexColumnMetaData createIndexColumnMetaData(IndexMetaData indexMetaData) {
    return new IndexColumnMetaData(indexMetaData);
  }

  @Override
  public String sqlCreateTableIntro(String tableName, String comment) {
    StringBuilder buf = new StringBuilder(sqlCreateTableIntroWithoutComment(tableName));
    if (comment != null) {
      buf.append(" -- ");
      buf.append(comment);
    }
    buf.append('\n');
    return buf.toString();
  }

  /**
   * Generates the first line of a CREATE TABLE statement.
   *
   * @param tableName the tablename with optional schema separated by a dot
   * @return the SQL code including the opening bracket
   */
  protected String sqlCreateTableIntroWithoutComment(String tableName) {
    return "CREATE TABLE " + tableName + " (";
  }

  @Override
  public String sqlCreateTableClosing(String tableName, String comment) {
    return ");\n";
  }

  @Override
  public String sqlCreateTableComment(String tableName, String comment) {
    return "";
  }

  @Override
  public String sqlAlterTableComment(String tableName, String comment) {
    return sqlCreateTableComment(tableName, comment);   // usually the same
  }

  @Override
  public String columnTypeToString(SqlType sqlType, int size, int scale) {
    StringBuilder buf = new StringBuilder();
    buf.append(sqlTypeToString(sqlType, size));
    if (size == 0) {
      size = getDefaultSize(sqlType);
    }
    int maxSize = getMaxSize(sqlType);
    if (maxSize != 0 && size > maxSize) {
      size = maxSize;
    }
    if (size > 0) {
      buf.append('(');
      buf.append(size);
      if (scale > 0) {
        buf.append(',');
        int maxScale = getMaxScale(sqlType, size);
        if (maxScale > 0 && scale > maxScale) {
          scale = maxScale;
        }
        buf.append(scale);
      }
      buf.append(')');
    }
    return buf.toString();
  }

  @Override
  public String columnTypeNullDefaultToString(String columnName, SqlType sqlType, int size, int scale,
                                              boolean nullable, Object defaultValue) {
    StringBuilder buf = new StringBuilder();
    buf.append(columnTypeToString(sqlType, size, scale));
    if (!nullable) {
      buf.append(" NOT NULL");
    }
    if (defaultValue != null) {
      buf.append(" DEFAULT ");
      buf.append(valueToLiteral(sqlType, defaultValue));
    }
    return buf.toString();
  }



  @Override
  public String valueToLiteral(SqlType sqlType, Object value) {
    switch(sqlType) {
      case CHAR:
      case VARCHAR:
      case TIME:
      case DATE:
      case TIMESTAMP:
      case BIT:
        return "'" + value + "'";

      default:
        return value.toString();
    }
  }

  @Override
  public String sqlCreateColumn(String columnName, String comment, SqlType sqlType, int size, int scale,
                                        boolean nullable, Object defaultValue, boolean primaryKey, boolean withTrailingComma) {
    StringBuilder buf = new StringBuilder(sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale,
            nullable, defaultValue, primaryKey, withTrailingComma));
    if (comment != null) {
      buf.append(" -- ");
      buf.append(comment);
    }
    buf.append('\n');
    return buf.toString();
  }

  @Override
  public boolean isDefaultEqual(ColumnMetaData column, SqlType sqlType, Object defaultValue) {
    String attributeDefault = normalizeDefault(defaultValue == null ? null : defaultValue.toString());
    String columnDefault = normalizeDefault(column.getDefaultValue());
    boolean rv = attributeDefault.equals(columnDefault);
    if (!rv && column.getDefaultValue() != null) {
      Object value = sqlType.parse(column.getDefaultValue());
      // retry with literal according to type
      rv = Objects.equals(defaultValue, value);
    }
    return rv;
  }

  /**
   * Normalize the default string to compare.
   *
   * @param str the default value as a string, may be null
   * @return the normalized value, never null
   */
  protected String normalizeDefault(String str) {
    if (str == null) {
      str = "";
    }
    else {
      str = str.toUpperCase();
      if (str.startsWith("'")) {
        str = str.substring(1);
      }
      if (str.endsWith("'")) {
        str = str.substring(0, str.length() - 1);
      }
    }
    return str;
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
    return  SQL_ALTER_INDEX + oldIndexName +
            " RENAME TO " +
            newIndexName +
            ";\n";
  }

  @Override
  public String sqlRenameAndAlterColumnType(String tableName, String oldColumnName, String newColumnName, String comment,
          SqlType sqlType, int size, int scale, boolean nullable, Object defaultValue) {
    throw new BackendException("backend supports rename column without full declaration");
  }



  @Override
  public String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType, int size, int scale,
          boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            " ADD COLUMN " +
            sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale, nullable, defaultValue, false, false) +
            ";\n";
  }

  @Override
  public String sqlDropColumn(String tableName, String columnName) {
    StringBuilder buf = new StringBuilder();
    buf.append(SQL_ALTER_TABLE).append(tableName).append(" DROP COLUMN ");
    if (isDropIfExistsEnabled()) {
      buf.append(SQL_IF_EXISTS);
    }
    buf.append(columnName).append(";\n");
    return buf.toString();
  }

  @Override
  public String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType,
                                   int size, int scale, boolean nullable, Object defaultValue) {
    return  SQL_ALTER_TABLE + tableName +
            SQL_ALTER_COLUMN +
            columnName +
            " " +
            columnTypeNullDefaultToString(columnName, sqlType, size, scale, nullable, defaultValue) +
            ";\n";
  }

  @Override
  public String sqlAlterColumnNullConstraint(String tableName, String columnName, boolean nullable) {
    return null;
  }

  @Override
  public String sqlUpdateToNotNull(String tableName, String columnName, SqlType sqlType, Object defaultValue) {
    return  "UPDATE " + tableName +
            " SET " +
            columnName +
            "=" +
            valueToLiteral(sqlType, defaultValue != null ? defaultValue : sqlType.getDefaultValue()) +
            SQL_WHERE +
            columnName +
            " IS NULL;\n";
  }

  @Override
  public String sqlAlterColumnDefault(String tableName, String columnName, SqlType sqlType, Object defaultValue) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(SQL_ALTER_COLUMN);
    buf.append(columnName);
    buf.append(" ");
    if (defaultValue == null) {
      buf.append("DROP DEFAULT");
    }
    else  {
      buf.append("SET DEFAULT ");
      buf.append(valueToLiteral(sqlType, defaultValue));
    }
    buf.append(";\n");
    return buf.toString();
  }






  /**
   * Generates the attribute definition of a CREATE TABLE statement.
   *
   * @param columnName the database column name
   * @param sqlType the JDBC sql type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @param primaryKey true if this is a primary key
   * @param withTrailingComma true if append a comma
   * @return the SQL code
   */
  protected String sqlCreateTableAttributeWithoutComment(String columnName, SqlType sqlType, int size, int scale,
                                        boolean nullable, Object defaultValue, boolean primaryKey, boolean withTrailingComma) {
    StringBuilder buf = new StringBuilder(columnName);
    buf.append(' ');
    buf.append(columnTypeNullDefaultToString(columnName, sqlType, size, scale, nullable, defaultValue));
    if (primaryKey) {
      buf.append(" PRIMARY KEY");
    }
    if (withTrailingComma) {
      buf.append(',');
    }
    return buf.toString();
  }

  @Override
  public String sqlCreateColumnComment(String tableName, String columnName, String comment) {
    return "";
  }

  @Override
  public String sqlAlterColumnComment(String tableName, String columnName, String comment) {
    return sqlCreateColumnComment(tableName, columnName, comment);   // usually the same
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    throw new TentackleRuntimeException(sqlType + " is not supported for " + this);
  }

  @Override
  public String sqlCreateIndex(String tableName, String indexName, boolean unique, String filterCondition, String... columnNames) {
    StringBuilder buf = new StringBuilder("CREATE ");
    if (unique) {
      buf.append("UNIQUE ");
    }
    buf.append("INDEX ");
    buf.append(indexName);
    buf.append(" ON ");
    buf.append(tableName);
    buf.append(" (");
    boolean needsComma = false;
    for (String columnName: columnNames) {
      if (needsComma) {
        buf.append(", ");
      }
      boolean descending = false;
      if (columnName.startsWith("-")) {
        columnName = columnName.substring(1);
        descending = true;
      }
      buf.append(columnName);
      if (descending) {
        buf.append(" DESC");
      }
      needsComma = true;
    }
    buf.append(')');
    if (filterCondition != null) {
      buf.append(" WHERE ").append(filterCondition);
    }
    buf.append(";\n");
    return buf.toString();
  }

  @Override
  public String sqlDropIndex(String schemaName, String tableNameWithoutSchema, String indexName) {
    StringBuilder buf = new StringBuilder("DROP INDEX ");
    if (isDropIfExistsEnabled()) {
      buf.append(SQL_IF_EXISTS);
    }
    if (schemaName != null) {
      buf.append(schemaName);
      buf.append(".");
    }
    buf.append(indexName);
    buf.append(";\n");
    return buf.toString();
  }

  @Override
  public void sqlJoinSelects(JoinType type, boolean addColumns, StringBuilder select,
                             String joinSelect, String joinSelectIdAlias, String joinAlias, String join) {

    // IMPORTANT: works only for generated code with proper UPPERCASE and WHERE 1=1

    // insert DISTINCT if missing
    int ndx = select.indexOf("SELECT DISTINCT ");
    if (ndx < 0) {
      ndx = select.indexOf("SELECT ");
      if (ndx < 0) {
        throw new BackendException("not a SELECT statement: " + select);
      }
      select.insert(6, " DISTINCT");
    }

    // remove trailing WHERE 1=1, if any
    joinSelect = optimizeSql(joinSelect);

    // remove ORDER BY if appended by sorting rule
    ndx = joinSelect.indexOf(SQL_ORDERBY);
    if (ndx > 0) {
      joinSelect = joinSelect.substring(0, ndx);
    }
    boolean joinSelectNeedsParentheses = joinSelect.contains("SELECT");


    int fromNdx = select.indexOf(SQL_FROM);
    if (fromNdx < 0) {
      throw new BackendException("missing FROM in '" + select + "'");
    }
    int tailNdx = select.indexOf(SQL_WHEREALL);   // must be present!
    if (tailNdx < 0) {
      throw new BackendException("missing WHERE 1=1 in '" + select + "'");
    }

    StringBuilder buf = new StringBuilder();    // this is faster than inserts.

    buf.append(select.substring(0, fromNdx));

    if (addColumns) {
      buf.append(",").append(joinAlias).append(".*");
    }

    buf.append(select.substring(fromNdx, tailNdx)).
        append(' ').append(type).append(' ');

    if (joinSelectNeedsParentheses) {
      buf.append('(');
    }

    if (joinSelectIdAlias != null) {
      String joinSelectUC = joinSelect.toUpperCase();
      int joinFromNdx = joinSelectUC.indexOf(SQL_FROM);
      if (joinFromNdx < 0) {
        throw new BackendException("missing FROM in '" + joinSelect + "'");
      }
      buf.append(joinSelect, 0, joinFromNdx).
          append(",").append(joinSelectIdAlias).append(joinSelect.substring(joinFromNdx));
    }
    else  {
      buf.append(joinSelect);
    }

    if (joinSelectNeedsParentheses) {
      buf.append(')');
    }

    buf.append(sqlAsBeforeTableAlias()).append(joinAlias).append(" ON ").append(join).
        append(select.substring(tailNdx));

    // replace contents
    select.delete(0, select.length());
    select.append(buf);
  }


  /**
   * Extracts the where clause from a given sql.
   *
   * @param sql the sql statement
   * @param whereOffset the location of WHERE in sql
   * @return the where clause without the keyword WHERE
   */
  protected String extractWhereClause(String sql, int whereOffset) {
    // cut order by
    int ndx = sql.lastIndexOf(SQL_ORDERBY);
    if (ndx >= 0) {
      sql = sql.substring(whereOffset + SQL_WHERE.length(), ndx);
    }
    else  {
      sql = sql.substring(whereOffset + SQL_WHERE.length());
    }
    sql = sql.trim();
    if (sql.equals("1=1")) {
      sql = "";
    }
    return sql;
  }


  @Override
  public void assertValidName(String nameType, String name) {
    if (name != null) {
      if (getReservedWords().contains(name.toUpperCase())) {
        throw new BackendException(nameType + " '" + name + "' is a reserved word for backend " + this);
      }
      if (isTemporaryName(name)) {
        throw new BackendException(nameType + " '" + name + "' is a temporary identifier for backend " + this);
      }
    }
  }


  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale) {
    // ideal backend, needs overrides for specific backend
    SqlType sqlType;

    switch(jdbcType) {
      case Types.BIT:
      case Types.BOOLEAN:
        sqlType = SqlType.BIT;
        break;

      case Types.TINYINT:
        sqlType = SqlType.TINYINT;
        break;

      case Types.SMALLINT:
        sqlType = SqlType.SMALLINT;
        break;

      case Types.INTEGER:
        sqlType = SqlType.INTEGER;
        break;

      case Types.BIGINT:
        sqlType = SqlType.BIGINT;
        break;

      case Types.FLOAT:
      case Types.REAL:
        sqlType = SqlType.FLOAT;
        break;

      case Types.DOUBLE:
        sqlType = SqlType.DOUBLE;
        break;

      case Types.NUMERIC:
      case Types.DECIMAL:
        sqlType = SqlType.DECIMAL;
        break;

      case Types.CHAR:
      case Types.NCHAR:
        sqlType = SqlType.CHAR;
        break;

      case Types.VARCHAR:
      case Types.LONGVARCHAR:
      case Types.NVARCHAR:
      case Types.LONGNVARCHAR:
        sqlType = SqlType.VARCHAR;
        break;

      case Types.DATE:
        sqlType = SqlType.DATE;
        break;

      case Types.TIME:
        sqlType = SqlType.TIME;
        break;

      case Types.TIMESTAMP:
        sqlType = SqlType.TIMESTAMP;
        break;

      case Types.BINARY:
      case Types.VARBINARY:
      case Types.LONGVARBINARY:
      case Types.BLOB:
        sqlType = SqlType.LONGVARBINARY;
        break;

      default:
        return new SqlType[0];
    }

    return new SqlType[] { sqlType };
  }



  @Override
  public String toQuotedString(String str) {
    StringBuilder buf = new StringBuilder("'");
    if (str != null) {
      for (int i = 0; i < str.length(); i++) {
        char c = str.charAt(i);
        buf.append(c);
        if (c == '\'') {
          buf.append(c);
        }
      }
    }
    buf.append("'");
    return buf.toString();
  }


  @Override
  public String optimizeSql(String sql)  {
    return sql.replace(Backend.SQL_WHEREAND, Backend.SQL_WHERE).
            replace(Backend.SQL_WHEREOR, Backend.SQL_WHERE).
              replace(Backend.SQL_WHEREALL, "");
  }

  @Override
  public String buildSelectSql(String sql, boolean writeLock, int limit, int offset) {
    StringBuilder buf = new StringBuilder(sql);
    buildSelectSql(buf, writeLock, limit, offset);
    return buf.toString();
  }

  @Override
  public String sqlJoinSelects(JoinType type, boolean addColumns, String select, String joinSelect, String joinSelectIdAlias, String joinAlias, String join) {
    StringBuilder buf = new StringBuilder(select);
    sqlJoinSelects(type, addColumns, buf, joinSelect, joinSelectIdAlias, joinAlias, join);
    return buf.toString();
  }

  @Override
  public boolean isReleaseSavepointSupported() {
    return false;
  }

  @Override
  public void setDropIfExistsEnabled(boolean dropIfExistsEnabled) {
    this.dropIfExistsEnabled = dropIfExistsEnabled;
  }

  @Override
  public boolean isDropIfExistsEnabled() {
    return dropIfExistsEnabled && isDropIfExistsSupported();
  }

  /**
   * Adds "IF EXISTS" to drop clauses, if supported by the backend.<br>
   * Makes migration a little more robust in case the same SQL scripts are
   * applied to different databases than those used to generate the migration scripts for.
   *
   * @return true if supported
   */
  protected boolean isDropIfExistsSupported() {
    return false;
  }

  /**
   * Checks if the exception's state starts with a given string.
   *
   * @param ex the exception
   * @param prefix the prefix the state starts with
   * @return true if so
   */
  private boolean isExceptionStateStartingWith(SQLException ex, String prefix) {
    if (ex != null) {
      String state = ex.getSQLState();
      return state != null && state.startsWith(prefix);
    }
    return false;
  }

}

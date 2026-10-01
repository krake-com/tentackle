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

import org.tentackle.common.Cryptor;
import org.tentackle.common.StringHelper;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendVersionInfo;
import org.tentackle.sql.DefaultScriptRunner;
import org.tentackle.sql.JoinType;
import org.tentackle.sql.MigrationStrategy;
import org.tentackle.sql.ScriptRunner;
import org.tentackle.sql.SqlNameType;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.DatabaseMetaDataTableHeader;
import org.tentackle.sql.metadata.IndexColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.lang.reflect.Method;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTransactionRollbackException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import javax.sql.XADataSource;

/**
 * Abstract parent class for backends.
 *
 * @author harald
 */
public abstract class AbstractBackend implements Backend {

  // --------------------- type strings ----------------------------
  // used by the backends to declare the column types

  /** BIGINT column type. */
  protected static final String TYPE_BIGINT = "BIGINT";

  /** BIT column type. */
  protected static final String TYPE_BIT = "BIT";

  /** BLOB column type. */
  protected static final String TYPE_BLOB = "BLOB";

  /** CLOB column type. */
  protected static final String TYPE_CLOB = "CLOB";

  /** BOOL column type. */
  protected static final String TYPE_BOOL = "BOOL";

  /** BOOLEAN column type. */
  protected static final String TYPE_BOOLEAN = "BOOLEAN";

  /** BYTEA column type. */
  protected static final String TYPE_BYTEA = "BYTEA";

  /** BYTE column type. */
  protected static final String TYPE_BYTE = "BYTE";

  /** CHAR(1) column type. */
  protected static final String TYPE_CHAR_1 = "CHAR(1)";

  /** DATE column type. */
  protected static final String TYPE_DATE = "DATE";

  /** DATETIME column type. */
  protected static final String TYPE_DATETIME = "DATETIME";

  /** DATETIME(6) column type. */
  protected static final String TYPE_DATETIME_6 = "DATETIME(6)";

  /** DATETIME YEAR TO SECOND column type. */
  protected static final String TYPE_DATETIME_YEAR_TO_SECOND = "DATETIME YEAR TO SECOND";

  /** DECIMAL column type. */
  protected static final String TYPE_DECIMAL = "DECIMAL";

  /** DECIMAL(19) column type. */
  protected static final String TYPE_DECIMAL_19 = "DECIMAL(19)";

  /** DOUBLE column type. */
  protected static final String TYPE_DOUBLE = "DOUBLE";

  /** DOUBLE PRECISION column type. */
  protected static final String TYPE_DOUBLE_PRECISION = "DOUBLE PRECISION";

  /** FLOAT column type. */
  protected static final String TYPE_FLOAT = "FLOAT";

  /** FLOAT4 column type. */
  protected static final String TYPE_FLOAT4 = "FLOAT4";

  /** FLOAT8 column type. */
  protected static final String TYPE_FLOAT8 = "FLOAT8";

  /** INT column type. */
  protected static final String TYPE_INT = "INT";

  /** INT2 column type. */
  protected static final String TYPE_INT2 = "INT2";

  /** INT4 column type. */
  protected static final String TYPE_INT4 = "INT4";

  /** INT8 column type. */
  protected static final String TYPE_INT8 = "INT8";

  /** INTEGER column type. */
  protected static final String TYPE_INTEGER = "INTEGER";

  /** LONGBLOB column type. */
  protected static final String TYPE_LONGBLOB = "LONGBLOB";

  /** LONGTEXT column type. */
  protected static final String TYPE_LONGTEXT = "LONGTEXT";

  /** NCHAR(1) column type. */
  protected static final String TYPE_NCHAR_1 = "NCHAR(1)";

  /** NUMBER column type. */
  protected static final String TYPE_NUMBER = "NUMBER";

  /** NUMBER(1) column type. */
  protected static final String TYPE_NUMBER_1 = "NUMBER(1)";

  /** NUMBER(5) column type. */
  protected static final String TYPE_NUMBER_5 = "NUMBER(5)";

  /** NUMBER(10) column type. */
  protected static final String TYPE_NUMBER_10 = "NUMBER(10)";

  /** NUMBER(19) column type. */
  protected static final String TYPE_NUMBER_19 = "NUMBER(19)";

  /** NVARCHAR column type. */
  protected static final String TYPE_NVARCHAR = "NVARCHAR";

  /** NVARCHAR(MAX) column type. */
  protected static final String TYPE_NVARCHAR_MAX = "NVARCHAR(MAX)";

  /** REAL column type. */
  protected static final String TYPE_REAL = "REAL";

  /** TEXT column type. */
  protected static final String TYPE_TEXT = "TEXT";

  /** TIME column type. */
  protected static final String TYPE_TIME = "TIME";

  /** TIME(6) column type. */
  protected static final String TYPE_TIME_6 = "TIME(6)";

  /** TIMESTAMP column type. */
  protected static final String TYPE_TIMESTAMP = "TIMESTAMP";

  /** TINYINT column type. */
  protected static final String TYPE_TINYINT = "TINYINT";

  /** SMALLFLOAT column type. */
  protected static final String TYPE_SMALLFLOAT = "SMALLFLOAT";

  /** SMALLINT column type. */
  protected static final String TYPE_SMALLINT = "SMALLINT";

  /** VARBINARY(MAX) column type. */
  protected static final String TYPE_VARBINARY_MAX = "VARBINARY(MAX)";

  /** VARCHAR column type. */
  protected static final String TYPE_VARCHAR = "VARCHAR";

  /** UUID column type. */
  protected static final String TYPE_UUID = "UUID";

  /** XML column type. */
  protected static final String TYPE_XML = "XML";

  /** XMLTYPE column type. */
  protected static final String TYPE_XMLTYPE = "XMLTYPE";

  /** JSON column type. */
  protected static final String TYPE_JSON = "JSON";

  /** JSONB column type. */
  protected static final String TYPE_JSONB = "JSONB";


  private static final String STATEMENT_SEPARATOR = ";";
  private static final String SINGLE_LINE_COMMENT = "--";
  private static final String BLOCK_COMMENT_BEGIN = "/*";
  private static final String BLOCK_COMMENT_END   = "*/";

  private static final String[] STATEMENT_SEPARATORS = { ";" };
  private static final String[] SINGLE_LINE_COMMENTS = { "--" };
  private static final String[] BLOCK_COMMENT_BEGINS = { "/*" };
  private static final String[] BLOCK_COMMENT_ENDS   = { "*/" };


  /** use DROP IF EXISTS, if supported. */
  private boolean dropIfExistsEnabled;


  /**
   * Parent constructor.
   */
  public AbstractBackend() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean isMatchingName(String name) {
    return getName().equalsIgnoreCase(name);
  }

  @Override
  public void validateVersion(BackendVersionInfo versionInfo) {
    // ok, by default
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public Connection createConnection(String url, String username, char[] password) throws SQLException {
    // DriverManager.getConnection() doesn't work in most containers
    // unless the class is preloaded within the application's classloader.
    try {
      Class.forName(getDriverClassName());
    }
    catch (ClassNotFoundException e) {
      // no logger available, will fail below anyway...
    }

    return DriverManager.getConnection(url, username, createPassword(password));
  }

  @Override
  public String getXADataSourceClassName() {
    return null;    // XA not supported by default
  }

  @Override
  public XADataSource createXADataSource(String url, String username, char[] password) throws SQLException {
    String className = getXADataSourceClassName();
    if (className == null) {
      throw new SQLFeatureNotSupportedException("backend " + getName() + " does not support XA");
    }
    try {
      Object dataSource = Class.forName(className).getDeclaredConstructor().newInstance();
      setDataSourceProperty(dataSource, url, "setURL", "setUrl");
      if (username != null) {
        setDataSourceProperty(dataSource, username, "setUser");
      }
      setDataSourceProperty(dataSource, createPassword(password), "setPassword");
      return (XADataSource) dataSource;
    }
    catch (ReflectiveOperationException | ClassCastException ex) {
      throw new SQLException("cannot create XA data source " + className + " for backend " + getName(), ex);
    }
  }

  /**
   * Sets a string property of a data source via reflection.<br>
   * The setter's name is not standardized among the JDBC drivers (e.g. {@code setURL} vs. {@code setUrl}),
   * hence the first matching setter is used.
   *
   * @param dataSource the data source
   * @param value the property value
   * @param setterNames the candidate setter names
   * @throws ReflectiveOperationException if no such setter or invocation failed
   */
  protected void setDataSourceProperty(Object dataSource, String value, String... setterNames)
      throws ReflectiveOperationException {
    for (String setterName : setterNames) {
      Method setter;
      try {
        setter = dataSource.getClass().getMethod(setterName, String.class);
      }
      catch (NoSuchMethodException nx) {
        continue;
      }
      setter.invoke(dataSource, value);
      return;
    }
    throw new NoSuchMethodException("none of " + String.join(", ", setterNames) + " found in " + dataSource.getClass().getName());
  }

  @Override
  public DatabaseMetaData[] getMetaData(BackendInfo backendInfo) throws SQLException {
    Connection con = createConnection(backendInfo.getUrl(), backendInfo.getUser(), backendInfo.getPassword());
    DatabaseMetaData metaData = con.getMetaData();
    return new DatabaseMetaData[] { metaData };
  }

  @Override
  public List<DatabaseMetaDataTableHeader> getTableHeaders(DatabaseMetaData metaData) throws SQLException {
    List<DatabaseMetaDataTableHeader> headers = new ArrayList<>();
    try (ResultSet rs = metaData.getTables(null, null, null, null)) {
      while (rs.next()) {
        headers.add(new DatabaseMetaDataTableHeader(metaData,
                                                    rs.getString("TABLE_CAT"),
                                                    rs.getString("TABLE_SCHEM"),
                                                    rs.getString("TABLE_NAME"),
                                                    rs.getString("TABLE_TYPE"),
                                                    rs.getString("REMARKS")));
      }
    }
    return headers;
  }

  @Override
  public boolean isTemporaryName(String name) {
    return name != null && name.startsWith("_");
  }

  /**
   * Checks whether the given string is a reserved word.
   *
   * @param word the word
   * @return true if reserved word
   */
  public boolean isReservedWord(String word) {
    return getReservedWords().contains(word.toUpperCase(Locale.ROOT));
  }

  @Override
  public boolean isReservedTableName(String name) {
    return isReservedWord(name);
  }

  @Override
  public boolean isReservedSchemaName(String name) {
    return isReservedWord(name);
  }

  @Override
  public String getEmptyString() {
    return "";
  }

  @Override
  public String getDummySelect() {
    return "SELECT 1";
  }

  @Override
  public String sqlAsBeforeTableAlias() {
    return " ";
  }

  @Override
  public boolean isAliasRequiredForSubSelect() {
    return false;
  }

  @Override
  public boolean isExtraCommitRequired() {
    return false;
  }

  @Override
  public String sqlComment(String text) {
    if (text != null) {
      StringTokenizer stok = new StringTokenizer(text, "\n");
      StringBuilder buf = new StringBuilder();
      while (stok.hasMoreTokens()) {
        String line = stok.nextToken();
        if (StringHelper.startsWithAnyOf(line, getSingleLineComments()) == null) {
          buf.append(getSingleLineComment()).append(' ');
        }
        buf.append(line);
        buf.append('\n');
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
    StringBuilder buf = new StringBuilder(functionName.toUpperCase(Locale.ROOT)).append('(');
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
  public boolean isExpressionReferringToTableBeingUpdatedSupported() {
    return true;
  }

  @Override
  public boolean isTxRequiredForFetchsize() {
    return false;
  }

  @Override
  public boolean isConstraintException(SQLException ex) {
    return isExceptionStateStartingWith(ex, "23");
  }

  @Override
  public boolean isCommunicationLinkException(SQLException ex) {
    // initial connection failed, or connection vanished or became unusable
    return isExceptionStateStartingWith(ex, "08", "57");
  }

  @Override
  public boolean isTransientTransactionException(SQLException ex) {
    return ex instanceof SQLTransactionRollbackException ||   // the preferred way, but rarely implemented
           isExceptionStateMatching(ex, "40001");     // serialization failure or deadlock
  }

  @Override
  public int getMaxSize(SqlType sqlType) {
    return switch (sqlType) {
      case BIT, TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, DOUBLE, CHAR, DATE, TIME, TIMESTAMP, BLOB, CLOB, XML, JSON -> -1;    // sizeless type
      default -> 0;   // default is unlimited
    };
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

    // try original table name
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
                                         schemaPattern == null ? null : schemaPattern.toUpperCase(Locale.ROOT),
                                         tablePattern.toUpperCase(Locale.ROOT));
            found = true;
            break;
          }
          catch (BackendException ex2) {
            try {
              table.setupTableFromMetaData(metaData, modelMetaData.getSchemas(),
                                           schemaPattern == null ? null : schemaPattern.toLowerCase(Locale.ROOT),
                                           tablePattern.toLowerCase(Locale.ROOT));
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
      buf.append(' ').append(getSingleLineComment()).append(' ').append(comment);
    }
    buf.append('\n');
    return buf.toString();
  }

  @Override
  public String sqlCreateTableClosing(String tableName, String comment) {
    return ")" + getStatementSeparator() + "\n";
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
    return switch (sqlType) {
      case CHAR, VARCHAR, CLOB, XML, JSON, TIME, DATE, TIMESTAMP, BIT -> "'" + value + "'";
      default -> value.toString();
    };
  }

  @Override
  public String[] getStatementSeparators() {
    return STATEMENT_SEPARATORS;
  }

  @Override
  public String getStatementSeparator() {
    return STATEMENT_SEPARATOR;
  }

  @Override
  public String[] getSingleLineComments() {
    return SINGLE_LINE_COMMENTS;
  }

  @Override
  public String getSingleLineComment() {
    return SINGLE_LINE_COMMENT;
  }

  @Override
  public String[] getBlockCommentBegins() {
    return BLOCK_COMMENT_BEGINS;
  }

  @Override
  public String getBlockCommentBegin() {
    return BLOCK_COMMENT_BEGIN;
  }

  @Override
  public String[] getBlockCommentEnds() {
    return BLOCK_COMMENT_ENDS;
  }

  @Override
  public String getBlockCommentEnd() {
    return BLOCK_COMMENT_END;
  }

  @Override
  public String sqlCreateColumn(String columnName, String comment, SqlType sqlType, int size, int scale,
                                        boolean nullable, Object defaultValue, boolean primaryKey, boolean withTrailingComma) {
    StringBuilder buf = new StringBuilder(sqlCreateTableAttributeWithoutComment(columnName, sqlType, size, scale,
            nullable, defaultValue, primaryKey, withTrailingComma));
    if (comment != null) {
      buf.append(' ').append(getSingleLineComment()).append(' ').append(comment);
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
                           // or changed from VARCHAR(x) to VARCHAR(maxSize).
                           // Some databases report an unbounded column with a size beyond maxSize.
                           size == 0 && sqlType == SqlType.VARCHAR &&
                           column.getSize() > 0 && column.getSize() < maxSize);
    boolean sizeWarning = maxSize != -1 && size != 0 && size < column.getSize();
    boolean scaleChanged = scale > column.getScale(); // smaller model scale is allowed
    boolean scaleWarning = maxSize != -1 && scale < column.getScale();

    // ideal backend: name, comment, null and default can be changed separately from type,size and scale
    if (nameChanged) {
      strategies.add(MigrationStrategy.NAME);   // comes first because next steps refer to the new column name
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
  public String sqlRenameTable(String tableName, String newTableName) {
    return SQL_ALTER_TABLE + tableName +
           " RENAME TO " +
           newTableName +
           getStatementSeparator() + "\n";
  }

  @Override
  public String sqlRenameColumn(String tableName, String oldColumnName, String newColumnName) {
    return  SQL_ALTER_TABLE + tableName +
            " RENAME COLUMN " +
            oldColumnName +
            " TO " +
            newColumnName +
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlRenameIndex(String tableName, String oldIndexName, String newIndexName) {
    return  SQL_ALTER_INDEX + oldIndexName +
            " RENAME TO " +
            newIndexName +
            getStatementSeparator() + "\n";
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
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlDropColumn(String tableName, String columnName) {
    StringBuilder buf = new StringBuilder();
    buf.append(SQL_ALTER_TABLE).append(tableName).append(" DROP COLUMN ");
    if (isDropIfExistsEnabled()) {
      buf.append(SQL_IF_EXISTS);
    }
    buf.append(columnName).append(getStatementSeparator()).append('\n');
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
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlAlterColumnNullConstraint(String tableName, String columnName, boolean nullable) {
    return null;
  }

  @Override
  public String sqlAlterColumnNullConstraint(String tableName, String columnName, SqlType sqlType, int size, int scale,
                                             boolean nullable, Object defaultValue) {
    // most backends don't need the full declaration
    return sqlAlterColumnNullConstraint(tableName, columnName, nullable);
  }

  @Override
  public String sqlUpdateToNotNull(String tableName, String columnName, SqlType sqlType, Object defaultValue, boolean migrated) {
    StringBuilder buf = new StringBuilder();
    buf.append("UPDATE ").append(tableName).append(" SET ").append(columnName).append('=')
       .append(valueToLiteral(sqlType, defaultValue != null ? defaultValue : sqlType.getDefaultValue()));
    if (migrated) {
      buf.append(SQL_WHERE).append(columnName).append(" IS NULL");
    }
    buf.append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  @Override
  public String sqlAlterColumnDefault(String tableName, String columnName, SqlType sqlType, Object defaultValue) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName).append(SQL_ALTER_COLUMN).append(columnName).append(' ');
    if (defaultValue == null) {
      buf.append("DROP DEFAULT");
    }
    else  {
      buf.append("SET DEFAULT ")
         .append(valueToLiteral(sqlType, defaultValue));
    }
    buf.append(getStatementSeparator()).append('\n');
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
    throw new BackendException(sqlType + " is not supported by " + this);
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
      // isFilteredIndexSupported already checked in model (IndexImpl)
      buf.append(SQL_WHERE).append(filterCondition);
    }
    buf.append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  @Override
  public String getIndexMetaDataCommentColumnName() {
    return null;
  }

  @Override
  public String sqlCreateIndexComment(String tableName, String indexName, String comment) {
    return "";
  }

  @Override
  public String sqlAlterIndexComment(String tableName, String indexName, String comment) {
    return sqlCreateIndexComment(tableName, indexName, comment);   // usually the same
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
    buf.append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  @Override
  public String sqlDropTable(String schemaName, String tableNameWithoutSchema) {
    StringBuilder buf = new StringBuilder("DROP TABLE ");
    if (isDropIfExistsEnabled()) {
      buf.append(SQL_IF_EXISTS);
    }
    if (schemaName != null) {
      buf.append(schemaName);
      buf.append(".");
    }
    buf.append(tableNameWithoutSchema);
    buf.append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  @Override
  public void sqlJoinSelects(JoinType type, boolean addColumns, StringBuilder select,
                             String joinSelect, String joinSelectIdAlias, String joinAlias, String join) {

    // IMPORTANT: works only for generated code with proper UPPERCASE and WHERE 1=1

    // remove trailing WHERE 1=1, if any
    joinSelect = optimizeSql(joinSelect);

    // remove ORDER BY if appended by sorting rule
    int ndx = joinSelect.indexOf(SQL_ORDERBY);
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

    buf.append(select, 0, fromNdx);

    if (addColumns) {
      buf.append(',').append(joinAlias).append(SQL_DOT_STAR);
    }

    buf.append(select, fromNdx, tailNdx).
        append(' ').append(type).append(' ');

    if (joinSelectNeedsParentheses) {
      buf.append('(');
    }

    if (joinSelectIdAlias != null) {
      String joinSelectUC = joinSelect.toUpperCase(Locale.ROOT);
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

  @Override
  public void assertValidName(SqlNameType nameType, String name) {
    if (name != null) {
      int maxNameLength = getMaxNameLength(nameType);
      int length = name.length();
      if (length > maxNameLength) {
        throw new BackendException(nameType + " '" + name + "' is " + length +
                                   " characters long, which is more than " +
                                   maxNameLength + " supported by backend " + this);
      }
      if (getReservedWords().contains(name.toUpperCase(Locale.ROOT))) {
        throw new BackendException(nameType + " '" + name + "' is a reserved word for backend " + this);
      }
      if (SqlNameType.TABLE_NAME == nameType) {
        int ndx = name.indexOf('.');
        String sn = ndx >= 0 ? name.substring(0, ndx) : "";
        String tn = ndx >= 0 ? name.substring(ndx + 1) : name;
        if (isReservedTableName(tn)) {
          throw new BackendException("'" + tn + "' conflicts with reserved table names for backend " + this);
        }
        if (isReservedSchemaName(sn)) {
          throw new BackendException("'" + sn + "' conflicts with reserved schema names for backend " + this);
        }
        if (isTemporaryName(tn)) {
          throw new BackendException(nameType + " '" + tn + "' is a temporary identifier for backend " + this);
        }
        if (isTemporaryName(sn)) {
          throw new BackendException("schema '" + sn + "' is a temporary identifier for backend " + this);
        }
      }
      else if (isTemporaryName(name)) {
        throw new BackendException(nameType + " '" + name + "' is a temporary identifier for backend " + this);
      }
    }
  }

  @Override
  public int getMaxNameLength(SqlNameType nameType) {
    return Integer.MAX_VALUE;
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
        sqlType = SqlType.BLOB;
        break;

      case Types.CLOB:
      case Types.NCLOB:
        // a backend without a native JSON type emulates it as CLOB, so a CLOB column
        // must match a JSON attribute as well, or the migration would alter it forever.
        // The same applies to XML, but only for backends without a native XML type,
        // which therefore add XML in their overrides.
        return new SqlType[] { SqlType.CLOB, SqlType.JSON };

      case Types.SQLXML:
        sqlType = SqlType.XML;
        break;

      default:
        return new SqlType[0];
    }

    return new SqlType[] { sqlType };
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, String typeName, int size, int scale) {
    return jdbcTypeToSqlType(jdbcType, size, scale);    // the type name is only needed by some backends
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
  public String reduceSql(String sql) {
    StringBuilder buf = new StringBuilder(sql);
    reduceSql(buf);
    return buf.toString();
  }

  @Override
  public void reduceSql(StringBuilder sql) {
    List<SqlToken> tokens = tokenizeSql(sql);
    boolean modified = false;
    while (reduceSqlTokens(tokens)) {
      modified = true;
    }
    if (modified) {
      sql.setLength(0);
      for (SqlToken token: tokens) {
        sql.append(token.text());
      }
    }
  }

  /**
   * Removes one superfluous token from the given token list.<br>
   * Empty parenthesized groups are removed first, then the operators becoming dangling.
   *
   * @param tokens the tokens, modified in place
   * @return true if a token was removed, false if there is nothing left to reduce
   */
  private boolean reduceSqlTokens(List<SqlToken> tokens) {

    // the indexes of all tokens that are not just whitespace
    List<Integer> indexes = new ArrayList<>();
    for (int index = 0; index < tokens.size(); index++) {
      if (tokens.get(index).type() != SqlTokenType.SPACE) {
        indexes.add(index);
      }
    }

    // remove the first parenthesized group without any operand
    List<Integer> openIndexes = new ArrayList<>();     // stack of positions within indexes
    for (int pos = 0; pos < indexes.size(); pos++) {
      SqlTokenType type = tokens.get(indexes.get(pos)).type();
      if (type == SqlTokenType.LEFT_PARENTHESIS) {
        openIndexes.add(pos);
      }
      else if (type == SqlTokenType.RIGHT_PARENTHESIS) {
        if (openIndexes.isEmpty()) {
          break;      // unbalanced: leave the SQL alone
        }
        int openPos = openIndexes.removeLast();
        boolean empty = true;
        for (int inner = openPos + 1; inner < pos; inner++) {
          if (tokens.get(indexes.get(inner)).type() == SqlTokenType.OPERAND) {
            empty = false;
            break;
          }
        }
        // an empty group is only removed if it is an operand of a logical expression.
        // this keeps parentheses belonging to an operand untouched, such as "now()" or "id IN()"
        SqlTokenType before = openPos > 0 ? tokens.get(indexes.get(openPos - 1)).type() : null;
        SqlTokenType after = pos < indexes.size() - 1 ? tokens.get(indexes.get(pos + 1)).type() : null;
        if (empty &&
            (before == null || before.isOperator() || before == SqlTokenType.LEFT_PARENTHESIS) &&
            (after == null || after.isOperator() || after == SqlTokenType.RIGHT_PARENTHESIS)) {
          // remove the group including everything in between (operators and nested empty groups)
          tokens.subList(indexes.get(openPos), indexes.get(pos) + 1).clear();
          return true;
        }
      }
    }

    // remove dangling operators
    for (int pos = 0; pos < indexes.size(); pos++) {
      SqlTokenType type = tokens.get(indexes.get(pos)).type();
      if (type.isOperator()) {
        SqlTokenType prev = pos > 0 ? tokens.get(indexes.get(pos - 1)).type() : null;
        SqlTokenType next = pos < indexes.size() - 1 ? tokens.get(indexes.get(pos + 1)).type() : null;
        boolean dangling;
        if (type == SqlTokenType.NOT) {
          // unary: needs an operand or a group to the right
          dangling = next == null || next == SqlTokenType.RIGHT_PARENTHESIS || next.isBinaryOperator();
        }
        else {
          // binary: needs an operand or a group on both sides.
          // if the operand in between two operators was skipped, the operand's leading operator is removed
          dangling = prev == null || prev == SqlTokenType.LEFT_PARENTHESIS ||
                     next == null || next == SqlTokenType.RIGHT_PARENTHESIS ||
                     next.isBinaryOperator();
        }
        if (dangling) {
          tokens.remove((int) indexes.get(pos));
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Splits the SQL text into tokens.<br>
   * The tokens hold the original text, i.e., concatenating all tokens restores the SQL text.
   * Only uppercase keywords are recognized as operators. Text within quotes is never interpreted.
   *
   * @param sql the SQL text
   * @return the tokens
   */
  private List<SqlToken> tokenizeSql(CharSequence sql) {
    List<SqlToken> tokens = new ArrayList<>();
    StringBuilder operand = new StringBuilder();      // pending operand or whitespace
    int index = 0;
    while (index < sql.length()) {
      char c = sql.charAt(index);
      SqlTokenType type = null;
      int length = 1;
      if (c == '\'' || c == '"') {
        // skip quoted text (may contain anything, doubled quotes are escapes)
        int end = index + 1;
        while (end < sql.length()) {
          if (sql.charAt(end) == c) {
            if (end + 1 < sql.length() && sql.charAt(end + 1) == c) {
              end++;      // escaped quote
            }
            else {
              break;
            }
          }
          end++;
        }
        length = Math.min(end + 1, sql.length()) - index;
      }
      else if (c == '(') {
        type = SqlTokenType.LEFT_PARENTHESIS;
      }
      else if (c == ')') {
        type = SqlTokenType.RIGHT_PARENTHESIS;
      }
      else if (isKeywordAt(sql, index, "AND")) {
        type = SqlTokenType.AND;
        length = 3;
      }
      else if (isKeywordAt(sql, index, "OR")) {
        type = SqlTokenType.OR;
        length = 2;
      }
      else if (isKeywordAt(sql, index, "NOT")) {
        // "NOT IN", "NOT LIKE" and "IS NOT NULL" belong to the operand
        int skipBlanks = skipBlanks(sql, index + 3);
        if (isKeywordAt(sql, skipBlanks, "IN") ||
            isKeywordAt(sql, skipBlanks, "LIKE") ||
            isKeywordAt(sql, skipBlanks, "NULL")) {
          length = 3;
        }
        else {
          type = SqlTokenType.NOT;
          length = 3;
        }
      }

      if (type == null) {
        operand.append(sql, index, index + length);
      }
      else {
        addOperandToken(tokens, operand);
        tokens.add(new SqlToken(type, sql.subSequence(index, index + length).toString()));
      }
      index += length;
    }
    addOperandToken(tokens, operand);
    return tokens;
  }

  /**
   * Adds the pending operand to the token list.
   *
   * @param tokens the tokens
   * @param operand the pending text, cleared
   */
  private void addOperandToken(List<SqlToken> tokens, StringBuilder operand) {
    if (!operand.isEmpty()) {
      String text = operand.toString();
      tokens.add(new SqlToken(StringHelper.isAllWhitespace(text) ? SqlTokenType.SPACE : SqlTokenType.OPERAND, text));
      operand.setLength(0);
    }
  }

  /**
   * Returns whether the given uppercase keyword starts at the given index.<br>
   * The keyword must not be part of a longer word.
   *
   * @param sql the SQL text
   * @param index the index within the text
   * @param keyword the keyword
   * @return true if the keyword starts at index
   */
  private boolean isKeywordAt(CharSequence sql, int index, String keyword) {
    int end = index + keyword.length();
    if (end > sql.length()) {
      return false;
    }
    for (int i = 0; i < keyword.length(); i++) {
      if (sql.charAt(index + i) != keyword.charAt(i)) {
        return false;
      }
    }
    return (index == 0 || !Character.isJavaIdentifierPart(sql.charAt(index - 1))) &&
           (end == sql.length() || !Character.isJavaIdentifierPart(sql.charAt(end)));
  }

  /**
   * Skips blanks.
   *
   * @param sql the SQL text
   * @param index the index within the text
   * @return the index of the next non-blank character
   */
  private int skipBlanks(CharSequence sql, int index) {
    while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
      index++;
    }
    return index;
  }


  /**
   * Kind of an {@link SqlToken}.
   */
  private enum SqlTokenType {

    /** anything that is not an operator, a parenthesis, or whitespace. */
    OPERAND,

    /** whitespace between tokens. */
    SPACE,

    /** an opening parenthesis. */
    LEFT_PARENTHESIS,

    /** a closing parenthesis. */
    RIGHT_PARENTHESIS,

    /** the logical AND. */
    AND,

    /** the logical OR. */
    OR,

    /** the logical NOT. */
    NOT;

    /**
     * Returns whether this is a logical operator.
     *
     * @return true if AND, OR, or NOT
     */
    private boolean isOperator() {
      return this == AND || this == OR || this == NOT;
    }

    /**
     * Returns whether this is a binary logical operator.
     *
     * @return true if AND or OR
     */
    private boolean isBinaryOperator() {
      return this == AND || this == OR;
    }
  }

  /**
   * A token of an SQL text.
   *
   * @param type the token type
   * @param text the original text
   */
  private record SqlToken(SqlTokenType type, String text) {}

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
  public boolean isClobSupported() {
    return true;
  }

  @Override
  public boolean isSqlxmlSupported() {
    // unlike isClobSupported(), this defaults to false: JDBC SQLXML driver support is far less
    // consistent across backends, and only a backend that has actually verified it should opt in.
    return false;
  }

  @Override
  public boolean isJsonSupported() {
    // same rationale as isSqlxmlSupported(): there is no JDBC binding for JSON at all,
    // so only a backend that has actually verified its native type should opt in.
    return false;
  }

  @Override
  public void setJson(PreparedStatement statement, int pos, String json) throws SQLException {
    // correct for every backend emulating JSON as a text column
    if (json == null) {
      statement.setNull(pos, Types.VARCHAR);
    }
    else {
      statement.setString(pos, json);
    }
  }

  @Override
  public String getJson(ResultSet resultSet, int pos) throws SQLException {
    return resultSet.getString(pos);
  }

  @Override
  public boolean isUUIDSupported() {
    return false;
  }

  @Override
  public boolean isFunctionBasedIndexSupported() {
    return false;
  }

  @Override
  public boolean isFilteredIndexSupported() {
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

  @Override
  public boolean isSetConstraintsDeferredSupported() {
    return false;
  }

  @Override
  public String sqlSetConstraintsDeferred(boolean deferred) {
    throw new BackendException(this + " does not support deferred constraints");
  }

  @Override
  public void enrichIndexDeferrability(TableMetaData tableMetaData, Map<String, IndexMetaData> indexesByName,
                                        DatabaseMetaData metaData) throws SQLException {
    // no-op by default: only backends supporting isSetConstraintsDeferredSupported() need this
  }

  @Override
  public String toInternalType(String sqlTypeName) {
    return sqlTypeName;
  }

  @Override
  public boolean isArrayOperatorSupported(String operator) {
    return false;
  }

  @Override
  public void setArray(PreparedStatement statement, int pos, SqlType type, Collection<?> elements, String operator) throws SQLException {
    String dbType = toInternalType(sqlTypeToString(type, 0));
    Object[] array = elements.toArray();
    Array jdbcArray = statement.getConnection().createArrayOf(dbType, array);
    statement.setArray(pos, jdbcArray);
  }

  @Override
  public boolean isSchemaSupported() {
    return true;
  }

  @Override
  public String sqlCreateSchema(String name) {
    if (isSchemaSupported()) {
      return "CREATE SCHEMA " + name;
    }
    throw new BackendException("schemas are not supported by backend " + this);
  }

  @Override
  public boolean isPosixEscapeSyntaxSupported() {
    return false;
  }

  @Override
  public ScriptRunner createScriptRunner(Connection connection) {
    return new DefaultScriptRunner(this, connection);
  }

  @Override
  public boolean isDatabaseInMemory(String url) {
    return false;
  }

  /**
   * Generates the first line of a CREATE TABLE statement.
   *
   * @param tableName the table name with optional schema separated by a dot
   * @return the SQL code including the opening parenthesis
   */
  protected String sqlCreateTableIntroWithoutComment(String tableName) {
    return "CREATE TABLE " + tableName + " (";
  }

  /**
   * Creates a password string from a password char array.<br>
   * Unfortunately, {@link DriverManager#getConnection(String, String, String)} requires the password as a string.
   * This method creates a string from a char array and optionally decrypts it.
   * Encrypted passwords are detected by a leading {@code ~}. If an unencrypted password must begin
   * with a {@code ~}, it must be escaped by a leading backslash {@code \~}.
   *
   * @param password the probably encrypted password
   * @return the cleartext password
   */
  protected String createPassword(char[] password) {
    String pw;
    if (password != null) {
      // encrypted passwords start with a ~.
      if (password.length > 0 && password[0] == '~') {
        // password is encrypted
        pw = Cryptor.getInstanceSafely().decrypt64(new String(password, 1, password.length - 1));
      }
      else if (password.length > 1 && password[0] == '\\' && password[1] == '~') {
        // unencrypted password with a leading tilde (~)
        pw = new String(password, 1, password.length - 1);
      }
      else {
        pw = new String(password);
      }
    }
    else {
      pw = "";
    }
    return pw;
  }

  /**
   * Normalizes the default string to compare.
   *
   * @param str the default value as a string, null if none
   * @return the normalized value, never null
   */
  protected String normalizeDefault(String str) {
    if (str == null) {
      str = "";
    }
    else {
      str = str.toUpperCase(Locale.ROOT);
      if (str.startsWith("'")) {
        str = str.substring(1);
      }
      if (str.endsWith("'")) {
        str = str.substring(0, str.length() - 1);
      }
    }
    return str;
  }

  /**
   * Generates the attribute definition of a CREATE TABLE statement.
   *
   * @param columnName the database column name
   * @param sqlType the SQL type
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
   * Checks if the exception's state starts with a given string(s).
   *
   * @param ex the exception
   * @param prefixes the prefixes the state starts with
   * @return true if so
   */
  protected boolean isExceptionStateStartingWith(SQLException ex, String... prefixes) {
    boolean match = false;
    String sqlState = extractSqlState(ex);
    if (sqlState != null) {
      for (String prefix : prefixes) {
        if (sqlState.startsWith(prefix)) {
          match = true;
          break;
        }
      }
    }
    return match;
  }

  /**
   * Checks if the exception's state equals given string(s).
   *
   * @param ex the exception
   * @param states the states to check for
   * @return true if matches
   */
  protected boolean isExceptionStateMatching(SQLException ex, String... states) {
    boolean match = false;
    String sqlState = extractSqlState(ex);
    if (sqlState != null) {
      for (String state : states) {
        if (sqlState.equals(state)) {
          match = true;
          break;
        }
      }
    }
    return match;
  }

  /**
   * Checks if the exception's error code equals the given number(s).
   *
   * @param ex the exception
   * @param codes the error codes to check for
   * @return true if matches
   */
  protected boolean isExceptionErrorCodeMatching(SQLException ex, int... codes) {
    boolean match = false;
    int errorCode = extractErrorCode(ex);
    for (int code : codes) {
      if (errorCode == code) {
        match = true;
        break;
      }
    }
    return match;
  }


  private String extractSqlState(SQLException ex) {
    String sqlState = null;
    while (ex != null) {
      sqlState = ex.getSQLState();
      if (sqlState != null && !sqlState.isBlank()) {
        break;
      }
      ex = ex.getNextException();
    }
    return sqlState;
  }

  private int extractErrorCode(SQLException ex) {
    int errorCode = 0;
    while (ex != null) {
      errorCode = ex.getErrorCode();
      if (errorCode != 0) {
        break;
      }
      ex = ex.getNextException();
    }
    return errorCode;
  }

}

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

import java.sql.Array;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
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
  protected static final String TYPE_CLOB = "CLOB";
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
  protected static final String TYPE_DOUBLE_PRECISION = "DOUBLE PRECISION";
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
  protected static final String TYPE_UUID = "UUID";


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

    return DriverManager.getConnection(url, username, createPassword(password));
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
   * Checks whether given string is a reserved word.
   *
   * @param word the word
   * @return true if reserved word
   */
  public boolean isReservedWord(String word) {
    return getReservedWords().contains(word.toUpperCase());
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
  public String sqlAsBeforeTableAlias() {
    return " ";
  }

  @Override
  public boolean needAliasForSubSelect() {
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
  public boolean isExpressionReferringToTableBeingUpdatedSupported() {
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
    // initial connection failed or connection vanished or became unusable
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
      case BIT, TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, DOUBLE, CHAR, DATE, TIME, TIMESTAMP, BLOB, CLOB -> -1;    // sizeless type
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
      case CHAR, VARCHAR, CLOB, TIME, DATE, TIMESTAMP, BIT -> "'" + value + "'";
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
                           // or changed from VARCHAR(x) to VARCHAR(maxSize)
                           size == 0 && sqlType == SqlType.VARCHAR &&
                           column.getSize() > 0 && column.getSize() != maxSize);
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
  public String sqlUpdateToNotNull(String tableName, String columnName, SqlType sqlType, Object defaultValue) {
    return  "UPDATE " + tableName +
            " SET " +
            columnName +
            "=" +
            valueToLiteral(sqlType, defaultValue != null ? defaultValue : sqlType.getDefaultValue()) +
            SQL_WHERE +
            columnName +
            " IS NULL" + getStatementSeparator() + "\n";
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

  @Override
  public void assertValidName(SqlNameType nameType, String name) {
    if (name != null) {
      if (getReservedWords().contains(name.toUpperCase())) {
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
        sqlType = SqlType.CLOB;
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
  public boolean isClobSupported() {
    return true;
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
   * @param tableName the tablename with optional schema separated by a dot
   * @return the SQL code including the opening bracket
   */
  protected String sqlCreateTableIntroWithoutComment(String tableName) {
    return "CREATE TABLE " + tableName + " (";
  }

  /**
   * Creates a password string from a password char array.<br>
   * Unfortunately, {@link DriverManager#getConnection(String, String, String)} requires the password as a string.
   * This method creates a string from a char array and optionally decrypts it.
   * Encrypted passwords are detected by a leading {@code ~}. If an unencrypted password must begin
   * with a {@code ~}, a double {@code ~~} must be used.
   *
   * @param password the probably encrypted password
   * @return the cleartext password
   */
  protected String createPassword(char[] password) {
    String pw;
    if (password != null) {
      // encrypted passwords start with a ~.
      // if a non-encrypted password must start with ~, ~~ must be used
      if (password.length > 1 && password[0] == '~' && password[1] != '~') {
        // password is encrypted
        pw = Cryptor.getInstance().decrypt64(new String(password, 1, password.length - 1));
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
   * Checks if the exception's error code equals given number(s).
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

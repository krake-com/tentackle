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

import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.IndexColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Set;

/**
 * A database backend.<br>
 * Defines the backend specifics.<br>
 * There is only one instance per database type.<br>
 * Implementations must not maintain any state except for the whole of all connections to the
 * specific kind of database.
 *
 * @author harald
 */
public interface Backend {

  /** WHERE string. */
  String SQL_WHERE = " WHERE ";

  /** SELECT string. */
  String SQL_SELECT = "SELECT ";

  /** SELECT * FROM string. */
  String SQL_SELECT_ALL_FROM = "SELECT * FROM ";

  /** DELETE string. */
  String SQL_DELETE = "DELETE ";

  /** UPDATE string. */
  String SQL_UPDATE = "UPDATE ";

  /** INSERT INTO string. */
  String SQL_INSERT_INTO = "INSERT INTO ";

  /** opening bracket. */
  String SQL_LEFT_PARENTHESIS = " (";

  /** closing bracket. */
  String SQL_RIGHT_PARENTHESIS = ") ";

  /** (VALUES) string for insert. */
  String SQL_INSERT_VALUES = SQL_RIGHT_PARENTHESIS + "VALUES" + SQL_LEFT_PARENTHESIS;

  /** SET string. */
  String SQL_SET = " SET ";

  /** FROM string. */
  String SQL_FROM = " FROM ";

  /** AND string. */
  String SQL_AND = " AND ";

  /** OR string. */
  String SQL_OR = " OR ";

  /** NOT string. */
  String SQL_NOT = "NOT ";

  /** AND NOT string. */
  String SQL_ANDNOT = SQL_AND + SQL_NOT;

  /** OR NOT string. */
  String SQL_ORNOT = SQL_OR + SQL_NOT;

  /** comma separator. */
  String SQL_COMMA = ",";

  /** ? string. */
  String SQL_PAR = "?";

  /** = operator string. */
  String SQL_EQUAL = "=";

  /** &lt;&gt; operator string. */
  String SQL_NOTEQUAL = "<>";

  /** &lt; operator string. */
  String SQL_LESS = "<";

  /** &gt; operator string. */
  String SQL_GREATER = ">";

  /** &lt;= operator string. */
  String SQL_LESSOREQUAL = "<=";

  /** &gt;= operator string. */
  String SQL_GREATEROREQUAL = ">=";

  /** LIKE operator string. */
  String SQL_LIKE = " LIKE ";

  /** NOT LIKE operator string. */
  String SQL_NOTLIKE = " NOT LIKE ";

  /** =0 string. */
  String SQL_EQUAL_ZERO = SQL_EQUAL + "0";

  /** &lt;&gt;0 string. */
  String SQL_NOTEQUAL_ZERO = SQL_NOTEQUAL + "0";

  /** Parameter ,? string. */
  String SQL_COMMA_PAR = SQL_COMMA + SQL_PAR;

  /** Parameter ?, string. */
  String SQL_PAR_COMMA = SQL_PAR + SQL_COMMA;

  /** Parameter =? string. */
  String SQL_EQUAL_PAR = SQL_EQUAL + SQL_PAR;

  /** Parameter =?, string. */
  String SQL_EQUAL_PAR_COMMA = SQL_EQUAL + SQL_PAR + SQL_COMMA;

  /** Parameter &lt;&gt;? string. */
  String SQL_NOTEQUAL_PAR = SQL_NOTEQUAL + SQL_PAR;

  /** Parameter &gt;? string. */
  String SQL_GREATER_PAR = SQL_GREATER + SQL_PAR;

  /** Parameter &ge;? string. */
  String SQL_GREATEROREQUAL_PAR = SQL_GREATEROREQUAL + SQL_PAR;

  /** Parameter &lt;? string. */
  String SQL_LESS_PAR = SQL_LESS + SQL_PAR;

  /** Parameter &lt;=? string. */
  String SQL_LESSOREQUAL_PAR = SQL_LESSOREQUAL + SQL_PAR;

  /** Parameter LIKE ? string. */
  String SQL_LIKE_PAR = SQL_LIKE + SQL_PAR;

  /** Parameter NOT LIKE ? string. */
  String SQL_NOTLIKE_PAR = SQL_NOTLIKE + SQL_PAR;

  /** '*' all string. */
  String SQL_ALLSTAR = "*";

  /** +1 string. */
  String SQL_PLUS_ONE = "+1";

  /** "WHERE 1=1" allows AND to be added. */
  String SQL_WHEREALL = SQL_WHERE + "1=1";

  /** "WHERE 1=0" to select nothing. */
  String SQL_WHERENOTHING = SQL_WHERE + "1=0";

  /** SQL_WHEREALL + AND will be replaced by... SQL_WHERE. */
  String SQL_WHEREAND = SQL_WHEREALL + SQL_AND;

  /** SQL_WHEREALL + OR will be replaced by... SQL_WHERE. */
  String SQL_WHEREOR = SQL_WHEREALL + SQL_OR;

  /** IS NULL string. */
  String SQL_ISNULL = " IS NULL";

  /** IS NOT NULL string. */
  String SQL_ISNOTNULL = " IS NOT NULL";

  /** ORDER BY string. */
  String SQL_ORDERBY = " ORDER BY ";

  /** sort ASC string. */
  String SQL_SORTASC = " ASC";

  /** sort DESC string. */
  String SQL_SORTDESC = " DESC";

  /** GROUP BY string. */
  String SQL_GROUPBY = " GROUP BY ";

  /** MAX function name. */
  String SQL_MAX = "MAX";

  /** MAX function name. */
  String SQL_MIN = "MIN";

  /** SQL EXISTS clause with SELECT 1 and opening left parenthesis. */
  String SQL_EXISTS = "EXISTS (SELECT 1 FROM ";

  /** SQL ALTER TABLE intro. */
  String SQL_ALTER_TABLE = "ALTER TABLE ";

  /** SQL ALTER INDEX intro. */
  String SQL_ALTER_INDEX = "ALTER INDEX ";

  /** SQL ALTER COLUMN fragment. */
  String SQL_ALTER_COLUMN = " ALTER COLUMN ";

  /** SQL IF EXISTS fragment. */
  String SQL_IF_EXISTS = "IF EXISTS ";


  /**
   * Checks whether the backend belongs to the given jdbc url.<br>
   * If multiple backend implementations are provided for the same database type,
   * only one backend should match the URL. The others must be selected via name.
   *
   * @param url the jdbc url
   * @return true if matches
   */
  boolean isMatchingUrl(String url);


  /**
   * Checks whether the backend belongs to the given name.
   *
   * @param name the backend's name
   * @return true if matches
   */
  boolean isMatchingName(String name);


  /**
   * Gets the name of the backend.<br>
   *
   * @return the name
   */
  String getName();

  /**
   * Gets the JDBC driver class name.
   *
   * @return the class name
   */
  String getDriverClassName();

  /**
   * Creates a jdbc connection.
   *
   * @param url the jdbc url
   * @param username the username
   * @param password the password
   * @return the created connection
   * @throws SQLException if connection could not be established
   */
  Connection createConnection(String url, String username, char[] password) throws SQLException;

  /**
   * Gets the metadata from the backend.
   * <p>
   * Important: the connection of the metadata is open!
   *
   * @param backendInfo the backend info
   * @return the metadata, may be more than one if schemas set in backendInfo
   * @throws SQLException if failed
   */
  DatabaseMetaData[] getMetaData(BackendInfo backendInfo) throws SQLException;

  /**
   * Checks whether this is a temporary name.<br>
   * Returns whether an entity-, attribute- or index-name is temporary and should not
   * be taken into account for migration or even used in the model.<br>
   * Depending on the backend, temporary names start with an underscore or some other
   * lead string.
   *
   * @param name the name
   * @return true temporary (invalid) name
   */
  boolean isTemporaryName(String name);

  /**
   * Creates a select statement string from an inner sql string.
   * <p>
   * {@code sqlBuilder} is anything as {@code FROM... WHERE... ORDER BY...}.<br>
   * It is decorated with a leading {@code SELECT} plus optional clauses
   * like for the given parameters.
   *
   * @param sqlBuilder the sql builder, initially containing the inner sql without leading {@code SELECT}.
   * @param writeLock true select should write lock
   * @param limit the limit value, &le; 0 if no limit clause
   * @param offset the offset value, &le; 0 if no offset clause
   */
  void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset);

  /**
   * Creates a select statement string from an inner sql string.
   * <p>
   * {@code sqlBuilder} is anything as {@code FROM... WHERE... ORDER BY...}.<br>
   * It is decorated with a leading {@code SELECT} plus optional clauses
   * like for the given parameters.
   *
   * @param sql the sql without leading {@code SELECT}.
   * @param writeLock true select should write lock
   * @param limit the limit value, &le; 0 if no limit clause
   * @param offset the offset value, &le; 0 if no offset clause
   * @return the select statement
   */
  String buildSelectSql(String sql, boolean writeLock, int limit, int offset);


  /**
   * Sets optional parameters before applying the original parameters.
   *
   * @param stmt the prepared statement
   * @param limit the limit value, &le; 0 if no limit clause
   * @param offset the offset value, &le; 0 if no offset clause
   * @return the next parameter index
   */
  int setLeadingSelectParameters(BackendPreparedStatement stmt, int limit, int offset);

  /**
   * Sets optional parameters after applying the original parameters.
   *
   * @param stmt the prepared statement
   * @param index the first parameter index
   * @param limit the limit value, &le; 0 if no limit clause
   * @param offset the offset value, &le; 0 if no offset clause
   * @return the next parameter index
   */
  int setTrailingSelectParameters(BackendPreparedStatement stmt, int index, int limit, int offset);


  /**
   * Gets the empty string.<p>
   * Some dbms (most famous: Oracle) handle empty strings as null.
   * In such cases the empty string (e.g. Oracle) may consist of a single blank
   * or whatever.
   *
   * @return the empty string (never null)
   */
  String getEmptyString();


  /**
   * According to the JDBC-specs {@link java.sql.Connection#setAutoCommit}(true)
   * should commit, but some backends require an extra {@link java.sql.Connection#commit}.
   *
   * @return true if the database needs an extra commit
   */
  boolean sqlRequiresExtraCommit();


  /**
   * Determines whether the jdbc driver supports {@link java.sql.ResultSet#isClosed()}.
   *
   * @return true if the driver supports it
   */
  boolean sqlResultSetIsClosedSupported();


  /**
   * Gets the keyword for the COALESCE function.
   *
   * @return the keyword
   */
  String getCoalesceKeyword();


  /**
   * Determines whether backend allows expressions referring to tables being updated.
   *
   * @return true if allowed
   */
  boolean allowsExpressionsReferringToTablesBeingUpdated();

  /**
   * True if backend provides transactionless sequences.
   *
   * @return true if database supports sequences
   */
  boolean supportsSequences();

  /**
   * Gets the string before the table alias.<br>
   * In most databases the " AS " is optional and some
   * don't accept it all.
   *
   * @return the as-string
   */
  String sqlAsBeforeTableAlias();

  /**
   * Returns whether backend needs an alias for a subselect.
   *
   * @return true if subselect needs an alias.
   */
  boolean needAliasForSubselect();

  /**
   * Creates the SQL string to retrieve the next id from a sequence.
   *
   * @param name the name of the sequence
   * @return the SQL code
   */
  String sqlNextFromSequene(String name);

  /**
   * Create comment sql code that is interpreted as a comment by the backend.
   *
   * @param text any text, single or multiline
   * @return the comment, null if text was null
   */
  String sqlComment(String text);

  /**
   * Creates a join clause.
   *
   * @param type the join type
   * @param joinedTableName the joined tablename
   * @param joinedTableAlias the joined table alias, null if none
   * @param join the join expression
   * @return the sql code
   */
  String sqlJoin(JoinType type, String joinedTableName, String joinedTableAlias, String join);

  /**
   * Creates an sql function expression.
   * <p>
   * Example:
   * <pre>
   *  sqlFunction("max", CN_ID) --&gt; "MAX(id)"
   * </pre>
   *
   * @param functionName the function name, will be translated to uppercase
   * @param expression the expression, may be null
   * @return the sql code
   */
  String sqlFunction(String functionName, String expression);

  /**
   * Checks whether we need a workaround for the (Ingres) setLong-Bug.
   *
   * @return true if workaround needed
   */
  boolean needSetLongWorkaround();


  /**
   * Checks for the (postgres) bug that fetchsize is ignored if not within a new transaction.
   *
   * @return true if start tx
   */
  boolean needTxForFetchsize();


  /**
   * Determines whether exception is a constraint violation.
   *
   * @param ex the exception
   * @return true if constraint violation
   */
  boolean isConstraintException(SQLException ex);


  /**
   * Determines whether exception is a communication error.
   *
   * @param ex the exception
   * @return true if comlink down or alike
   */
  boolean isCommunicationLinkException(SQLException ex);


  /**
   * Returns the maximum size for a given SQL-type.<br>
   *
   * @param sqlType the SQL-type
   * @return the maximum size, 0 if unlimited, -1 if type without size
   */
  int getMaxSize(SqlType sqlType);

  /**
   * Returns the maximum scale for a given SQL-type and size.<br>
   *
   * @param sqlType the SQL-type
   * @param size the size, 0 if unlimited
   * @return the maximum scale, 0 if unlimited
   */
  int getMaxScale(SqlType sqlType, int size);

  /**
   * Gets the default size if no size given in model.
   *
   * @param sqlType the SQL-type
   * @return the default size, 0 if no default size
   */
  int getDefaultSize(SqlType sqlType);

  /**
   * Gets the default schema name.<br>
   * Some backends store the objects in a default schema, if no explicit schema is given.
   *
   * @return the defailt schema, null if none
   */
  String getDefaultSchema();

  /**
   * Gets the list of reserved words for this backend.
   *
   * @return the list of reserved words
   */
  Set<String> getReservedWords();

  /**
   * Asserts that given string is a valid name and usage.
   *
   * @param nameType the type/usage of the name
   * @param name the name
   * @throws BackendException if invalid name
   */
  void assertValidName(String nameType, String name);


  /**
   * Determines the unique id or name used by the backend for a given connection.
   * <p>
   * Useful to figure out the corresponding {@code ManagedConnection} from the backend's logfiles.
   *
   * @param connection the jdbc connection
   * @return the backend id, null if none
   */
  String getBackendId(Connection connection);


  /**
   * Retrieves the metadata.<br>
   *
   * @param metaData the meta data for the whole database
   * @param schemas the optional schemas to filter tables, null if no filter
   * @param tableNames the tables of the model
   * @return the meta data, null if no such table
   */
  ModelMetaData getModelMetaData(DatabaseMetaData[] metaData, String[] schemas, String... tableNames);

  /**
   * Creates a column meta data instance.
   *
   * @param tableMetaData the table meta data this column belongs to
   * @return the meta data
   */
  ColumnMetaData createColumnMetaData(TableMetaData tableMetaData);

  /**
   * Creates a meta data instance.
   *
   * @param metaData the whole meta data
   * @param tableName the model's table name
   * @return the meta data
   */
  TableMetaData createTableMetaData(ModelMetaData metaData, String tableName);

  /**
   * Creates an index meta data instance.
   *
   * @param tableMetaData the table meta data this index belongs to
   * @return the meta data
   */
  IndexMetaData createIndexMetaData(TableMetaData tableMetaData);

  /**
   * Creates an index column meta data instance.
   *
   * @param indexMetaData the meta meta data this column belongs to
   * @return the meta data
   */
  IndexColumnMetaData createIndexColumnMetaData(IndexMetaData indexMetaData);

  /**
   * Converts the java SQL-type to the database type name.
   *
   * @param sqlType the java sql type
   * @param size the column's size
   * @return the corresponding type string
   */
  String sqlTypeToString(SqlType sqlType, int size);

  /**
   * Converts an sqltype, size and scale to a database type declaration.
   *
   * @param sqlType the SQL type
   * @param size the size
   * @param scale the scale
   * @return the type definition
   */
  String columnTypeToString(SqlType sqlType, int size, int scale);

  /**
   * Converts an sqltype, size, scale, nullable and default value to a database type declaration.
   *
   * @param columnName the database column name
   * @param sqlType the JDBC sql type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @return the SQL code
   */
  String columnTypeNullDefaultToString(String columnName, SqlType sqlType, int size, int scale,
                                              boolean nullable, Object defaultValue);

  /**
   * Conberts a type and value to a literal string.
   *
   * @param sqlType the sql type
   * @param value the value
   * @return the constant
   */
  String valueToLiteral(SqlType sqlType, Object value);

  /**
   * Generates the first line of a CREATE TABLE statement.
   *
   * @param tableName the tablename with optional schema separated by a dot
   * @param comment optional comment, null if none
   * @return the SQL code including the opening bracket
   */
  String sqlCreateTableIntro(String tableName, String comment);

  /**
   * Generates the last line of a CREATE TABLE statement.
   *
   * @param tableName the tablename with optional schema separated by a dot
   * @param comment optional comment, null if none
   * @return the SQL code including the closing bracket
   */
  String sqlCreateTableClosing(String tableName, String comment);

  /**
   * Generates SQL code to create the comment for a table.<br>
   *
   * @param tableName the table name
   * @param comment optional comment, null if none
   * @return the SQL code, empty string if comment is created via {@link #sqlCreateTableIntro} or {@link #sqlCreateTableClosing}
   */
  String sqlCreateTableComment(String tableName, String comment);

  /**
   * Generates SQL code to alter the comment for a table.<br>
   *
   * @param tableName the table name
   * @param comment optional comment, null to clear
   * @return the SQL code
   */
  String sqlAlterTableComment(String tableName, String comment);

  /**
   * Generates the attribute definition of a CREATE TABLE statement.
   *
   * @param columnName the database column name
   * @param comment optional comment, null if none
   * @param sqlType the JDBC sql type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @param primaryKey true if this is a primary key
   * @param withTrailingComma true if append a comma
   * @return the SQL code
   */
  String sqlCreateColumn(String columnName, String comment, SqlType sqlType, int size, int scale, boolean nullable,
                         Object defaultValue, boolean primaryKey, boolean withTrailingComma);

  /**
   * Checks whether the column's default corresponds to the model's default value.
   *
   * @param column the column to inspect
   * @param sqlType the sql type
   * @param defaultValue the model's default value
   * @return true if same
   */
  boolean isDefaultEqual(ColumnMetaData column, SqlType sqlType, Object defaultValue);

  /**
   * Determines the best migration strategy.
   *
   * @param column old column meta data
   * @param columnName new column name
   * @param comment new comment
   * @param sqlType new sql type
   * @param size new size
   * @param scale new scale
   * @param nullable new nullable
   * @param defaultValue new default
   * @return the strategies in order to achieve the migration
   */
  MigrationStrategy[] getMigrationStrategy(ColumnMetaData column, String columnName, String comment, SqlType sqlType,
                                           int size, int scale, boolean nullable, Object defaultValue);

  /**
   * Generates sql code to rename a column.
   *
   * @param tableName the tablename
   * @param oldColumnName the old column name
   * @param newColumnName the new column name
   * @return the SQL code, null if need full spec sqlRenameAndAlterColumnType below
   */
  String sqlRenameColumn(String tableName, String oldColumnName, String newColumnName);

  /**
   * Generates sql code to rename an index.
   *
   * @param tableName the tablename
   * @param oldIndexName the old column name
   * @param newIndexName the new column name
   * @return the SQL code, null if not supported by the backend and we need to drop and create
   */
  String sqlRenameIndex(String tableName, String oldIndexName, String newIndexName);

  /**
   * Generates sql code to rename a column.
   *
   * @param tableName the tablename
   * @param oldColumnName the old column name
   * @param newColumnName the new column name
   * @param comment optional comment, null if none
   * @param sqlType the JDBC sql type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @return the SQL code
   */
  String sqlRenameAndAlterColumnType(String tableName, String oldColumnName, String newColumnName, String comment, SqlType sqlType,
                                     int size, int scale, boolean nullable, Object defaultValue);

  /**
   * Generates sql code to add a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @param comment optional comment, null if none
   * @param sqlType the JDBC sql type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @return the SQL code
   */
  String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType,
                      int size, int scale, boolean nullable, Object defaultValue);

  /**
   * Generates sql code to drop a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @return the SQL code
   */
  String sqlDropColumn(String tableName, String columnName);

  /**
   * Generates sql code to change the datatype of a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @param comment optional comment, null if none
   * @param sqlType the JDBC sql type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @return the SQL code
   */
  String sqlAlterColumnType(String tableName, String columnName, String comment, SqlType sqlType, int size, int scale, boolean nullable, Object defaultValue);

  /**
   * Generates code to update a column to a non-null value.
   *
   * @param tableName the tablename
   * @param columnName the column name
   * @param sqlType the sql type
   * @param defaultValue the optional default value if defined in the model
   * @return the SQL code
   */
  String sqlUpdateToNotNull(String tableName, String columnName, SqlType sqlType, Object defaultValue);

  /**
   * Generates sql code to change the null-constraint of a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @param nullable true if NULL, else NOT NULL
   * @return the SQL code, null if need {@link #sqlAlterColumnType}
   */
  String sqlAlterColumnNullConstraint(String tableName, String columnName, boolean nullable);

  /**
   * Generates sql code to change the default value a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @param sqlType the JDBC sql type
   * @param defaultValue the optional default value
   * @return the SQL code, null if need {@link #sqlAlterColumnType}
   */
  String sqlAlterColumnDefault(String tableName, String columnName, SqlType sqlType, Object defaultValue);

  /**
   * Generates SQL code to create the comment for an attribute.<br>
   *
   * @param tableName the table name
   * @param columnName the column name
   * @param comment optional comment, null if none
   * @return the SQL code, empty string if comment is created via {@link #sqlCreateColumn}
   */
  String sqlCreateColumnComment(String tableName, String columnName, String comment);

  /**
   * Generates the CREATE INDEX statement.
   *
   * @param tableName the table name
   * @param indexName the name of the index
   * @param unique true if index is unique
   * @param filterCondition the filter condition (null if none)
   * @param columnNames the column names (with a leading '-' if descending)
   * @return the SQL code
   */
  String sqlCreateIndex(String tableName, String indexName, boolean unique, String filterCondition, String... columnNames);

  /**
   * Generates the DROP INDEX statement.
   *
   * @param schemaName the optional schema, null if none
   * @param tableNameWithoutSchema the table name without the schema
   * @param indexName the name of the index
   * @return the SQL code
   */
  String sqlDropIndex(String schemaName, String tableNameWithoutSchema, String indexName);

  /**
   * Generates the ALTER TABLE statement to add a foreign key constraint.
   *
   * @param referencingTableName the referencing foreign table name
   * @param referencingColumnName the referencing foreign column name
   * @param referencedTableName the referenced primary table name
   * @param referencedColumnName the referenced primary column name
   * @param foreignKeyName the name of the foreign key
   * @param composite true if referencing table is a component of the referenced table
   * @return the SQL code
   */
  String sqlCreateForeignKey(String referencingTableName, String referencingColumnName,
                             String referencedTableName, String referencedColumnName,
                             String foreignKeyName, boolean composite);

  /**
   * Generates the sql statement to drop a foreign key constraint.
   *
   * @param referencingTableName the referencing foreign table name
   * @param foreignKeyName the name of the foreign key
   * @return the SQL code
   */
  String sqlDropForeignKey(String referencingTableName, String foreignKeyName);

  /**
   * Generates SQL code to alter the comment for an attribute.<br>
   *
   * @param tableName the table name
   * @param columnName the column name
   * @param comment optional comment, null to clear
   * @return the SQL code, null if use {@link #sqlRenameAndAlterColumnType} instead
   */
  String sqlAlterColumnComment(String tableName, String columnName, String comment);

  /**
   * Generate SQL code for joining a table to an existing select.
   *
   * @param type the join type
   * @param addColumns true if columns of joined table should be added to the result set
   * @param select the original select statement
   * @param joinSelect the select to join with the original sql or just a tablename
   * @param joinSelectIdAlias extra id alias to add to the joined select, null if none
   * @param joinAlias the alias of the joined select
   * @param join the join clause
   */
  void sqlJoinSelects(JoinType type, boolean addColumns, StringBuilder select,
                      String joinSelect, String joinSelectIdAlias, String joinAlias, String join);

  /**
   * Generate SQL code for joining a table to an existing select.
   *
   * @param type the join type
   * @param addColumns true if columns of joined table should be added to the result set
   * @param select the original select statement
   * @param joinSelect the select to join with the original sql or just a tablename
   * @param joinSelectIdAlias extra id alias to add to the joined select, null if none
   * @param joinAlias the alias of the joined select
   * @param join the join clause
   * @return the select statement
   */
  String sqlJoinSelects(JoinType type, boolean addColumns, String select,
                        String joinSelect, String joinSelectIdAlias, String joinAlias, String join);

  /**
   * Converts the JDBC data type integer to a tentackle backend SqlTypes.
   * <p>
   * There may be more than one sqltype returned!
   *
   * @param jdbcType the jdbc data type
   * @param size the column size
   * @param scale the column's scale
   * @return the SqlTypes, empty array if no mapping available or no exact match (not generated by TT)
   * @see java.sql.Types
   */
  SqlType[] jdbcTypeToSqlType(int jdbcType, int size, int scale);

  /**
   * Gets the table meta data for a given table name.
   *
   * @param modelMetaData the model the table belongs to
   * @param tableName the model's table name
   * @return the table data
   */
  TableMetaData getTableMetaData(ModelMetaData modelMetaData, String tableName);

  /**
   * Converts a string to a string enclosed in single quotes.
   *
   * @param str the string
   * @return the quoted string
   */
  String toQuotedString(String str);


  /**
   * Optimize SQL code.<br>
   * Replace WHERE 1=1 AND/OR to WHERE.
   * Any remaining WHERE 1=1 will be removed too.
   * If you don't want your statements getting optimized, use lowercase for those keywords.
   *
   * @param sql the original sql code
   * @return the optimized sql code
   */
  String optimizeSql(String sql);


  /**
   * Returns whether the backend supports releasing savepoints explicitly.
   * If not, the savepoints are released when the transaction finishs.
   *
   * @return true if release is supported, else false
   */
  boolean isReleaseSavepointSupported();

  /**
   * Returns whether the backend support CLOB types.<br>
   * If not, it will be emulated as VARCHAR with the maximum possible size.
   *
   * @return true if supported, false if treat as varchar
   */
  boolean isClobSupported();

  /**
   * Returns whether backend supports filtered indexes.
   *
   * @return true if supported, false if model will raise an error if filtered index is used
   */
  boolean isFilteredIndexSupported();

  /**
   * Some database support drop index/constraint/column IF EXISTS.<br>
   * This may be useful if migration scripts are generated against non-production databases
   * that contain test indexes etc...<br>
   * Notice, that those temporary DDL changes should better use the temporary prefix (see {@link #isTemporaryName(String)} in
   * order to be ignored by the migrator.
   *
   * @param dropIfExists true if use IF EXISTS if the backend supports it
   */
  void setDropIfExistsEnabled(boolean dropIfExists);

  /**
   * Returns whether backend should add IF EXISTS for generated DROPs.
   *
   * @return true if IF EXISTS is turned on and supported, false if not supported or turned off
   */
  boolean isDropIfExistsEnabled();
}

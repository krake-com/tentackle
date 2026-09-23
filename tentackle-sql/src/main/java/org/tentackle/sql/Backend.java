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
import org.tentackle.sql.metadata.DatabaseMetaDataTableHeader;
import org.tentackle.sql.metadata.Deferrability;
import org.tentackle.sql.metadata.IndexColumnMetaData;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import javax.sql.XADataSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

  /** DELETE FROM string. */
  String SQL_DELETE_FROM = "DELETE FROM ";

  /** UPDATE string. */
  String SQL_UPDATE = "UPDATE ";

  /** INSERT INTO string. */
  String SQL_INSERT_INTO = "INSERT INTO ";

  /** opening bracket. */
  String SQL_LEFT_PARENTHESIS = " (";

  /** closing bracket. */
  String SQL_RIGHT_PARENTHESIS = ") ";

  /** dot all columns. */
  String SQL_DOT_STAR = ".*";

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

  /** Array parameter (?). */
  String SQL_ARRAY_PAR = "(?)";

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

  /** IN array operator string. */
  String SQL_ARRAY_IN = "IN";

  /** NOT IN array operator string. */
  String SQL_ARRAY_NOT_IN = "NOT IN";

  /** ANY array operator string. */
  String SQL_ARRAY_ANY = "ANY";

  /** ALL array operator string. */
  String SQL_ARRAY_ALL = "ALL";

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

  /** Parameter IN array operator string. */
  String SQL_ARRAY_IN_PAR = " " + SQL_ARRAY_IN + SQL_ARRAY_PAR;

  /** Parameter NOT IN array operator string. */
  String SQL_ARRAY_NOT_IN_PAR = " " + SQL_ARRAY_NOT_IN + SQL_ARRAY_PAR;

  /** Parameter ANY array operator string. */
  String SQL_ARRAY_ANY_PAR = SQL_ARRAY_ANY + SQL_ARRAY_PAR;

  /** Parameter ALL array operator string. */
  String SQL_ARRAY_ALL_PAR = SQL_ARRAY_ALL + SQL_ARRAY_PAR;

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
   * Returns whether this backend is deprecated and superseded by a newer implementation.<br>
   * Deprecated backends are omitted from {@link BackendFactory#getAllBackends()}.
   *
   * @return true if deprecated
   */
  boolean isDeprecated();

  /**
   * Verifies that the major and minor version of the connection's metadata is compatible with this backend.<br>
   * If the latest backend is not compatible, the URL may be post-fixed with a backend type for an older version, for example:
   * <pre>
   *   jdbc:oracle:thin:@//localhost:1521/xe|Oracle8
   * </pre>
   *
   * <p>
   * Throws {@link BackendException} if incompatible.
   *
   * @param versionInfo the database version
   * @see BackendInfo#BackendInfo(String, String, char[], String[])
   */
  void validateVersion(BackendVersionInfo versionInfo);

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
   * @throws SQLException if the connection could not be established
   */
  Connection createConnection(String url, String username, char[] password) throws SQLException;

  /**
   * Gets the name of the JDBC driver's {@link XADataSource} class.<br>
   * Required for connections taking part in global (XA) transactions.
   *
   * @return the class name, null if the backend does not support XA
   */
  String getXADataSourceClassName();

  /**
   * Creates a data source for XA connections.<br>
   * The data source is configured with the given jdbc url and credentials.
   *
   * @param url the jdbc url
   * @param username the username, null if none
   * @param password the password, null if none
   * @return the XA data source
   * @throws SQLException if the backend does not support XA or the data source could not be created
   */
  XADataSource createXADataSource(String url, String username, char[] password) throws SQLException;

  /**
   * Gets the metadata from the backend.
   * <p>
   * Important: the connection of the metadata is open!
   *
   * @param backendInfo the backend info
   * @return the metadata
   * @throws SQLException if failed
   */
  DatabaseMetaData[] getMetaData(BackendInfo backendInfo) throws SQLException;

  /**
   * Gets all table headers for a given metadata.
   *
   * @param metaData the database metadata
   * @return the list of table headers
   * @throws SQLException if failed
   */
  List<DatabaseMetaDataTableHeader> getTableHeaders(DatabaseMetaData metaData) throws SQLException;

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
   * Checks whether this a reserved table name for this backend.<br>
   * Applies only to tables. Not to be mixed up with {@link #getReservedWords()}.<br>
   * Useful to avoid conflicts with naming conventions for backend-specific things,
   * such as snapshots.
   *
   * @param name the table name
   * @return true if reserved by backend
   */
  boolean isReservedTableName(String name);

  /**
   * Checks whether this is a reserved schema name for this backend.
   *
   * @param name the schema name
   * @return true if reserved by backend
   */
  boolean isReservedSchemaName(String name);

  /**
   * Creates a select statement string from an inner SQL string.
   * <p>
   * {@code sqlBuilder} is anything as {@code [SELECT] FROM... WHERE... ORDER BY...}.<br>
   * It is decorated with a leading {@code SELECT}, if missing, plus optional clauses
   * like for the given parameters.
   *
   * @param sqlBuilder the SQL builder, initially containing the inner SQL without leading {@code SELECT}.
   * @param writeLock true select should write lock
   * @param limit the limit value, &le; 0 if no limit clause
   * @param offset the offset value, &le; 0 if no offset clause
   */
  void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset);

  /**
   * Creates a select statement string from an inner SQL string.
   * <p>
   * Same as {@link #buildSelectSql(StringBuilder, boolean, int, int)}, but for strings.
   *
   * @param sql the SQL without leading {@code SELECT}.
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
   * Gets the empty string.<br>
   * Some dbms (most famous: Oracle) handle empty strings as null.
   * In such cases the empty string (e.g., Oracle) may consist of a single blank,
   * or whatever.
   *
   * @return the empty string (never null)
   */
  String getEmptyString();

  /**
   * Gets the dummy select to keep/test connections alive.<br>
   * Most databases just require a <code>"SELECT 1"</code>.
   *
   * @return the select string
   */
  String getDummySelect();

  /**
   * According to the JDBC-specs {@link java.sql.Connection#setAutoCommit}(true)
   * should commit, but some backends require an extra {@link java.sql.Connection#commit}.
   *
   * @return true if the database needs an extra commit
   */
  boolean isExtraCommitRequired();

  /**
   * Gets the keyword for the COALESCE function.
   *
   * @return the keyword
   */
  String getCoalesceKeyword();

  /**
   * Determines whether the backend allows expressions referring to tables being updated.
   *
   * @return true if allowed
   */
  boolean isExpressionReferringToTableBeingUpdatedSupported();

  /**
   * Returns whether the backend provides locking-free sequences.<br>
   * Sequences must not take part in transactions, i.e., no read- or write-locks!
   *
   * @return true if the database supports sequences
   */
  boolean isSequenceSupported();

  /**
   * Gets the string before the table alias.<br>
   * In most databases the {@code " AS "} is optional and some
   * don't accept it all.
   *
   * @return the as-string
   */
  String sqlAsBeforeTableAlias();

  /**
   * Returns whether the backend requires an alias for a sub-select.
   *
   * @return true if a sub-select needs an alias.
   */
  boolean isAliasRequiredForSubSelect();

  /**
   * Creates the SQL string to create a sequence.
   *
   * @param name the sequence name
   * @param start the optional start value, defaults to 1
   * @param increment the optional increment, defaults to 1
   * @return the SQL code
   */
  String sqlCreateSequence(String name, Long start, Long increment);

  /**
   * Creates the SQL string to comment a sequence.
   *
   * @param name the sequence name
   * @param comment the comment
   * @return the SQL code
   */
  String sqlCreateSequenceComment(String name, String comment);

  /**
   * Creates the SQL string to retrieve the next id from a sequence.
   *
   * @param name the name of the sequence
   * @return the SQL code
   */
  String sqlNextFromSequence(String name);

  /**
   * Creates the SQL string to create a schema.
   *
   * @param name the schema name
   * @return the SQL code
   * @see #isSchemaSupported()
   */
  String sqlCreateSchema(String name);

  /**
   * Create SQL code interpreted as a comment by the backend.
   *
   * @param text any text, single or multiline
   * @return the comment, null if the text was null
   */
  String sqlComment(String text);

  /**
   * Creates a join clause.
   *
   * @param type the join type
   * @param joinedTableName the joined tablename
   * @param joinedTableAlias the joined table alias, null if none
   * @param join the join expression
   * @return the SQL code
   */
  String sqlJoin(JoinType type, String joinedTableName, String joinedTableAlias, String join);

  /**
   * Creates an SQL function expression.
   * <p>
   * Example:
   * <pre>
   *  sqlFunction("max", CN_ID) --&gt; "MAX(id)"
   * </pre>
   *
   * @param functionName the function name (will be translated to uppercase)
   * @param expression the expression (can be null)
   * @return the SQL code
   */
  String sqlFunction(String functionName, String expression);

  /**
   * Checks for the (Postgres) bug that fetch-size is ignored if not within a new transaction.
   *
   * @return true if start tx
   */
  boolean isTxRequiredForFetchsize();

  /**
   * Determines whether exception is a constraint violation.
   *
   * @param ex the exception
   * @return true if constraint violation
   */
  boolean isConstraintException(SQLException ex);

  /**
   * Determines whether the exception is a communication error.
   *
   * @param ex the exception
   * @return true if comlink down or alike
   */
  boolean isCommunicationLinkException(SQLException ex);

  /**
   * Determines whether the exception is transient and retrying the transaction may succeed.<br>
   * Typical transient exceptions are thrown when a deadlock is detected or when serialized transaction isolation is used.
   * <p>
   * Other transient errors are not covered by this method (e.g., connection timeouts, etc...).
   *
   * @param ex the exception
   * @return true if retrying the transaction may succeed
   */
  boolean isTransientTransactionException(SQLException ex);

  /**
   * Returns the maximum size for a given SQL type.<br>
   *
   * @param sqlType the SQL-type
   * @return the maximum size, 0 if unlimited, -1 for types without a size
   */
  int getMaxSize(SqlType sqlType);

  /**
   * Returns the maximum scale for a given SQL type and size.<br>
   *
   * @param sqlType the SQL-type
   * @param size the size, 0 if unlimited
   * @return the maximum scale, 0 if unlimited
   */
  int getMaxScale(SqlType sqlType, int size);

  /**
   * Gets the default size if no size given in the model.
   *
   * @param sqlType the SQL-type
   * @return the default size, 0 if no default size
   */
  int getDefaultSize(SqlType sqlType);

  /**
   * Gets the default schema name.<br>
   * Some backends store the objects in a default schema if no explicit schema is given.
   *
   * @return the default schema, null if none
   */
  String getDefaultSchema();

  /**
   * Gets the list of reserved words for this backend.
   *
   * @return the set of reserved words
   */
  Set<String> getReservedWords();

  /**
   * Asserts that the given string is a valid name and usage.
   *
   * @param nameType the type/usage of the name
   * @param name the name
   * @throws BackendException if invalid name
   */
  void assertValidName(SqlNameType nameType, String name);

  /**
   * Gets the maximum allowed length for a name.<br>
   * This method is used by {@link #assertValidName(SqlNameType, String)}.
   *
   * @param nameType the type of the name
   * @return the length
   */
  int getMaxNameLength(SqlNameType nameType);


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
   * @param metaData the metadata for the whole database
   * @param schemas the optional schemas to filter tables, null if no filter
   * @param tableNames the tables of the model
   * @return the metadata, null if no such table
   */
  ModelMetaData getModelMetaData(DatabaseMetaData[] metaData, String[] schemas, String... tableNames);

  /**
   * Creates a column metadata instance.
   *
   * @param tableMetaData the table metadata this column belongs to
   * @return the meta data
   */
  ColumnMetaData createColumnMetaData(TableMetaData tableMetaData);

  /**
   * Creates a meta data instance.
   *
   * @param metaData the whole metadata
   * @param tableName the model's table name
   * @return the meta data
   */
  TableMetaData createTableMetaData(ModelMetaData metaData, String tableName);

  /**
   * Creates an index metadata instance.
   *
   * @param tableMetaData the table metadata this index belongs to
   * @return the meta data
   */
  IndexMetaData createIndexMetaData(TableMetaData tableMetaData);

  /**
   * Creates an index column meta data instance.
   *
   * @param indexMetaData the metameta this column belongs to
   * @return the meta data
   */
  IndexColumnMetaData createIndexColumnMetaData(IndexMetaData indexMetaData);

  /**
   * Converts the java SQL type to the database type name.
   *
   * @param sqlType the java SQL type
   * @param size the column's size
   * @return the corresponding type string
   */
  String sqlTypeToString(SqlType sqlType, int size);

  /**
   * Converts the given SQL type, size, and scale to a database type declaration.
   *
   * @param sqlType the SQL type
   * @param size the size
   * @param scale the scale
   * @return the type definition
   */
  String columnTypeToString(SqlType sqlType, int size, int scale);

  /**
   * Converts an SqlType, size, scale, nullable, and default value to a database type declaration.
   *
   * @param columnName the database column name
   * @param sqlType the JDBC SQL type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @return the SQL code
   */
  String columnTypeNullDefaultToString(String columnName, SqlType sqlType, int size, int scale,
                                              boolean nullable, Object defaultValue);

  /**
   * Converts a type and value to a literal string.
   *
   * @param sqlType the SQL type
   * @param value the value
   * @return the constant
   */
  String valueToLiteral(SqlType sqlType, Object value);

  /**
   * The all SQL statement separators.
   *
   * @return the separators, usually {@code ";"}
   */
  String[] getStatementSeparators();

  /**
   * The SQL statement separator used in generated code.
   *
   * @return the separator, usually {@code ";"}
   */
  String getStatementSeparator();

  /**
   * Gets all strings that start a single line comment.
   *
   * @return the single line comment strings, usually {@code "--"}
   */
  String[] getSingleLineComments();

  /**
   * Gets the string to begin a single line comment in generated code.
   *
   * @return the single line comment string, usually {@code "--"}
   */
  String getSingleLineComment();

  /**
   * Gets all strings that begin a block comment.
   *
   * @return the strings to begin a block comment, usually {@code "/*"}
   */
  String[] getBlockCommentBegins();

  /**
   * Gets the string to begin a block comment in generated code.
   *
   * @return the string to begin a block comment, usually {@code "/*"}
   */
  String getBlockCommentBegin();

  /**
   * Gets all strings that end a block comment.
   *
   * @return the strings to end a block comment, usually {@code "*&#47;"}
   */
  String[] getBlockCommentEnds();

  /**
   * Gets the string to end a block comment in generated code.
   *
   * @return the string to end a block comment, usually {@code "*&#47;"}
   */
  String getBlockCommentEnd();

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
   * @return the SQL code, empty string if the comment is created via {@link #sqlCreateTableIntro} or {@link #sqlCreateTableClosing}
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
   * @param sqlType the JDBC SQL type
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
   * @param sqlType the SQL type
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
   * @param sqlType new SQL type
   * @param size new size
   * @param scale new scale
   * @param nullable new nullable
   * @param defaultValue new default
   * @return the strategies to achieve the migration
   */
  MigrationStrategy[] getMigrationStrategy(ColumnMetaData column, String columnName, String comment, SqlType sqlType,
                                           int size, int scale, boolean nullable, Object defaultValue);

  /**
   * Generate SQL code to rename a table.
   *
   * @param tableName the old tablename (with leading schema, if any)
   * @param newTableName the new tablename (without the schema name)
   * @return the SQL code
   */
  String sqlRenameTable(String tableName, String newTableName);

  /**
   * Generates SQL code to rename a column.
   *
   * @param tableName the tablename
   * @param oldColumnName the old column name
   * @param newColumnName the new column name
   * @return the SQL code, null if full spec sqlRenameAndAlterColumnType below is necessary
   */
  String sqlRenameColumn(String tableName, String oldColumnName, String newColumnName);

  /**
   * Generates sql code to rename an index.
   *
   * @param tableName the tablename
   * @param oldIndexName the old column name
   * @param newIndexName the new column name
   * @return the SQL code, null if not supported by the backend, and we need to drop and create
   */
  String sqlRenameIndex(String tableName, String oldIndexName, String newIndexName);

  /**
   * Generates sql code to rename a column.
   *
   * @param tableName the tablename
   * @param oldColumnName the old column name
   * @param newColumnName the new column name
   * @param comment optional comment, null if none
   * @param sqlType the JDBC SQL type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @return the SQL code
   */
  String sqlRenameAndAlterColumnType(String tableName, String oldColumnName, String newColumnName, String comment, SqlType sqlType,
                                     int size, int scale, boolean nullable, Object defaultValue);

  /**
   * Generates SQL code to add a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @param comment optional comment, null if none
   * @param sqlType the JDBC SQL type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @return the SQL code
   */
  String sqlAddColumn(String tableName, String columnName, String comment, SqlType sqlType,
                      int size, int scale, boolean nullable, Object defaultValue);

  /**
   * Generates SQL code to drop a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @return the SQL code
   */
  String sqlDropColumn(String tableName, String columnName);

  /**
   * Generates SQL code to change the datatype of a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @param comment optional comment, null if none
   * @param sqlType the JDBC SQL type
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
   * @param sqlType the SQL type
   * @param defaultValue the optional default value if defined in the model
   * @param migrated true if the column is migrated, false if added
   * @return the SQL code
   */
  String sqlUpdateToNotNull(String tableName, String columnName, SqlType sqlType, Object defaultValue, boolean migrated);

  /**
   * Generates SQL code to change the null-constraint of a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @param nullable true if NULL, else NOT NULL
   * @return the SQL code, null if need {@link #sqlAlterColumnType}
   */
  String sqlAlterColumnNullConstraint(String tableName, String columnName, boolean nullable);

  /**
   * Generates SQL code to change the null-constraint of a column, given its full declaration.<br>
   * Some backends (MySQL, for example) cannot change a single property of a column: the whole
   * declaration must be repeated. Backends that can change the null-constraint on their own
   * simply ignore the type, size, scale, and default.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @param sqlType the JDBC SQL type
   * @param size the optional size
   * @param scale the optional scale
   * @param nullable true if NULL, else NOT NULL
   * @param defaultValue the optional default value
   * @return the SQL code, null if need {@link #sqlAlterColumnType}
   * @see #sqlAlterColumnNullConstraint(String, String, boolean)
   */
  String sqlAlterColumnNullConstraint(String tableName, String columnName, SqlType sqlType, int size, int scale,
                                      boolean nullable, Object defaultValue);

  /**
   * Generates SQL code to change the default value of a column.
   *
   * @param tableName the tablename
   * @param columnName the new column name
   * @param sqlType the JDBC SQL type
   * @param defaultValue the optional default value
   * @return the SQL code, null if need {@link #sqlAlterColumnType}
   */
  String sqlAlterColumnDefault(String tableName, String columnName, SqlType sqlType, Object defaultValue);

  /**
   * Generates SQL code to create the comment for an attribute.
   *
   * @param tableName the table name
   * @param columnName the column name
   * @param comment optional comment, null if none
   * @return the SQL-code, the empty string if comment is created via {@link #sqlCreateColumn}
   */
  String sqlCreateColumnComment(String tableName, String columnName, String comment);

  /**
   * Generates SQL code to alter the comment for an attribute.
   *
   * @param tableName the table name
   * @param columnName the column name
   * @param comment optional comment, null to clear
   * @return the SQL code, null if {@link #sqlRenameAndAlterColumnType} must be used instead
   */
  String sqlAlterColumnComment(String tableName, String columnName, String comment);

  /**
   * Generates the CREATE INDEX statement.
   *
   * @param tableName the table name
   * @param indexName the name of the index
   * @param unique true if the index is unique
   * @param filterCondition the filter condition (null if none)
   * @param columnNames the column names (with leading '-' if descending). Simple function-based indexes are also supported.
   * @return the SQL code
   */
  String sqlCreateIndex(String tableName, String indexName, boolean unique, String filterCondition, String... columnNames);

  /**
   * Generates the ALTER TABLE statement to add a unique constraint.<br>
   * Used instead of {@link #sqlCreateIndex} whenever the index is unique and deferrable
   * (see {@link #isSetConstraintsDeferredSupported()}), since deferrability applies to
   * constraints, not to plain indexes.
   *
   * @param tableName the table name
   * @param constraintName the name of the constraint
   * @param deferrability the deferrability, {@link Deferrability#NOT_DEFERRABLE} for none
   * @param columnNames the column names
   * @return the SQL code
   */
  String sqlCreateUniqueConstraint(String tableName, String constraintName, Deferrability deferrability, String... columnNames);

  /**
   * Gets the column name for the comment of within an index metadata result set.
   *
   * @return the column name, null if not supported by the backend
   */
  String getIndexMetaDataCommentColumnName();

  /**
   * Generates SQL code to create the comment for an index.
   *
   * @param tableName the table name
   * @param indexName the name of the index
   * @param comment optional comment, null if none
   * @return the SQL-code, empty string if not supported by the backend
   */
  String sqlCreateIndexComment(String tableName, String indexName, String comment);

  /**
   * Generates SQL code to alter the comment for an index.
   *
   * @param tableName the table name
   * @param indexName the name of the index
   * @param comment optional comment, null if none
   * @return the SQL-code (the empty string if not supported by the backend)
   */
  String sqlAlterIndexComment(String tableName, String indexName, String comment);

  /**
   * Generates the DROP INDEX statement.
   *
   * @param schemaName the optional schema, null for none
   * @param tableNameWithoutSchema the table name without the schema
   * @param indexName the name of the index
   * @return the SQL code
   */
  String sqlDropIndex(String schemaName, String tableNameWithoutSchema, String indexName);

  /**
   * Generates the ALTER TABLE statement to drop a named constraint (unique or foreign key).
   *
   * @param tableName the table name
   * @param constraintName the name of the constraint
   * @return the SQL code
   */
  String sqlDropConstraint(String tableName, String constraintName);

  /**
   * Enriches previously read index metadata with the deferrability of the unique constraint
   * backing each deferrable unique index, if any.<br>
   * The standard JDBC {@code DatabaseMetaData.getIndexInfo()} result has no deferrability
   * column (unlike {@code getImportedKeys()}), so backends that support
   * {@link #isSetConstraintsDeferredSupported()} must look this up via their own system
   * catalogs. Does nothing by default.
   *
   * @param tableMetaData the table the indexes belong to
   * @param indexesByName the indexes read so far for this table, keyed by index name
   * @param metaData the JDBC database metadata, to get at the connection/catalog if needed
   * @throws SQLException if the lookup fails
   */
  void enrichIndexDeferrability(TableMetaData tableMetaData, Map<String, IndexMetaData> indexesByName,
                                 DatabaseMetaData metaData) throws SQLException;

  /**
   * Generates the DROP TABLE statement.
   *
   * @param schemaName the optional schema, null for none
   * @param tableNameWithoutSchema the table name without the schema
   * @return the SQL code
   */
  String sqlDropTable(String schemaName, String tableNameWithoutSchema);

  /**
   * Generates the ALTER TABLE statement to add a foreign key constraint.
   *
   * @param referencingTableName the name of the referencing foreign table
   * @param referencingColumnName the referencing foreign column name
   * @param referencedTableName the referenced primary table name
   * @param referencedColumnName the referenced primary column name
   * @param foreignKeyName the name of the foreign key
   * @param composite true if the referencing table is a component of the referenced table
   * @param deferrability the deferrability, {@link Deferrability#NOT_DEFERRABLE} for none
   * @return the SQL code
   */
  String sqlCreateForeignKey(String referencingTableName, String referencingColumnName,
                             String referencedTableName, String referencedColumnName,
                             String foreignKeyName, boolean composite, Deferrability deferrability);

  /**
   * Generates the SQL statement to drop a foreign key constraint.
   *
   * @param referencingTableName the name of the referencing foreign table
   * @param foreignKeyName the name of the foreign key
   * @return the SQL code
   */
  String sqlDropForeignKey(String referencingTableName, String foreignKeyName);

  /**
   * Generate SQL code for joining a table with an existing {@code SELECT}.
   *
   * @param type the join type
   * @param addColumns true if columns of the joined table should be added to the result set
   * @param select the original select statement
   * @param joinSelect the select to join with the original SQL or just a tablename
   * @param joinSelectIdAlias extra id alias to add to the joined select, null if none
   * @param joinAlias the alias of the joined select
   * @param join the join clause
   */
  void sqlJoinSelects(JoinType type, boolean addColumns, StringBuilder select,
                      String joinSelect, String joinSelectIdAlias, String joinAlias, String join);

  /**
   * Generate SQL code for joining a table with an existing {@code SELECT}.
   *
   * @param type the join type
   * @param addColumns true if columns of the joined table should be added to the result set
   * @param select the original select statement
   * @param joinSelect the select to join with the original SQL or just a tablename
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
   * Converts the JDBC data type integer plus the backend-specific type name to tentackle SqlTypes.
   * <p>
   * Some drivers report more than one logical type as the same JDBC type: pgjdbc, for example,
   * reports {@code uuid}, {@code json} and {@code jsonb} all as {@link Types#OTHER}, and only the
   * type name tells them apart. Without the name both are possible, which would make the migration
   * mistake one for the other. Backends that do not need the name simply ignore it.
   *
   * @param jdbcType the jdbc data type
   * @param typeName the backend-specific type name; may be null
   * @param size the column size
   * @param scale the column's scale
   * @return the SqlTypes, empty array if no mapping available
   * @see #jdbcTypeToSqlType(int, int, int)
   */
  SqlType[] jdbcTypeToSqlType(int jdbcType, String typeName, int size, int scale);

  /**
   * Gets the table metadata for a given table name.
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
   * @param sql the original SQL code
   * @return the optimized SQL code
   */
  String optimizeSql(String sql);

  /**
   * Reduces a WHERE-clause holding empty sub-expressions.<br>
   * Wurblets generating a finder with optional conditions (see {@code ?<boolean>} wurblet arguments)
   * simply skip the SQL text of conditions that are not applicable. As a result, the SQL may contain
   * empty parentheses and dangling logical operators, for example:
   * <pre>
   *   code=? AND NOT ()
   * </pre>
   * which is reduced to:
   * <pre>
   *   code=?
   * </pre>
   * The reduction removes parenthesized groups without any operand, along with the
   * {@link #SQL_NOT}, {@link #SQL_AND} and {@link #SQL_OR} operators becoming superfluous.
   * If two operators become adjacent because the operand in between was skipped, the first one
   * is removed, i.e., the operand takes its leading operator with it.<br>
   * Only parentheses that are an operand of a logical expression are removed. Parentheses belonging
   * to an operand, such as {@code now()} or {@code id IN()}, are left alone, as is anything within quotes.
   * Only uppercase keywords are treated as operators. If you don't want your statements
   * getting reduced, use lowercase for those keywords.<br>
   * The SQL text must end with the logical expression, i.e., the reduction must be applied
   * before appending an ORDER BY or any other trailing clause. Otherwise, a dangling operator
   * at the end of the expression cannot be detected.
   *
   * @param sql the SQL code; will be modified in place
   */
  void reduceSql(StringBuilder sql);

  /**
   * Reduces a WHERE-clause holding empty sub-expressions.
   *
   * @param sql the original SQL code
   * @return the reduced SQL code
   * @see #reduceSql(StringBuilder)
   */
  String reduceSql(String sql);

  /**
   * Returns whether the backend supports releasing savepoints explicitly.
   * If not, the savepoints are released when the transaction finishs.
   *
   * @return true if the feature is supported, else false
   */
  boolean isReleaseSavepointSupported();

  /**
   * Returns whether the backend supports CLOB types.<br>
   * If not, it will be emulated as VARCHAR with the maximum possible size.
   *
   * @return true if supported, false if treat as varchar
   */
  boolean isClobSupported();

  /**
   * Returns whether the backend supports the JDBC {@link java.sql.SQLXML} binding natively, i.e., a native
   * XML column type plus a driver implementing {@code createSQLXML}/{@code setSQLXML}/{@code getSQLXML}.<br>
   * If not, XML content is emulated as CLOB/VARCHAR.
   *
   * @return true if supported, false if emulated
   */
  boolean isSqlxmlSupported();

  /**
   * Returns whether the backend stores JSON in a native JSON column.<br>
   * If not, JSON content is emulated as CLOB/VARCHAR.
   * <p>
   * Unlike {@link #isSqlxmlSupported()} there is no JDBC binding to rely on, so a backend opting in
   * must also implement {@link #setJson} and, if reading differs from a plain string, {@link #getJson}.
   *
   * @return true if stored natively, false if emulated
   */
  boolean isJsonSupported();

  /**
   * Returns whether the backend supports the {@link java.util.UUID} natively via JDBC setObject/getObject.
   *
   * @return true if supported, false if mapped to VARCHAR(36)
   */
  boolean isUUIDSupported();

  /**
   * Returns whether function-based indexes are supported.
   *
   * @return true if supported
   */
  boolean isFunctionBasedIndexSupported();

  /**
   * Returns whether backend supports filtered indexes.
   *
   * @return true if supported, false if the model will raise an error if a filtered index is used
   */
  boolean isFilteredIndexSupported();

  /**
   * Some database support drop index/constraint/column {@code IF EXISTS}.<br>
   * This may be useful if migration scripts are generated against non-production databases
   * that contain test indexes etc...<br>
   * Please notice that those temporary DDL changes should better use the temporary prefix
   * (see {@link #isTemporaryName(String)}) to be ignored by the migrator.
   *
   * @param dropIfExists true if {@code IF EXISTS} should be used
   */
  void setDropIfExistsEnabled(boolean dropIfExists);

  /**
   * Returns whether backend should add {@code IF EXISTS} for generated DROPs.
   *
   * @return true if {@code IF EXISTS} is turned on and supported, false if not supported or turned off
   */
  boolean isDropIfExistsEnabled();

  /**
   * Returns whether the backend supports deferring constraint checks to the end of a
   * transaction, i.e. {@code DEFERRABLE} constraints plus {@code SET CONSTRAINTS ... DEFERRED}.
   *
   * @return true if supported
   */
  boolean isSetConstraintsDeferredSupported();

  /**
   * Generates the statement to defer or immediately check deferrable constraints for the
   * rest of the current transaction.<br>
   * Only called if {@link #isSetConstraintsDeferredSupported()} returns true.
   *
   * @param deferred true to defer, false to check immediately
   * @return the SQL code
   */
  String sqlSetConstraintsDeferred(boolean deferred);

  /**
   * Converts a backend-specific type name to an internal name used by driver-specific methods.
   *
   * @param sqlTypeName the original type used in SQL scripts
   * @return the JDBC internal name
   */
  String toInternalType(String sqlTypeName);

  /**
   * Returns whether the backend supports the given array operator.
   *
   * @param operator the array operator
   * @return true if supported
   */
  boolean isArrayOperatorSupported(String operator);

  /**
   * Sets an array parameter in a prepared statement.
   *
   * @param statement the prepared statement
   * @param pos the parameter position (starting at 1)
   * @param type the element's SQL type
   * @param elements the elements to be converted to an array parameter
   * @param operator the array operator
   */
  void setArray(PreparedStatement statement, int pos, SqlType type, Collection<?> elements, String operator) throws SQLException;

  /**
   * Sets a JSON parameter in a prepared statement.
   * <p>
   * JDBC has no JSON binding, and the backends disagree on what a native JSON column accepts:
   * Postgres wants an object of type {@link Types#OTHER}, H2 only accepts the UTF-8 bytes (a java
   * string would be stored as a JSON <em>string value</em>), and mysql takes a plain string.
   * The default implementation sets a plain string, which is correct for every backend emulating
   * JSON as a text column.
   *
   * @param statement the prepared statement
   * @param pos the parameter position (starting at 1)
   * @param json the JSON document, null to set SQL NULL
   * @throws SQLException if setting the parameter failed
   * @see #isJsonSupported()
   */
  void setJson(PreparedStatement statement, int pos, String json) throws SQLException;

  /**
   * Gets a JSON parameter from a result set.
   *
   * @param resultSet the result set
   * @param pos the column position (starting at 1)
   * @return the JSON document, null if SQL NULL
   * @throws SQLException if reading the column failed
   * @see #isJsonSupported()
   */
  String getJson(ResultSet resultSet, int pos) throws SQLException;

  /**
   * Returns whether the backend supports schemas.
   *
   * @return true if supported
   * @see #sqlCreateSchema(String)
   */
  boolean isSchemaSupported();

  /**
   * Returns whether the backend supports posix-style escapes via backslash.
   *
   * @return true if supported
   */
  boolean isPosixEscapeSyntaxSupported();

  /**
   * Creates a script runner instance.
   *
   * @param connection the SQL-connection
   */
  ScriptRunner createScriptRunner(Connection connection);

  /**
   * Returns whether the URL denotes an in-memory database.<br>
   * Used by test suites to decide whether to populate the database before running the tests.
   *
   * @param url the JDBC url
   * @return true if in-memory
   */
  boolean isDatabaseInMemory(String url);

}

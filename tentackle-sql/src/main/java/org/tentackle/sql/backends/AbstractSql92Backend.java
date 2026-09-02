/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendPreparedStatement;
import org.tentackle.sql.metadata.Deferrability;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


/**
 * Common to all SQL92 backends.
 *
 * @author harald
 */
public abstract class AbstractSql92Backend extends AbstractBackend {

  /** FOR UPDATE string. */
  public static final String SQL_FOR_UPDATE = " FOR UPDATE";

  /** COALESCE keyword. */
  public static final String SQL_COALESCE = "COALESCE";

  /** array of reserved words. */
  public static final String[] RESERVED_WORDS_SQL92 = new String[] {
    "ABSOLUTE", "ACTION", "ADD", "ALL", "ALLOCATE", "ALTER", "AND", "ANY", "ARE", "AS", "ASC",
    "ASSERTION", "AT", "AUTHORIZATION", "AVG", "BEGIN", "BETWEEN", "BIT", "BIT_LENGTH", "BOTH", "BY",
    "CALL", "CASCADE", "CASCADED", "CASE", "CAST", "CATALOG", "CHAR", "CHARACTER", "CHARACTER_LENGTH",
    "CHAR_LENGTH", "CHECK", "CLOSE", "COALESCE", "COLLATE", "COLLATION", "COLUMN", "COMMENT", "COMMIT",
    "CONDITION", "CONNECT", "CONNECTION", "CONSTRAINT", "CONSTRAINTS", "CONTAINS", "CONTINUE", "CONVERT",
    "CORRESPONDING", "COUNT", "CREATE", "CROSS", "CURRENT", "CURRENT_DATE", "CURRENT_PATH", "CURRENT_TIME",
    "CURRENT_TIMESTAMP", "CURRENT_USER", "CURSOR", "DATE", "DAY", "DEALLOCATE", "DEC", "DECIMAL", "DECLARE",
    "DEFAULT", "DEFERRABLE", "DEFERRED", "DELETE", "DESC", "DESCRIBE", "DESCRIPTOR", "DETERMINISTIC",
    "DIAGNOSTICS", "DISCONNECT", "DISTINCT", "DO", "DOMAIN", "DOUBLE", "DROP", "ELSE", "ELSEIF", "END",
    "ESCAPE", "EXCEPT", "EXCEPTION", "EXEC", "EXECUTE", "EXISTS", "EXIT", "EXTERNAL", "EXTRACT", "FALSE",
    "FETCH", "FIRST", "FLOAT", "FOR", "FOREIGN", "FOUND", "FROM", "FULL", "FUNCTION", "GET", "GLOBAL", "GO",
    "GOTO", "GRANT", "GROUP", "HANDLER", "HAVING", "HOUR", "IDENTITY", "IF", "IMMEDIATE", "IN", "INDICATOR",
    "INITIALLY", "INNER", "INOUT", "INPUT", "INSENSITIVE", "INSERT", "INT", "INTEGER", "INTERSECT",
    "INTERVAL", "INTO", "IS", "ISOLATION", "JOIN", "KEY", "LANGUAGE", "LAST", "LEADING", "LEAVE",
    "LEFT", "LEVEL", "LIKE", "LOCAL", "LOOP", "LOWER", "MATCH", "MAX", "MIN", "MINUTE", "MODULE",
    "MONTH", "NAMES", "NATIONAL", "NATURAL", "NCHAR", "NEXT", "NO", "NOT", "NULL", "NULLIF", "NUMERIC",
    "OCTET_LENGTH", "OF", "ON", "ONLY", "OPEN", "OPTION", "OR", "ORDER", "OUT", "OUTER", "OUTPUT",
    "OVERLAPS", "PAD", "PARAMETER", "PARTIAL", "PATH", "POSITION", "PRECISION", "PREPARE", "PRESERVE",
    "PRIMARY", "PRIOR", "PRIVILEGES", "PROCEDURE", "PUBLIC", "READ", "REAL", "REFERENCES", "RELATIVE",
    "REPEAT", "RESIGNAL", "RESTRICT", "RETURN", "RETURNS", "REVOKE", "RIGHT", "ROLLBACK", "ROUTINE",
    "ROWS", "SCHEMA", "SCROLL", "SECOND", "SECTION", "SELECT", "SESSION", "SESSION_USER", "SET", "SIGNAL",
    "SIZE", "SMALLINT", "SOME", "SPACE", "SPECIFIC", "SQL", "SQLCODE", "SQLERROR", "SQLEXCEPTION",
    "SQLSTATE", "SQLWARNING", "SUBSTRING", "SUM", "SYSTEM_USER", "TABLE", "TEMPORARY", "THEN", "TIME",
    "TIMESTAMP", "TIMEZONE_HOUR", "TIMEZONE_MINUTE", "TO", "TRAILING", "TRANSACTION", "TRANSLATE",
    "TRANSLATION", "TRIM", "TRUE", "UNDO", "UNION", "UNIQUE", "UNKNOWN", "UNTIL", "UPDATE", "UPPER",
    "USAGE", "USER", "USING", "VALUE", "VALUES", "VARCHAR", "VARYING", "VIEW", "WHEN",
    "WHENEVER", "WHERE", "WHILE", "WITH", "WORK", "WRITE", "YEAR", "ZONE"
  };


  private Set<String> reservedWords;      // set of reserved words (built only once)


  /**
   * Parent constructor.
   */
  public AbstractSql92Backend() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public synchronized Set<String> getReservedWords() {
    if (reservedWords == null) {
      reservedWords = new HashSet<>(Arrays.asList(RESERVED_WORDS_SQL92));
    }
    return reservedWords;
  }

  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (limit > 0) {
      throw new BackendException("backend " + this + " does not support selects with LIMIT clause");
    }
    if (offset > 0) {
      throw new BackendException("backend " + this + " does not support selects with OFFSET clause");
    }
    if (isLeadingSelectMissing(sqlBuilder)) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
  }

  /**
   * Determines whether {@link #buildSelectSql(StringBuilder, boolean, int, int)} must prepend a <code>"SELECT "</code>.
   *
   * @param sqlBuilder the sql builder
   * @return true if leading select keyword is missing
   */
  protected boolean isLeadingSelectMissing(StringBuilder sqlBuilder) {
    // does not start with SELECT or WITH
    String sql = sqlBuilder.toString().toUpperCase(Locale.ROOT);
    return !sql.startsWith(SQL_SELECT) && !sql.startsWith("WITH ");
  }

  @Override
  public int setLeadingSelectParameters(BackendPreparedStatement stmt, int limit, int offset) {
    if (limit > 0) {
      throw new BackendException("backend " + this + " does not support selects with LIMIT clause");
    }
    if (offset > 0) {
      throw new BackendException("backend " + this + " does not support selects with OFFSET clause");
    }
    return 1;
  }

  @Override
  public int setTrailingSelectParameters(BackendPreparedStatement stmt, int index, int limit, int offset) {
    if (limit > 0) {
      throw new BackendException("backend " + this + " does not support selects with LIMIT clause");
    }
    if (offset > 0) {
      throw new BackendException("backend " + this + " does not support selects with OFFSET clause");
    }
    return index;
  }

  @Override
  public String getCoalesceKeyword() {
    return SQL_COALESCE;
  }

  @Override
  public String sqlCreateForeignKey(String referencingTableName, String referencingColumnName, String referencedTableName,
          String referencedColumnName, String foreignKeyName, boolean composite, Deferrability deferrability) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(referencingTableName);
    buf.append(" ADD CONSTRAINT ");
    buf.append(foreignKeyName);
    buf.append(" FOREIGN KEY (");
    buf.append(referencingColumnName);
    buf.append(") REFERENCES ");
    buf.append(referencedTableName);
    buf.append(" (");
    buf.append(referencedColumnName);
    buf.append(")");
    if (composite) {
      buf.append(" ON DELETE CASCADE");
    }
    if (deferrability != null && deferrability.isDeferrable() && isSetConstraintsDeferredSupported()) {
      buf.append(' ').append(deferrability.getSql());
    }
    buf.append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  @Override
  public String sqlCreateUniqueConstraint(String tableName, String constraintName, Deferrability deferrability, String... columnNames) {
    StringBuilder buf = new StringBuilder(SQL_ALTER_TABLE);
    buf.append(tableName);
    buf.append(" ADD CONSTRAINT ");
    buf.append(constraintName);
    buf.append(" UNIQUE (");
    boolean needsComma = false;
    for (String columnName : columnNames) {
      if (needsComma) {
        buf.append(", ");
      }
      buf.append(columnName);
      needsComma = true;
    }
    buf.append(')');
    if (deferrability != null && deferrability.isDeferrable() && isSetConstraintsDeferredSupported()) {
      buf.append(' ').append(deferrability.getSql());
    }
    buf.append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  @Override
  public String sqlDropForeignKey(String referencingTableName, String foreignKeyName) {
    return sqlDropConstraint(referencingTableName, foreignKeyName);
  }

  @Override
  public String sqlDropConstraint(String tableName, String constraintName) {
    StringBuilder buf = new StringBuilder();
    buf.append(SQL_ALTER_TABLE).append(tableName).append(" DROP CONSTRAINT ");
    if (isDropIfExistsEnabled()) {
      buf.append(SQL_IF_EXISTS);
    }
    buf.append(constraintName).append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  @Override
  public boolean isSequenceSupported() {
    return false;
  }

  @Override
  public String sqlCreateSequence(String name, Long start, Long increment) {
    throw new BackendException(this + " does not support sequences");
  }

  @Override
  public String sqlCreateSequenceComment(String name, String comment) {
    return "";
  }

  @Override
  public String sqlNextFromSequence(String name) {
    throw new BackendException(this + " does not support sequences");
  }

}

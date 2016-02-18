/*
 * Tentackle - http://www.tentackle.org.
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

import java.util.Arrays;
import java.util.HashSet;
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
    sqlBuilder.insert(0, SQL_SELECT);
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
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
          String referencedColumnName, String foreignKeyName, boolean composite) {
    StringBuilder buf = new StringBuilder("ALTER TABLE ");
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
    buf.append(";\n");
    return buf.toString();
  }

  @Override
  public String sqlDropForeignKey(String referencingTableName, String foreignKeyName) {
    return  "ALTER TABLE " + referencingTableName +
            " DROP CONSTRAINT " +
            foreignKeyName +
            ";\n";
  }

  @Override
  public boolean supportsSequences() {
    return false;
  }

  @Override
  public String sqlNextFromSequene(String name) {
    throw new BackendException(this + " does not support sequences");
  }

}

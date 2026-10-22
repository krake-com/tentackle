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

import org.tentackle.sql.BackendPreparedStatement;
import org.tentackle.sql.NonStandardCommons;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Common to all SQL2003 backends.
 * <p>
 * Paging is implemented by numbering the rows of the original select with the window function
 * {@code ROW_NUMBER()} and filtering on that number in an enclosing select.
 *
 * @author harald
 */
public abstract class AbstractSql2003Backend extends AbstractSql92Backend {

  /** window function part 1: inserted after the leading SELECT. */
  public static final String SQL_WINDOW_1 = "* FROM (SELECT F_O_O.*, ROW_NUMBER() OVER() AS R_O_W FROM (SELECT ";

  /** window function part 2: appended to the original select. */
  public static final String SQL_WINDOW_2 = ") AS F_O_O) AS B_A_R WHERE ";

  /** condition for the limit (upper row number bound). */
  public static final String SQL_WINDOW_LIMIT = "R_O_W <= ?";

  /** condition for the offset (lower row number bound). */
  public static final String SQL_WINDOW_OFFSET = "R_O_W > ?";


  /** array of additional reserved words. */
  public static final String[] RESERVED_WORDS_SQL2003 = new String[] {
    "ARRAY", "ASENSITIVE", "ATOMIC", "BIGINT", "BLOB", "BOOLEAN", "CALLED",
    "CLOB", "CUBE", "CYCLE", "DEREF", "DYNAMIC", "EACH", "ELEMENT", "FILTER", "ITERATE", "LARGE",
    "LATERAL", "LOCALTIME", "LOCALTIMESTAMP", "MEMBER", "MERGE", "MULTISET", "NCLOB", "NEW",
    "NONE", "OVER", "PARTITION", "RANGE", "RECURSIVE", "REF", "REFERENCING", "RELEASE", "RESULT",
    "ROLLUP", "ROW", "SAVEPOINT", "SCOPE", "SEARCH", "SENSITIVE", "SIMILAR", "SPECIFICTYPE",
    "START", "SYMMETRIC", "SYSTEM", "TABLESAMPLE", "TREAT", "TRIGGER", "UNNEST", "WINDOW", "WITHOUT"
  };

  /** set of reserved words (built only once). */
  private Set<String> reservedWords;


  /**
   * Parent constructor.
   */
  public AbstractSql2003Backend() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public synchronized Set<String> getReservedWords() {
    if (reservedWords == null) {
      reservedWords = new HashSet<>(super.getReservedWords());
      reservedWords.addAll(Arrays.asList(RESERVED_WORDS_SQL2003));
    }
    return reservedWords;
  }

  /**
   * {@inheritDoc}
   * <p>
   * With a limit or offset, the original select is wrapped into an enclosing select that filters by row number.
   * A write lock cannot be combined with a limit or offset, since most databases refuse
   * {@code FOR UPDATE} within a nested select.
   */
  @Override
  public void buildSelectSql(StringBuilder sqlBuilder, boolean writeLock, int limit, int offset) {
    if (limit > 0 || offset > 0) {
      removeLeadingSelect(sqlBuilder);
      sqlBuilder.insert(0, SQL_WINDOW_1).insert(0, SQL_SELECT);
    }
    else if (isLeadingSelectMissing(sqlBuilder)) {
      sqlBuilder.insert(0, SQL_SELECT);
    }
    if (writeLock) {
      sqlBuilder.append(SQL_FOR_UPDATE);
    }
    if (limit > 0 || offset > 0) {
      sqlBuilder.append(SQL_WINDOW_2);
      if (offset > 0) {
        sqlBuilder.append(SQL_WINDOW_OFFSET);
      }
      if (limit > 0) {
        if (offset > 0) {
          sqlBuilder.append(SQL_AND);
        }
        sqlBuilder.append(SQL_WINDOW_LIMIT);
      }
    }
  }

  @Override
  public int setLeadingSelectParameters(BackendPreparedStatement stmt, int limit, int offset) {
    return 1;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Because the limit is applied as an upper bound of the row number, it is bound as {@code offset + limit}.
   */
  @Override
  public int setTrailingSelectParameters(BackendPreparedStatement stmt, int index, int limit, int offset) {
    if (offset > 0) {
      stmt.setInt(index++, offset);
    }
    if (limit > 0) {
      stmt.setInt(index++, offset > 0 ? offset + limit : limit);
    }
    return index;
  }

  @Override
  public boolean isSequenceSupported() {
    return true;
  }

  @Override
  public String sqlCreateSequence(String name, Long start, Long increment) {
    return NonStandardCommons.sqlCreateSequence(name, start, increment);
  }

  @Override
  public String sqlNextFromSequence(String name) {
    return "SELECT NEXT VALUE FOR " + name;
  }

}

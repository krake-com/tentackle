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

package org.tentackle.dbms;

import java.util.Objects;

/**
 * The statement key.
 * <p>
 * The key describes the statement within its usage scope, i.e., the StatementId and using class.
 * The using class is necessary to locate the prepared statement because a select method may be
 * implemented in a superclass of the actual class using the statement, which may result in a
 * different SQL string.
 * <p>
 * As an alternative, an SQL string may express a statement key directly.
 * In this case, no using class is required. Notice that the SQL string must be
 * exactly the string sent to the database backend! If there are different backends
 * with different SQLs, each SQL must get its own statement key.
 *
 * @author harald
 */
public class StatementKey {

  /**
   * The global statement id.
   */
  private final StatementId statementId;

  /**
   * The persistent object class.
   */
  private final Class<?> usingClass;

  /**
   * Usage variant for embedded PDOs.
   */
  private final String variant;

  /**
   * The SQL string.
   */
  private final String sql;


  /**
   * Creates the statement key.
   *
   * @param statementId the global statement id
   * @param usingClass the using class
   * @param variant the optional usage variant, null if none
   */
  public StatementKey(StatementId statementId, Class<?> usingClass, String variant) {
    this.statementId = Objects.requireNonNull(statementId, "statementId");
    this.usingClass = Objects.requireNonNull(usingClass, "usingClass");
    this.variant = variant;
    this.sql = null;
  }

  /**
   * Creates the statement key.
   *
   * @param statementId the global statement id
   * @param usingClass the using class
   */
  public StatementKey(StatementId statementId, Class<?> usingClass) {
    this(statementId, usingClass, null);
  }

  /**
   * Creates a statement key.
   *
   * @param sql the SQL string
   */
  public StatementKey(String sql) {
    this.statementId = null;
    this.usingClass = null;
    this.variant = null;
    if (Objects.requireNonNull(sql, "sql").isEmpty()) {
      throw new IllegalArgumentException("empty sql");
    }
    this.sql = sql;
  }

  /**
   * Gets the statement id.
   *
   * @return the id, null if SQL key
   */
  public StatementId getStatementId() {
    return statementId;
  }

  /**
   * Gets the using class.
   *
   * @return the class, null if SQL key
   */
  public Class<?> getUsingClass() {
    return usingClass;
  }

  /**
   * Gets the usage variant.
   *
   * @return the variant, null if none
   */
  public String getVariant() {
    return variant;
  }

  /**
   * Gets the SQL string.
   *
   * @return the SQL, null if statement id key
   */
  public String getSql() {
    return sql;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append('<');
    if (sql != null) {
      buf.append(sql);
    }
    else {
      buf.append(usingClass.getSimpleName());
      if (variant != null) {
        buf.append('/').append(variant);
      }
      buf.append('#').append(statementId);
    }
    buf.append('>');
    return buf.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StatementKey that = (StatementKey) o;

    if (!Objects.equals(statementId, that.statementId)) return false;
    if (!Objects.equals(usingClass, that.usingClass)) return false;
    if (!Objects.equals(variant, that.variant)) return false;
    return Objects.equals(sql, that.sql);
  }

  @Override
  public int hashCode() {
    int result = statementId != null ? statementId.hashCode() : 0;
    result = 31 * result + (usingClass != null ? usingClass.hashCode() : 0);
    result = 31 * result + (variant != null ? variant.hashCode() : 0);
    result = 31 * result + (sql != null ? sql.hashCode() : 0);
    return result;
  }

}

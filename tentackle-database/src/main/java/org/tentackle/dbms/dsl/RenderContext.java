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

package org.tentackle.dbms.dsl;

import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Collects the SQL-code and the parameters while an {@link SqlElement} is rendered.
 * <p>
 * The parameters are collected in the order of the question marks emitted into the SQL-code.
 * Notice that a single parameter may span more than one question mark, because some
 * {@link org.tentackle.sql.DataType}s are mapped to more than one column
 * (see {@link org.tentackle.dbms.Query#applyParameters}).
 *
 * @author harald
 */
public class RenderContext {

  private final Backend backend;              // the backend the SQL-code is rendered for
  private final List<Object> parameters;      // the collected parameters
  private StringBuilder sql;                  // the collected SQL-code


  /**
   * Creates a render context.
   *
   * @param backend the backend to render the SQL-code for
   */
  public RenderContext(Backend backend) {
    this.backend = Objects.requireNonNull(backend, "backend must be provided");
    this.sql = new StringBuilder();
    this.parameters = new ArrayList<>();
  }


  /**
   * Gets the backend the SQL-code is rendered for.
   *
   * @return the backend
   */
  public Backend getBackend() {
    return backend;
  }

  /**
   * Appends SQL-code.
   *
   * @param sqlCode the SQL-code
   * @return this context
   */
  public RenderContext append(CharSequence sqlCode) {
    sql.append(sqlCode);
    return this;
  }

  /**
   * Appends a single character of SQL-code.
   *
   * @param sqlChar the character
   * @return this context
   */
  public RenderContext append(char sqlChar) {
    sql.append(sqlChar);
    return this;
  }

  /**
   * Appends a comma separating two elements of a list.
   *
   * @return this context
   */
  public RenderContext appendComma() {
    sql.append(Backend.SQL_COMMA).append(' ');
    return this;
  }

  /**
   * Registers a parameter.<br>
   * The parameter is applied to the prepared statement at the position of the
   * question mark(s) emitted for it.
   *
   * @param parameter the parameter value, never null
   * @return this context
   */
  public RenderContext addParameter(Object parameter) {
    if (parameter == null) {
      // the statement cannot tell the backend the SQL-type of an untyped null
      throw new PersistenceException("null is not a valid parameter, use isNull() or isNotNull() instead");
    }
    parameters.add(parameter);
    return this;
  }

  /**
   * Renders an element into this context.
   *
   * @param element the element
   * @return this context
   */
  public RenderContext render(SqlElement element) {
    element.render(this);
    return this;
  }

  /**
   * Renders an element to a string.<br>
   * The SQL-code is not appended to this context, but the parameters are registered
   * as if the element was rendered in place. Useful for the {@link Backend}-methods
   * expecting SQL-fragments as strings, such as {@link Backend#sqlJoin} or {@link Backend#sqlFunction}.
   *
   * @param element the element
   * @return the rendered SQL-code
   */
  public String renderToString(SqlElement element) {
    StringBuilder outerSql = sql;
    sql = new StringBuilder();
    try {
      element.render(this);
      return sql.toString();
    }
    finally {
      sql = outerSql;
    }
  }

  /**
   * Gets the rendered SQL-code.
   *
   * @return the SQL-code
   */
  public String getSql() {
    return sql.toString();
  }

  /**
   * Gets the collected parameters.
   *
   * @return the unmodifiable list of parameters, in the order of the question marks
   */
  public List<Object> getParameters() {
    return Collections.unmodifiableList(parameters);
  }

  @Override
  public String toString() {
    return sql.toString();
  }

}

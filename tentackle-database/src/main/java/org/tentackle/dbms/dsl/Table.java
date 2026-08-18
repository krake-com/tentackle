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
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;

import java.util.Objects;

/**
 * A table or a derived table (i.e. a sub-select in the {@code FROM}-clause).
 * <p>
 * Tables are immutable. {@link #as} creates an aliased copy, which is what
 * self-joins need:
 * <pre>
 *   Table parent = TABLE.as("p");
 *   Table child  = TABLE.as("c");
 * </pre>
 *
 * @author harald
 */
public class Table implements SqlElement {

  private final String name;      // the table name, null for derived tables
  private final Select select;    // the sub-select, null for named tables
  private final String alias;     // the table alias, may be null for named tables


  /**
   * Creates a table.
   *
   * @param name the table name, optionally qualified by a schema name
   * @param alias the table alias, null if none
   */
  public Table(String name, String alias) {
    this.name = Identifiers.check(name, "table name");
    this.alias = alias == null ? null : Identifiers.check(alias, "table alias");
    this.select = null;
  }

  /**
   * Creates a derived table.
   *
   * @param select the sub-select
   * @param alias the table alias, mandatory for derived tables
   */
  public Table(Select select, String alias) {
    this.select = Objects.requireNonNull(select, "sub-select must be provided");
    this.alias = Identifiers.check(alias, "table alias");
    this.name = null;
  }


  /**
   * Creates an aliased copy of this table.
   *
   * @param alias the table alias
   * @return the aliased table
   */
  public Table as(String alias) {
    return name == null ? new Table(select, alias) : new Table(name, alias);
  }

  /**
   * Gets the table name.
   *
   * @return the name, null for derived tables
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the sub-select of a derived table.
   *
   * @return the sub-select, null for named tables
   */
  public Select getSelect() {
    return select;
  }

  /**
   * Gets the table alias.
   *
   * @return the alias, null if none
   */
  public String getAlias() {
    return alias;
  }

  /**
   * Gets the qualifier the columns of this table are prefixed with.
   *
   * @return the alias, or the table name if there is no alias
   */
  public String getQualifier() {
    return alias == null ? name : alias;
  }


  /**
   * Creates a typed field of this table.
   *
   * @param name the column name
   * @param type the java type of the column
   * @param <T> the java type of the column
   * @return the field
   */
  public <T> Field<T> field(String name, Class<T> type) {
    DataType<T> dataType = DataTypeFactory.getInstance().get(
        Objects.requireNonNull(type, "type must be provided"));
    if (dataType == null) {
      throw new PersistenceException("no datatype registered for " + type.getName() +
                                     ", use field(String) for an untyped field");
    }
    return new Field<>(this, name, dataType);
  }

  /**
   * Creates a typed field of this table.
   *
   * @param name the column name
   * @param dataType the datatype of the column
   * @param <T> the java type of the column
   * @return the field
   */
  public <T> Field<T> field(String name, DataType<T> dataType) {
    return new Field<>(this, name, Objects.requireNonNull(dataType, "datatype must be provided"));
  }

  /**
   * Creates an untyped field of this table.<br>
   * Untyped fields span a single column and cannot be compared to values,
   * because the datatype of a value is determined by its class.
   *
   * @param name the column name
   * @return the field
   */
  public Field<Object> field(String name) {
    return new Field<>(this, name, null);
  }

  /**
   * Creates an expression for all columns of this table.
   *
   * @return the expression rendered as {@code <table>.*}
   */
  public Expression<Object> asterisk() {
    return new RawExpression<>(getQualifier() + Backend.SQL_DOT_STAR, null);
  }


  /**
   * Creates the SQL-code of the table itself, i.e. without the alias.
   *
   * @param context the render context
   * @return the SQL-code
   */
  String createNameSql(RenderContext context) {
    return name != null ? name : "(" + context.renderToString(select) + ")";
  }

  @Override
  public void render(RenderContext context) {
    context.append(createNameSql(context));
    if (alias != null) {
      context.append(context.getBackend().sqlAsBeforeTableAlias()).append(alias);
    }
  }

  @Override
  public String toString() {
    return name != null ? (alias == null ? name : name + " " + alias) : "(select) " + alias;
  }

}

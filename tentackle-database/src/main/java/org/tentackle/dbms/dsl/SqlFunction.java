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

import org.tentackle.dbms.SqlSupplier;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * An SQL function or aggregate.
 * <p>
 * The arguments must span a single column each, and the result of a function
 * is always a single column as well.
 *
 * @param <T> the java type of the result
 * @author harald
 */
public class SqlFunction<T> implements Expression<T> {

  private static final String SQL_DISTINCT = "DISTINCT ";

  private final String name;                    // the function name, null if backend-specific
  private final SqlSupplier nameSupplier;       // supplies the function name
  private final DataType<T> dataType;           // the datatype of the result, may be null
  private final boolean distinct;               // true to apply the function to distinct values only
  private final List<Expression<?>> arguments;  // the arguments


  /**
   * Creates a function.
   *
   * @param name the function name
   * @param dataType the datatype of the result, null if untyped
   * @param distinct true to apply the function to distinct values only
   * @param arguments the arguments
   */
  public SqlFunction(String name, DataType<T> dataType, boolean distinct, List<Expression<?>> arguments) {
    this.name = Identifiers.check(name, "function name");
    this.nameSupplier = backend -> name;
    this.dataType = dataType;
    this.distinct = distinct;
    this.arguments = List.copyOf(arguments);
  }

  /**
   * Creates a function with a backend-specific name, such as {@code COALESCE} vs. {@code NVL}.
   *
   * @param nameSupplier the supplier for the function name
   * @param dataType the datatype of the result, null if untyped
   * @param distinct true to apply the function to distinct values only
   * @param arguments the arguments
   */
  public SqlFunction(SqlSupplier nameSupplier, DataType<T> dataType, boolean distinct, List<Expression<?>> arguments) {
    this.nameSupplier = Objects.requireNonNull(nameSupplier, "name supplier must be provided");
    this.name = null;
    this.dataType = dataType;
    this.distinct = distinct;
    this.arguments = List.copyOf(arguments);
  }

  /**
   * Creates a function.
   *
   * @param name the function name
   * @param dataType the datatype of the result, null if untyped
   * @param arguments the arguments
   */
  public SqlFunction(String name, DataType<T> dataType, Expression<?>... arguments) {
    this(name, dataType, false, List.of(arguments));
  }


  /**
   * Gets the function name.
   *
   * @return the name, null if backend-specific
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the arguments.
   *
   * @return the unmodifiable list of arguments
   */
  public List<Expression<?>> getArguments() {
    return arguments;
  }

  /**
   * Returns whether the function is applied to distinct values only.
   *
   * @return true if distinct
   */
  public boolean isDistinct() {
    return distinct;
  }

  @Override
  public DataType<T> getDataType() {
    return dataType;
  }

  @Override
  public int getColumnCount(Backend backend) {
    return 1;
  }

  @Override
  public void render(RenderContext context, int columnIndex) {
    Backend backend = context.getBackend();
    String functionName = nameSupplier.get(backend).toString();
    StringJoiner argumentSql = new StringJoiner(Backend.SQL_COMMA + " ");

    for (Expression<?> argument : arguments) {
      if (argument.getColumnCount(backend) != 1) {
        throw new PersistenceException("argument of " + functionName + "() spans more than one column: " +
                                       argument.getDataType());
      }
      // renders in place, i.e. the parameters are registered in the order of the arguments
      argumentSql.add(context.renderToString(argument));
    }

    context.append(backend.sqlFunction(functionName,
                                       distinct ? SQL_DISTINCT + argumentSql : argumentSql.toString()));
  }

}

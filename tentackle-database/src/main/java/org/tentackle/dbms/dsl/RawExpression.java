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
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;

import java.util.List;
import java.util.Objects;

/**
 * An expression given as plain SQL-code.
 * <p>
 * The escape hatch for expressions the DSL does not cover.
 * The application is responsible for providing one parameter per question mark,
 * according to the number of columns of the parameter's datatype.
 * <p>
 * A raw expression always spans a single column, whatever its datatype is,
 * because the DSL cannot tell the columns of the SQL-code apart.
 *
 * @param <T> the java type of the expression
 * @author harald
 */
public class RawExpression<T> implements Expression<T> {

  private final SqlSupplier sqlSupplier;      // the SQL-code
  private final DataType<T> dataType;         // the datatype, may be null if untyped
  private final List<Object> parameters;      // the parameters, one per question mark


  /**
   * Creates an expression from backend-specific SQL-code.
   *
   * @param sqlSupplier the supplier for the SQL-code
   * @param dataType the datatype, null if untyped
   * @param parameters the parameters, in the order of the question marks
   */
  public RawExpression(SqlSupplier sqlSupplier, DataType<T> dataType, Object... parameters) {
    this.sqlSupplier = Objects.requireNonNull(sqlSupplier, "SQL-supplier must be provided");
    this.dataType = dataType;
    this.parameters = List.of(parameters);
  }

  /**
   * Creates an expression from SQL-code.
   *
   * @param sql the SQL-code
   * @param dataType the datatype, null if untyped
   * @param parameters the parameters, in the order of the question marks
   */
  public RawExpression(CharSequence sql, DataType<T> dataType, Object... parameters) {
    this(backend -> sql, dataType, parameters);
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
    context.append(sqlSupplier.get(context.getBackend()));
    for (Object parameter : parameters) {
      context.addParameter(parameter);
    }
  }

}

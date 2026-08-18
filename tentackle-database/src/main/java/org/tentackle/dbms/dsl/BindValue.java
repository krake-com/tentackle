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

import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;

import java.util.Objects;

/**
 * A value bound to the prepared statement.
 * <p>
 * The value never becomes part of the SQL code but is applied to the statement
 * by {@link org.tentackle.dbms.Query#applyParameters}. Since a value may span more than
 * one column, a question mark is emitted per column, but the value is registered only once.
 *
 * @param <T> the java type of the value
 * @author harald
 */
public class BindValue<T> implements Expression<T> {

  private final T value;                // the value, never null
  private final DataType<T> dataType;   // the datatype; may be null if unknown


  /**
   * Creates a bind value.
   *
   * @param value the value, never null
   * @param dataType the datatype, null to determine it from the value's class
   */
  public BindValue(T value, DataType<T> dataType) {
    this.value = Objects.requireNonNull(value, "value must be provided, use isNull() or isNotNull() instead");
    this.dataType = dataType;
  }


  /**
   * Gets the value.
   *
   * @return the value, never null
   */
  public T getValue() {
    return value;
  }

  @Override
  public DataType<T> getDataType() {
    return dataType;
  }

  @Override
  public void render(RenderContext context, int columnIndex) {
    if (columnIndex == 0) {
      // the parameter is applied to as many question marks as its datatype spans columns
      context.addParameter(value);
    }
    context.append(Backend.SQL_PAR);
  }

}

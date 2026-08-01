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
 * A sub-select used as a scalar expression.
 *
 * @param <T> the java type of the single column returned by the sub-select
 * @author harald
 */
public class SubSelect<T> implements Expression<T> {

  private final Select select;            // the sub-select
  private final DataType<T> dataType;     // the datatype, may be null if untyped


  /**
   * Creates a scalar sub-select.
   *
   * @param select the sub-select returning a single row and column
   * @param dataType the datatype, null if untyped
   */
  public SubSelect(Select select, DataType<T> dataType) {
    this.select = Objects.requireNonNull(select, "sub-select must be provided");
    this.dataType = dataType;
  }


  /**
   * Gets the sub-select.
   *
   * @return the sub-select
   */
  public Select getSelect() {
    return select;
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
    context.append('(');
    select.render(context);
    context.append(')');
  }

}

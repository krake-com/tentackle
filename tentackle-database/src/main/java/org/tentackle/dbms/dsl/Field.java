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

/**
 * A column of a table.
 * <p>
 * The name of a field is the column name as defined in the model, i.e. without the
 * suffix of the second and following columns of a multi-column datatype
 * (see {@link DataType#getColumnSuffix}). The suffixes are added while rendering.
 *
 * @param <T> the java type of the column
 * @author harald
 */
public class Field<T> implements Expression<T> {

  private final Table table;              // the table, null if unqualified
  private final String name;              // the column name
  private final DataType<T> dataType;     // the datatype, may be null if untyped


  /**
   * Creates a field.
   *
   * @param table the table, null if unqualified
   * @param name the column name
   * @param dataType the datatype, null if untyped
   */
  public Field(Table table, String name, DataType<T> dataType) {
    this.table = table;
    this.name = Identifiers.check(name, "column name");
    this.dataType = dataType;
  }


  /**
   * Gets the table this field belongs to.
   *
   * @return the table, null if unqualified
   */
  public Table getTable() {
    return table;
  }

  /**
   * Gets the column name.
   *
   * @return the name, without the suffix of multi-column datatypes
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the name of one of the columns of this field.
   *
   * @param backend the backend
   * @param columnIndex the column index, 0 for the first column
   * @return the column name, suffixed for the second and following columns
   */
  public String getColumnName(Backend backend, int columnIndex) {
    return dataType == null ? name : name + dataType.getColumnSuffix(backend, columnIndex).orElse("");
  }

  @Override
  public DataType<T> getDataType() {
    return dataType;
  }

  @Override
  public void render(RenderContext context, int columnIndex) {
    if (table != null) {
      context.append(table.getQualifier()).append('.');
    }
    context.append(getColumnName(context.getBackend(), columnIndex));
  }

}

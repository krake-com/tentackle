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

import java.util.Objects;

/**
 * An expression of the {@code ORDER BY}-clause.
 * <p>
 * Expressions spanning more than one column are sorted by their sortable columns
 * in the order defined by the datatype (see {@link DataType#getSortableColumns()}).
 * Expressions of a datatype that cannot be sorted at all, such as {@code UUID}, are rejected.
 * Single-column results of functions, such as the {@code SUM} of a multi-column datatype, can always be sorted.
 *
 * @author harald
 */
public class SortField implements SqlElement {

  private final Expression<?> expression;     // the sorted expression
  private final SortOrder sortOrder;          // the sort order


  /**
   * Creates a sort field.
   *
   * @param expression the sorted expression
   * @param sortOrder the sort order
   */
  public SortField(Expression<?> expression, SortOrder sortOrder) {
    this.expression = Objects.requireNonNull(expression, "expression must be provided");
    this.sortOrder = Objects.requireNonNull(sortOrder, "sort order must be provided");
  }


  /**
   * Gets the sorted expression.
   *
   * @return the expression
   */
  public Expression<?> getExpression() {
    return expression;
  }

  /**
   * Gets the sort order.
   *
   * @return the sort order
   */
  public SortOrder getSortOrder() {
    return sortOrder;
  }


  @Override
  public void render(RenderContext context) {
    Backend backend = context.getBackend();
    int columnCount = expression.getColumnCount(backend);
    DataType<?> dataType = expression.getDataType();
    int[] sortableColumns = null;

    if (dataType != null && dataType.getColumnCount(backend) == columnCount) {
      // the expression maps the columns of its datatype, e.g. a field (but not a function result)
      sortableColumns = dataType.getSortableColumns();
      if (sortableColumns == null || sortableColumns.length == 0) {
        throw new PersistenceException(dataType + " cannot be sorted");
      }
    }

    if (columnCount == 1) {
      expression.render(context, 0);
      context.append(sortOrder.getSql());
    }
    else {
      boolean needComma = false;
      for (int columnIndex : sortableColumns) {
        if (needComma) {
          context.appendComma();
        }
        else {
          needComma = true;
        }
        expression.render(context, columnIndex);
        context.append(sortOrder.getSql());
      }
    }
  }

}

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
 *
 */

package org.tentackle.fx.table.edit;

import org.tentackle.fx.table.TableColumnConfiguration;

/**
 * Addresses a cell by its row object and column.<br>
 * In contrast to a row index, the row object remains valid if rows are added, removed or sorted.
 * The {@link TableEditContext} compares the row objects by identity.
 *
 * @param <S> type of the objects contained within the table's item list
 * @param row the row object
 * @param column the column configuration
 * @author harald
 */
public record TableCellRef<S>(S row, TableColumnConfiguration<S,?> column) {

  /**
   * Gets the name of the column.
   *
   * @return the column name, i.e. the binding path
   */
  public String columnName() {
    return column.getName();
  }

}

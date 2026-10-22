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

/**
 * A value entered in a table cell.
 *
 * @param <S> type of the objects contained within the table's item list
 * @param <T> type of the cell's value
 * @param cell the edited cell
 * @param oldValue the model value when editing started
 * @param newValue the entered value
 * @param trigger the reason why the value was committed
 * @author harald
 */
public record TableCellEdit<S,T>(TableCellRef<S> cell, T oldValue, T newValue, TableEditTrigger trigger) {

  /**
   * Gets the row object of the edited cell.
   *
   * @return the row object
   */
  public S row() {
    return cell.row();
  }

  /**
   * Gets the name of the edited cell's column.
   *
   * @return the column name, i.e. the binding path
   */
  public String columnName() {
    return cell.columnName();
  }

  /**
   * Gets the entered value with its type.<br>
   * Convenient for handlers that receive the edit with a wildcard value type.
   *
   * @param type the value's type
   * @param <V> the value's type
   * @return the entered value
   */
  public <V> V newValue(Class<V> type) {
    return type.cast(newValue);
  }

  /**
   * Gets the model value when editing started with its type.
   *
   * @param type the value's type
   * @param <V> the value's type
   * @return the old value
   */
  public <V> V oldValue(Class<V> type) {
    return type.cast(oldValue);
  }

}

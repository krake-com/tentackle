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
 * Edit handler that ends editing when a value is committed.
 *
 * @param <S> type of the objects contained within the table's item list
 * @author harald
 */
public class SimpleEditHandler<S> implements TableEditHandler<S> {

  /**
   * Creates the handler.
   */
  public SimpleEditHandler() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public TableCellRef<S> committed(TableEditContext<S> context, TableCellEdit<S,?> edit) {
    return null;
  }

}

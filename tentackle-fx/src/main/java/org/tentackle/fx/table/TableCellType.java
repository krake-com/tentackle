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

package org.tentackle.fx.table;

import org.tentackle.fx.FxComponent;

/**
 * A table cell type.<p>
 * Table cell types are singletons per java class.
 * They are created by a factory on demand when a new type is used in a table view.
 *
 * @param <T> type of the content in a cell
 * @author harald
 */
public interface TableCellType<T> {

  /**
   * Updates the cells view.
   *
   * @param tableCell the table cell, never null
   * @param item the item for the cell, never null
   */
  void updateItem(FxTableCell<?,T> tableCell, T item);

  /**
   * Gets the editor component.
   *
   * @return the editor component, never null
   */
  FxComponent getEditor();

  /**
   * Gets the intrinsic type of the editor.
   *
   * @return the editor's type
   */
  Class<?> getEditorType();

}

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

import javafx.scene.Node;

/**
 * A table and treetable cell type.<p>
 * Table cell types are singletons per java class.
 * They are created by a factory on demand when a new type is used in a table or treetable view.
 *
 * @param <T> type of the content in a cell
 * @author harald
 */
public interface TableCellType<T> {

  /**
   * Gets the text displayed for an item.<p>
   * This is the same text as rendered by {@link #updateItem(FxTableCell, Object)}, but without a cell.
   * It is used wherever the displayed text is needed outside a cell, such as the column filters
   * or the spreadsheet export.<br>
   * Implementations rendering a graphic only should return a textual representation, nevertheless.
   *
   * @param columnConfiguration the column configuration providing the format, case conversion, etc...
   * @param item the item, never null
   * @return the displayed text, null if none
   */
  default String getText(TableColumnConfiguration<?,T> columnConfiguration, T item) {
    return item.toString();
  }

  /**
   * Gets the graphic displayed for an item.<p>
   * Cell types rendering their items as a graphic instead of text must create a <em>new</em>
   * node for each invocation, since a node has only one parent.
   *
   * @param columnConfiguration the column configuration
   * @param item the item, never null
   * @return the graphic, null if the item is rendered as text only (default)
   */
  default Node getGraphic(TableColumnConfiguration<?,T> columnConfiguration, T item) {
    return null;
  }

  /**
   * Updates the table cells view.
   *
   * @param tableCell the table cell, never null
   * @param item the item for the cell, never null
   */
  void updateItem(FxTableCell<?,T> tableCell, T item);

  /**
   * Updates the treetable cells view.
   *
   * @param treeTableCell the table cell, never null
   * @param item the item for the cell, never null
   */
  void updateItem(FxTreeTableCell<?,T> treeTableCell, T item);

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

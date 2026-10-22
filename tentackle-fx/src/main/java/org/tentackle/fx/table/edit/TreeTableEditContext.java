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

import java.util.List;

/**
 * The {@link TableEditContext} of a treetable.<br>
 * Adds the operations that are specific to trees. A {@link TableEditHandler} that needs them
 * tests the context with {@code instanceof}.
 *
 * @param <S> type of the objects contained within the tree-table's items
 * @author harald
 */
public interface TreeTableEditContext<S> extends TableEditContext<S> {

  /**
   * Gets the parent of a row.
   *
   * @param row the row object
   * @return the parent row object, null if the row is the root, a child of a hidden root, or not found
   */
  S getParent(S row);

  /**
   * Gets the row's children.<br>
   * Tree items that load their children lazily will do so.
   *
   * @param row the row object, null for the root
   * @return the row objects of the children, empty if none or the row is not found
   */
  List<S> getChildren(S row);

  /**
   * Appends a child to a row.<br>
   * The expansion state of the parent is not changed. However, {@link #edit editing} a cell of the
   * child expands its parents.
   *
   * @param parent the row object of the parent, null for the root
   * @param row the row object to add
   * @throws org.tentackle.fx.FxRuntimeException if there is no such parent
   */
  void addChild(S parent, S row);

  /**
   * Expands or collapses a row.
   *
   * @param row the row object
   * @param expanded true to expand, false to collapse
   */
  void setExpanded(S row, boolean expanded);

}

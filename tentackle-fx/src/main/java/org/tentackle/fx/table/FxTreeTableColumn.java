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

package org.tentackle.fx.table;

import javafx.scene.control.TreeTableColumn;

/**
 * An extended tree table column.<br>
 * Holds the column configuration.
 *
 * @param <S> the TreeTableView's generic type (i.e. S == TreeTableView&lt;S&gt;)
 * @param <T> The type of the content in all cells in this column.
 * @author harald
 */
public class FxTreeTableColumn<S, T> extends TreeTableColumn<S, T> {

  private final TableColumnConfiguration<S, T> configuration;

  /**
   * Creates a tree table column.
   *
   * @param configuration the column configuration
   */
  public FxTreeTableColumn(TableColumnConfiguration<S, T> configuration) {
    this.configuration = configuration;
  }

  /**
   * Creates a tree table column.
   *
   * @param configuration the column configuration
   * @param text the string to show when the TreeTableColumn is placed within the TreeTableView
   */
  public FxTreeTableColumn(TableColumnConfiguration<S, T> configuration, String text) {
    super(text);
    this.configuration = configuration;
  }

  /**
   * Gets the configuration.
   *
   * @return the configuration
   */
  public TableColumnConfiguration<S, T> getConfiguration() {
    return configuration;
  }

}

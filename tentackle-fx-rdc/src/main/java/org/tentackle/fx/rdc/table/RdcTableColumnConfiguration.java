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
package org.tentackle.fx.rdc.table;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;

import org.tentackle.fx.rdc.PdoTableCell;
import org.tentackle.fx.rdc.PdoTreeTableCell;
import org.tentackle.fx.table.DefaultTableColumnConfiguration;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * PDO-aware configuration for a table column.<br>
 * Columns holding PDOs get a {@link PdoTableCell} or {@link PdoTreeTableCell}, which provide
 * the PDO's context menu.
 *
 * @param <S> type of the objects contained within the table's items list
 * @param <T> type of the content in all cells in this column
 */
public class RdcTableColumnConfiguration<S, T> extends DefaultTableColumnConfiguration<S, T> {

  private boolean pdoTypeConfigured;

  /**
   * Creates a column configuration.
   *
   * @param tableConfiguration the table configuration
   * @param name the column's binding path
   * @param displayedName the displayed column name
   */
  public RdcTableColumnConfiguration(RdcTableConfiguration<S> tableConfiguration, String name, String displayedName) {
    super(tableConfiguration, name, displayedName);
  }

  @Override
  public void setType(Class<T> type) {
    super.setType(type);
    pdoTypeConfigured = type != null && PersistentDomainObject.class.isAssignableFrom(type);
  }

  /**
   * Returns whether this column holds PDOs.
   *
   * @return true if this is a PDO column
   */
  public boolean isPdoTypeConfigured() {
    return pdoTypeConfigured;
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })    // since T is not PDO-type by def, but only by fact
  protected TableCell<S, T> createTableCell(TableColumn<S, T> column) {
    return pdoTypeConfigured ? new PdoTableCell(this) : super.createTableCell(column);
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected TreeTableCell<S, T> createTreeTableCell(TreeTableColumn<S, T> column) {
    return pdoTypeConfigured ? new PdoTreeTableCell(this) : super.createTreeTableCell(column);
  }
}

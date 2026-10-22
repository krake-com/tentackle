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

package org.tentackle.fx.rdc;

import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.fx.rdc.search.PdoSearch;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.scene.control.TreeView;


interface RdcFactoryHolder {
  RdcFactory INSTANCE = ServiceFactory.createService(RdcFactory.class, DefaultRdcFactory.class);
}


/**
 * Factory for the RDC controllers, tree items, cells, and table popups.<br>
 * Replaceable via {@code @Service(RdcFactory.class)}.
 *
 * @author harald
 */
public interface RdcFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static RdcFactory getInstance() {
    return RdcFactoryHolder.INSTANCE;
  }


  /**
   * Creates a CRUD controller for a PDO.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @param editable true if the user may edit the PDO, false if view only
   * @param modal true if modal mode
   * @return the CRUD controller
   */
  <T extends PersistentDomainObject<T>> PdoCrud<T> createPdoCrud(T pdo, boolean editable, boolean modal);

  /**
   * Creates a search controller for a PDO.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @return the search controller
   */
  <T extends PersistentDomainObject<T>> PdoSearch<T> createPdoSearch(T pdo);

  /**
   * Creates a tree item for a PDO.
   *
   * @param pdo the PDO
   * @param <T> the PDO type
   * @return the tree item
   */
  <T extends PersistentDomainObject<T>> PdoTreeItem<T> createTreeItem(T pdo);

  /**
   * Creates a tree cell for a PDO type.
   *
   * @param treeView the tree view
   * @param <T> the PDO type
   * @return the tree cell
   */
  <T extends PersistentDomainObject<T>> PdoTreeCell<T> createTreeCell(TreeView<T> treeView);

  /**
   * Creates a tree table cell for a PDO type.
   *
   * @param columnConfig the table column configuration
   * @param <S> the table row's type
   * @param <T> the table cell's PDO type
   * @return the tree table cell
   */
  <S, T extends PersistentDomainObject<T>> PdoTreeTableCell<S, T> createTreeTableCell(TableColumnConfiguration<S, T> columnConfig);

  /**
   * Creates a table cell for a PDO type.
   *
   * @param columnConfig the table column configuration
   * @param <S> the table row's type
   * @param <T> the table cell's PDO type
   * @return the table cell
   */
  <S, T extends PersistentDomainObject<T>> PdoTableCell<S, T> createTableCell(TableColumnConfiguration<S, T> columnConfig);

  /**
   * Creates a table popup for a table view.
   *
   * @param <S> the row type
   * @param table the table
   * @param preferencesSuffix the optional preferences suffix to load/save table preferences
   * @param title the optional title of the printed table
   * @return the popup
   */
  <S> TablePopup<S> createTablePopup(FxTableView<S> table, String preferencesSuffix, String title);

  /**
   * Creates a table popup for a tree table view.
   *
   * @param <S> the row type
   * @param treeTable the tree table
   * @param preferencesSuffix the optional preferences suffix to load/save table preferences
   * @param title the optional title of the printed table
   * @return the popup
   */
  <S> TablePopup<S> createTablePopup(FxTreeTableView<S> treeTable, String preferencesSuffix, String title);

}

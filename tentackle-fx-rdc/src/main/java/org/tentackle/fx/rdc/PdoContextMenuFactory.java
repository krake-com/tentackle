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

package org.tentackle.fx.rdc;

import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.scene.control.ContextMenu;


interface PdoContextMenuFactoryHolder {
  PdoContextMenuFactory INSTANCE = ServiceFactory.createService(PdoContextMenuFactory.class, DefaultPdoContextMenuFactory.class);
}


/**
 * A factory for PDO context menus.<br>
 * The context menus are used as popups of the PDO tree, table, and tree table cells.
 * They are assembled from the menu items registered for the PDO's class and its superclasses.
 *
 * @author harald
 */
public interface PdoContextMenuFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static PdoContextMenuFactory getInstance() {
    return PdoContextMenuFactoryHolder.INSTANCE;
  }

  /**
   * Creates a context menu for a tree cell.
   *
   * @param <T> the PDO's class
   * @param cell the tree cell
   * @return the context menu, null if no menu items
   */
  <T extends PersistentDomainObject<T>> ContextMenu create(PdoTreeCell<T> cell);

  /**
   * Creates a context menu for a table cell.
   *
   * @param <S> the table row's type
   * @param <T> the table cell's PDO type
   * @param cell the table cell
   * @return the context menu, null if no menu items
   */
  <S, T extends PersistentDomainObject<T>> ContextMenu create(PdoTableCell<S, T> cell);

  /**
   * Creates a context menu for a tree table cell.
   *
   * @param <S> the table row's type
   * @param <T> the table cell's PDO type
   * @param cell the table cell
   * @return the context menu, null if no menu items
   */
  <S, T extends PersistentDomainObject<T>> ContextMenu create(PdoTreeTableCell<S, T> cell);

}

/*
 * Tentackle - http://www.tentackle.org.
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
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.fx.rdc.search.PdoSearch;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.pdo.PersistentDomainObject;


interface RdcFactory$Singleton {
  RdcFactory INSTANCE = ServiceFactory.createService(RdcFactory.class);
}

/**
 * A factory for FX-RDC-related stuff.
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
    return RdcFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a CRUD controller for a pdo.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @param editable true if edit pdo, false to view only
   * @return the crud controller
   */
  <T extends PersistentDomainObject<T>> PdoCrud<T> createPdoCrud(T pdo, boolean editable);

  /**
   * Creates a search controller for a pdo.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @return the search controller
   */
  <T extends PersistentDomainObject<T>> PdoSearch<T> createPdoSearch(T pdo);


  /**
   * Creates a tree cell for a pdo type.
   *
   * @param <T> the pdo type
   * @return the tree cell
   */
  <T extends PersistentDomainObject<T>> PdoTreeCell<T> createTreeCell();

  /**
   * Creates a table cell for a pdo type.
   *
   * @param <S> the table row's type
   * @param <T> the table cell's pdo type
   * @return the table cell
   */
  <S, T extends PersistentDomainObject<T>> PdoTableCell<S, T> createTableCell();

  /**
   * Creates a table popup.
   *
   * @param table the table
   * @param preferencesSuffix the preferences suffix to load/save table preferences
   * @param noViewSize true if don't set the table view's size
   * @param title the title of the printed table
   */
  <S> TablePopup<S> createTablePopup(FxTableView<S> table, String preferencesSuffix, boolean noViewSize, String title);

}

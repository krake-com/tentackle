/**
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.swing.rdc;

import java.awt.Component;
import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;


interface PdoSearchDialogFactory$Singleton {
  static PdoSearchDialogFactory INSTANCE = ServiceFactory.createService(
              PdoSearchDialogFactory.class, DefaultPdoSearchDialogFactory.class);
}


/**
 * A factory for {@link PdoSearchDialog}s.
 *
 * @author harald
 */
public interface PdoSearchDialogFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static PdoSearchDialogFactory getInstance() {
    return PdoSearchDialogFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a search dialog.
   * <p>
   * @param <T>           the pdo type
   * @param comp          the component to determine the owner window, null if none
   * @param pdoSearch     the search plugin
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param allowCreate   true if "new"-button for creation of a new object of searchClass
   * @param modal         true if modal dialog
   * <p>
   * @return the search dialog
   */
  <T extends PersistentDomainObject<T>> PdoSearchDialog<T> createPdoSearchDialog(Component comp,
                                                                                 PdoSearch<T> pdoSearch,
                                                                                 SelectionFilter selectionFilter,
                                                                                 boolean allowCreate,
                                                                                 boolean modal);

  /**
   * Creates a search dialog.
   * <p>
   * @param <T>           the pdo type
   * @param comp          the component to determine the owner window, null if none
   * @param context       the database context
   * @param searchClass   the object class'es table to search in, null = all tables
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param allowCreate   true if "new"-button for creation of a new object of searchClass
   * @param modal         true if modal dialog
   * <p>
   * @return the search dialog
   */
  <T extends PersistentDomainObject<T>> PdoSearchDialog<T> createPdoSearchDialog(Component comp,
                                                                                 DomainContext context,
                                                                                 Class<T> searchClass,
                                                                                 SelectionFilter selectionFilter,
                                                                                 boolean allowCreate,
                                                                                 boolean modal);

  /**
   * Creates a search dialog.
   * <p>
   * @param <T>           the pdo type
   * @param context       the database context
   * @param searchClass   the object class'es table to search in, null = all tables
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param allowCreate   true if "new"-button for creation of a new object of searchClass
   * @param modal         true if modal dialog
   * <p>
   * @return the search dialog
   */
  <T extends PersistentDomainObject<T>> PdoSearchDialog<T> createPdoSearchDialog(DomainContext context,
                                                                                 Class<T> searchClass,
                                                                                 SelectionFilter selectionFilter,
                                                                                 boolean allowCreate,
                                                                                 boolean modal);

}

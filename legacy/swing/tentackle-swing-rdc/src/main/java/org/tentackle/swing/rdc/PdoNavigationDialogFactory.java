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

import java.awt.Window;
import java.util.List;
import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.PersistentDomainObject;


interface PdoNavigationDialogFactory$Singleton {
  PdoNavigationDialogFactory INSTANCE = ServiceFactory.createService(
              PdoNavigationDialogFactory.class, DefaultPdoNavigationDialogFactory.class);
}

/**
 * A factory for {@link PdoNavigationDialog}s.
 *
 * @author harald
 */
public interface PdoNavigationDialogFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static PdoNavigationDialogFactory getInstance() {
    return PdoNavigationDialogFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a navigation dialog for a list of objects.
   * <p>
   * @param <T>           the pdo type
   * @param owner         the owner window of this dialog, null if none
   * @param list          the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode    the visibility of buttons, one of {@code PdoNavigationPanel.SHOW_...}
   * @param showTable     true if initially show the table view, false = tree view
   * <p>
   * @return the navigation dialog
   */
  <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, List<T> list,
                                                                                         SelectionFilter selectionFilter,
                                                                                         int buttonMode,
                                                                                         boolean showTable);

  /**
   * Creates a navigation dialog for a list of objects.
   * <p>
   * @param <T>           the pdo type
   * @param owner         the owner window of this dialog, null if none
   * @param list          the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * <p>
   * @return the navigation dialog
   */
  <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, List<T> list,
                                                                                         SelectionFilter selectionFilter);

  /**
   * Creates a navigation dialog for a list of objects.
   * <p>
   * @param <T>           the pdo type
   * @param list          the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param showTable     true if initially show the table view, false = tree view
   * <p>
   * @return the navigation dialog
   */
  <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(List<T> list,
                                                                                         SelectionFilter selectionFilter,
                                                                                         boolean showTable);

  /**
   * Creates a navigation dialog for a list of objects.
   * <p>
   * @param <T>           the pdo type
   * @param list          the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * <p>
   * @return the navigation dialog
   */
  <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(List<T> list,
                                                                                         SelectionFilter selectionFilter);

  /**
   * Creates a navigation dialog for a single object.
   * <p>
   * @param <T>           the pdo type
   * @param owner         the owner window of this dialog, null if none
   * @param obj           the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode    the visibility of buttons, one of {@code PdoNavigationPanel.SHOW_...}
   * @param showTable     true if initially show the table view, false = tree view
   * <p>
   * @return the navigation dialog
   */
  <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, T obj,
                                                                                         SelectionFilter selectionFilter,
                                                                                         int buttonMode,
                                                                                         boolean showTable);

  /**
   * Creates a navigation dialog for a single object.
   * <p>
   * @param <T>           the pdo type
   * @param owner         the owner window of this dialog, null if none
   * @param obj           the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * <p>
   * @return the navigation dialog
   */
  <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, T obj,
                                                                                         SelectionFilter selectionFilter);

  /**
   * Creates a navigation dialog for a single object.
   * <p>
   * @param <T>           the pdo type
   * @param obj           the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param showTable     true if initially show the table view, false = tree view
   * <p>
   * @return the navigation dialog
   */
  <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(T obj, SelectionFilter selectionFilter,
                                                                                         boolean showTable);

  /**
   * Creates a navigation dialog for a single object.
   * <p>
   * @param <T>           the pdo type
   * @param obj           the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * <p>
   * @return the navigation dialog
   */
  <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(T obj, SelectionFilter selectionFilter);

}

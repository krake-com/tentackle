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

import java.util.List;
import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.PersistentDomainObject;


interface PdoNavigationPanelFactory$Singleton {
  PdoNavigationPanelFactory INSTANCE = ServiceFactory.createService(
              PdoNavigationPanelFactory.class, DefaultPdoNavigationPanelFactory.class);
}


/**
 * A factory for {@link PdoNavigationPanel}s.
 *
 * @author harald
 */
public interface PdoNavigationPanelFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static PdoNavigationPanelFactory getInstance() {
    return PdoNavigationPanelFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a navigation panel.
   * <p>
   * @param <T>           the pdo type
   * @param list          the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode    the visibility of buttons, one of SHOW_...
   * @param showTable     true if initially show the table view, false = tree view
   * @param tableName     the preferences tablename, null if preferences by getFormTableName() from 1st object in list
   * <p>
   * @return the navigation panel
   */
  <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list,
                                                                                       SelectionFilter selectionFilter,
                                                                                       int buttonMode, boolean showTable,
                                                                                       String tableName);

  /**
   * Creates a navigation panel.<br>
   * The preferences table name is determined by the first object.
   * <p>
   * @param <T>           the pdo type
   * @param list          the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode    the visibility of buttons, one of SHOW_...
   * @param showTable     true if initially show the table view, false = tree view
   * <p>
   * @return the navigation panel
   */
  <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list,
                                                                                       SelectionFilter selectionFilter,
                                                                                       int buttonMode, boolean showTable);

  /**
   * Creates a navigation panel.<br>
   * The preferences table name is determined by the first object. The default buttons are shown (select and cancel).
   * The initial view mode is tree-view.
   * <p>
   * @param <T>           the pdo type
   * @param list          the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * <p>
   * @return the navigation panel
   */
  <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list,
                                                                                       SelectionFilter selectionFilter);

  /**
   * Creates a navigation panel.<br>
   * The preferences table name is determined by the first object. The default buttons are shown (select and cancel).
   * The initial view mode is tree-view. Nothing to select.
   * <p>
   * @param <T>  the pdo type
   * @param list the list of objects
   * <p>
   * @return the navigation panel
   */
  <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list);

  /**
   * Creates a navigation panel for a single object.<br>
   * <p>
   * @param <T>           the pdo type
   * @param obj           the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode    the visibility of buttons, one of SHOW_...
   * @param showTable     true if initially show the table view, false = tree view
   * @param tableName     the preferences tablename, null if preferences object
   * <p>
   * @return the navigation panel
   */
  <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj, SelectionFilter selectionFilter,
                                                                                       int buttonMode, boolean showTable,
                                                                                       String tableName);

  /**
   * Creates a navigation panel for a single object.<br>
   * The preferences table name is determined by the object.
   * <p>
   * @param <T>           the pdo type
   * @param obj           the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode    the visibility of buttons, one of SHOW_...
   * @param showTable     true if initially show the table view, false = tree view
   * <p>
   * @return the navigation panel
   */
  <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj, SelectionFilter selectionFilter,
                                                                                       int buttonMode, boolean showTable);

  /**
   * Creates a navigation panel for a single object.<br>
   * The preferences table name is determined by the object. The default buttons are shown (select and cancel). The
   * initial view mode is tree-view.
   * <p>
   * @param <T>           the pdo type
   * @param obj           the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * <p>
   * @return the navigation panel
   */
  <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj, SelectionFilter selectionFilter);

  /**
   * Creates a navigation panel for a single object.<br>
   * The preferences table name is determined by the object. The default buttons are shown (select and cancel). The
   * initial view mode is tree-view. Nothing to select.
   * <p>
   * @param <T> the pdo type
   * @param obj the database object
   * <p>
   * @return the navigation panel
   */
  <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj);

}

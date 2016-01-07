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
import java.awt.Window;
import java.util.Collection;
import java.util.List;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;


/**
 * Collected factory methods for the RDC client.<br>
 * Combines all the rdc factories and enhances code readability.
 *
 * @author harald
 */
public class Rdc {


  /**
   * Creates a GUI-service object for a given PDO.
   *
   * @param <T> the PDO class
   * @param pdo the PDO
   * @return the service object
   */
  public static <T extends PersistentDomainObject<T>> GuiProvider<T> createGuiProvider(T pdo) {
    return GuiProviderFactory.getInstance().createGuiProvider(pdo);
  }


  /**
   * Creates a PDO edit dialog.
   *
   * @param <T> the PDO's class
   * @param owner the owner window, null if no owner
   * @param pdo is the database object
   * @param modal is true if modal, else false
   * @return the edit dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoEditDialog<T> createPdoEditDialog(Window owner, T pdo, boolean modal) {
    return PdoEditDialogFactory.getInstance().createPdoEditDialog(owner, pdo, modal);
  }



  /**
   * Creates a navigation dialog for a list of objects.
   *
   * @param <T> the pdo type
   * @param owner the owner window of this dialog, null if none
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of {@code PdoNavigationPanel.SHOW_...}
   * @param showTable true if initially show the table view, false = tree view
   * @return the navigation dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, List<T> list, SelectionFilter selectionFilter, int buttonMode, boolean showTable)  {
    return PdoNavigationDialogFactory.getInstance().createPdoNavigationDialog(owner, list, selectionFilter, buttonMode, showTable);
  }

  /**
   * Creates a navigation dialog for a list of objects.
   *
   * @param <T> the pdo type
   * @param owner the owner window of this dialog, null if none
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @return the navigation dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, List<T> list, SelectionFilter selectionFilter)  {
    return PdoNavigationDialogFactory.getInstance().createPdoNavigationDialog(owner, list, selectionFilter);
  }

  /**
   * Creates a navigation dialog for a list of objects.
   *
   * @param <T> the pdo type
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param showTable true if initially show the table view, false = tree view
   * @return the navigation dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(List<T> list, SelectionFilter selectionFilter, boolean showTable)  {
    return PdoNavigationDialogFactory.getInstance().createPdoNavigationDialog(list, selectionFilter, showTable);
  }

  /**
   * Creates a navigation dialog for a list of objects.
   *
   * @param <T> the pdo type
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @return the navigation dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(List<T> list, SelectionFilter selectionFilter)  {
    return PdoNavigationDialogFactory.getInstance().createPdoNavigationDialog(list, selectionFilter);
  }


  /**
   * Creates a navigation dialog for a single object.
   *
   * @param <T> the pdo type
   * @param owner the owner window of this dialog, null if none
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of {@code PdoNavigationPanel.SHOW_...}
   * @param showTable true if initially show the table view, false = tree view
   * @return the navigation dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, T obj, SelectionFilter selectionFilter, int buttonMode, boolean showTable)  {
    return PdoNavigationDialogFactory.getInstance().createPdoNavigationDialog(owner, obj, selectionFilter, buttonMode, showTable);
  }

  /**
   * Creates a navigation dialog for a single object.
   *
   * @param <T> the pdo type
   * @param owner the owner window of this dialog, null if none
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @return the navigation dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, T obj, SelectionFilter selectionFilter)  {
    return PdoNavigationDialogFactory.getInstance().createPdoNavigationDialog(owner, obj, selectionFilter);
  }

  /**
   * Creates a navigation dialog for a single object.
   *
   * @param <T> the pdo type
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param showTable true if initially show the table view, false = tree view
   * @return the navigation dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(T obj, SelectionFilter selectionFilter, boolean showTable)  {
    return PdoNavigationDialogFactory.getInstance().createPdoNavigationDialog(obj, selectionFilter, showTable);
  }

  /**
   * Creates a navigation dialog for a single object.
   *
   * @param <T> the pdo type
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @return the navigation dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(T obj, SelectionFilter selectionFilter)  {
    return PdoNavigationDialogFactory.getInstance().createPdoNavigationDialog(obj, selectionFilter);
  }


  /**
   * Creates a navigation panel.
   *
   * @param <T> the pdo type
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of SHOW_...
   * @param showTable true if initially show the table view, false = tree view
   * @param tableName the preferences tablename, null if preferences by getFormTableName() from 1st object in list
   * @return the navigation panel
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list, SelectionFilter selectionFilter,
                                          int buttonMode, boolean showTable, String tableName)  {
    return PdoNavigationPanelFactory.getInstance().createPdoNavigationPanel(list, selectionFilter, buttonMode, showTable, tableName);
  }

  /**
   * Creates a navigation panel.<br>
   * The preferences table name is determined by the first object.
   *
   * @param <T> the pdo type
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of SHOW_...
   * @param showTable true if initially show the table view, false = tree view
   * @return the navigation panel
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list, SelectionFilter selectionFilter, int buttonMode, boolean showTable)  {
    return PdoNavigationPanelFactory.getInstance().createPdoNavigationPanel(list, selectionFilter, buttonMode, showTable);
  }

  /**
   * Creates a navigation panel.<br>
   * The preferences table name is determined by the first object.
   * The default buttons are shown (select and cancel).
   * The initial view mode is tree-view.
   *
   * @param <T> the pdo type
   * @param list the list of objects
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @return the navigation panel
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list, SelectionFilter selectionFilter) {
    return PdoNavigationPanelFactory.getInstance().createPdoNavigationPanel(list, selectionFilter);
  }

  /**
   * Creates a navigation panel.<br>
   * The preferences table name is determined by the first object.
   * The default buttons are shown (select and cancel).
   * The initial view mode is tree-view.
   * Nothing to select.
   *
   * @param <T> the pdo type
   * @param list the list of objects
   * @return the navigation panel
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list) {
    return PdoNavigationPanelFactory.getInstance().createPdoNavigationPanel(list);
  }

  /**
   * Creates a navigation panel for a single object.<br>
   *
   * @param <T> the pdo type
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of SHOW_...
   * @param showTable true if initially show the table view, false = tree view
   * @param tableName the preferences tablename, null if preferences object
   * @return the navigation panel
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj, SelectionFilter selectionFilter, int buttonMode, boolean showTable, String tableName)  {
    return PdoNavigationPanelFactory.getInstance().createPdoNavigationPanel(obj, selectionFilter, buttonMode, showTable, tableName);
  }

  /**
   * Creates a navigation panel for a single object.<br>
   * The preferences table name is determined by the object.
   *
   * @param <T> the pdo type
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param buttonMode the visibility of buttons, one of SHOW_...
   * @param showTable true if initially show the table view, false = tree view
   * @return the navigation panel
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj, SelectionFilter selectionFilter, int buttonMode, boolean showTable)  {
    return PdoNavigationPanelFactory.getInstance().createPdoNavigationPanel(obj, selectionFilter, buttonMode, showTable);
  }

  /**
   * Creates a navigation panel for a single object.<br>
   * The preferences table name is determined by the object.
   * The default buttons are shown (select and cancel).
   * The initial view mode is tree-view.
   *
   * @param <T> the pdo type
   * @param obj the database object
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @return the navigation panel
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj, SelectionFilter selectionFilter) {
    return PdoNavigationPanelFactory.getInstance().createPdoNavigationPanel(obj, selectionFilter);
  }

  /**
   * Creates a navigation panel for a single object.<br>
   * The preferences table name is determined by the object.
   * The default buttons are shown (select and cancel).
   * The initial view mode is tree-view.
   * Nothing to select.
   *
   * @param <T> the pdo type
   * @param obj the database object
   * @return the navigation panel
   */
  public static <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj) {
    return PdoNavigationPanelFactory.getInstance().createPdoNavigationPanel(obj);
  }


  /**
   * Creates a search dialog.
   *
   * @param <T> the pdo type
   * @param comp the component to determine the owner window, null if none
   * @param pdoSearch the search plugin
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param allowCreate true if "new"-button for creation of a new object of searchClass
   * @param modal true if modal dialog
   * @return the search dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoSearchDialog<T> createPdoSearchDialog (
                                  Component comp,
                                  PdoSearch<T> pdoSearch,
                                  SelectionFilter selectionFilter,
                                  boolean allowCreate,
                                  boolean modal) {
    return PdoSearchDialogFactory.getInstance().createPdoSearchDialog(comp, pdoSearch, selectionFilter, allowCreate, modal);
  }

  /**
   * Creates a search dialog.
   *
   * @param <T> the pdo type
   * @param comp the component to determine the owner window, null if none
   * @param context the database context
   * @param searchClass the object class'es table to search in, null = all tables
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param allowCreate true if "new"-button for creation of a new object of searchClass
   * @param modal true if modal dialog
   * @return the search dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoSearchDialog<T> createPdoSearchDialog (
                                  Component comp,
                                  DomainContext context,
                                  Class<T> searchClass,
                                  SelectionFilter selectionFilter,
                                  boolean allowCreate,
                                  boolean modal) {
    return PdoSearchDialogFactory.getInstance().createPdoSearchDialog(comp, context, searchClass, selectionFilter, allowCreate, modal);
  }


  /**
   * Creates a search dialog.
   *
   * @param <T> the pdo type
   * @param context the database context
   * @param searchClass the object class'es table to search in, null = all tables
   * @param selectionFilter filter selectable objects, null if nothing selectable
   * @param allowCreate true if "new"-button for creation of a new object of searchClass
   * @param modal true if modal dialog
   * @return the search dialog
   */
  public static <T extends PersistentDomainObject<T>> PdoSearchDialog<T> createPdoSearchDialog (
                                DomainContext context,
                                Class<T> searchClass,
                                SelectionFilter selectionFilter,
                                boolean allowCreate,
                                boolean modal) {
    return PdoSearchDialogFactory.getInstance().createPdoSearchDialog(context, searchClass, selectionFilter, allowCreate, modal);
  }


  /**
   * Creates a tree.<br>
   * If the given object is a {@link Collection} the objects of the collection
   * will be shown in the tree. If it is some other object, only that
   * object is shown.<br>
   * Notice that the objects need not necessarily be {@link PersistentDomainObject}s.
   *
   * @param objects the objects, null if empty tree
   * @return the tree
   */
  public static PdoTree createPdoTree(Collection<?> objects) {
    return PdoTreeFactory.getInstance().createPdoTree(objects);
  }


  /**
   * Creates a tree.<br>
   * If the given object is a {@link Collection} the objects of the collection
   * will be shown in the tree. If it is some other object, only that
   * object is shown.<br>
   * Notice that the objects need not necessarily be {@link PersistentDomainObject}s.
   *
   * @param object the object or collection of objects, null if empty tree
   * @return the tree
   */
  public static PdoTree createPdoTree(Object object) {
    return PdoTreeFactory.getInstance().createPdoTree(object);
  }


  /**
   * Creates an empty tree.
   *
   * @return the tree
   */
  public static PdoTree createPdoTree() {
    return PdoTreeFactory.getInstance().createPdoTree();
  }



  private Rdc() {
    // no instance
  }

}

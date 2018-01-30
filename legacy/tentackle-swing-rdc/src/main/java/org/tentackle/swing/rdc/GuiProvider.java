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
import java.awt.datatransfer.Transferable;
import java.util.Collection;
import javax.swing.ImageIcon;
import org.tentackle.misc.ObjectFilter;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PdoHolder;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormPanel;
import org.tentackle.swing.FormTableEntry;

/**
 * Provider of GUI-functionality for a pdo.
 *
 * @param <T> the PDO type
 * @author harald
 */
public interface GuiProvider<T extends PersistentDomainObject<T>> extends PdoHolder<T>, DomainContextProvider {

  /**
   * Gets the PDO's icon.<br>
   * The icon is displayed in trees, for example.
   *
   * @return the icon
   */
  ImageIcon getIcon();

  /**
   * Determines whether a panel exists to edit or view this object.
   * The default is false.
   *
   * @return true if a panel exists
   * @see #createPanel()
   */
  boolean panelExists();

  /**
   * Creates a panel to edit or view this object.
   * The default implementation returns null.
   *
   * @return the panel, null if none
   * @see #panelExists()
   */
  FormPanel createPanel();

  /**
   * Creates a new dialog to edit this object.
   *
   * @param comp the component to determine the window owner, null if none
   * @param modal true if dialog should be modal
   * @return the dialog or null if use default
   */
  PdoEditDialog<T> createDialog(Component comp, boolean modal);

  /**
   * Gets the effective object that should be edited or viewed in a panel.
   *
   * @return the object to be edited or viewed in a panel
   */
  T getPanelObject();

  /**
   * Gets the {@link Transferable} for this object.<br>
   * Used for drag and drop.
   * If the object is new, null is returned because the object
   * has no ID yet.
   *
   * @return the transferable, null if none
   * @see PdoTransferable
   */
  PdoTransferable<T> getTransferable();


  /**
   * Drops a transferable on this object.<br>
   *
   * @param transferable the Transferable
   * @return true if drop succeeded, else false
   */
  boolean dropTransferable(Transferable transferable);

  /**
   * Gets the object to be displayed in trees in place of
   * this object. The default implementation returns "this".
   * Override if necessary.
   *
   * @return the object to be displayed in a tree instead of this.
   * @see PdoTree
   */
  T getTreeRoot();

  /**
   * Gets the text to be displayed in trees for this object.
   *
   * @return the tree text
   * @see PdoTree
   */
  String getTreeText();

  /**
   * Gets the tree text with respect to the parent object this
   * object is displayed in a tree.<br>
   *
   * @param parent is the parent object, null if no parent
   * @return the tree text
   * @see PdoTree
   */
  String getTreeText(Object parent);

  /**
   * Gets the tooltip to be displayed for an object in a tree.
   *
   * @return the tooltip text
   * @see PdoTree
   */
  String getToolTipText();

  /**
   * Gets the tooltip text with respect to the parent object this
   * object is displayed in a tree.<br>
   *
   * @param parent is the parent object, null if no parent
   * @return the tooltip text
   * @see PdoTree
   */
  String getToolTipText(Object parent);

  /**
   * Determines whether tree explansion should stop at this object.<br>
   * Sometimes treechilds should not be expanded if in a recursive "expand all".
   * However they should expand if the user explicitly wants to.
   * If this method returns true, the tree will not further expand this node
   * automatically (only on demand).
   *
   * @return true if expansion should stop.
   * @see PdoTree
   */
  boolean stopTreeExpansion();

  /**
   * Gets the optional tree-view filter.<br>
   * The filter is used in {@link PdoNavigationPanel} to
   * show only certain objects in the tree view as root nodes.
   * Nice for hierarchical data.
   *
   * @return the filter, null if none
   */
  ObjectFilter<T> getTreeFilter();

  /**
   * Determines whether this object may have child objects that should
   * be visible in a navigatable tree.
   *
   * @return true if object may have childs
   * @see PdoTree
   */
  boolean allowsTreeChildObjects();

  /**
   * Gets all childs of this objects that should be visible to the user
   * in a navigatable object tree.
   *
   * @return the childs (may be of any type, not only PDOs, and mixed!)
   * @see PdoTree
   */
  Collection getTreeChildObjects();

  /**
   * Gets the childs with respect to the parent object this
   * object is displayed in the current tree.
   *
   * @param parentObject the parent object of this object in the tree, null = no parent
   * @return the childs (may be of any type, not only PDOs, and mixed!)
   * @see PdoTree
   */
  Collection getTreeChildObjects(Object parentObject);

  /**
   * Gets the maximum level of expansion allowed in a navigatable object tree.<br>
   * The default is 0, i.e. no limit.
   *
   * @return the maximum depth of a subtree of this object, 0 = no limit (default)
   * @see PdoTree
   */
  int getTreeExpandMaxDepth();

  /**
   * Determines whether the navigatable object tree should show the
   * parents of this object.<br>
   * Objects may be childs of different parents in terms of "being referenced from"
   * or any other relation, for example: the warehouses this customer
   * visited.
   * Trees may provide a button to make these parents visible.
   * The default implementation returns false.
   *
   * @return true if showing parents is enabled
   * @see PdoTree
   */
  boolean allowsTreeParentObjects();

  /**
   * Gets the parents of this object.
   *
   * @return the parents (may be of any type, not only PDOs, and mixed!)
   * @see PdoTree
   * @see #allowsTreeParentObjects
   */
  Collection getTreeParentObjects();

  /**
   * Gets the parents with respect to the parent object this
   * object is displayed in the current tree.
   *
   * @param parentObject the parent object, null if no parent
   * @return the parents (may be of any type, not only PDOs, and mixed!)
   * @see PdoTree
   */
  Collection getTreeParentObjects(Object parentObject);

  /**
   * Gets the table entry for viewing lists of this object
   * in a {@link org.tentackle.swing.FormTable}.
   *
   * @return the formtable entry
   * @see #getFormTableName()
   */
  FormTableEntry<T> getFormTableEntry();

  /**
   * Gets the tablename used in to initialize the table's gui.<br>
   *
   * @return the table name
   * @see #getFormTableEntry()
   */
  String getFormTableName();

  /**
   * Creates the search plugin for the search dialog.
   *
   * @return the plugin
   */
  PdoSearch<T> createPdoSearch();

}

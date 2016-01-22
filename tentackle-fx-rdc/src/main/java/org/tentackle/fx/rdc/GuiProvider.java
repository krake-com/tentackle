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


package org.tentackle.fx.rdc;

import java.util.Collection;
import java.util.ResourceBundle;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PdoHolder;
import org.tentackle.pdo.PersistentDomainObject;


/**
 * Provider of GUI-functionality for a pdo.
 *
 * @param <T> the PDO type
 * @author harald
 */
public interface GuiProvider<T extends PersistentDomainObject<T>> extends PdoHolder<T>, DomainContextProvider {

  /**
   * Gets the resource bundle for this provider.
   *
   * @return the resource bundle
   */
  ResourceBundle getBundle();

  /**
   * Gets the PDO's icon.<br>
   * The icon is displayed in trees, for example.
   *
   * @return the icon
   */
  ImageView createIcon();

  /**
   * Returns whether a controller exists to edit or view this object.
   *
   * @return true if a controller exists
   * @see #createEditor()
   */
  boolean editorExists();

  /**
   * Creates a controller to edit or view this object.
   *
   * @param <C> the controller type
   * @return the controller
   * @throws FxRuntimeException if {@link #editorExists()} == false
   */
  <C extends PdoEditor<T>> C createEditor();

  /**
   * Returns whether a finder exists to search for PDOs.
   *
   * @return true if controller exists
   */
  boolean finderExists();

  /**
   * Creates a controller to search PDOs.
   *
   * @param <C> the controller type
   * @return the controller
   * @throws FxRuntimeException if {@link #editorExists()} == false
   */
  <C extends PdoFinder<T>> C createFinder();

  /**
   * Creates the dragboard for the pdo.
   *
   * @return the dragboard, null if PDO is not a DnD source
   */
  Dragboard createDragboard();

  /**
   * Drops the dragboard on the pdo.
   *
   * @param dragbord the dragboard
   * @return true if dropped, false if PDO does not accept the dragboard
   */
  boolean dropDragboard(Dragboard dragbord);

  /**
   * Gets the object to be displayed in trees in place of this object.<br>
   *
   * @return the object to be displayed in a tree
   */
  T getTreeRoot();

  /**
   * Gets the text to be displayed in trees for this object.
   *
   * @return the tree text
   */
  String getTreeText();

  /**
   * Gets the tree text with respect to the parent object this
   * object is displayed in a tree.<br>
   *
   * @param parent is the parent object, null if no parent
   * @return the tree text
   */
  String getTreeText(Object parent);

  /**
   * Gets the tooltip to be displayed for an object in a tree.
   *
   * @return the tooltip text
   */
  String getToolTipText();

  /**
   * Gets the tooltip text with respect to the parent object this
   * object is displayed in a tree.<br>
   *
   * @param parent is the parent object, null if no parent
   * @return the tooltip text
   */
  String getToolTipText(Object parent);

  /**
   * Creates the tree item for this pdo.
   *
   * @return the tree item
   */
  TreeItem<T> createTreeItem();

  /**
   * Creates a tree cell for this type of pdo.
   *
   * @return the tree cell
   */
  TreeCell<T> createTreeCell();

  /**
   * Determines whether tree explansion should stop at this object.<br>
   * Sometimes treechilds should not be expanded if in a recursive "expand all".
   * However they should expand if the user explicitly wants to.
   * If this method returns true, the tree will not further expand this node
   * automatically (only on demand).
   *
   * @return true if expansion should stop.
   */
  boolean stopTreeExpansion();

  /**
   * Determines whether this object may have child objects that should
   * be visible in a navigatable tree.
   *
   * @return true if object may have childs
   */
  boolean providesTreeChildObjects();

  /**
   * Gets all childs of this objects that should be visible to the user
   * in a navigatable object tree.
   *
   * @param <S> the item type
   * @return the childs
   */
  <S extends PersistentDomainObject<S>> Collection<S> getTreeChildObjects();

  /**
   * Gets the childs with respect to the parent object this
   * object is displayed in the current tree.
   *
   * @param <S> the item type
   * @param parentObject the parent object of this object in the tree, null = no parent
   * @return the childs
   */
  <S extends PersistentDomainObject<S>> Collection<S> getTreeChildObjects(Object parentObject);

  /**
   * Gets the maximum level of expansion allowed in a navigatable object tree.<br>
   * The default is 0, i.e. no limit.
   *
   * @return the maximum depth of a subtree of this object, 0 = no limit (default)
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
   */
  boolean providesTreeParentObjects();

  /**
   * Gets the parents of this object.
   *
   * @param <S> the item type
   * @return the parents
   * @see #providesTreeParentObjects
   */
  <S extends PersistentDomainObject<S>> Collection<S> getTreeParentObjects();

  /**
   * Gets the parents with respect to the parent object this
   * object is displayed in the current tree.
   *
   * @param <S> the item type
   * @param parentObject the parent object, null if no parent
   * @return the parents
   */
  <S extends PersistentDomainObject<S>> Collection<S> getTreeParentObjects(Object parentObject);

  /**
   * Gets the tablename used in to initialize the table's gui.<br>
   *
   * @return the table configuration
   */
  TableConfiguration<T> createTableConfiguration();

  /**
   * Creates the table view for given configuration.
   *
   * @return the view
   */
  TableView<T> createTableView();

}

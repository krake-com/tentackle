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

import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.util.Callback;

import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PdoProvider;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.Collection;
import java.util.ResourceBundle;


/**
 * Provider of GUI-functionality for a pdo.
 *
 * @param <T> the PDO type
 * @author harald
 */
public interface GuiProvider<T extends PersistentDomainObject<T>>
       extends PdoProvider<T>, TableConfigurationProvider<T>, DomainContextProvider {

  /**
   * Returns whether a resource bundle is provided.
   *
   * @return true if provided, false if no bundle
   */
  boolean isBundleProvided();

  /**
   * Gets the resource bundle for this provider.
   * <p>
   * Throws {@link java.util.MissingResourceException} if no bundle found.
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
   * Returns whether the editor allows editing of the current pdo.
   *
   * @return true if editing allowed, always false if !editorExists
   */
  boolean isEditAllowed();

  /**
   * Returns whether the editor allows viewing of the current pdo.
   *
   * @return true if viewing allowed, always false if !editorExists
   */
  boolean isViewAllowed();

  /**
   * Creates a controller to edit or view this object.
   *
   * @return the controller
   * @throws FxRuntimeException if {@link #editorExists()} == false
   */
  PdoEditor<T> createEditor();

  /**
   * Returns whether a finder exists to search for PDOs.
   *
   * @return true if controller exists
   */
  boolean finderExists();

  /**
   * Creates a controller to search PDOs.
   *
   * @return the controller
   * @throws FxRuntimeException if {@link #editorExists()} == false
   */
  PdoFinder<T> createFinder();

  /**
   * Creates the dragboard for the pdo.
   *
   * @param node the drag source
   * @return the dragboard, null if PDO is not a DnD source
   */
  Dragboard createDragboard(Node node);

  /**
   * Checks whether drag over the pdo is accepted.
   *
   * @param event the drag event
   * @return true if accepted, false if PDO does not accept the drag
   */
  boolean isDragAccepted(DragEvent event);

  /**
   * Drops the dragboard on the pdo.
   *
   * @param dragbord the dragboard
   */
  void dropDragboard(Dragboard dragbord);

  /**
   * Gets the object to be displayed in trees in place of this object.<br>
   *
   * @return the object to be displayed in a tree
   */
  T getTreeRoot();

  /**
   * Gets the tree text with respect to the parent object this
   * object is displayed in a tree.<br>
   *
   * @param <P> the parent PDO type
   * @param parent is the parent object, null if no parent
   * @return the tree text
   */
  <P extends PersistentDomainObject<P>> String getTreeText(P parent);

  /**
   * Gets the tooltip text with respect to the parent object this
   * object is displayed in a tree.<br>
   *
   * @param <P> the parent PDO type
   * @param parent is the parent object, null if no parent
   * @return the tooltip text
   */
  <P extends PersistentDomainObject<P>> String getToolTipText(P parent);

  /**
   * Creates the tree item for this pdo.
   *
   * @return the tree item
   */
  TreeItem<T> createTreeItem();

  /**
   * Creates a tree cell factory for a treeview.
   *
   * @return the tree cell factory, never null
   */
  Callback<TreeView<T>, TreeCell<T>> getTreeCellFactory();

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
   * Gets the childs with respect to the parent object this
   * object is displayed in the current tree.
   *
   * @param <P> the parent PDO type
   * @param parent the parent object of this object in the tree, null = no parent
   * @return the childs
   */
  <P extends PersistentDomainObject<P>> Collection<? extends PersistentDomainObject<?>> getTreeChildObjects(P parent);

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
   * Gets the parents with respect to the parent object this
   * object is displayed in the current tree.
   *
   * @param <P> the parent PDO type
   * @param parent the parent object, null if no parent
   * @return the parents
   */
  <P extends PersistentDomainObject<P>> Collection<? extends PersistentDomainObject<?>> getTreeParentObjects(P parent);

  /**
   * Creates the table view for given configuration.
   *
   * @return the view
   */
  FxTableView<T> createTableView();

}

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

import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PdoProvider;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.util.Callback;
import java.util.Collection;
import java.util.ResourceBundle;

/**
 * Provider of the GUI functionality for a PDO.<br>
 * Exactly one provider class exists per PDO type. It bundles all the entity-specific UI behavior:
 * the icon and resource bundle, the editor and finder, the table configuration, the tree rendering
 * and navigation, and drag and drop.
 * <p>
 * Providers are registered with {@link GuiProviderService} and created per PDO instance via
 * {@link GuiProviderFactory}. Applications usually extend {@link DefaultGuiProvider} and override
 * only what differs.
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
   * Creates the PDO's icon.<br>
   * The icon is displayed in trees, for example.
   *
   * @return the icon
   */
  Node createGraphic();

  /**
   * Returns whether a controller is available to edit or view the PDO.
   *
   * @return true if a controller exists
   * @see #createEditor()
   */
  boolean isEditorAvailable();

  /**
   * Returns whether the current user is allowed to edit the PDO.
   *
   * @return true if editing is allowed, always false if not {@link #isEditorAvailable()}
   */
  boolean isEditAllowed();

  /**
   * Returns whether the current user is allowed to view the PDO.
   *
   * @return true if viewing is allowed, always false if not {@link #isEditorAvailable()}
   */
  boolean isViewAllowed();

  /**
   * Creates a controller to edit or view the PDO.
   *
   * @return the controller
   * @throws RdcRuntimeException if {@link #isEditorAvailable()} == false
   */
  PdoEditor<T> createEditor();

  /**
   * Returns whether a finder is available to search for PDOs.
   *
   * @return true if a finder exists
   * @see #createFinder()
   */
  boolean isFinderAvailable();

  /**
   * Creates a controller to search PDOs.
   *
   * @return the controller
   * @throws RdcRuntimeException if {@link #isFinderAvailable()} == false
   */
  PdoFinder<T> createFinder();

  /**
   * Creates the dragboard for the PDO.
   *
   * @param node the drag source
   * @return the dragboard, null if the PDO is not a DnD source
   */
  Dragboard createDragboard(Node node);

  /**
   * Checks whether a drag over the PDO is accepted.
   *
   * @param event the drag event
   * @return true if accepted, false if the PDO does not accept the drag
   */
  boolean isDragAccepted(DragEvent event);

  /**
   * Drops the dragboard on the PDO.
   *
   * @param dragboard the dragboard
   */
  void dropDragboard(Dragboard dragboard);

  /**
   * Gets the object to be displayed in trees in place of the PDO.
   *
   * <p>
   * Not used by the framework's {@link PdoTreeItem}. Provided for application-specific navigable trees.
   *
   * @return the object to be displayed in a tree
   */
  T getTreeRoot();

  /**
   * Gets the tree text with respect to the parent object
   * the PDO is displayed under in a tree.
   *
   * @param <P> the parent PDO type
   * @param parent is the parent object, null if no parent
   * @return the tree text
   */
  <P extends PersistentDomainObject<P>> String getTreeText(P parent);

  /**
   * Gets the tooltip text with respect to the parent object
   * the PDO is displayed under in a tree.
   *
   * @param <P> the parent PDO type
   * @param parent is the parent object, null if no parent
   * @return the tooltip text
   */
  <P extends PersistentDomainObject<P>> String getToolTipText(P parent);

  /**
   * Creates the tree item for the PDO.
   *
   * @return the tree item
   */
  TreeItem<T> createTreeItem();

  /**
   * Creates a tree cell factory for a tree view.
   *
   * @return the tree cell factory, never null
   */
  Callback<TreeView<T>, TreeCell<T>> getTreeCellFactory();

  /**
   * Determines whether tree expansion should stop at the PDO.<br>
   * Sometimes the child nodes should not be expanded in a recursive "expand all".
   * However, they should expand if the user explicitly wants to.
   * If this method returns true, the tree will not further expand this node
   * automatically (only on demand).
   * <p>
   * Not used by the framework's {@link PdoTreeItem}. Provided for application-specific navigable trees.
   *
   * @return true if expansion should stop
   */
  boolean stopTreeExpansion();

  /**
   * Determines whether the PDO may have child objects that should
   * be visible in a navigable tree.<br>
   * If false, the PDO is a leaf in the tree.
   *
   * @return true if object may have children
   */
  boolean providesTreeChildObjects();

  /**
   * Gets the children with respect to the parent object
   * the PDO is displayed under in the current tree.
   *
   * @param <P> the parent PDO type
   * @param parent the parent object of the PDO in the tree, null if no parent
   * @return the children
   */
  <P extends PersistentDomainObject<P>> Collection<? extends PersistentDomainObject<?>> getTreeChildObjects(P parent);

  /**
   * Gets the maximum level of expansion allowed in a navigable object tree.
   * <p>
   * Not used by the framework's {@link PdoTreeItem}. Provided for application-specific navigable trees.
   *
   * @return the maximum depth of a subtree of this object, 0 = no limit (default)
   */
  int getTreeExpandMaxDepth();

  /**
   * Determines whether the navigable object tree should show the
   * parents of the PDO.<br>
   * Objects may be children of different parents in terms of "being referenced from"
   * or any other relation, for example: the warehouses a customer
   * visited.
   * Trees may provide a button to make these parents visible.
   * <p>
   * Not used by the framework's {@link PdoTreeItem}. Provided for application-specific navigable trees.
   *
   * @return true if showing parents is enabled
   */
  boolean providesTreeParentObjects();

  /**
   * Gets the parents with respect to the parent object
   * the PDO is displayed under in the current tree.
   * <p>
   * Not used by the framework's {@link PdoTreeItem}. Provided for application-specific navigable trees.
   *
   * @param <P> the parent PDO type
   * @param parent the parent object, null if no parent
   * @return the parents
   */
  <P extends PersistentDomainObject<P>> Collection<? extends PersistentDomainObject<?>> getTreeParentObjects(P parent);

  /**
   * Creates a table view for the PDO type.<br>
   * The view is configured and bound according to {@link #createTableConfiguration()}.
   *
   * @return the table view
   */
  FxTableView<T> createTableView();

}

/*
 * Tentackle - https://tentackle.org.
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

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import org.tentackle.fx.FxUtilities;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tree item for a PDO.<br>
 * The child items are loaded lazily via {@link GuiProvider#getTreeChildObjects(PersistentDomainObject)}
 * when they are requested for the first time.
 *
 * @author harald
 * @param <T> the PDO type
 */
public class PdoTreeItem<T extends PersistentDomainObject<T>> extends TreeItem<T> {

  private T pdo;
  private GuiProvider<T> provider;
  private boolean childrenLoaded;

  /**
   * Creates a tree item for a PDO.
   *
   * @param pdo the PDO
   */
  public PdoTreeItem(T pdo) {
    super(pdo);
    this.pdo = pdo;
  }

  /**
   * Returns the PDO represented by this item.
   *
   * @return the PDO
   */
  public T getPdo() {
    return pdo;
  }

  /**
   * Sets the PDO.<br>
   * Can be used to reload the PDO, for example. The child items will be loaded again on demand.
   *
   * @param pdo the PDO
   */
  public void setPdo(T pdo) {
    setValue(pdo);
    this.pdo = pdo;
    provider = null;    // force reload
    childrenLoaded = false;
    super.getChildren().clear();
  }

  @Override
  public boolean isLeaf() {
    return !getGuiProvider().providesTreeChildObjects() || childrenLoaded && super.getChildren().isEmpty();
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public ObservableList<TreeItem<T>> getChildren() {
    if (!childrenLoaded) {
      childrenLoaded = true;
      if (getGuiProvider().providesTreeChildObjects()) {
        List<TreeItem<T>> childItems = new ArrayList<>();
        PersistentDomainObject parentPdo = getParentPdo();
        Collection<PersistentDomainObject> children = getGuiProvider().getTreeChildObjects(parentPdo);
        for (PersistentDomainObject child : children) {
          childItems.add(Rdc.createGuiProvider(child).createTreeItem());
        }
        super.getChildren().setAll(childItems);
        // it is important to return super.getChildren() below after setAll to make sure that checkSortState is invoked!
      }
    }
    return super.getChildren();
  }


  /**
   * Gets the parent PdoTreeItem.
   *
   * @param <P> the parent PDO type
   * @return the parent, null if no parent or parent is no PdoTreeItem
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public <P extends PersistentDomainObject<P>> PdoTreeItem<P> getParentPdoItem() {
    TreeItem<?> parent = getParent();
    return parent instanceof PdoTreeItem ? (PdoTreeItem) parent : null;
  }


  /**
   * Gets the parent PDO.
   *
   * @param <P> the parent PDO type
   * @return the parent PDO, null if no parent or parent item is no PdoTreeItem
   */
  public <P extends PersistentDomainObject<P>> P getParentPdo() {
    PdoTreeItem<P> parentItem = getParentPdoItem();
    return parentItem == null ? null : parentItem.getPdo();
  }


  /**
   * Gets the parent item's PDO.
   *
   * @param <P> the parent PDO type
   * @param parentPdoClass the entity class of the parent PDO
   * @return the parent PDO, null if no parent, parent is no PdoTreeItem, or its PDO is not an instance of {@code parentPdoClass}
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <P extends PersistentDomainObject<P>> P getParentPdo(Class<P> parentPdoClass) {
    PersistentDomainObject parentPdo = getParentPdo();
    return parentPdo != null && parentPdoClass.isAssignableFrom(parentPdo.getClass()) ? (P) parentPdo : null;
  }


  /**
   * Lazily gets the GUI provider for the PDO.
   *
   * @return the provider, never null
   */
  public GuiProvider<T> getGuiProvider() {
    if (provider == null) {
      provider = Rdc.createGuiProvider(pdo);
    }
    return provider;
  }


  /**
   * Expands this and all child tree items.<br>
   * If the item is already expanded, the item and all child items will be collapsed before all is expanded again.
   */
  public void expand() {
    if (isExpanded()) {
      collapse();
    }
    FxUtilities.getInstance().expandAll(this);
  }


  /**
   * Collapses this and all child tree items.
   */
  public void collapse() {
    FxUtilities.getInstance().collapseAll(this);
  }

}

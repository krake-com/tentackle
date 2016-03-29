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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Tree item for a PDO.
 *
 * @author harald
 * @param <T> the pdo type
 */
public class PdoTreeItem<T extends PersistentDomainObject<T>> extends TreeItem<T> {

  private T pdo;
  private GuiProvider<T> provider;
  private boolean childrenLoaded;

  /**
   * Creates a tree item for a PDO.
   *
   * @param pdo the pdo
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
   * Sets the pdo.<br>
   * Can be used to reload the pdo, for example.
   *
   * @param pdo the pdo
   */
  public void setPdo(T pdo) {
    setValue(pdo);
    this.pdo = pdo;
    provider = null;    // force reload
  }

  @Override
  public boolean isLeaf() {
    return !getGuiProvider().providesTreeChildObjects();
  }

  @Override
  public ObservableList<TreeItem<T>> getChildren() {
    if (!childrenLoaded) {
      childrenLoaded = true;
      List<TreeItem<T>> childList = new ArrayList<>();
      PdoTreeItem<T> parentItem = getParentPdoItem();
      for (PersistentDomainObject child:
              getGuiProvider().getTreeChildObjects(parentItem == null ? null : parentItem.getPdo())) {
        @SuppressWarnings("unchecked")
        TreeItem<T> treeItem = GuiProviderFactory.getInstance().createGuiProvider(child).createTreeItem();
        childList.add(treeItem);
      }
      super.getChildren().setAll(childList);
    }
    return super.getChildren();
  }


  /**
   * Gets the parent PdoTreeItem.
   *
   * @param <P> the parent PDO type
   * @return the parent, null if no parent or parent is no PdoTreeItem
   */
  @SuppressWarnings("unchecked")
  public <P extends PersistentDomainObject<P>> PdoTreeItem<P> getParentPdoItem() {
    TreeItem<?> parent = getParent();
    return parent instanceof PdoTreeItem ? (PdoTreeItem) parent : null;
  }


  /**
   * Lazily gets the GUI provider for the PDO.
   *
   * @return the provider, never null
   */
  public GuiProvider<T> getGuiProvider() {
    if (provider == null) {
      provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
    }
    return provider;
  }


  /**
   * Expands this and all child tree items.
   */
  public void expand() {
    if (isExpanded()) {
      collapse();
    }
    expand(new HashSet<>());
  }


  /**
   * Collapses this and all child tree items.
   */
  public void collapse() {
    collapse(new HashSet<>());
  }


  /**
   * Recursively expands given item and all child items.
   *
   * @param pdoSet holds already expanded PDOs to detect recursion loops
   */
  protected void expand(Set<T> pdoSet) {
    if (getGuiProvider().providesTreeChildObjects()) {
      if (pdoSet.add(getPdo())) {
        setExpanded(true);
        for (TreeItem<T> childItem : getChildren()) {
          ((PdoTreeItem<T>) childItem).expand(pdoSet);
        }
      }
    }
  }


  /**
   * Recursively collapses given item and all child items.
   *
   * @param pdoSet holds already collapsed PDOs to detect recursion loops
   */
  protected void collapse(Set<T> pdoSet) {
    if (pdoSet.add(getPdo())) {
      setExpanded(false);
      for (TreeItem<T> childItem : getChildren()) {
        ((PdoTreeItem<T>) childItem).collapse(pdoSet);
      }
    }
  }

}

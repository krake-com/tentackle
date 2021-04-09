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

import javafx.scene.Scene;
import javafx.scene.control.TreeItem;

import org.tentackle.common.Service;
import org.tentackle.fx.FxUtilities;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * {@link FxUtilities} for RDC.
 */
@Service(FxUtilities.class)
public class RdcFxUtilities extends FxUtilities {

  @Override
  public void applyStylesheets(Scene scene) {
    super.applyStylesheets(scene);
    scene.getStylesheets().add("/org/tentackle/fx/rdc/translate/pdo-style.css");
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public <T> boolean isValueInParentPath(TreeItem<T> treeItem) {
    if (treeItem instanceof PdoTreeItem) {
      return isPdoTypeInParentPath((PdoTreeItem) treeItem);
    }
    return super.isValueInParentPath(treeItem);
  }

  /**
   * Sames as {@link #isValueInParentPath(TreeItem)} but for PDO tree items.
   *
   * @param treeItem the PDO tree item
   * @param <T> the PDO type
   * @return true if PDO type is in path
   */
  protected <T extends PersistentDomainObject<T>> boolean isPdoTypeInParentPath(PdoTreeItem<T> treeItem) {
    // we limit by PDO type instead of PDO identity.
    // Depending on the application and the implementations of GuiProvider#getTreeChildObjects,
    // the expansion may take far too long to traverse the database.
    boolean inPath = false;
    T pdo = treeItem.getValue();
    if (pdo != null) {
      TreeItem<T> parentItem = treeItem.getParent();
      while (parentItem != null) {
        T parentPdo = parentItem.getValue();
        if (parentPdo != null && parentPdo.getClass() == pdo.getClass()) {
          inPath = true;
          break;
        }
        parentItem = parentItem.getParent();
      }
    }
    return inPath;
  }

}

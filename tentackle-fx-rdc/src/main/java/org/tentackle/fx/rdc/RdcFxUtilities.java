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

import org.tentackle.common.Service;
import org.tentackle.fx.FxUtilities;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.scene.control.TreeItem;

/**
 * {@link FxUtilities} for RDC.<br>
 * Limits the recursive expansion of PDO trees by PDO type.
 */
@Service(FxUtilities.class)
public class RdcFxUtilities extends FxUtilities {

  /**
   * Creates the RDC FX utilities.
   */
  public RdcFxUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
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
   * Same as {@link #isValueInParentPath(TreeItem)} but for PDO tree items.<br>
   * Compares the PDO classes instead of the PDOs themselves, i.e. a PDO is considered to be in the
   * parent path if any parent item holds a PDO of the same class.
   *
   * @param treeItem the PDO tree item
   * @param <T> the PDO type
   * @return true if the PDO type is in the path
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

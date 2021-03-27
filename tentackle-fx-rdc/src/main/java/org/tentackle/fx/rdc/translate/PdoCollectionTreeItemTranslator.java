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

package org.tentackle.fx.rdc.translate;

import javafx.scene.control.TreeItem;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.component.FxTreeView;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.translate.AbstractValueTranslator;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * PDO to TreeItem translator for {@link FxTreeView} and {@link FxTreeTableView}.<br>
 * Used if the model provides a collection of PDOs.
 *
 * @author harald
 * @param <T> the PDO type
 */
@ValueTranslatorService(modelClass = Collection.class, viewClass = TreeItem.class)
public class PdoCollectionTreeItemTranslator<T extends PersistentDomainObject<T>, C extends List<T>>
    extends AbstractValueTranslator<C, TreeItem<T>> {


  private C pdoCollection;

  /**
   * Creates a translator.
   *
   * @param component the component
   */
  @SuppressWarnings("unchecked")
  public PdoCollectionTreeItemTranslator(FxComponent component) {
    super(component);
    if (component instanceof FxTreeView) {
      FxTreeView<T> treeView = (FxTreeView<T>) component;
      treeView.setShowRoot(false);
      treeView.setCellFactory(Rdc::createTreeCell);
    }
  }

  @Override
  public Function<C, TreeItem<T>> toViewFunction() {
    return m -> {
      pdoCollection = m;
      TreeItem<T> root = new TreeItem<>();    // invisible root (disabled in FxTreeViewDelegate and FxTreeTableViewDelegate)
      if (m != null) {
        for (T pdo : m) {
          root.getChildren().add(Rdc.createTreeItem(pdo));
        }
      }
      return root;
    };
  }

  @Override
  @SuppressWarnings("unchecked")
  public Function<TreeItem<T>, C> toModelFunction() {
    return v -> {
      if (pdoCollection != null) {
        pdoCollection.clear();
        TreeItem<T> root;
        if (getComponent() instanceof FxTreeView) {
          root = ((FxTreeView) getComponent()).getRoot();
        }
        else if (getComponent() instanceof FxTreeTableView) {
          root = ((FxTreeTableView) getComponent()).getRoot();
        }
        else {
          root = null;
        }
        if (root != null) {
          for (TreeItem<T> treeItem : root.getChildren()) {
            pdoCollection.add(treeItem.getValue());
          }
        }
      }
      return pdoCollection;
    };
  }

}

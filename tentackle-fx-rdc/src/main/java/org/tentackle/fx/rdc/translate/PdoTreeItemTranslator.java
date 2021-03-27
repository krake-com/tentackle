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
import org.tentackle.fx.component.FxTreeView;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.translate.AbstractValueTranslator;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.function.Function;

/**
 * PDO to TreeItem translator for {@link org.tentackle.fx.component.FxTreeView} and
 * {@link org.tentackle.fx.component.FxTreeTableView}.<br>
 * Used if the model provides a single PDO object.
 *
 * @author harald
 * @param <T> the PDO type
 */
@ValueTranslatorService(modelClass = PersistentDomainObject.class, viewClass = TreeItem.class)
public class PdoTreeItemTranslator<T extends PersistentDomainObject<T>> extends AbstractValueTranslator<T, TreeItem<T>> {

  /**
   * Creates a translator.
   *
   * @param component the component
   */
  @SuppressWarnings("unchecked")
  public PdoTreeItemTranslator(FxComponent component) {
    super(component);
    if (component instanceof FxTreeView) {
      FxTreeView<T> treeView = (FxTreeView<T>) component;
      treeView.setShowRoot(true);
      treeView.setCellFactory(Rdc::createTreeCell);
    }
  }

  @Override
  public Function<T, TreeItem<T>> toViewFunction() {
    return Rdc::createTreeItem;
  }

  @Override
  public Function<TreeItem<T>, T> toModelFunction() {
    return TreeItem::getValue;
  }

}

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

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.component.FxTreeView;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.translate.TreeItemTranslator;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.scene.control.TreeItem;

/**
 * PDO-aware TreeItem translator for {@link org.tentackle.fx.component.FxTreeView} and
 * {@link org.tentackle.fx.component.FxTreeTableView}.<br>
 * The tree cells or table configuration are provided by the PDO's {@link GuiProvider}.
 *
 * @author harald
 * @param <T> the PDO type
 */
@ValueTranslatorService(modelClass = PersistentDomainObject.class, viewClass = TreeItem.class)
public class PdoTreeItemTranslator<T extends PersistentDomainObject<T>> extends TreeItemTranslator<T> {

  /**
   * Creates a translator.
   *
   * @param component the component
   */
  public PdoTreeItemTranslator(FxComponent component) {
    super(component);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void configureComponent() {
    FxComponent component = getComponent();
    Class<?> elemClass = component.getType();
    if (elemClass != null && PersistentDomainObject.class.isAssignableFrom(elemClass)) {
      GuiProvider<T> provider = Rdc.createGuiProvider(Pdo.create((Class<T>) elemClass));
      if (component instanceof FxTreeView) {
        FxTreeView<T> treeView = (FxTreeView<T>) component;
        treeView.setCellFactory(provider.getTreeCellFactory());
      }
      else if (component instanceof FxTreeTableView) {
        FxTreeTableView<T> treeTableView = (FxTreeTableView<T>) component;
        configureTreeTableView(treeTableView, provider.createTableConfiguration());
      }
    }
    else {
      super.configureComponent();
    }
  }

}
